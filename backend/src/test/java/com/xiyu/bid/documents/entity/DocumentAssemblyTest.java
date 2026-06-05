package com.xiyu.bid.documents.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocumentAssembly实体单元测试
 * 测试文档组装记录实体的基本功能
 */
class DocumentAssemblyTest {

    @Test
    void documentAssemblyBuilder_ShouldCreateValidEntity() {
        // When
        DocumentAssembly assembly = DocumentAssembly.builder()
                .id(1L)
                .projectId(100L)
                .templateId(10L)
                .assembledContent("尊敬的XX公司：\n\n我方愿意参与ABC项目的投标...")
                .variables("{\"招标方名称\":\"XX公司\",\"项目名称\":\"ABC项目\"}")
                .assembledBy(200L)
                .assembledAt(LocalDateTime.now())
                .build();

        // Then
        assertThat(assembly).isNotNull();
        assertThat(assembly.getId()).isEqualTo(1L);
        assertThat(assembly.getProjectId()).isEqualTo(100L);
        assertThat(assembly.getTemplateId()).isEqualTo(10L);
        assertThat(assembly.getAssembledContent()).isNotNull();
        assertThat(assembly.getVariables()).isNotNull();
        assertThat(assembly.getAssembledBy()).isEqualTo(200L);
        assertThat(assembly.getAssembledAt()).isNotNull();
    }

    @Test
    void documentAssemblyBuilder_WithMinimumFields_ShouldCreateEntity() {
        // When
        DocumentAssembly assembly = DocumentAssembly.builder()
                .projectId(100L)
                .templateId(10L)
                .assembledContent("组装内容")
                .build();

        // Then
        assertThat(assembly).isNotNull();
        assertThat(assembly.getProjectId()).isEqualTo(100L);
        assertThat(assembly.getTemplateId()).isEqualTo(10L);
        assertThat(assembly.getAssembledContent()).isEqualTo("组装内容");
    }

    @Test
    void documentAssemblySetterGetter_ShouldWorkCorrectly() {
        // Given
        DocumentAssembly assembly = new DocumentAssembly();
        LocalDateTime now = LocalDateTime.now();

        // When
        assembly.setId(2L);
        assembly.setProjectId(101L);
        assembly.setTemplateId(11L);
        assembly.setAssembledContent("组装的内容");
        assembly.setVariables("{\"key\":\"value\"}");
        assembly.setAssembledBy(201L);
        assembly.setAssembledAt(now);

        // Then
        assertThat(assembly.getId()).isEqualTo(2L);
        assertThat(assembly.getProjectId()).isEqualTo(101L);
        assertThat(assembly.getTemplateId()).isEqualTo(11L);
        assertThat(assembly.getAssembledContent()).isEqualTo("组装的内容");
        assertThat(assembly.getVariables()).isEqualTo("{\"key\":\"value\"}");
        assertThat(assembly.getAssembledBy()).isEqualTo(201L);
        assertThat(assembly.getAssembledAt()).isEqualTo(now);
    }

    @Test
    void documentAssembly_WithNullContent_ShouldBeAllowed() {
        // When
        DocumentAssembly assembly = DocumentAssembly.builder()
                .projectId(100L)
                .templateId(10L)
                .assembledContent(null)
                .build();

        // Then
        assertThat(assembly).isNotNull();
        assertThat(assembly.getAssembledContent()).isNull();
    }

    @Test
    void documentAssembly_WithEmptyVariables_ShouldBeAllowed() {
        // When
        DocumentAssembly assembly = DocumentAssembly.builder()
                .projectId(100L)
                .templateId(10L)
                .assembledContent("内容")
                .variables("")
                .build();

        // Then
        assertThat(assembly).isNotNull();
        assertThat(assembly.getVariables()).isEmpty();
    }
}
