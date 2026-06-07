package com.xiyu.bid.bidmatch.application;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.businessqualification.application.command.QualificationListCriteria;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.CaseRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidMatchEvidenceAssemblerTest {

    @Mock
    private TenderRepository tenderRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private BusinessQualificationRepository qualificationRepository;

    @Mock
    private BidResultFetchResultRepository bidResultRepository;

    @Test
    @DisplayName("证据按当前标讯上下文收窄，不再读取全量中标案例和全量确认中标结果")
    void assemble_ShouldScopeEvidenceByTenderContext() {
        Tender tender = Tender.builder()
                .id(110L)
                .title("西域能源设备数字化运维平台采购")
                .description("需要能源设备巡检和运维能力")
                .industry("能源")
                .region("上海")
                .purchaserName("西域能源集团")
                .tags("能源设备,数字化运维")
                .budget(new BigDecimal("500000"))
                .build();
        Case scopedCase = Case.builder()
                .id(21L)
                .title("西域能源集团设备运维中标案例")
                .industry(Case.Industry.ENERGY)
                .outcome(Case.Outcome.WON)
                .amount(new BigDecimal("480000"))
                .customerName("西域能源集团")
                .locationName("上海")
                .productLine("能源设备")
                .searchDocument("能源设备 数字化运维")
                .build();
        BidResultFetchResult scopedResult = BidResultFetchResult.builder()
                .id(31L)
                .tenderId(110L)
                .projectName("西域能源设备数字化运维平台采购")
                .result(BidResultFetchResult.Result.WON)
                .status(BidResultFetchResult.Status.CONFIRMED)
                .fetchTime(LocalDateTime.now())
                .build();
        BusinessQualification scopedQualification = qualification(41L, "能源设备安装服务资质");
        when(tenderRepository.findById(110L)).thenReturn(Optional.of(tender));
        when(caseRepository.findScopedWonCasesForBidMatch(
                eq(Case.Industry.ENERGY),
                eq("能源设备"),
                eq("西域能源集团"),
                eq("上海"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(scopedCase)));
        when(bidResultRepository.findScopedConfirmedWins(eq(110L), eq("能源设备"), any(Pageable.class)))
                .thenReturn(List.of(scopedResult));
        when(qualificationRepository.findAll(any(QualificationListCriteria.class)))
                .thenReturn(List.of(scopedQualification));

        BidMatchEvidenceBundle bundle = assembler().assemble(110L);

        assertThat(bundle.evidence().numbers())
                .containsEntry("case.wonCount", BigDecimal.ONE)
                .containsEntry("qualification.validCount", BigDecimal.ONE)
                .containsEntry("bidResult.confirmedWinCount", BigDecimal.ONE);
        assertThat(bundle.evidence().texts().get("case.searchText"))
                .contains("西域能源集团")
                .doesNotContain("无关客户");
        assertThat(bundle.snapshot().toString())
                .contains("caseIds=[21]")
                .contains("qualificationIds=[41]")
                .contains("resultIds=[31]");
        verify(caseRepository, never()).findByOutcome(eq(Case.Outcome.WON), any(Pageable.class));
        verify(bidResultRepository, never())
                .findByStatusOrderByFetchTimeDesc(BidResultFetchResult.Status.CONFIRMED);
    }

    @Test
    @DisplayName("资质查询带上标讯关键词和有效状态，避免把全部有效资质当作匹配证据")
    void assemble_ShouldQueryQualificationsWithTenderKeyword() {
        Tender tender = Tender.builder()
                .id(111L)
                .title("能源设备维护采购")
                .industry("能源")
                .build();
        when(tenderRepository.findById(111L)).thenReturn(Optional.of(tender));
        when(caseRepository.findScopedWonCasesForBidMatch(
                eq(Case.Industry.ENERGY),
                eq("能源"),
                eq(null),
                eq(null),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));
        when(bidResultRepository.findScopedConfirmedWins(eq(111L), eq("能源"), any(Pageable.class)))
                .thenReturn(List.of());
        when(qualificationRepository.findAll(any(QualificationListCriteria.class)))
                .thenReturn(List.of());

        assembler().assemble(111L);

        ArgumentCaptor<QualificationListCriteria> criteriaCaptor =
                ArgumentCaptor.forClass(QualificationListCriteria.class);
        verify(qualificationRepository).findAll(criteriaCaptor.capture());
        QualificationListCriteria criteria = criteriaCaptor.getValue();
        assertThat(criteria.getStatus()).isEqualTo("VALID");
        assertThat(criteria.getKeyword()).isEqualTo("能源");
    }

    private BidMatchEvidenceAssembler assembler() {
        return new BidMatchEvidenceAssembler(
                tenderRepository,
                caseRepository,
                qualificationRepository,
                bidResultRepository
        );
    }

    private BusinessQualification qualification(Long id, String name) {
        return BusinessQualification.create(
                id,
                name,
                QualificationSubject.of(QualificationSubjectType.COMPANY, "西域"),
                QualificationCategory.LICENSE,
                "CERT-" + id,
                "行业主管部门",
                "西域",
                new ValidityPeriod(LocalDate.now().minusYears(1), LocalDate.now().plusYears(1)),
                new ReminderPolicy(true, 30, null),
                LoanStatus.AVAILABLE,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }
}
