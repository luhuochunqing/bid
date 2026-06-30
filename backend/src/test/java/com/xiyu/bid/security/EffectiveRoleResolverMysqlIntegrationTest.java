package com.xiyu.bid.security;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.security.domain.EffectiveRoleResult;
import com.xiyu.bid.support.AbstractMysqlIntegrationTest;
import com.xiyu.bid.support.InMemoryRoleCodeCachePortConfig;
import com.xiyu.bid.support.InMemoryRoleCodeCachePortConfig.InMemoryRoleCodeCachePort;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * EffectiveRoleResolver 真实 MySQL 集成测试。
 *
 * <p>覆盖 CO-373 根因场景：OSS 用户 role_id=NULL 时实体回退 "manager" 导致权限误判。
 * 既有 {@link EffectiveRoleResolverTest}（Mockito unit test）验证决策逻辑，
 * 本测试验证真实 MySQL 下 users + roles 表 round-trip + 缓存端口协作。
 *
 * <p>不使用 disabledWithoutDocker=true，Docker 不可用时 fail-fast。
 *
 * <p>测试数据策略：
 * <ul>
 *   <li>用 JPA repository 创建 RoleProfile + User，避免硬编码 ID</li>
 *   <li>清理用 username LIKE 'test-int-' / code LIKE 'test-int-' 模式匹配</li>
 *   <li>测试方法不加 @Transactional，让 resolver 真实读 DB（虽然 resolve 本身无 @Transactional，
 *       但被测代码若在事务中调用，行为应一致）</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
@ActiveProfiles("flyway-mysql")
@Import({
        NoOpPasswordEncryptionTestConfig.class,
        InMemoryRoleCodeCachePortConfig.class
})
class EffectiveRoleResolverMysqlIntegrationTest extends AbstractMysqlIntegrationTest {

    @Autowired
    private EffectiveRoleResolver effectiveRoleResolver;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleProfileRepository roleProfileRepository;

    /** 注入共享的 InMemory stub，直接调用 clear()/put() 无需类型转换。 */
    @Autowired
    private InMemoryRoleCodeCachePort roleCodeCachePort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTestData() {
        // 顺序：先删 users（FK 引用 roles），再删 roles
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'test-int-%'");
        jdbcTemplate.update("DELETE FROM roles WHERE code LIKE 'test-int-%'");
        // 清空缓存 stub，避免上一个测试的缓存污染
        roleCodeCachePort.clear();
    }

    // ── 辅助方法 ──

    private void putCache(String username, String roleCode) {
        roleCodeCachePort.put(username, roleCode);
    }

    /**
     * 创建并落库一个 RoleProfile。
     *
     * @param code 角色码（自动加 'test-int-' 前缀，便于清理）
     */
    private RoleProfile createRoleProfile(String code, String name) {
        return roleProfileRepository.saveAndFlush(RoleProfile.builder()
                .code(code)
                .name(name)
                .description("集成测试角色")
                .isSystem(false)
                .enabled(true)
                .dataScope("self")
                .build());
    }

    /**
     * 创建并落库一个 User，可指定 roleProfile 与 externalOrgSourceApp。
     *
     * @param usernameSuffix 用户名后缀（自动加 'test-int-' 前缀）
     * @param roleProfile    关联角色；null 表示 role_id=NULL（OSS 用户场景）
     * @param externalOrgSourceApp 外部组织来源 app；null 表示本地用户
     */
    private User createAndSaveUser(String usernameSuffix, RoleProfile roleProfile, String externalOrgSourceApp) {
        String username = "test-int-" + usernameSuffix;
        User user = User.builder()
                .username(username)
                .password("dummy-password")
                .email(username + "@test.local")
                .fullName("测试用户-" + usernameSuffix)
                .role(User.Role.MANAGER)  // role 字段 not null，OSS 用户也是 MANAGER
                .roleProfile(roleProfile)
                .externalOrgSourceApp(externalOrgSourceApp)
                .enabled(true)
                .emailVerified(true)
                .build();
        return userRepository.saveAndFlush(user);
    }

    // ════════════════════════════════════════════════════════════════════
    //  T003: CO-373 OSS 用户 role_id=NULL fail-closed
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CO-373: OSS 用户 role_id=NULL fail-closed")
    class OssUserWithNullRoleId {

        @Test
        @DisplayName("缓存 miss 时返回 null（fail-closed，不回退 manager）")
        void ossUserWithNullRoleId_cacheMiss_returnsNullAndFailClosedSource() {
            // given: OSS 用户 role_id=NULL（roleProfile=null），缓存无数据
            User user = createAndSaveUser("oss-001", null, "ehsy-oss");
            flushAndClear();

            // when: 缓存未命中
            EffectiveRoleResult result = effectiveRoleResolver.resolve(user);

            // then: fail-closed 返回 null，绝不回退 "manager"
            assertNull(result.roleCode(),
                    "OSS 用户缓存 miss 时必须 fail-closed 返回 null，不得回退 manager");
            assertEquals(EffectiveRoleResult.Source.CACHE_MISS_FAIL_CLOSED, result.source());
        }

