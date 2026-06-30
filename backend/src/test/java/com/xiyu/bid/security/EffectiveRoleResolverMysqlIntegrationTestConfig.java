package com.xiyu.bid.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * EffectiveRoleResolver MySQL 集成测试专用配置。
 *
 * <p>提供 {@link RoleCodeCachePort} 的内存 stub，替代真实 crm 包的
 * {@code OssPermissionCache}（依赖 OSS HTTP 调用，不适合集成测试）。
 * 测试可通过 {@link InMemoryRoleCodeCachePort#put(String, String)} 精确控制缓存命中/未命中。
 *
 * <p>使用 {@code @Primary} 覆盖生产 bean，需配合
 * {@code spring.main.allow-bean-definition-overriding=true}（测试类 @SpringBootTest properties）。
 *
 * <p>线程安全：测试方法串行执行，无需同步；如未来并行化可改 ConcurrentHashMap。
 */
@TestConfiguration
public class EffectiveRoleResolverMysqlIntegrationTestConfig {

    @Bean(name = "roleCodeCachePort")
    @Primary
    public RoleCodeCachePort roleCodeCachePort() {
        return new InMemoryRoleCodeCachePort();
    }

    /**
     * 内存实现的 RoleCodeCachePort，用 HashMap 模拟 OSS 权限缓存。
     *
     * <p>测试辅助方法：
     * <ul>
     *   <li>{@link #put} — 模拟 OSS 登录写入缓存</li>
     *   <li>{@link #clear} — 模拟登出/缓存过期（@BeforeEach 必调）</li>
     * </ul>
     */
    public static class InMemoryRoleCodeCachePort implements RoleCodeCachePort {

        private final Map<String, String> cache = new HashMap<>();

        @Override
        public Optional<String> getRoleCode(String username) {
            return Optional.ofNullable(cache.get(username));
        }

        /**
         * 模拟 OSS 登录写入缓存。
         *
         * @param username 用户名
         * @param roleCode 角色码（如 "bid-Team"、"admin"）
         */
        public void put(String username, String roleCode) {
            cache.put(username, roleCode);
        }

        /**
         * 清空缓存，每个测试方法 @BeforeEach 必调，避免上一个测试的缓存污染。
         */
        public void clear() {
            cache.clear();
        }
    }
}
