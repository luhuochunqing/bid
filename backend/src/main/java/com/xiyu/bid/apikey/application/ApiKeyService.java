package com.xiyu.bid.apikey.application;

import com.xiyu.bid.apikey.entity.ApiKey;
import com.xiyu.bid.apikey.entity.ApiKeyStatus;
import com.xiyu.bid.apikey.infrastructure.ApiKeyRepository;
import com.xiyu.bid.util.DigestUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApiKeyService {

    private static final int RAW_KEY_BYTES = 32;
    private static final String KEY_PREFIX = "xiyu_sk_";

    private final ApiKeyRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 创建 API Key。返回含明文 secret 的结果（仅此一次可获取）。 */
    public CreateResult create(String name, List<String> scopes, String createdBy, LocalDateTime expiresAt) {
        String rawSecret = generateRawSecret();
        String keyHash = sha256(rawSecret);
        String scopesJson = String.join(",", scopes);

        ApiKey key = ApiKey.builder()
                .name(name)
                .keyHash(keyHash)
                .scopes(scopesJson)
                .status(ApiKeyStatus.ACTIVE)
                .createdBy(createdBy)
                .expiresAt(expiresAt)
                .build();
        ApiKey saved = repository.save(key);
        log.info("Created API Key id={} name={} scopes={}", saved.getId(), name, scopesJson);
        return new CreateResult(saved.getId(), rawSecret, name, scopes, saved.getExpiresAt());
    }

    /** 根据请求头中的明文 Key 查找有效的 ApiKey。返回 empty 表示无效。 */
    @Transactional(readOnly = true)
    public Optional<ApiKey> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return Optional.empty();
        String hash = sha256(rawKey.trim());
        return repository.findByKeyHash(hash)
                .filter(this::isActive);
    }

    public Optional<ApiKey> findById(Long id) {
        return repository.findById(id);
    }

    public List<ApiKey> listAll() {
        return repository.findAll();
    }

    public void disable(Long id) {
        repository.findById(id).ifPresent(key -> {
            key.setStatus(ApiKeyStatus.DISABLED);
            repository.save(key);
            log.info("Disabled API Key id={}", id);
        });
    }

    public void enable(Long id) {
        repository.findById(id).ifPresent(key -> {
            key.setStatus(ApiKeyStatus.ACTIVE);
            repository.save(key);
            log.info("Enabled API Key id={}", id);
        });
    }

    public void delete(Long id) {
        repository.deleteById(id);
        log.info("Deleted API Key id={}", id);
    }

    private boolean isActive(ApiKey key) {
        if (key.getStatus() != ApiKeyStatus.ACTIVE) return false;
        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) {
            key.setStatus(ApiKeyStatus.EXPIRED);
            repository.save(key);
            return false;
        }
        return true;
    }

    private String generateRawSecret() {
        byte[] bytes = new byte[RAW_KEY_BYTES];
        secureRandom.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(final String input) {
        return DigestUtils.sha256(input);
    }

    public record CreateResult(Long id, String secret, String name, List<String> scopes, LocalDateTime expiresAt) {}
}
