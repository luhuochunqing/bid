package com.xiyu.bid.project.service;

import com.xiyu.bid.project.dto.ProjectDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectPayloadValidatorTest {

    @Test
    void validateAndNormalize_convertsChineseCustomerTypeToCanonicalValue() {
        ProjectDTO input = ProjectDTO.builder()
                .name("测试项目")
                .tenderId(1L)
                .managerId(2L)
                .customerType("央企")
                .build();

        ProjectDTO normalized = ProjectPayloadValidator.validateAndNormalize(input, true);

        assertEquals("CENTRAL_SOE", normalized.getCustomerType());
    }
}
