package com.xiyu.bid.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI xiyuBidOpenAPI() {
        SecurityScheme jwtScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT Bearer Token（Web 用户认证）");

        SecurityScheme apiKeyScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-API-Key")
                .description("API Key（第三方系统对接认证）");

        return new OpenAPI()
                .info(new Info()
                        .title("西域数智化投标管理平台 API")
                        .version("1.0")
                        .description("""
                                投标管理系统的 REST API 文档。
                                认证方式：
                                - **JWT**: 用于 Web 前端用户，通过 `/api/auth/login` 获取
                                - **API Key**: 用于第三方系统（CRM）对接，通过管理后台 `/api/admin/api-keys` 创建
                                """)
                        .contact(new Contact().name("西域投标平台团队"))
                        .license(new License().name("内部使用")))
                .servers(List.of(
                        new Server().url("http://127.0.0.1:18080").description("本地开发"),
                        new Server().url("/").description("生产环境")
                ))
                .components(new Components()
                        .addSecuritySchemes("JWT", jwtScheme)
                        .addSecuritySchemes("API-Key", apiKeyScheme))
                .addSecurityItem(new SecurityRequirement().addList("JWT"))
                .addSecurityItem(new SecurityRequirement().addList("API-Key"));
    }
}
