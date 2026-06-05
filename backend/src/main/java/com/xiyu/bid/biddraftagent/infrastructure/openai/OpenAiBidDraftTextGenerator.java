package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.application.BidDraftGenerationResult;
import com.xiyu.bid.biddraftagent.application.BidDraftTextGenerator;
import com.xiyu.bid.biddraftagent.application.GeneratedArtifactSpec;
import com.xiyu.bid.biddraftagent.domain.BidDraftSnapshot;
import com.xiyu.bid.biddraftagent.domain.GapCheckResult;
import com.xiyu.bid.biddraftagent.domain.ManualConfirmationDecision;
import com.xiyu.bid.biddraftagent.domain.MaterialMatchScore;
import com.xiyu.bid.biddraftagent.domain.RequirementClassification;
import com.xiyu.bid.biddraftagent.domain.WriteCoverageDecision;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpenAiBidDraftTextGenerator implements BidDraftTextGenerator {

    private static final String USE_CASE = "bid draft generation";

    private final OpenAiBidAgentConfigurationResolver configurationResolver;
    private final OpenAiStructuredOutputService structuredOutputService;

    public OpenAiBidDraftTextGenerator(
            OpenAiBidAgentConfigurationResolver pConfigurationResolver,
            OpenAiStructuredOutputService pStructuredOutputService
    ) {
        this.configurationResolver = pConfigurationResolver;
        this.structuredOutputService = pStructuredOutputService;
    }

    @Override
    public BidDraftGenerationResult generate(
            BidDraftSnapshot snapshot,
            RequirementClassification classification,
            MaterialMatchScore materialMatchScore,
            GapCheckResult gapCheck,
            ManualConfirmationDecision manualConfirmation,
            WriteCoverageDecision writeCoverage
    ) {
        DraftOutput output = requestDraft(buildPrompt(
                snapshot,
                classification,
                materialMatchScore,
                gapCheck,
                manualConfirmation,
                writeCoverage
        ));
        String draftText = requireText(output.draftText, "draftText");
        String reviewSummary = requireText(output.reviewSummary, "reviewSummary");
        String handoffChecklist = requireText(output.handoffChecklist, "handoffChecklist");

        return new BidDraftGenerationResult(
                draftText,
                reviewSummary,
                List.of(
                        new GeneratedArtifactSpec("DRAFT_TEXT", "自动生成投标草稿", draftText, "document-writer"),
                        new GeneratedArtifactSpec("REVIEW_SUMMARY", "草稿审阅摘要", reviewSummary, "bid-reviewer"),
                        new GeneratedArtifactSpec("HANDOFF_CHECKLIST", "文档写手交接清单", handoffChecklist, "document-writer")
                )
        );
    }

    private DraftOutput requestDraft(String prompt) {
        OpenAiBidAgentRequestConfig config = configurationResolver.resolve(USE_CASE);
        return structuredOutputService.request(
                prompt,
                DraftOutput.class,
                config,
                "OpenAI structured response did not include bid draft output"
        );
    }

    private String buildPrompt(
            BidDraftSnapshot snapshot,
            RequirementClassification classification,
            MaterialMatchScore materialMatchScore,
            GapCheckResult gapCheck,
            ManualConfirmationDecision manualConfirmation,
            WriteCoverageDecision writeCoverage
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                你是企业投标写作助手。请根据结构化项目快照生成可审阅的中文标书初稿。
                必须遵守：
                - 不得自动定稿最终报价、法务承诺或资质真实性。
                - 涉及报价、法务、资质真实性、关键商务偏离时，必须明确标记“需人工确认”。
                - 内容应包含来源线索、覆盖度、缺漏项和下一步建议。
                - 返回结构化字段 draftText、reviewSummary、handoffChecklist。

                """);
        appendLine(prompt, "项目名称", snapshot.projectName());
        appendLine(prompt, "项目描述", snapshot.projectDescription());
        appendLine(prompt, "标讯标题", snapshot.tenderTitle());
        appendLine(prompt, "招标描述", snapshot.tenderDescription());
        appendLine(prompt, "客户名称", snapshot.customerName());
        appendLine(prompt, "行业", snapshot.industry());
        appendLine(prompt, "区域", snapshot.region());
        appendLine(prompt, "预算", snapshot.budget() == null ? null : snapshot.budget().toPlainString());
        appendLine(prompt, "截止日期", snapshot.deadline() == null ? null : snapshot.deadline().toString());
        appendList(prompt, "价格要求", classification.pricingRequirements());
        appendList(prompt, "法务要求", classification.legalRequirements());
        appendList(prompt, "资质要求", classification.qualificationRequirements());
        appendList(prompt, "技术要求", classification.technicalRequirements());
        appendList(prompt, "交付要求", classification.deliveryRequirements());
        appendList(prompt, "商务要求", classification.commercialRequirements());
        appendList(prompt, "结构化招标要求", snapshot.structuredRequirementSignals());
        appendList(prompt, "必须提供材料", snapshot.requiredMaterialSignals());
        appendList(prompt, "评分标准线索", snapshot.scoringSignals());
        appendList(prompt, "资质材料线索", snapshot.qualificationSignals());
        appendList(prompt, "模板线索", snapshot.templateSignals());
        appendList(prompt, "案例线索", snapshot.caseSignals());
        appendLine(prompt, "材料匹配得分", String.valueOf(materialMatchScore.score()));
        appendList(prompt, "缺漏项", gapCheck.gaps());
        appendList(prompt, "补充建议", gapCheck.suggestions());
        appendLine(prompt, "写作覆盖得分", String.valueOf(writeCoverage.coverageScore()));
        appendList(prompt, "建议章节", writeCoverage.recommendedSections());
        appendList(prompt, "人工确认原因", manualConfirmation.reasons());
        return prompt.toString();
    }

    private void appendLine(StringBuilder prompt, String label, String value) {
        if (value != null && !value.isBlank()) {
            prompt.append(label).append(": ").append(value).append('\n');
        }
    }

    private void appendList(StringBuilder prompt, String label, List<String> values) {
        prompt.append(label).append(": ");
        prompt.append(values == null || values.isEmpty() ? "无" : String.join("；", values));
        prompt.append('\n');
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("OpenAI bid draft output missing " + fieldName);
        }
        return value.trim();
    }

    public static class DraftOutput {
        public String draftText;
        public String reviewSummary;
        public String handoffChecklist;
    }
}
