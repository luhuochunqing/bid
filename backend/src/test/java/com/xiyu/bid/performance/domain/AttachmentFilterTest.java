package com.xiyu.bid.performance.domain;

import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 附件类型过滤器单元测试（spec 5.1）
 * 纯核心函数，不依赖 Spring。
 */
class AttachmentFilterTest {

    private PerformanceDTO.AttachmentDTO att(String fileType) {
        return new PerformanceDTO.AttachmentDTO(1L, "file.pdf", "/path/file.pdf", fileType);
    }

    @Test
    void filterByTypes_nullTypes_returnsAll() {
        List<PerformanceDTO.AttachmentDTO> attachments = List.of(
                att("CONTRACT_AGREEMENT"), att("BID_NOTICE"), att("OTHER"));

        List<PerformanceDTO.AttachmentDTO> result =
                AttachmentFilter.filterByTypes(attachments, null);

        assertThat(result).hasSize(3);
    }

    @Test
    void filterByTypes_emptyTypes_returnsAll() {
        List<PerformanceDTO.AttachmentDTO> attachments = List.of(
                att("CONTRACT_AGREEMENT"), att("BID_NOTICE"));

        List<PerformanceDTO.AttachmentDTO> result =
                AttachmentFilter.filterByTypes(attachments, Set.of());

        assertThat(result).hasSize(2);
    }

    @Test
    void filterByTypes_singleType_returnsOnlyMatching() {
        List<PerformanceDTO.AttachmentDTO> attachments = List.of(
                att("CONTRACT_AGREEMENT"), att("BID_NOTICE"), att("OTHER"));

        List<PerformanceDTO.AttachmentDTO> result =
                AttachmentFilter.filterByTypes(attachments, Set.of("BID_NOTICE"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fileType()).isEqualTo("BID_NOTICE");
    }

    @Test
    void filterByTypes_multipleTypes_returnsUnion() {
        List<PerformanceDTO.AttachmentDTO> attachments = List.of(
                att("CONTRACT_AGREEMENT"), att("BID_NOTICE"), att("OTHER"), att("MALL_SCREENSHOT"));

        List<PerformanceDTO.AttachmentDTO> result =
                AttachmentFilter.filterByTypes(attachments, Set.of("CONTRACT_AGREEMENT", "BID_NOTICE"));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PerformanceDTO.AttachmentDTO::fileType)
                .containsExactlyInAnyOrder("CONTRACT_AGREEMENT", "BID_NOTICE");
    }

    @Test
    void filterByTypes_noMatch_returnsEmpty() {
        List<PerformanceDTO.AttachmentDTO> attachments = List.of(
                att("CONTRACT_AGREEMENT"), att("BID_NOTICE"));

        List<PerformanceDTO.AttachmentDTO> result =
                AttachmentFilter.filterByTypes(attachments, Set.of("OTHER"));

        assertThat(result).isEmpty();
    }

    @Test
    void validateTypes_validType_passes() {
        AttachmentFilter.validateTypes(Set.of("CONTRACT_AGREEMENT", "BID_NOTICE", "OTHER"));
        AttachmentFilter.validateTypes(Set.of("MALL_SCREENSHOT", "SOE_DIRECTORY",
                "RELATIONSHIP_PROOF", "CATEGORY_PAGE"));
    }

    @Test
    void validateTypes_invalidType_throws() {
        assertThatThrownBy(() -> AttachmentFilter.validateTypes(Set.of("INVALID_TYPE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法附件类型");
    }

    @Test
    void validateTypes_null_passes() {
        AttachmentFilter.validateTypes(null);
    }

    @Test
    void allowedTypes_containsAllSevenTypes() {
        assertThat(AttachmentFilter.ALLOWED_TYPES).containsExactlyInAnyOrder(
                "CONTRACT_AGREEMENT", "MALL_SCREENSHOT", "SOE_DIRECTORY",
                "RELATIONSHIP_PROOF", "CATEGORY_PAGE", "BID_NOTICE", "OTHER");
        assertThat(AttachmentFilter.ALLOWED_TYPES).hasSize(7);
    }
}
