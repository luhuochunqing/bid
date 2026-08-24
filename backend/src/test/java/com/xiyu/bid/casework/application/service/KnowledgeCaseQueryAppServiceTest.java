package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.dto.KnowledgeCaseDetailResponse;
import com.xiyu.bid.casework.infrastructure.KnowledgeCase;
import com.xiyu.bid.casework.infrastructure.KnowledgeCaseRepository;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeCaseQueryAppServiceTest {

    @Mock
    private KnowledgeCaseRepository caseRepository;

    @Mock
    private ProjectInitiationDetailsRepository initiationDetailsRepository;

    @InjectMocks
    private KnowledgeCaseQueryAppService service;

    private KnowledgeCase createCase(Long id, Long sourceProjectId) {
        KnowledgeCase kCase = new KnowledgeCase();
        kCase.setId(id);
        kCase.setSourceProjectId(sourceProjectId);
        kCase.setSourceProjectName("测试历史项目");
        kCase.setScoringPointTitle("技术方案完整性");
        kCase.setScoringPointId(11L);
        kCase.setRequirementRaw("须提供完整技术方案");
        kCase.setResponseText("我方提供完整技术方案……");
        kCase.setReuseCount(3);
        kCase.setIsPinned(false);
        kCase.setStatus("ACTIVE");
        kCase.setCustomerType("CENTRAL_SOE");
        kCase.setProjectType("IT");
        kCase.setBidResult("WON");
        kCase.setScoringCategory("技术");
        kCase.setProductLine("综合产品线");
        kCase.setCreatedAt(LocalDateTime.of(2026, 5, 20, 14, 20, 0));
        return kCase;
    }

    @Test
    void getCaseDetailReturnsOwnerUnitWhenInitiationDetailsExist() {
        KnowledgeCase kCase = createCase(1L, 200L);
        when(caseRepository.findById(1L)).thenReturn(Optional.of(kCase));
        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setOwnerUnit("某某集团有限公司");
        when(initiationDetailsRepository.findByProjectId(200L)).thenReturn(Optional.of(details));

        KnowledgeCaseDetailResponse detail = service.getCaseDetail(1L);

        assertThat(detail.ownerUnit()).isEqualTo("某某集团有限公司");
        assertThat(detail.sourceProjectName()).isEqualTo("测试历史项目");
        assertThat(detail.bidResult()).isEqualTo("WON");
        assertThat(detail.pinned()).isFalse();
        assertThat(detail.createdAt()).isEqualTo(LocalDateTime.of(2026, 5, 20, 14, 20, 0));
    }

    @Test
    void getCaseDetailOwnerUnitIsNullWhenInitiationDetailsMissing() {
        KnowledgeCase kCase = createCase(2L, 300L);
        when(caseRepository.findById(2L)).thenReturn(Optional.of(kCase));
        when(initiationDetailsRepository.findByProjectId(300L)).thenReturn(Optional.empty());

        KnowledgeCaseDetailResponse detail = service.getCaseDetail(2L);

        assertThat(detail.ownerUnit()).isNull();
    }

    @Test
    void getCaseDetailThrowsWhenCaseNotFound() {
        when(caseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCaseDetail(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("案例不存在");
    }
}
