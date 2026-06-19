// Input: ApplicationContext 中的 OrganizationDirectoryGateway bean 列表
// Output: 当未配置真实网关时注册 NoOpOrganizationDirectoryGateway Bean 作为 fallback
// Pos: integration/organization - 组织同步网关 fallback 注册
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.integration.organization.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 当没有真实 {@link OrganizationDirectoryGateway} 实现可用时，
 * （例如未配置 {@code xiyu.integrations.organization.directory.base-url}），
 * 注册 {@link NoOpOrganizationDirectoryGateway} 作为兜底，避免
 * {@link OrganizationDirectorySyncAppService} 等依赖 bean 启动失败。
 * <p>
 * 注：原 NoOp 类标在 {@code @Component} 上 + {@code @ConditionalOnMissingBean}，
 * 但 {@code @ConditionalOnMissingBean} 在 component scan 阶段不生效（只对
 * {@code @Bean} 工厂方法生效），导致生产与测试都拿不到 fallback。
 * 本 {@code @Configuration} + {@code @Bean} 形式让条件装配真正生效。
 *
 * <p>
 * <b>状态说明（2026-06）</b>：当前所有 service
 * （OssRoleMenuPermissionAutoSync / OrganizationRoleMenuSyncAppService /
 * OrganizationDirectorySyncAppService / OrganizationSyncRunAppService /
 * OrganizationManualResyncAppService）均已使用 {@code ObjectProvider<OrganizationDirectoryGateway>}
 * 延迟解析 + {@code getIfAvailable()} 显式判空，自行处理 gateway 不可用场景。
 * NoOp Bean 在此场景下实际是 dead code（service 拿到 null，不调用 NoOp）。
 * <p>
 * 保留 NoOp 作为 <b>防御性安全网</b>：未来新加 service 如果漏用 ObjectProvider 直接
 * {@code @Autowired OrganizationDirectoryGateway}，NoOp 让 Spring Context 仍能启动，
 * 避免 233 个 @SpringBootTest 级联失败。删除前需审计所有 gateway 注入路径。
 */
@Configuration
public class NoOpOrganizationDirectoryGatewayConfig {

    @Bean
    @ConditionalOnMissingBean(OrganizationDirectoryGateway.class)
    public NoOpOrganizationDirectoryGateway noOpOrganizationDirectoryGateway() {
        return new NoOpOrganizationDirectoryGateway();
    }
}