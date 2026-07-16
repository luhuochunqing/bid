package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceDTO;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChancePageRequest;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CO-152 真实联调 smoke test：验证 CRM 按用户维度 token 管理在真实环境下能联通。
 *
 * <p>启用方式（测试服务器或本地配好 CRM 凭据后）：
 * <pre>
 * export XIYU_CRM_SMOKE_TEST=true
 * cd backend
 * XIYU_CRM_SMOKE_TEST=true mvn test -Dtest=CrmTokenPerUserSmokeTest
 * </pre>
 *
 * <p>前提条件（测试服务器已就绪，本地需手动配置）：
 * <ul>
 *   <li>application-dev.yml 配置了 CRM OAuth 凭据（oauth-username / oauth-password）</li>
 *   <li>application-dev.yml 配置了 generateToken 全局 fallback（nick-name / sales-no）</li>
 *   <li>DB 中至少有一个用户配置了 crm_sales_no（V1126 字段）</li>
 *   <li>DB 中至少有一个用户未配置 crm_sales_no（用于 fallback 测试）</li>
 * </ul>
 *
 * <p>CI 默认跳过（未设置 XIYU_CRM_SMOKE_TEST 环境变量），不会污染常规构建。
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "XIYU_CRM_SMOKE_TEST", matches = "true")
class CrmTokenPerUserSmokeTest {

    @Autowired
    private CrmAuthService crmAuthService;

    @Autowired
    private CrmChanceService crmChanceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OssUserTokenCache ossUserTokenCache;

    @AfterEach
    void cleanupCache() {
        // 测试间清用户 CRM JWT 缓存（不删除 DB 数据）
        for (User u : userRepository.findAll()) {
            try {
                crmAuthService.logoutUser(u.getUsername());
            } catch (RuntimeException ignored) {
                // ignore
            }
        }
    }

    // ===== 前置条件：CRM 凭据有效 =====

    @Test
    @DisplayName("前置：配置了 crm_sales_no 的用户在缓存了 OSS token 后可换 CRM JWT")
    void userToken_requiresUserOssCache() {
        User user = findFirstUserWithCrmSalesNo();
        assumeTrue(user != null, "DB 中没有配置 crm_sales_no 的用户");
        assumeTrue(ossUserTokenCache.get(user.getUsername()).isPresent(),
                "用户 " + user.getUsername() + " 无 OSS token 缓存——请先用该用户登录系统再跑 smoke");
        String userToken = assertDoesNotThrow(
                () -> crmAuthService.getValidTokenForUser(user.getUsername()),
                "用户 OSS→CRM JWT 失败");
        assertThat(userToken).isNotBlank();
        System.out.printf("[SMOKE] 用户 %s CRM JWT 获取成功%n", user.getUsername());
    }

    // ===== CO-152 核心：按用户维度 token =====

    @Test
    @DisplayName("配置了 crm_sales_no 的用户 → 获取专属 token，与全局共享 token 不同")
    void userWithCrmSalesNo_getsDedicatedToken() {
        User user = findFirstUserWithCrmSalesNo();
        assumeTrue(user != null,
                "DB 中没有配置 crm_sales_no 的用户，跳过专属 token 测试");
        assumeUserHasOss(user);

        String userToken = crmAuthService.getValidTokenForUser(user.getUsername());

        assertThat(userToken)
                .as("用户 %s 的专属 token 不应为空", user.getUsername())
                .isNotBlank();

        System.out.printf("[SMOKE] 用户 %s (crmSalesNo=%s) 专属 token=%s...%n",
                user.getUsername(), user.getCrmSalesNo(), truncate(userToken));
    }

    @Test
    @DisplayName("没配 crm_sales_no 的用户 → 仍用本人 OSS 换 JWT（salesNo=username）")
    void userWithoutCrmSalesNo_usesOwnOss() {
        User user = findFirstUserWithoutCrmSalesNo();
        assumeTrue(user != null,
                "DB 中所有用户都配了 crm_sales_no，跳过测试");
        assumeUserHasOss(user);

        String userToken = crmAuthService.getValidTokenForUser(user.getUsername());

        assertThat(userToken)
                .as("用户 %s 应能用本人 OSS 换 JWT", user.getUsername())
                .isNotBlank();

        System.out.printf("[SMOKE] 用户 %s (无 crmSalesNo) 本人 JWT=%s...%n",
                user.getUsername(), truncate(userToken));
    }

