package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.dto.ProjectDTO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 验证 ProjectListEnrichmentSupport.populateFromTender 对 customerType/projectType/priority 的归一化：
 * 从 Tender 拷贝字段时，必须经 InitiationFieldPolicy.normalizeXxx 转换为后端枚举名/标准 value，
 * 避免列表筛选时前端 value 与后端值不一致导致筛不出数据。
 */
class ProjectListEnrichmentSupportTest {

    @Test
    void populateFromTender_normalizesCentralSoeChineseTextToCanonical() {
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .customerType(null)
                .build();
        Tender tender = Tender.builder().id(1L).customerType("央企").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("CENTRAL_SOE", dto.getCustomerType());
    }

    @Test
    void populateFromTender_normalizesGovernmentLegacyFrontendValue() {
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .customerType(null)
                .build();
        // Tender 存的是前端旧 value GOVERNMENT_INSTITUTION（CRM 推送或历史数据）
        Tender tender = Tender.builder().id(1L).customerType("GOVERNMENT_INSTITUTION").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("GOVERNMENT", dto.getCustomerType());
    }

    @Test
    void populateFromTender_keepsUnknownValueWhenMappingMisses() {
        // 无法识别的值应保留原值，避免丢失数据
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .customerType(null)
                .build();
        Tender tender = Tender.builder().id(1L).customerType("外星人企业").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("外星人企业", dto.getCustomerType());
    }

    @Test
    void populateFromTender_doesNotOverwriteExistingCustomerType() {
        // DTO 已有 customerType 时不覆盖（populateFromTender 仅做 fallback）
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .customerType("PRIVATE")
                .build();
        Tender tender = Tender.builder().id(1L).customerType("央企").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("PRIVATE", dto.getCustomerType());
    }

    @Test
    void populateFromTender_tenderNotFound_leavesCustomerTypeNull() {
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(999L)
                .customerType(null)
                .build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of());

        assertNull(dto.getCustomerType());
    }

    @Test
    void populateFromTender_normalizesOtherEnum() {
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .customerType(null)
                .build();
        Tender tender = Tender.builder().id(1L).customerType("其他").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("OTHER", dto.getCustomerType());
    }

    // ===== projectType 归一化测试（与 customerType 同款修复模式）=====

    @Test
    void populateFromTender_normalizesProjectTypeChineseTextToCanonical() {
        // Tender 存中文"工业品" → DTO 应归一化为枚举名 INDUSTRIAL
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .projectType(null)
                .build();
        Tender tender = Tender.builder().id(1L).projectType("工业品").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("INDUSTRIAL", dto.getProjectType());
    }

    @Test
    void populateFromTender_normalizesProjectTypeLegacyFrontendValue() {
        // Tender 存前端旧 value GROUP_PURCHASE/INDUSTRIAL_EC → DTO 应归一化为枚举名 COLLECTIVE/INDUSTRIAL
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .projectType(null)
                .build();
        Tender tender = Tender.builder().id(1L).projectType("GROUP_PURCHASE").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("COLLECTIVE", dto.getProjectType());
    }

    @Test
    void populateFromTender_keepsUnknownProjectTypeWhenMappingMisses() {
        // 无法识别的值应保留原值，避免丢失数据
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .projectType(null)
                .build();
        Tender tender = Tender.builder().id(1L).projectType("外星人项目").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("外星人项目", dto.getProjectType());
    }

    @Test
    void populateFromTender_doesNotOverwriteExistingProjectType() {
        // DTO 已有 projectType 时不覆盖（populateFromTender 仅做 fallback）
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .projectType("OFFICE")
                .build();
        Tender tender = Tender.builder().id(1L).projectType("工业品").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("OFFICE", dto.getProjectType());
    }

    // ===== priority 归一化测试 =====

    @Test
    void populateFromTender_normalizesPriorityWithLevelSuffix() {
        // Tender 存"A级" → DTO 应归一化为 "A"
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .priority(null)
                .build();
        Tender tender = Tender.builder().id(1L).priority("A级").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("A", dto.getPriority());
    }

    @Test
    void populateFromTender_normalizesPriorityLowercase() {
        // Tender 存小写"s" → DTO 应归一化为 "S"
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .priority(null)
                .build();
        Tender tender = Tender.builder().id(1L).priority("s").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("S", dto.getPriority());
    }

    @Test
    void populateFromTender_keepsUnknownPriorityWhenMappingMisses() {
        // 无法识别的值应保留原值，避免丢失数据
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .priority(null)
                .build();
        Tender tender = Tender.builder().id(1L).priority("Z").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("Z", dto.getPriority());
    }

    @Test
    void populateFromTender_doesNotOverwriteExistingPriority() {
        // DTO 已有 priority 时不覆盖（populateFromTender 仅做 fallback）
        ProjectDTO dto = ProjectDTO.builder()
                .tenderId(1L)
                .priority("S")
                .build();
        Tender tender = Tender.builder().id(1L).priority("A级").build();

        ProjectListEnrichmentSupport.populateFromTender(dto, Map.of(1L, tender));

        assertEquals("S", dto.getPriority());
    }
}
