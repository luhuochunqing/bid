package com.xiyu.bid.documents.service;

import com.xiyu.bid.documents.dto.AssemblyTemplateDTO;
import com.xiyu.bid.documents.dto.TemplateCreateRequest;
import com.xiyu.bid.documents.entity.AssemblyTemplate;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentAssemblyServiceTemplateCatalogTest extends AbstractDocumentAssemblyServiceTest {

    @Test
    void createTemplate_ShouldReturnSavedTemplate() {
        when(templateRepository.save(any(AssemblyTemplate.class))).thenReturn(testTemplate);

        AssemblyTemplateDTO result = documentAssemblyService.createTemplate(createRequest);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("投标书模板");
        assertThat(result.getCategory()).isEqualTo("BIDDING_DOCUMENT");
        verify(templateRepository).save(any(AssemblyTemplate.class));
    }

    @Test
    void createTemplate_WithNullName_ShouldThrowException() {
        TemplateCreateRequest invalidRequest = TemplateCreateRequest.builder()
                .name(null)
                .templateContent("内容")
                .build();

        assertThatThrownBy(() -> documentAssemblyService.createTemplate(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template name");
    }

    @Test
    void createTemplate_WithEmptyContent_ShouldThrowException() {
        TemplateCreateRequest invalidRequest = TemplateCreateRequest.builder()
                .name("模板")
                .templateContent("")
                .build();

        assertThatThrownBy(() -> documentAssemblyService.createTemplate(invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template content");
    }

    @Test
    void getTemplatesByCategory_ShouldReturnListOfTemplates() {
        AssemblyTemplate contractTemplate1 = AssemblyTemplate.builder()
                .id(1L)
                .name("销售合同模板")
                .category("CONTRACT")
                .templateContent("销售合同内容")
                .build();
        AssemblyTemplate contractTemplate2 = AssemblyTemplate.builder()
                .id(2L)
                .name("采购合同模板")
                .category("CONTRACT")
                .templateContent("采购合同内容")
                .build();
        when(templateRepository.findByCategory("CONTRACT"))
                .thenReturn(Arrays.asList(contractTemplate1, contractTemplate2));

        List<AssemblyTemplateDTO> result = documentAssemblyService.getTemplatesByCategory("CONTRACT");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCategory()).isEqualTo("CONTRACT");
        assertThat(result.get(1).getCategory()).isEqualTo("CONTRACT");
    }

    @Test
    void getTemplatesByCategory_WithEmptyResult_ShouldReturnEmptyList() {
        when(templateRepository.findByCategory("UNKNOWN")).thenReturn(List.of());

        List<AssemblyTemplateDTO> result = documentAssemblyService.getTemplatesByCategory("UNKNOWN");

        assertThat(result).isEmpty();
    }
}
