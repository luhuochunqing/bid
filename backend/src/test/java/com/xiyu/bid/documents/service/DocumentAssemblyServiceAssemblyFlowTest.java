package com.xiyu.bid.documents.service;

import com.xiyu.bid.documents.dto.DocumentAssemblyDTO;
import com.xiyu.bid.documents.entity.DocumentAssembly;
import com.xiyu.bid.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentAssemblyServiceAssemblyFlowTest extends AbstractDocumentAssemblyServiceTest {

    @Test
    void assembleDocument_ShouldReturnAssembledContent() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(testTemplate));
        when(assemblyRepository.save(any(DocumentAssembly.class))).thenReturn(testAssembly);

        DocumentAssemblyDTO result = documentAssemblyService.assembleDocument(
                200L,
                1L,
                "{\"招标方名称\":\"XX公司\",\"项目名称\":\"ABC项目\",\"报价金额\":500000}",
                300L
        );

        assertThat(result).isNotNull();
        assertThat(result.getProjectId()).isEqualTo(200L);
        assertThat(result.getTemplateId()).isEqualTo(1L);
        assertThat(result.getAssembledContent()).contains("XX公司");
        assertThat(result.getAssembledContent()).contains("ABC项目");
        verify(assemblyRepository).save(any(DocumentAssembly.class));
    }

    @Test
    void assembleDocument_WithInvalidTemplateId_ShouldThrowException() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentAssemblyService.assembleDocument(200L, 999L, "{}", 300L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Template not found");
    }

    @Test
    void assembleDocument_WithMissingVariable_ShouldLeavePlaceholder() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(testTemplate));
        when(assemblyRepository.save(any(DocumentAssembly.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentAssemblyDTO result = documentAssemblyService.assembleDocument(
                200L,
                1L,
                "{\"招标方名称\":\"XX公司\",\"项目名称\":\"ABC项目\"}",
                300L
        );

        assertThat(result.getAssembledContent()).contains("XX公司");
        assertThat(result.getAssembledContent()).contains("ABC项目");
    }

    @Test
    void getAssembliesByProject_ShouldReturnListOfAssemblies() {
        DocumentAssembly assembly2 = DocumentAssembly.builder()
                .id(2L)
                .projectId(200L)
                .templateId(2L)
                .assembledContent("第二个组装文档")
                .build();
        when(assemblyRepository.findByProjectId(200L)).thenReturn(Arrays.asList(testAssembly, assembly2));

        List<DocumentAssemblyDTO> result = documentAssemblyService.getAssembliesByProject(200L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProjectId()).isEqualTo(200L);
        assertThat(result.get(1).getProjectId()).isEqualTo(200L);
    }

    @Test
    void getAssembliesByProject_WithEmptyResult_ShouldReturnEmptyList() {
        when(assemblyRepository.findByProjectId(999L)).thenReturn(List.of());

        List<DocumentAssemblyDTO> result = documentAssemblyService.getAssembliesByProject(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void regenerateAssembly_ShouldReturnNewAssembly() {
        DocumentAssembly newAssembly = DocumentAssembly.builder()
                .id(2L)
                .projectId(200L)
                .templateId(1L)
                .assembledContent("重新生成的内容")
                .build();
        when(assemblyRepository.findById(1L)).thenReturn(Optional.of(testAssembly));
        when(templateRepository.findById(1L)).thenReturn(Optional.of(testTemplate));
        when(assemblyRepository.save(any(DocumentAssembly.class))).thenReturn(newAssembly);

        DocumentAssemblyDTO result = documentAssemblyService.regenerateAssembly(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(2L);
        verify(assemblyRepository).save(any(DocumentAssembly.class));
    }

    @Test
    void regenerateAssembly_WithInvalidAssemblyId_ShouldThrowException() {
        when(assemblyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentAssemblyService.regenerateAssembly(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Assembly not found");
    }

    @Test
    void regenerateAssembly_WithDeletedTemplate_ShouldThrowException() {
        when(assemblyRepository.findById(1L)).thenReturn(Optional.of(testAssembly));
        when(templateRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentAssemblyService.regenerateAssembly(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Template not found");
    }
}