        @Test
        @DisplayName("缓存命中时返回缓存值（绕过实体 role_id=NULL）")
        void ossUserWithNullRoleId_cacheHit_returnsCachedValue() {
            // given: 同样 role_id=NULL 的 OSS 用户，但缓存有值
            User user = createAndSaveUser("oss-002", null, "ehsy-oss");
            putCache(user.getUsername(), "bid-Team");
            flushAndClear();

            // when
            EffectiveRoleResult result = effectiveRoleResolver.resolve(user);

            // then: 返回缓存值，source=CACHE_HIT
            assertEquals("bid-Team", result.roleCode());
            assertEquals(EffectiveRoleResult.Source.CACHE_HIT, result.source());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  T004: OSS 用户缓存 miss（修正：policy 不读实体，直接 fail-closed）
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("OSS 用户缓存 miss：即使实体 role_id 非空也 fail-closed")
    class OssUserCacheMissDoesNotReadEntity {

        @Test
        @DisplayName("OSS 用户 role_id 非空但缓存 miss → 仍返回 null（不读实体）")
        void ossUserWithRealRoleId_cacheMiss_returnsNullAndFailClosedSource() {
            // given: OSS 用户 role_id 指向真实 RoleProfile（非 NULL）
            RoleProfile rp = createRoleProfile("test-int-role-001", "集成测试角色A");
            User user = createAndSaveUser("oss-003", rp, "ehsy-oss");
            flushAndClear();

            // when: 缓存未命中
            EffectiveRoleResult result = effectiveRoleResolver.resolve(user);

            // then: policy 设计——OSS 用户缓存 miss 时直接 fail-closed，不读实体 role_id
            // 这是防止 OSS 用户用陈旧的本地 role_id 提权的安全设计
            assertNull(result.roleCode(),
                    "OSS 用户缓存 miss 时即使实体 role_id 非空也必须 fail-closed，不读实体");
            assertEquals(EffectiveRoleResult.Source.CACHE_MISS_FAIL_CLOSED, result.source());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  T005: 本地用户（external_org_source_app 为 null/blank）回退实体
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("本地用户：缓存 miss 回退实体角色码")
    class LocalUserFallback {

        @Test
        @DisplayName("缓存 miss 时返回实体 roleProfile.code（LOCAL_USER）")
        void localUser_cacheMiss_returnsEntityRoleCode() {
            // given: 本地用户（externalOrgSourceApp=null），roleProfile 有值
            RoleProfile rp = createRoleProfile("test-int-role-002", "集成测试角色B");
            User user = createAndSaveUser("local-001", rp, null);
            flushAndClear();

            // when: 缓存未命中
            EffectiveRoleResult result = effectiveRoleResolver.resolve(user);

            // then: 返回实体 roleProfile.code，source=LOCAL_USER
            assertEquals("test-int-role-002", result.roleCode());
            assertEquals(EffectiveRoleResult.Source.LOCAL_USER, result.source());
        }

        @Test
        @DisplayName("缓存命中时返回缓存值（缓存优先于实体）")
        void localUser_cacheHit_returnsCachedValue() {
            // given: 本地用户，但缓存有值（如管理员 SSO 后写入）
            RoleProfile rp = createRoleProfile("test-int-role-003", "集成测试角色C");
            User user = createAndSaveUser("local-002", rp, null);
            putCache(user.getUsername(), "admin");
            flushAndClear();

            // when
            EffectiveRoleResult result = effectiveRoleResolver.resolve(user);

            // then: 缓存优先于实体
            assertEquals("admin", result.roleCode());
            assertEquals(EffectiveRoleResult.Source.CACHE_HIT, result.source());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  T006: external_org_source_app 边界场景（blank/empty/null 归一化为本地用户）
    // ════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("external_org_source_app 边界：blank/empty/null 均视为本地用户")
    class ExternalOrgSourceAppBoundary {

        @Test
        @DisplayName("空格字符串 '   ' 视为本地用户（isBlank 归一化）")
        void blankExternalOrgSourceApp_treatedAsLocalUser() {
            // given: externalOrgSourceApp 为空格字符串
            RoleProfile rp = createRoleProfile("test-int-role-004", "集成测试角色D");
            User user = createAndSaveUser("boundary-001", rp, "   ");
            flushAndClear();

            // when: 缓存未命中
            EffectiveRoleResult result = effectiveRoleResolver.resolve(user);

            // then: blank 归一化为本地用户，返回实体角色码
            assertEquals("test-int-role-004", result.roleCode());
            assertEquals(EffectiveRoleResult.Source.LOCAL_USER, result.source());
        }

        @Test
        @DisplayName("空字符串 '' 视为本地用户")
        void emptyStringExternalOrgSourceApp_treatedAsLocalUser() {
            // given: externalOrgSourceApp 为空字符串
            RoleProfile rp = createRoleProfile("test-int-role-005", "集成测试角色E");
            User user = createAndSaveUser("boundary-002", rp, "");
            flushAndClear();

            // when
            EffectiveRoleResult result = effectiveRoleResolver.resolve(user);

            // then: empty 归一化为本地用户
            assertEquals("test-int-role-005", result.roleCode());
            assertEquals(EffectiveRoleResult.Source.LOCAL_USER, result.source());
        }

        @Test
        @DisplayName("null 视为本地用户")
        void nullExternalOrgSourceApp_treatedAsLocalUser() {
            // given: externalOrgSourceApp 为 null
            RoleProfile rp = createRoleProfile("test-int-role-006", "集成测试角色F");
            User user = createAndSaveUser("boundary-003", rp, null);
            flushAndClear();

            // when
            EffectiveRoleResult result = effectiveRoleResolver.resolve(user);

            // then: null 归一化为本地用户
            assertEquals("test-int-role-006", result.roleCode());
            assertEquals(EffectiveRoleResult.Source.LOCAL_USER, result.source());
        }
    }
}
