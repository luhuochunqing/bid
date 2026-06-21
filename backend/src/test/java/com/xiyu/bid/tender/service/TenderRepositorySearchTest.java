package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TenderRepositorySearchTest {

    @Autowired
    private TenderRepository tenderRepository;

    @Test
    @DisplayName("标讯检索 - 来源筛选 CRM创建 兼容旧空格标签")
    void searchTenders_CrmSourceFilterMatchesLegacySpacedLabel() {
        Tender legacyCrmTender = Tender.builder()
                .title("CRM 历史商机标讯")
                .source("CRM 创建")
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .build();
        Tender externalTender = Tender.builder()
                .title("第三方平台标讯")
                .source("第三方平台")
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .build();

        tenderRepository.saveAll(List.of(legacyCrmTender, externalTender));

        TenderSearchCriteria criteria = TenderSearchCriteria.builder()
                .source(List.of("CRM创建"))
                .build();

        List<Tender> result = tenderRepository.findAll(TenderSpecification.byCriteria(criteria));

        assertThat(result)
                .extracting(Tender::getTitle)
                .containsExactly("CRM 历史商机标讯");
    }

    @Test
    @DisplayName("标讯检索 - 来源筛选旧空格标签兼容 CRM创建")
    void searchTenders_LegacyCrmSourceFilterMatchesCurrentLabel() {
        Tender currentCrmTender = Tender.builder()
                .title("CRM 新标签商机标讯")
                .source("CRM创建")
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .build();
        Tender externalTender = Tender.builder()
                .title("第三方平台标讯")
                .source("第三方平台")
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .build();

        tenderRepository.saveAll(List.of(currentCrmTender, externalTender));

        TenderSearchCriteria criteria = TenderSearchCriteria.builder()
                .source(List.of("CRM 创建"))
                .build();

        List<Tender> result = tenderRepository.findAll(TenderSpecification.byCriteria(criteria));

        assertThat(result)
                .extracting(Tender::getTitle)
                .containsExactly("CRM 新标签商机标讯");
    }

    @Test
    @DisplayName("标讯检索 - 支持真实字段与组合条件")
    void searchTenders_ShouldFilterByRealDimensions() {
        Tender matchingTender = Tender.builder()
                .title("华东数据中心 GPU 算力平台采购项目")
                .source("外部标讯聚合平台")
                .budget(new BigDecimal("5000000.00"))
                .deadline(LocalDateTime.of(2026, 5, 8, 18, 0))
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .aiScore(91)
                .riskLevel(Tender.RiskLevel.LOW)
                .region("上海")
                .industry("数据中心")
                .purchaserName("上海西域采购中心")
                .purchaserHash("hash-shanghai-xiyu")
                .publishDate(LocalDate.of(2026, 4, 20))
                .contactName("王经理")
                .contactPhone("13800138000")
                .description("采购 GPU 服务器、运维平台与交付服务")
                .tags("数据中心,GPU,重点")
                .build();

        Tender excludedTender = Tender.builder()
                .title("华北办公电脑采购项目")
                .source("人工录入")
                .budget(new BigDecimal("300000.00"))
                .deadline(LocalDateTime.of(2026, 4, 30, 18, 0))
                .status(Tender.Status.TRACKING)
                .aiScore(70)
                .riskLevel(Tender.RiskLevel.MEDIUM)
                .region("北京")
                .industry("办公设备")
                .purchaserName("北京采购中心")
                .purchaserHash("hash-beijing")
                .publishDate(LocalDate.of(2026, 3, 28))
                .description("办公终端设备")
                .tags("办公")
                .build();

        tenderRepository.saveAll(List.of(matchingTender, excludedTender));

        TenderSearchCriteria criteria = TenderSearchCriteria.builder()
                .keyword("GPU")
                .status(java.util.List.of(Tender.Status.PENDING_ASSIGNMENT))
                .source(java.util.List.of("外部标讯聚合平台"))
                .region("上海")
                .industry("数据中心")
                .purchaserName("西域采购")
                .purchaserHash("hash-shanghai-xiyu")
                .budgetMin(new BigDecimal("4000000.00"))
                .budgetMax(new BigDecimal("6000000.00"))
                .deadlineFrom(LocalDateTime.of(2026, 5, 1, 0, 0))
                .deadlineTo(LocalDateTime.of(2026, 5, 10, 23, 59))
                .publishDateFrom(LocalDate.of(2026, 4, 1))
                .publishDateTo(LocalDate.of(2026, 4, 30))
                .aiScoreMin(90)
                .aiScoreMax(95)
                .build();

        List<Tender> result = tenderRepository.findAll(TenderSpecification.byCriteria(criteria));

        assertThat(result)
                .extracting(Tender::getTitle)
                .containsExactly("华东数据中心 GPU 算力平台采购项目");
    }
}