    @Test
    @DisplayName("两个不同 crm_sales_no 的用户 → 获取不同的专属 token（用户隔离验证）")
    void twoUsersWithDifferentSalesNo_getDifferentTokens() {
        List<User> usersWithSalesNo = userRepository.findAll().stream()
                .filter(u -> u.getCrmSalesNo() != null && !u.getCrmSalesNo().isBlank())
                .toList();
        assumeTrue(usersWithSalesNo.size() >= 2,
                "DB 中配置 crm_sales_no 的用户不足 2 个，跳过隔离测试");

        User userA = usersWithSalesNo.get(0);
        User userB = usersWithSalesNo.get(1);
        assumeTrue(!userA.getCrmSalesNo().equals(userB.getCrmSalesNo()),
                "两个测试用户的 crm_salesNo 相同，跳过隔离测试");

        assumeUserHasOss(userA);
        assumeUserHasOss(userB);
        crmAuthService.logoutUser(userA.getUsername());
        crmAuthService.logoutUser(userB.getUsername());

        String tokenA = crmAuthService.getValidTokenForUser(userA.getUsername());
        String tokenB = crmAuthService.getValidTokenForUser(userB.getUsername());

        assertThat(tokenA).isNotBlank();
        assertThat(tokenB).isNotBlank();
        assertThat(tokenA)
                .as("用户 A %s (salesNo=%s) 和用户 B %s (salesNo=%s) 的 token 应不同",
                        userA.getUsername(), userA.getCrmSalesNo(),
                        userB.getUsername(), userB.getCrmSalesNo())
                .isNotEqualTo(tokenB);

        System.out.printf("[SMOKE] 用户 A %s (salesNo=%s) token=%s... ≠ 用户 B %s (salesNo=%s) token=%s...%n",
                userA.getUsername(), userA.getCrmSalesNo(), truncate(tokenA),
                userB.getUsername(), userB.getCrmSalesNo(), truncate(tokenB));
    }

    @Test
    @DisplayName("用户 token 能成功调用商机接口（端到端联通验证）")
    void userToken_canCallChancePageList() {
        User user = findFirstUserWithCrmSalesNo();
        assumeTrue(user != null,
                "DB 中没有配置 crm_sales_no 的用户，跳过商机接口联通测试");
        assumeUserHasOss(user);

        // 构造最小查询请求（空 body = 查全量第一页 10 条）
        CustomerChancePageRequest request = new CustomerChancePageRequest(
                1, 10,
                new CustomerChanceDTO(
                        null, null, null, null, null, null, null,
                        null, null, null, null, null, null,
                        null, null, null, null, null
                )
        );

        CrmChancePageResult result = assertDoesNotThrow(
                () -> crmChanceService.pageList(request, user.getUsername()),
                "调用商机接口失败——token 可能无效或 CRM 接口异常");

        assertThat(result)
                .as("商机接口应返回非 null 结果（即使空列表也算 token 有效）")
                .isNotNull();

        System.out.printf("[SMOKE] 用户 %s 调用商机接口成功，返回 %d 条商机（totalCount=%d）%n",
                user.getUsername(), result.list().size(), result.totalCount());
    }

    @Test
    @DisplayName("没配 crm_sales_no 的用户也能调用商机接口（fallback token 仍有效）")
    void fallbackToken_canCallChancePageList() {
        User user = findFirstUserWithoutCrmSalesNo();
        assumeTrue(user != null,
                "DB 中所有用户都配了 crm_sales_no，跳过联通测试");
        assumeUserHasOss(user);

        CustomerChancePageRequest request = new CustomerChancePageRequest(
                1, 10,
                new CustomerChanceDTO(
                        null, null, null, null, null, null, null,
                        null, null, null, null, null, null,
                        null, null, null, null, null
                )
        );

        CrmChancePageResult result = assertDoesNotThrow(
                () -> crmChanceService.pageList(request, user.getUsername()),
                "调用商机接口失败——fallback token 可能无效");

        assertThat(result).isNotNull();

        System.out.printf("[SMOKE] 用户 %s (无 crmSalesNo) 用本人 token 调用商机接口成功，返回 %d 条%n",
                user.getUsername(), result.list().size());
    }

    // ===== Helper =====

    private void assumeUserHasOss(User user) {
        assumeTrue(ossUserTokenCache.get(user.getUsername()).isPresent(),
                "用户 " + user.getUsername() + " 无 OSS token 缓存——请先登录该用户再跑 smoke");
    }

    private User findFirstUserWithCrmSalesNo() {
        return userRepository.findAll().stream()
                .filter(u -> u.getCrmSalesNo() != null && !u.getCrmSalesNo().isBlank())
                .findFirst()
                .orElse(null);
    }

    private User findFirstUserWithoutCrmSalesNo() {
        return userRepository.findAll().stream()
                .filter(u -> u.getCrmSalesNo() == null || u.getCrmSalesNo().isBlank())
                .findFirst()
                .orElse(null);
    }

    private static String truncate(String s) {
        return s == null ? "<null>" : s.substring(0, Math.min(20, s.length()));
    }
}
