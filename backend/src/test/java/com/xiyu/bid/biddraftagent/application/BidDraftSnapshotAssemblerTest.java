package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.entity.BidRequirementItem;
import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import com.xiyu.bid.biddraftagent.repository.BidRequirementItemRepository;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.CaseRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.QualificationRepository;
import com.xiyu.bid.repository.TemplateRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidDraftSnapshotAssemblerTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private TenderRepository tenderRepository;
    @Mock
    private QualificationRepository qualificationRepository;
    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private CaseRepository caseRepository;
    @Mock
    private BidRequirementItemRepository requirementItemRepository;
    @Mock
    private BidTenderDocumentSnapshotRepository documentSnapshotRepository;

    private BidDraftSnapshotAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new BidDraftSnapshotAssembler(
                projectRepository,
                tenderRepository,
                qualificationRepository,
                templateRepository,
                caseRepository,
                new BidRequirementSnapshotReader(requirementItemRepository, documentSnapshotRepository)
        );
    }

    @Test
    void assemble_shouldUseOnlyLatestTenderDocumentSnapshotRequirements() {
        mockProjectAndTender();
        when(documentSnapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(11L))
                .thenReturn(Optional.of(BidTenderDocumentSnapshot.builder()
                        .projectId(11L)
                        .tenderId(22L)
                        .projectDocumentId(202L)
                        .build()));
        when(requirementItemRepository.findByProjectIdAndProjectDocumentIdOrderByCreatedAtDesc(11L, 202L))
                .thenReturn(List.of(BidRequirementItem.builder()
                        .projectId(11L)
                        .projectDocumentId(202L)
                        .category("technical")
                        .title("新文件实施方案")
                        .content("只响应修正版招标文件")
                        .mandatory(true)
                        .build()));
        mockEmptyKnowledgePages();

        var snapshot = assembler.assemble(11L);

        assertThat(snapshot.structuredRequirementSignals())
                .singleElement()
                .satisfies(signal -> assertThat(signal).contains("新文件实施方案", "只响应修正版招标文件"));
        verify(requirementItemRepository, never()).findByProjectIdOrderByCreatedAtDesc(any());
    }

    @Test
    void assemble_shouldNotReadHistoricalRequirementsWhenNoTenderSnapshotExists() {
        mockProjectAndTender();
        when(documentSnapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(11L))
                .thenReturn(Optional.empty());
        mockEmptyKnowledgePages();

        var snapshot = assembler.assemble(11L);

        assertThat(snapshot.structuredRequirementSignals()).isEmpty();
        verify(requirementItemRepository, never()).findByProjectIdOrderByCreatedAtDesc(any());
        verify(requirementItemRepository, never()).findByProjectIdAndProjectDocumentIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void assemble_shouldFallbackProjectEmptyFieldsToTenderStructuredFields() {
        when(projectRepository.findById(11L)).thenReturn(Optional.of(Project.builder()
                .id(11L)
                .tenderId(22L)
                .name("华东智慧园区改造项目")
                .region(" ")
                .build()));
        when(tenderRepository.findById(22L)).thenReturn(Optional.of(Tender.builder()
                .id(22L)
                .title("2026园区改造招标公告")
                .description("修正版招标文件")
                .budget(new BigDecimal("1250000.00"))
                .region("上海浦东")
                .industry("智慧园区")
                .deadline(LocalDateTime.of(2026, 5, 30, 17, 30))
                .build()));
        when(documentSnapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(11L))
                .thenReturn(Optional.empty());
        mockEmptyKnowledgePages();

        var snapshot = assembler.assemble(11L);

        assertThat(snapshot.region()).isEqualTo("上海浦东");
        assertThat(snapshot.industry()).isEqualTo("智慧园区");
        assertThat(snapshot.budget()).isEqualByComparingTo("1250000.00");
        assertThat(snapshot.deadline()).isEqualTo(LocalDate.of(2026, 5, 30));
    }

    private void mockProjectAndTender() {
        when(projectRepository.findById(11L)).thenReturn(Optional.of(Project.builder()
                .id(11L)
                .tenderId(22L)
                .name("华东智慧园区改造项目")
                .build()));
        when(tenderRepository.findById(22L)).thenReturn(Optional.of(Tender.builder()
                .id(22L)
                .title("2026园区改造招标公告")
                .description("修正版招标文件")
                .build()));
    }

    private void mockEmptyKnowledgePages() {
        when(qualificationRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(templateRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(caseRepository.searchCases(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
    }
}
