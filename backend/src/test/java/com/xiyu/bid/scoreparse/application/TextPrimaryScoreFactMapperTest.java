package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextPrimaryScoreFactMapperTest {

    @Test void exactNameCellAnchorCanRestoreTheOnlyClosedRuleInItsSourceRow() {
        var artifact = namedRuleRow();
        var model = new MultimodalScoreFact("第三章 评审办法", "1", "", "平台对接", BigDecimal.valueOf(5),
                "", "OBJECTIVE", 1, "t", List.of("r1c1"), "平台对接（5分）", .9);
        var validation = new MultimodalScoreFactValidator().validate(List.of(model), artifact, BigDecimal.valueOf(5));
        assertThat(TextPrimaryScoreFactMapper.map(model, artifact, validation).sourceText())
                .isEqualTo("支持系统对接得5分，提供承诺书。");
    }

    @Test void tamperedNameCellAnchorCannotRestoreAnUnrelatedRow() {
        var artifact = namedRuleRow();
        var model = new MultimodalScoreFact("第三章 评审办法", "1", "", "平台对接", BigDecimal.valueOf(5),
                "", "OBJECTIVE", 1, "t", List.of("r1c1"), "不存在的名称（5分）", .9);
        var validation = new MultimodalScoreFactValidator().validate(List.of(model), artifact, BigDecimal.valueOf(5));
        assertThat(TextPrimaryScoreFactMapper.map(model, artifact, validation).sourceText()).isEmpty();
    }

    private static com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact namedRuleRow() {
        var cells = new java.util.ArrayList<com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows = {{"序号", "评审因素", "评审标准", "分值"},
                {"1", "平台对接（5分）", "支持系统对接得5分，提供承诺书。", "5"}};
        for (int r = 0; r < rows.length; r++) for (int c = 0; c < rows[r].length; c++) {
            String id = "r" + r + "c" + c;
            cells.add(new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto(
                    id, r, c, rows[r][c], rows[r][c], 1, 1, false, "pdf:page=1", 1, "t-r" + r));
        }
        var table = new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable("t", 1,
                "第三章 评审办法", 2, 4, List.of(), cells, true, "PDF");
        return new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact(
                new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta("doc", "hash", "PDF", "2.2"),
                List.of(table), List.of());
    }

    @Test
    void bounds_fact_id_for_long_section_titles_and_preserves_parent_link() {
        String sectionTitle = "技术评审部分".repeat(20);
        MultimodalScoreFact parent = fact(sectionTitle, "1", "", 10);
        MultimodalScoreFact child = fact(sectionTitle, "1.1", "1", 10);
        var validation = new MultimodalScoreFactValidator()
                .validate(List.of(parent, child), null, BigDecimal.TEN);

        var mappedParent = TextPrimaryScoreFactMapper.map(parent, null, validation);
        var mappedChild = TextPrimaryScoreFactMapper.map(child, null, validation);

        assertThat(mappedParent.factId()).hasSizeLessThanOrEqualTo(64);
        assertThat(mappedChild.factId()).hasSizeLessThanOrEqualTo(64);
        assertThat(mappedChild.parentFactId()).isEqualTo(mappedParent.factId());
        assertThat(mappedChild.sourceMetadata().modelSourceClaim().sectionTitle()).isEqualTo(sectionTitle);
        assertThat(mappedChild.sourceMetadata().modelScoreType()).isEqualTo("OBJECTIVE");
        assertThat(mappedChild.withParentFactId("new").withDimension("新名称")
                .withValidationStatus(com.xiyu.bid.scoreparse.domain.ScoreFact.ValidationStatus.REVIEW_REQUIRED)
                .sourceMetadata()).isEqualTo(mappedChild.sourceMetadata());
    }

    @Test
    void preserves_parent_link_when_source_paragraph_and_detail_tables_use_different_sections() {
        String schemeId = com.xiyu.bid.scoreparse.domain.OriginalScoringContext.schemeId("第三章 评审标准");
        MultimodalScoreFact parent = fact("评审标准", "BUSINESS", "", 40)
                .withScope(schemeId, "IN_SCHEME");
        MultimodalScoreFact child = fact("商务评分表", "B1", "BUSINESS", 40)
                .withScope(schemeId, "IN_SCHEME");
        var validation = new MultimodalScoreFactValidator()
                .validate(List.of(parent, child), null, BigDecimal.valueOf(40));

        var mappedParent = TextPrimaryScoreFactMapper.map(parent, null, validation);
        var mappedChild = TextPrimaryScoreFactMapper.map(child, null, validation);

        assertThat(mappedChild.parentFactId()).isEqualTo(mappedParent.factId());
    }

    @Test
    void source_side_violations_attach_to_the_fact_that_owns_them() {
        // 来源侧问题的 factId 是 modelFactId；过滤键若退化成「方案+状态+章节+code」，问题就挂不回
        // 事实，复核人只看到一条没有原因的事实。空 code 兄弟共用后者那个键 —— 本类缺陷的现场。
        MultimodalScoreFact blankA = fact("第三章 评审办法", "", "", 7);
        MultimodalScoreFact blankB = fact("第三章 评审办法", "", "", 3);
        var validation = new MultimodalScoreFactValidator()
                .validate(List.of(blankA, blankB), null, BigDecimal.TEN);

        var perFact = validation.violations().stream()
                .filter(violation -> !violation.factId().isBlank()).toList();
        assertThat(perFact).as("来源侧问题必须带 factId").isNotEmpty();

        for (MultimodalScoreFact blank : List.of(blankA, blankB)) {
            String owner = com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.modelFactId(blank);
            var ownProblem = perFact.stream().filter(violation -> owner.equals(violation.factId()))
                    .findFirst().orElseThrow();
            assertThat(TextPrimaryScoreFactMapper.map(blank, null, validation).weightEvidence().explanation())
                    .contains(ownProblem.code());
        }
    }

    @Test void sharedCellRulesRetainTheirOwnMaterialsAndThresholds() {
        String first="第一部分：一级集采商品（合计28分）\n每偏离1%扣0.2分。材料要求：报价表1。";
        String second="第二部分：非一级集采商品（合计12分）\n每偏离1%扣0.5分。材料要求：报价表2。";
        var a=new com.xiyu.bid.scoreparse.domain.OriginalRuleUnitText.Unit("一级集采商品","价格","合计28分");
        var b=new com.xiyu.bid.scoreparse.domain.OriginalRuleUnitText.Unit("非一级集采商品","价格","合计12分");
        assertThat(com.xiyu.bid.scoreparse.domain.OriginalRuleUnitText.restore(first+"\n"+second,a,List.of(a,b))).isEqualTo(first);
        assertThat(com.xiyu.bid.scoreparse.domain.OriginalRuleUnitText.restore(first+"\n"+second,b,List.of(a,b))).isEqualTo(second);
    }
    static com.xiyu.bid.scoreparse.domain.ScoreFact ambiguousSharedRule() {
        String raw="商品甲满分28分；商品乙满分12分";
        var cell=new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto("c",0,0,raw,raw,1,1,false,"/w:document/w:body/w:tbl[1]/w:tr[1]/w:tc[1]",1);
        var table=new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable("t",1,"第三章 评审办法",1,1,List.of(),List.of(cell),true,"DOCX");
        var artifact=new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact(new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta("doc","hash","DOCX","v"),List.of(table),List.of());
        var a=new MultimodalScoreFact("第三章 评审办法","a","","甲",new BigDecimal("28"),"甲模型说明","OBJECTIVE",1,"t",List.of("c"),"满分28分",.8);
        var b=new MultimodalScoreFact("第三章 评审办法","b","","乙",new BigDecimal("12"),"乙模型说明","OBJECTIVE",1,"t",List.of("c"),"满分12分",.8);
        var validation=new MultimodalScoreFactValidator().validate(List.of(a,b),artifact,new BigDecimal("40"));
        return TextPrimaryScoreFactMapper.map(a,artifact,validation);
    }
    @Test void ambiguousSharedCellDoesNotPretendTheWholeCellIsOneOriginalRule() {
        var mapped=ambiguousSharedRule();
        assertThat(mapped.sourceText()).isEmpty();
        assertThat(mapped.sourceMetadata().diagnostics()).contains("FULL_RULE_UNIT_UNRESOLVED");
        assertThat(mapped.sourceMetadata().sourceSpans().getFirst().rawText()).isEqualTo("商品甲满分28分；商品乙满分12分");
    }
    static final String COMPLETE_RULE = "业绩综合评分最高10分。\n1、合同材料（5分）\n提供合同首页及签章页得5分，缺少任一项不得分。\n2、验收证明（5分）\n提供对应验收证明得5分，未提供不得分。";
    static com.xiyu.bid.scoreparse.domain.ScoreFact mappedCompleteRule() {
        var cells = new java.util.ArrayList<com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows = {{"序号", "评分因素", "评分规则", "分值"}, {"1", "业绩", COMPLETE_RULE, "10分"},
                {"2", "其他评分项", "其他评分项最高20分，另需资格证书", "20分"}};
        for (int r = 0; r < rows.length; r++) for (int c = 0; c < rows[r].length; c++) {
            String id = r == 1 && c == 2 ? "c" : "r" + r + "c" + c;
            cells.add(new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto(id, r, c,
                    rows[r][c], rows[r][c], 1, 1, false, "/w:document/w:body/w:tbl[1]/w:tr[" + (r + 1) + "]/w:tc[" + (c + 1) + "]", null, "t-r" + r));
        }
        var table=new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable("t",null,"第三章 评审办法",3,4,List.of(),cells,true,"DOCX");
        var artifact=new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact(new com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta("doc","hash","DOCX","v"),List.of(table),List.of());
        var model=new MultimodalScoreFact("第三章 评审办法","1","","业绩",BigDecimal.TEN,"按项目经验评分","OBJECTIVE",1,"t",List.of("c"),"最高10分",.8);
        var validation=new MultimodalScoreFactValidator().validate(List.of(model),artifact,BigDecimal.TEN);
        return TextPrimaryScoreFactMapper.map(model,artifact,validation);
    }
    @Test void shortQuoteRestoresOnlyCompleteCitedRuleUnit() {
        var mapped=mappedCompleteRule();
        assertThat(mapped.sourceText()).isEqualTo(COMPLETE_RULE).doesNotContain("资格证书");
        assertThat(mapped.sourceMetadata().modelQuotedText()).isEqualTo("最高10分");
        assertThat(mapped.sourceMetadata().modelRuleText()).isEqualTo("按项目经验评分");
    }

    private static MultimodalScoreFact fact(String sectionTitle, String code, String parentCode, int weight) {
        return new MultimodalScoreFact(sectionTitle, code, parentCode, "技术方案",
                BigDecimal.valueOf(weight), "满足要求得" + weight + "分", "OBJECTIVE", 1,
                "table-1", List.of("cell-" + code), "满足要求", 1.0);
    }
}
