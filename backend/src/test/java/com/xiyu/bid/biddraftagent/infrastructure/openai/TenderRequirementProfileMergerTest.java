package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementItemSnapshot;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenderRequirementProfileMergerTest {

    @Test
    void merge_shouldCombineChunkProfilesAndDeduplicateSignals() {
        TenderRequirementItemSnapshot sharedItem = new TenderRequirementItemSnapshot(
                "technical",
                "接口能力",
                "支持电子商城接口对接",
                true,
                "接口对接",
                92
        );
        TenderRequirementProfile first = profile(
                "中国兵器电子商城项目",
                List.of("具备独立法人资格"),
                List.of("支持电子商城接口对接"),
                List.of(sharedItem)
        );
        TenderRequirementProfile second = profile(
                null,
                List.of("具备独立法人资格", "提供售后服务承诺"),
                List.of("支持电子商城接口对接", "提供商品上架能力"),
                List.of(sharedItem, new TenderRequirementItemSnapshot(
                        "material",
                        "附件清单",
                        "提供营业执照扫描件",
                        true,
                        "营业执照",
                        88
                ))
        );

        TenderRequirementProfile merged = TenderRequirementProfileMerger.merge(List.of(first, second));

        assertThat(merged.projectName()).isEqualTo("中国兵器电子商城项目");
        assertThat(merged.budget()).isEqualByComparingTo("6800000");
        assertThat(merged.region()).isEqualTo("新疆乌鲁木齐");
        assertThat(merged.industry()).isEqualTo("工业品电商");
        assertThat(merged.publishDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(merged.deadline()).isEqualTo(LocalDateTime.of(2026, 5, 30, 17, 30));
        assertThat(merged.qualificationRequirements())
                .containsExactly("具备独立法人资格", "提供售后服务承诺");
        assertThat(merged.technicalRequirements())
                .containsExactly("支持电子商城接口对接", "提供商品上架能力");
        assertThat(merged.items()).hasSize(2);
    }

    @Test
    void parser_shouldReturnNullForUnconfirmedStructuredValues() {
        assertThat(TenderFieldParser.parseBudget("6800000.50"))
                .isEqualByComparingTo("6800000.50");
        assertThat(TenderFieldParser.parseBudget("680万元"))
                .isEqualByComparingTo("6800000");
        assertThat(TenderFieldParser.parseBudget("约680万元")).isNull();
        assertThat(TenderFieldParser.parsePublishDate("2026-04-01"))
                .isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(TenderFieldParser.parsePublishDate("2026-02-30")).isNull();
        assertThat(TenderFieldParser.parseDeadline("2026-05-30T17:30:00"))
                .isEqualTo(LocalDateTime.of(2026, 5, 30, 17, 30));
        assertThat(TenderFieldParser.parseDeadline("2026-05-30"))
                .isEqualTo(LocalDateTime.of(2026, 5, 30, 23, 59, 59));
        assertThat(TenderFieldParser.parseDeadline("2026-05-30 17:30")).isNull();
    }

    private TenderRequirementProfile profile(
            String projectName,
            List<String> qualificationRequirements,
            List<String> technicalRequirements,
            List<TenderRequirementItemSnapshot> items
    ) {
        return new TenderRequirementProfile(
                projectName,
                "谈判采购文件",
                "电商供应商引入",
                "中国兵器装备集团有限公司",
                projectName == null ? null : new BigDecimal("6800000"),
                projectName == null ? null : "新疆乌鲁木齐",
                projectName == null ? null : "工业品电商",
                projectName == null ? null : LocalDate.of(2026, 4, 1),
                projectName == null ? null : LocalDateTime.of(2026, 5, 30, 17, 30),
                qualificationRequirements,
                technicalRequirements,
                List.of(),
                List.of(),
                "2024-09-24",
                List.of(),
                List.of(),
                List.of(),
                items
        );
    }
}
