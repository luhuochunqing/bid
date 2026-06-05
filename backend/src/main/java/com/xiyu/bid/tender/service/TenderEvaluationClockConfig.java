// Input: 无（Spring 自举时调用）
// Output: 项目评估提交服务所需的 Clock bean（系统默认时区）
// Pos: Service/Spring 配置层
// 维护声明: 仅为 TenderEvaluationSubmissionService 提供注入；如需测试覆盖，直接构造而非通过 Spring。
package com.xiyu.bid.tender.service;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 暴露 {@link Clock} bean。仅当 Spring context 中没有现成 Clock 时生效。
 * <p>测试侧通过 {@code new Clock.fixed(...)} 直接构造 service，
 * 走 Spring autowire 时使用本默认时钟。
 */
@Configuration
public class TenderEvaluationClockConfig {

    @Bean
    public Clock evaluationClock() {
        return Clock.systemDefaultZone();
    }
}
