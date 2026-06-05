// Input: Spring 配置属性、环境变量、外部 bean 依赖
// Output: 配置 Bean、过滤器、线程池和启动级常量
// Pos: Config/基础设施层
// 维护声明: 仅维护配置与启动约束；业务规则变更请同步到对应 service/controller.
package com.xiyu.bid.config;

import com.xiyu.bid.auth.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")  // 默认24小时
    private Long expiration;

    @Bean
    public JwtUtil jwtUtil() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET environment variable must be set with at least 32 characters");
        }
        return new JwtUtil(secret, expiration);
    }
}
