package com.xiyu.bid.documents.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AssemblyTemplate实体单元测试
 * 测试文档组装模板实体的基本功能
 */
class AssemblyTemplateTest {

    @Test
    void assemblyTemplateBuilder_ShouldCreateValidEntity() {
        // When
        AssemblyTemplate template = AssemblyTemplate.builder()
                .id(1L)
                .name("投标书模板")
                .description("标准投标书模板")
                .category("BIDDING_DOCUMENT")
                .templateContent("尊敬的${招标方名称}：\n\n我方愿意参与${项目名称}的投标...")
                .variables("{\"招标方名称\":\"string\",\"项目名称\":\"string\"}")
                .createdBy(100L)
                .createdAt(LocalDateTime.now())
                .build();

        // Then
        assertThat(template).isNotNull();
        assertThat(template.getId()).isEqualTo(1L);
        assertThat(template.getName()).isEqualTo("投标书模板");
        assertThat(template.getDescription()).isEqualTo("标准投标书模板");
        assertThat(template.getCategory()).isEqualTo("BIDDING_DOCUMENT");
        assertThat(template.getTemplateContent()).contains("${招标方名称}");
        assertThat(template.getVariables()).isNotNull();
        assertThat(template.getCreatedBy()).isEqualTo(100L);
        assertThat(template.getCreatedAt()).isNotNull();
    }

    @Test
    void assemblyTemplateBuilder_WithMinimumFields_ShouldCreateEntity() {
        // When
        AssemblyTemplate template = AssemblyTemplate.builder()
                .name("简单模板")
                .templateContent("内容: ${变量}")
                .build();

        // Then
        assertThat(template).isNotNull();
        assertThat(template.getName()).isEqualTo("简单模板");
        assertThat(template.getTemplateContent()).isEqualTo("内容: ${变量}");
    }

    @Test
    void assemblyTemplateSetterGetter_ShouldWorkCorrectly() {
        // Given
        AssemblyTemplate template = new AssemblyTemplate();
        LocalDateTime now = LocalDateTime.now();

        // When
        template.setId(2L);
        template.setName("资质证明模板");
        template.setDescription("企业资质证明文件模板");
        template.setCategory("QUALIFICATION");
        template.setTemplateContent("${企业名称}资质证明");
        template.setVariables("{\"企业名称\":\"string\"}");
        template.setCreatedBy(101L);
        template.setCreatedAt(now);

        // Then
        assertThat(template.getId()).isEqualTo(2L);
        assertThat(template.getName()).isEqualTo("资质证明模板");
        assertThat(template.getDescription()).isEqualTo("企业资质证明文件模板");
        assertThat(template.getCategory()).isEqualTo("QUALIFICATION");
        assertThat(template.getTemplateContent()).isEqualTo("${企业名称}资质证明");
        assertThat(template.getVariables()).isEqualTo("{\"企业名称\":\"string\"}");
        assertThat(template.getCreatedBy()).isEqualTo(101L);
        assertThat(template.getCreatedAt()).isEqualTo(now);
    }
}
