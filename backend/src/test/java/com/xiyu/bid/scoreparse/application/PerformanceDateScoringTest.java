package com.xiyu.bid.scoreparse.application;
import com.xiyu.bid.scoreparse.domain.*;
import com.xiyu.bid.scoreparse.entity.*;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreAssessmentOutput;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class PerformanceDateScoringTest {
    ScoreResult evaluate(String date) throws Exception {
        return evaluateBody("类似项目供货合同：项目A，合同签订日期"+date+"，合同金额100万元。", "2023年1月1日至今，以合同签订时间为准，每提供一个业绩得5分，满分5分");
    }
    ScoreResult evaluateBody(String body, String detail) throws Exception {
        return evaluateBody(body, detail, body);
    }
    ScoreResult evaluateBody(String body, String detail, String quote) throws Exception {
        var document=new ScoringDocument(body,List.of(),new ScoringExtractionReport(1,"fixture","COMPLETE",1,1,0,0,0,0,List.of(),List.of(),List.of(),0));
        var row=ScoreItem.builder().id(1L).dim("供应商业绩").detail(detail).weight(new BigDecimal("5")).scoreType("OBJECTIVE").build();
        var output=new ScoreAssessmentOutput();output.actualScore=5d;output.matchRatio=100;output.quote=quote;output.quoteMissing=false;
        var method=ScoreItemAssessor.class.getDeclaredMethod("validateScoring",ScoreParseTask.class,ScoreItem.class,ScoringDocument.class,String.class,ScoreAssessmentOutput.class,boolean.class);
        method.setAccessible(true);
        return (ScoreResult)method.invoke(new ScoreItemAssessor(null,new ImageBudget(3,1024)),ScoreParseTask.builder().id(1L).build(),row,document,body,output,false);
    }
    @Test void verifiedOldContractQuoteCannotPublishPositiveScore() throws Exception {
        var result=evaluate("2020年5月27日");assertThat(result.getActualScore()).isNull();
        assertThat(result.getMissedReason()).contains("日期");
    }
    @Test void missingSigningDateCannotPublishDateConstrainedPositiveScore() throws Exception {
        assertThat(evaluateBody("类似项目供货合同：项目A，合同名称2023年度工业品采购。", "2023年1月1日至今，以合同签订时间为准，业绩满分5分").getActualScore()).isNull();
    }
    @Test void insufficientAmountCannotPublishPositiveScore() throws Exception {
        assertThat(evaluateBody("类似项目供货合同：项目A，合同签订日期2023年1月1日，合同金额10万元。", "2023年1月1日至今，合同金额不少于100万元，业绩满分5分").getActualScore()).isNull();
    }
    @Test void matchingBoundaryKeepsExistingPublicationPath() throws Exception {
        assertThat(evaluate("2023年1月1日").getActualScore()).isEqualByComparingTo("5");
    }
    @Test void verifiedAttachmentsDoNotInheritKnowledgeEstimatePendingChecks() throws Exception {
        for (String material : List.of("合同关键页", "合同复印件", "发票")) {
            String body="同类平台供货业绩：项目A，合同签订日期"+java.time.LocalDate.now().minusYears(1)
                    +"，合同金额100万元。"+material+"：采购方甲，供货方乙，平台设备供货，金额100万元。";
            var result=evaluateBody(body,"近三年内每提供一个同类平台供货业绩得2分，最高得10分，须提供"+material+"。");
            assertThat(result.getActualScore()).as(material).isEqualByComparingTo("5");
        }
    }
    @Test void attachmentRequirementStillRejectsInvalidDateAndAmount() throws Exception {
        String detail="2023年1月1日至2025年12月31日，合同金额不少于100万元，须提供合同关键页，业绩满分5分";
        assertThat(evaluateBody("合同关键页：合同签订日期2020年5月27日，合同金额100万元。",detail).getActualScore()).isNull();
        assertThat(evaluateBody("合同关键页：合同签订日期2024年5月27日，合同金额10万元。",detail).getActualScore()).isNull();
        assertThat(evaluateBody("合同关键页：项目A，合同金额100万元。",detail).getActualScore()).isNull();
    }
    @Test void attachmentRequirementDoesNotBypassQuoteVerification() throws Exception {
        var result=evaluateBody("项目A，仅有业绩目录。","须提供合同关键页，业绩满分5分",
                "合同关键页：合同签订日期2024年5月27日，采购方甲，供货方乙，金额100万元。");
        assertThat(result.getActualScore()).isNull();
    }
    @Test void attachmentDoesNotResolveUnknownQuotationDeadline() throws Exception {
        var result=evaluateBody("合同关键页：合同签订日期2024年5月27日，合同金额100万元。",
                "2023年1月1日至首次报价截止日，以合同签订日期为准，须提供合同关键页，业绩满分5分");
        assertThat(result.getActualScore()).isNull();
        assertThat(result.getMissedReason()).contains("截止日");
    }
}
