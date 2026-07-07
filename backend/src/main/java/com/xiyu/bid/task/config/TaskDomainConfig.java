package com.xiyu.bid.task.config;

import com.xiyu.bid.task.core.TaskDueReminderPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 任务域纯核心策略 Bean 注册。
 *
 * <p>纯核心类不依赖 Spring 注解，通过此配置显式注册。
 * 对齐 {@link com.xiyu.bid.businessqualification.config.QualificationDomainConfig} 模式。
 */
@Configuration
public class TaskDomainConfig {

    @Bean
    public TaskDueReminderPolicy taskDueReminderPolicy() {
        return new TaskDueReminderPolicy();
    }
}
