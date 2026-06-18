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
 */
@Configuration
public class NoOpOrganizationDirectoryGatewayConfig {

    @Bean
    @ConditionalOnMissingBean(OrganizationDirectoryGateway.class)
    public NoOpOrganizationDirectoryGateway noOpOrganizationDirectoryGateway() {
        return new NoOpOrganizationDirectoryGateway();
    }
}