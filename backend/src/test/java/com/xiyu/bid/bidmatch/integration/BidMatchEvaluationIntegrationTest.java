package com.xiyu.bid.bidmatch.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.bidmatch.application.BidMatchEvaluationAppService;
import com.xiyu.bid.bidmatch.application.BidMatchModelAppService;
import com.xiyu.bid.bidmatch.dto.BidMatchEvaluationResponse;
import com.xiyu.bid.bidmatch.dto.BidMatchModelRequest;
import com.xiyu.bid.bidmatch.dto.BidMatchModelResponse;
import com.xiyu.bid.bidmatch.infrastructure.persistence.repository.BidMatchModelVersionJpaRepository;
import com.xiyu.bid.bidmatch.infrastructure.persistence.repository.BidMatchScoreEvaluationJpaRepository;
import com.xiyu.bid.bidmatch.infrastructure.persistence.repository.BidMatchScoringModelJpaRepository;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.CaseRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class BidMatchEvaluationIntegrationTest {

    @Autowired
    private BidMatchModelAppService modelAppService;

    @Autowired
    private BidMatchEvaluationAppService evaluationAppService;

    @Autowired
    private TenderRepository tenderRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private BidResultFetchResultRepository bidResultRepository;

    @Autowired
    private BusinessQualificationJpaRepository qualificationJpaRepository;

    @Autowired
    private BidMatchScoreEvaluationJpaRepository evaluationRepository;

    @Autowired
    private BidMatchModelVersionJpaRepository versionRepository;

    @Autowired
    private BidMatchScoringModelJpaRepository modelRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        evaluationRepository.deleteAll();
        versionRepository.deleteAll();
        modelRepository.deleteAll();
        bidResultRepository.deleteAll();
        qualificationJpaRepository.deleteAll();
        caseRepository.deleteAll();
        tenderRepository.deleteAll();
    }

    @Test
    @DisplayName("评估服务集成链路只把当前标讯相关证据写入快照")
    void evaluate_ShouldPersistOnlyTenderScopedEvidence() throws Exception {
        Long modelId = activeModel();
        Tender tender = tenderRepository.save(Tender.builder()
                .title("西域能源设备数字化运维平台采购")
                .description("需要能源设备巡检和运维能力")
                .industry("能源")
                .region("上海")
                .purchaserName("西域能源集团")
                .tags("能源设备,数字化运维")
                .budget(new BigDecimal("500000"))
                .build());
        Case scopedCase = caseRepository.save(caseEntity(
                "西域能源集团设备运维中标案例",
                Case.Industry.ENERGY,
                "西域能源集团",
                "上海",
                "能源设备 数字化运维"
        ));
        Case unrelatedCase = caseRepository.save(caseEntity(
                "华北制造工厂生产线改造案例",
                Case.Industry.MANUFACTURING,
                "华北制造公司",
                "北京",
                "制造生产线改造"
        ));
        BusinessQualificationEntity scopedQualification = qualificationJpaRepository.save(qualification(
                "能源设备安装服务资质"
        ));
        BusinessQualificationEntity unrelatedQualification = qualificationJpaRepository.save(qualification(
                "制造生产许可资质"
        ));
        BidResultFetchResult scopedResult = bidResultRepository.save(bidResult(
                tender.getId(),
                "西域能源设备数字化运维平台采购",
                "能源设备项目确认中标"
        ));
        BidResultFetchResult unrelatedResult = bidResultRepository.save(bidResult(
                99999L,
                "制造生产线改造采购",
                "制造项目确认中标"
        ));

        BidMatchEvaluationResponse response = evaluationAppService.evaluate(tender.getId());

        JsonNode evidence = objectMapper.readTree(response.evidenceSnapshotJson());
        assertThat(response.modelId()).isEqualTo(modelId);
        assertThat(response.stale()).isFalse();
        assertThat(evidence.at("/caseEvidence/wonCount").asInt()).isEqualTo(1);
        assertThat(evidence.at("/qualificationEvidence/validCount").asInt()).isEqualTo(1);
        assertThat(evidence.at("/bidResultEvidence/confirmedWinCount").asInt()).isEqualTo(1);
        assertThat(ids(evidence.at("/caseEvidence/caseIds")))
                .containsExactly(scopedCase.getId())
                .doesNotContain(unrelatedCase.getId());
        assertThat(ids(evidence.at("/qualificationEvidence/qualificationIds")))
                .containsExactly(scopedQualification.getId())
                .doesNotContain(unrelatedQualification.getId());
        assertThat(ids(evidence.at("/bidResultEvidence/resultIds")))
                .containsExactly(scopedResult.getId())
                .doesNotContain(unrelatedResult.getId());
    }

    private Long activeModel() {
        BidMatchModelResponse model = modelAppService.createModel(new BidMatchModelRequest(
                null,
                "集成测试自定义匹配模型",
                "验证证据按当前标讯收窄",
                List.of(new BidMatchModelRequest.DimensionRequest(
                        "evidence",
                        "证据覆盖",
                        100,
                        true,
                        List.of(
                                new BidMatchModelRequest.RuleRequest(
                                        "caseWon",
                                        "相关中标案例",
                                        "QUANTITY",
                                        "case.wonCount",
                                        List.of(),
                                        BigDecimal.ONE,
                                        null,
                                        50,
                                        true
                                ),
                                new BidMatchModelRequest.RuleRequest(
                                        "bidResultWon",
                                        "相关中标结果",
                                        "QUANTITY",
                                        "bidResult.confirmedWinCount",
                                        List.of(),
                                        BigDecimal.ONE,
                                        null,
                                        50,
                                        true
                                )
                        )
                ))
        ));
        modelAppService.activateModel(model.id());
        return model.id();
    }

    private Case caseEntity(
            String title,
            Case.Industry industry,
            String customerName,
            String locationName,
            String searchDocument
    ) {
        return Case.builder()
                .title(title)
                .industry(industry)
                .outcome(Case.Outcome.WON)
                .amount(new BigDecimal("480000"))
                .customerName(customerName)
                .locationName(locationName)
                .productLine("MRO")
                .archiveSummary(searchDocument)
                .searchDocument(searchDocument)
                .build();
    }

    private BusinessQualificationEntity qualification(String name) {
        return BusinessQualificationEntity.builder()
                .name(name)
                .subjectType(QualificationSubjectType.COMPANY)
                .subjectName("西域")
                .category(QualificationCategory.LICENSE)
                .certificateNo("CERT-" + name.hashCode())
                .issuer("行业主管部门")
                .holderName("西域")
                .issueDate(LocalDate.now().minusYears(1))
                .expiryDate(LocalDate.now().plusYears(1))
                .status(QualificationStatus.VALID)
                .reminderEnabled(true)
                .reminderDays(30)
                .currentBorrowStatus(LoanStatus.AVAILABLE)
                .build();
    }

    private BidResultFetchResult bidResult(Long tenderId, String projectName, String remark) {
        return BidResultFetchResult.builder()
                .source("TEST")
                .tenderId(tenderId)
                .projectName(projectName)
                .result(BidResultFetchResult.Result.WON)
                .status(BidResultFetchResult.Status.CONFIRMED)
                .amount(new BigDecimal("500000"))
                .fetchTime(LocalDateTime.now())
                .remark(remark)
                .build();
    }

    private List<Long> ids(JsonNode node) {
        return StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asLong)
                .toList();
    }
}
