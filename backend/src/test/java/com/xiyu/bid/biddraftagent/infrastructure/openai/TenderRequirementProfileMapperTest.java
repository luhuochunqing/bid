// Input: TenderRequirementOutput instances (all fields populated or partially null)
// Output: assertions on TenderRequirementProfile and AnalysisRequirementItem conversions
// Pos: biddraftagent/infrastructure/openai (unit test)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementItemSnapshot;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.docinsight.application.DocumentAnalysisResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenderRequirementProfileMapperTest {

    // ── toTenderProfile – all 18 output fields round-trip ─────────────────────

    @Test
    void toTenderProfile_allFieldsPopulated_roundTripComplete() {
        TenderRequirementOutput out = new TenderRequirementOutput();
        out.projectName = "西域信息系统采购";
        out.tenderTitle = "招标公告标题";
        out.tenderScope = "软件系统集成";
        out.purchaserName = "西域集团有限公司";
        out.budget = "6800000";
        out.region = "新疆";
        out.industry = "信息技术";
        out.publishDate = "2024-01-15";
        out.deadline = "2024-03-20T17:00:00";
        out.qualificationRequirements = List.of("具备软件开发资质");
        out.technicalRequirements = List.of("支持国产化操作系统");
        out.commercialRequirements = List.of("价格不超过预算");
        out.scoringCriteria = List.of("技术分60，商务分40");
        out.deadlineText = "2024年3月20日17时整";
        out.requiredMaterials = List.of("营业执照", "资质证书");
        out.riskPoints = List.of("交付周期风险");
        out.tags = List.of("信息化", "集成");

        TenderRequirementProfile profile = TenderRequirementProfileMapper.toTenderProfile(out, List.of("第一章"));

        assertThat(profile.projectName()).isEqualTo("西域信息系统采购");
        assertThat(profile.tenderTitle()).isEqualTo("招标公告标题");
        assertThat(profile.tenderScope()).isEqualTo("软件系统集成");
        assertThat(profile.purchaserName()).isEqualTo("西域集团有限公司");
        assertThat(profile.budget()).isEqualByComparingTo(new BigDecimal("6800000"));
        assertThat(profile.region()).isEqualTo("新疆");
        assertThat(profile.industry()).isEqualTo("信息技术");
        assertThat(profile.publishDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(profile.deadline()).isEqualTo(LocalDateTime.of(2024, 3, 20, 17, 0, 0));
        assertThat(profile.qualificationRequirements()).containsExactly("具备软件开发资质");
        assertThat(profile.technicalRequirements()).containsExactly("支持国产化操作系统");
        assertThat(profile.commercialRequirements()).containsExactly("价格不超过预算");
        assertThat(profile.scoringCriteria()).containsExactly("技术分60，商务分40");
        assertThat(profile.deadlineText()).isEqualTo("2024年3月20日17时整");
        assertThat(profile.requiredMaterials()).containsExactly("营业执照", "资质证书");
        assertThat(profile.riskPoints()).containsExactly("交付周期风险");
        assertThat(profile.tags()).containsExactly("信息化", "集成");
    }

    @Test
    void toTenderProfile_nullLists_producesEmptyLists() {
        TenderRequirementOutput out = new TenderRequirementOutput();
        // all list fields remain null

        TenderRequirementProfile profile = TenderRequirementProfileMapper.toTenderProfile(out, List.of());

        assertThat(profile.qualificationRequirements()).isEmpty();
        assertThat(profile.technicalRequirements()).isEmpty();
        assertThat(profile.commercialRequirements()).isEmpty();
        assertThat(profile.scoringCriteria()).isEmpty();
        assertThat(profile.requiredMaterials()).isEmpty();
        assertThat(profile.riskPoints()).isEmpty();
        assertThat(profile.tags()).isEmpty();
        assertThat(profile.items()).isEmpty();
    }

    // ── toTenderItem – sectionPath resolution ─────────────────────────────────

    @Test
    void toTenderItem_aiPathPresent_aiPathTakesPrecedence() {
        TenderRequirementItemOutput item = new TenderRequirementItemOutput();
        item.category = "technical";
        item.sectionPath = "第二章 > 2.1 技术规格";

        TenderRequirementItemSnapshot snapshot =
                TenderRequirementProfileMapper.toTenderItem(item, "默认路径");

        assertThat(snapshot.sectionPath()).isEqualTo("第二章 > 2.1 技术规格");
    }

    @Test
    void toTenderItem_aiPathBlank_fallsBackToDefault() {
        TenderRequirementItemOutput item = new TenderRequirementItemOutput();
        item.category = "qualification";
        item.sectionPath = "";

        TenderRequirementItemSnapshot snapshot =
                TenderRequirementProfileMapper.toTenderItem(item, "第一章");

        assertThat(snapshot.sectionPath()).isEqualTo("第一章");
    }

    @Test
    void toTenderItem_aiPathNull_fallsBackToDefault() {
        TenderRequirementItemOutput item = new TenderRequirementItemOutput();
        item.sectionPath = null;

        TenderRequirementItemSnapshot snapshot =
                TenderRequirementProfileMapper.toTenderItem(item, "默认路径");

        assertThat(snapshot.sectionPath()).isEqualTo("默认路径");
    }

    // ── toAnalysisItem – round-trip ────────────────────────────────────────────

    @Test
    void toAnalysisItem_mapsAllFields() {
        TenderRequirementItemSnapshot snapshot = new TenderRequirementItemSnapshot(
                "technical", "服务器配置", "需支持国产CPU", true,
                "服务器应支持", 85, "第二章"
        );

        DocumentAnalysisResult.AnalysisRequirementItem item =
                TenderRequirementProfileMapper.toAnalysisItem(snapshot);

        assertThat(item.category()).isEqualTo("technical");
        assertThat(item.title()).isEqualTo("服务器配置");
        assertThat(item.content()).isEqualTo("需支持国产CPU");
        assertThat(item.mandatory()).isTrue();
        assertThat(item.sourceExcerpt()).isEqualTo("服务器应支持");
        assertThat(item.confidence()).isEqualTo(85);
        assertThat(item.sectionPath()).isEqualTo("第二章");
    }

    // ── toSnapshot – round-trip ────────────────────────────────────────────────

    @Test
    void toSnapshot_mapsAllFields() {
        DocumentAnalysisResult.AnalysisRequirementItem item =
                new DocumentAnalysisResult.AnalysisRequirementItem(
                        "commercial", "付款条件", "验收后30日内付款", false,
                        "验收后30日", 90, "第三章"
                );

        TenderRequirementItemSnapshot snapshot = TenderRequirementProfileMapper.toSnapshot(item);

        assertThat(snapshot.category()).isEqualTo("commercial");
        assertThat(snapshot.title()).isEqualTo("付款条件");
        assertThat(snapshot.content()).isEqualTo("验收后30日内付款");
        assertThat(snapshot.mandatory()).isFalse();
        assertThat(snapshot.sourceExcerpt()).isEqualTo("验收后30日");
        assertThat(snapshot.confidence()).isEqualTo(90);
        assertThat(snapshot.sectionPath()).isEqualTo("第三章");
    }
}
