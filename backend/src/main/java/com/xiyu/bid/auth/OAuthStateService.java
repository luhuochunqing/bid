package com.xiyu.bid.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage OAuth2 state tokens for CSRF protection.
 * Uses Redis if available, otherwise falls back to in-memory map.
 */
@Service
@Slf4j
public class OAuthStateService {

    /** Redis template for distributed state storage. */
    private final StringRedisTemplate redisTemplate;

    /** Local fallback map for environments without Redis. */
    private final ConcurrentHashMap<String, Instant> localMap =
            new ConcurrentHashMap<>();

    /** Redis key prefix for OAuth states. */
    private static final String REDIS_PREFIX = "oauth_state:";

    /** Time-to-live for state tokens. */
    private static final Duration TTL = Duration.ofMinutes(10);

    /**
     * 消息推送场景 state 前缀。
     * <p>识别此前缀的 state 时只验证不删除，允许用户在 TTL 内多次点击同一条消息。
     * <p>不使用固定值（如 "msg"），每条消息的 state 都是 UUID，避免 Session Fixation 风险
     * （攻击者无法预先获取有效 state，无法构造钓鱼链接让受害者登录攻击者账号）。
     */
    public static final String MESSAGE_STATE_PREFIX = "msg:";

    /** 消息推送 state 的 TTL（7 天，覆盖用户休假场景）。 */
    private static final Duration MESSAGE_TTL = Duration.ofDays(7);

    /**
     * 企微工作台应用主页入口 state 前缀。
     * <p>用于企微工作台点击应用图标直接登录的场景。应用主页配置为 OAuth 授权链接
     * （按企微开发文档公式构造），state 使用固定值（如 {@code entry:workbench}），
     * 避免每次访问都需要先调后端获取动态 state。
     * <p>识别此前缀的 state 时直接返回 true（不删除，因为是固定值，可重复使用）。
     * <p>CSRF 风险评估：攻击者构造钓鱼链接只能让受害者走完企微 OAuth 后登录到我们系统，
     * 不会泄露敏感信息或登录到攻击者账号；且 code 必须是企微生成的一次性有效值，
     * 攻击者无法伪造，风险可接受。
     */
    public static final String WORKBENCH_ENTRY_PREFIX = "entry:";

    /** Delay for local map cleanup task. */
    private static final long CLEAN_DELAY = 60000;

    /**
     * Constructor for OAuthStateService.
     *
     * @param redisTemplateParam Optional Redis template
     */
    public OAuthStateService(
            @Autowired(required = false)
            final StringRedisTemplate redisTemplateParam
    ) {
        this.redisTemplate = redisTemplateParam;
    }

    /**
     * Stores a state token with a 10-minute expiration.
     *
     * @param state CSRF state token to store
     */
    public void storeState(final String state) {
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(REDIS_PREFIX + state, "true",
                        TTL);
                return;
            } catch (Exception e) {
                log.warn("Failed to store OAuth state in Redis, falling back",
                        e);
            }
        }
        localMap.put(state, Instant.now());
    }

    /**
     * 为消息推送场景生成并存储一次性 state（7 天 TTL）。
     * <p>消息推送是异步行为（用户可能几小时/几天后点击），普通 state 的 10 分钟 TTL 不适用。
     * <p>state 格式：{@code msg:<uuid>}，每条消息独立，避免 Session Fixation 风险。
     * <p>验证时由 {@link #validateAndRemoveState(String)} 识别前缀，只验证不删除，
     * 允许用户在 TTL 内多次点击同一条消息（如刷新回调页）。
     *
     * @return 新生成的 state 值（带 {@link #MESSAGE_STATE_PREFIX} 前缀）
     */
    public String storeStateForMessage() {
        String state = MESSAGE_STATE_PREFIX
                + UUID.randomUUID().toString().replace("-", "");
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(REDIS_PREFIX + state, "message",
                        MESSAGE_TTL);
                return state;
            } catch (RuntimeException e) {
                log.warn("Failed to store message OAuth state in Redis, falling back",
                        e);
            }
        }
        localMap.put(state, Instant.now());
        return state;
    }

    /**
     * Validates and removes a state token.
     * <p>对 {@link #MESSAGE_STATE_PREFIX} 前缀的 state（消息推送场景），只验证存在性不删除，
     * 允许用户在 TTL 内多次点击同一条消息。其他 state 验证后立即删除（一次性）。
     *
     * @param state CSRF state token to validate
     * @return true if state is valid and not expired
     */
    public boolean validateAndRemoveState(final String state) {
        if (state == null || state.isBlank()) {
            return false;
        }

        // 消息推送 state：只验证不删除（允许同一条消息多次点击）
        if (state.startsWith(MESSAGE_STATE_PREFIX)) {
            return validateMessageState(state);
        }

        // 工作台入口 state：固定值直接通过（不删除，可重复使用）
        if (state.startsWith(WORKBENCH_ENTRY_PREFIX)) {
            log.info("Workbench entry state accepted: {}", state);
            return true;
        }

        if (redisTemplate != null) {
            try {
                Boolean deleted = redisTemplate.delete(REDIS_PREFIX + state);
                return Boolean.TRUE.equals(deleted);
            } catch (Exception e) {
                log.warn("Failed to validate OAuth state, checking map",
                        e);
            }
        }

        Instant timestamp = localMap.remove(state);
        if (timestamp == null) {
            return false;
        }

        return Duration.between(timestamp, Instant.now())
                .compareTo(TTL) <= 0;
    }

    /**
     * 验证消息推送 state 是否存在且未过期（不删除）。
     */
    private boolean validateMessageState(final String state) {
        if (redisTemplate != null) {
            try {
                Boolean exists = redisTemplate.hasKey(REDIS_PREFIX + state);
                return Boolean.TRUE.equals(exists);
            } catch (RuntimeException e) {
                log.warn("Failed to validate message OAuth state, checking map",
                        e);
            }
        }
        Instant timestamp = localMap.get(state);
        if (timestamp == null) {
            return false;
        }
        return Duration.between(timestamp, Instant.now())
                .compareTo(MESSAGE_TTL) <= 0;
    }

    /**
     * Periodically clean expired states from local map.
     */
    @Scheduled(fixedDelay = CLEAN_DELAY)
    public void cleanExpiredLocalStates() {
        if (localMap.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        int removedCount = 0;

        var iterator = localMap.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (Duration.between(entry.getValue(), now).compareTo(TTL) > 0) {
                iterator.remove();
                removedCount++;
            }
        }

        if (removedCount > 0) {
            log.debug("Cleaned up {} expired local OAuth states", removedCount);
        }
    }
}
