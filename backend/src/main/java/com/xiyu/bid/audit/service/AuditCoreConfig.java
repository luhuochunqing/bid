package com.xiyu.bid.audit.service;

import com.xiyu.bid.audit.core.AuditActionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditCoreConfig {

    @Bean
    AuditActionPolicy auditActionPolicy() {
        return new AuditActionPolicy();
    }
}
