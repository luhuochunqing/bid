package com.xiyu.bid.tenderreminder.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 提醒模块 DTO 配置
 */
@Configuration
public class TenderReminderDtoConfig {

    @Bean
    public TenderReminderMapper tenderReminderMapper(ObjectMapper objectMapper) {
        return new TenderReminderMapper(objectMapper);
    }
}
