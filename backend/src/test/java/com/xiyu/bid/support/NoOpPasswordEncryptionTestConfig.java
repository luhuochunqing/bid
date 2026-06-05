package com.xiyu.bid.support;

import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@TestConfiguration
public class NoOpPasswordEncryptionTestConfig {

    @Bean(name = "auditLogExecutor")
    TaskExecutor auditLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.initialize();
        return executor;
    }

    @Bean(name = "passwordEncryptionUtil")
    @Primary
    PasswordEncryptionUtil passwordEncryptionUtil() {
        return new TestPasswordEncryptionUtil();
    }
}
