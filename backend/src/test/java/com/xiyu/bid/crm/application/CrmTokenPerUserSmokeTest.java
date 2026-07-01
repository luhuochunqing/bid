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

    @AfterEach
    void cleanupCache() {
        // 测试间清缓存避免互相污染（不删除 DB 数据）
        try {
            crmAuthService.logout();
        } catch (RuntimeException ignored) {
            // 清理失败不影响下一个测试
        }
    }

    // ===== 前置条件：CRM 凭据有效 =====

    @Test
    @DisplayName("前置：全局共享 token 可获取（验证 CRM OAuth 凭据已配置且有效）")
    void sharedToken_acquiredSuccessfully() {
        String sharedToken = assertDoesNotThrow(
                () -> crmAuthService.getValidToken(),
                "获取全局共享 token 失败——请检查 CRM OAuth 凭据（XIYU_CRM_OAUTH_USERNAME/PASSWORD）"
                        + "和 generateToken 配置（NICK_NAME/SALES_NO）");

        assertThat(sharedToken)
                .as("全局共享 token 不应为空")
                .isNotBlank();

        System.out.printf("[SMOKE] 全局共享 token 获取成功：%s（前 20 字符）%n",
                sharedToken.substring(0, Math.min(20, sharedToken.length())));
    }

    // ===== CO-152 核心：按用户维度 token =====

    @Test
    @DisplayName("配置了 crm_sales_no 的用户 → 获取专属 token，与全局共享 token 不同")
    void userWithCrmSalesNo_getsDedicatedToken() {
        String sharedToken = acquireSharedTokenOrSkip();

        User user = findFirstUserWithCrmSalesNo();
        assumeTrue(user != null,
                "DB 中没有配置 crm_sales_no 的用户，跳过专属 token 测试"
                        + "（请在组织管理页给某用户配置 CRM 工号后再跑）");

        String userToken = crmAuthService.getValidTokenForUser(user.getUsername());

        assertThat(userToken)
                .as("用户 %s 的专属 token 不应为空", user.getUsername())
                .isNotBlank();
        assertThat(userToken)
                .as("用户 %s (crmSalesNo=%s) 的专属 token 应与全局共享 token 不同"
                        + "（如果相同，可能 CRM 后端未按 salesNo 区分，或 salesNo 配置错误）",
                        user.getUsername(), user.getCrmSalesNo())
                .isNotEqualTo(sharedToken);

        System.out.printf("[SMOKE] 用户 %s (crmSalesNo=%s) 专属 token=%s... ≠ 共享 token=%s...%n",
                user.getUsername(), user.getCrmSalesNo(),
                truncate(userToken), truncate(sharedToken));
    }

    @Test
    @DisplayName("没配 crm_sales_no 的用户 → 回退全局共享 token")
    void userWithoutCrmSalesNo_fallsBackToShared() {
        String sharedToken = acquireSharedTokenOrSkip();

        User user = findFirstUserWithoutCrmSalesNo();
        assumeTrue(user != null,
                "DB 中所有用户都配了 crm_sales_no，跳过 fallback 测试");

        String userToken = crmAuthService.getValidTokenForUser(user.getUsername());

        assertThat(userToken)
                .as("没配 crm_sales_no 的用户 %s 应回退全局共享 token", user.getUsername())
                .isEqualTo(sharedToken);

        System.out.printf("[SMOKE] 用户 %s (无 crmSalesNo) 回退共享 token=%s...%n",
                user.getUsername(), truncate(userToken));
    }

    @Test
    @DisplayName("两个不同 crm_sales_no 的用户 → 获取不同的专属 token（用户隔离验证）")
    void twoUsersWithDifferentSalesNo_getDifferentTokens() {
        acquireSharedTokenOrSkip();

        List<User> usersWithSalesNo = userRepository.findAll().stream()
                .filter(u -> u.getCrmSalesNo() != null && !u.getCrmSalesNo().isBlank())
                .toList();
        assumeTrue(usersWithSalesNo.size() >= 2,
                "DB 中配置 crm_sales_no 的用户不足 2 个，跳过隔离测试");

        User userA = usersWithSalesNo.get(0);
        User userB = usersWithSalesNo.get(1);
        assumeTrue(!userA.getCrmSalesNo().equals(userB.getCrmSalesNo()),
                "两个测试用户的 crm_salesNo 相同，跳过隔离测试");

        // 清缓存确保重新获取
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
        acquireSharedTokenOrSkip();

        User user = findFirstUserWithCrmSalesNo();
        assumeTrue(user != null,
                "DB 中没有配置 crm_sales_no 的用户，跳过商机接口联通测试");

        // 构造最小查询请求（空 body = 查全量第一页 10 条）
        CustomerChancePageRequest request = new CustomerChancePageRequest(
                1, 10,
                new CustomerChanceDTO(
                        null, null, null, null, null, null,
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
        acquireSharedTokenOrSkip();

        User user = findFirstUserWithoutCrmSalesNo();
        assumeTrue(user != null,
                "DB 中所有用户都配了 crm_sales_no，跳过 fallback 联通测试");

        CustomerChancePageRequest request = new CustomerChancePageRequest(
                1, 10,
                new CustomerChanceDTO(
                        null, null, null, null, null, null,
                        null, null, null, null, null, null,
                        null, null, null, null, null
                )
        );

        CrmChancePageResult result = assertDoesNotThrow(
                () -> crmChanceService.pageList(request, user.getUsername()),
                "调用商机接口失败——fallback token 可能无效");

        assertThat(result).isNotNull();

        System.out.printf("[SMOKE] 用户 %s (无 crmSalesNo) 用 fallback token 调用商机接口成功，返回 %d 条%n",
                user.getUsername(), result.list().size());
    }

    // ===== Helper =====

    /** 获取全局共享 token，失败则跳过整个测试（CRM 凭据未配置）。 */
    private String acquireSharedTokenOrSkip() {
        try {
            String token = crmAuthService.getValidToken();
            assumeTrue(token != null && !token.isBlank(),
                    "全局共享 token 为空——CRM OAuth 凭据未配置，跳过测试");
            return token;
        } catch (RuntimeException e) {
            assumeTrue(false, "CRM OAuth 凭据未配置或无效，跳过测试: " + e.getMessage());
            return null; // unreachable
        }
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
