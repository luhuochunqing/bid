// Input: TenderDocumentAnalysisInput (legacy) or DocumentAnalysisInput (generic doc-insight path)
// Output: TenderRequirementProfile (legacy) or DocumentAnalysisResult (generic)
// Pos: biddraftagent/infrastructure/openai (Spring @Service, disabled in e2e profile)
package com.xiyu.bid.biddraftagent.infrastructure.openai;

import com.xiyu.bid.biddraftagent.application.TenderDocumentAnalysisInput;
import com.xiyu.bid.biddraftagent.application.TenderDocumentAnalyzer;
import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.docinsight.application.DocumentAnalysisInput;
import com.xiyu.bid.docinsight.application.DocumentAnalysisResult;
import com.xiyu.bid.docinsight.domain.DocInsightProfiles;
import com.xiyu.bid.docinsight.domain.DocumentChunk;
import com.xiyu.bid.docinsight.domain.StructuralDocumentChunker;
import com.xiyu.bid.docinsight.infrastructure.openai.BaseOpenAiDocumentAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.xiyu.bid.biddraftagent.infrastructure.openai.TenderIntakeTextProcessor.buildTenderIntakeCandidateText;
import static com.xiyu.bid.biddraftagent.infrastructure.openai.TenderIntakeTextProcessor.sanitizeUntrusted;

@Service
@Profile("!e2e")
@Slf4j
public class OpenAiTenderDocumentAnalyzer
        extends BaseOpenAiDocumentAnalyzer<TenderRequirementOutput>
        implements TenderDocumentAnalyzer {

    private static final String USE_CASE = "tender document analysis";

    private final OpenAiBidAgentConfigurationResolver configurationResolver;
    private final OpenAiStructuredOutputService structuredOutputService;

    public OpenAiTenderDocumentAnalyzer(
            OpenAiBidAgentConfigurationResolver pConfigurationResolver,
            OpenAiStructuredOutputService pStructuredOutputService,
            StructuralDocumentChunker pStructuralChunker
    ) {
        super(pStructuralChunker);
        this.configurationResolver = pConfigurationResolver;
        this.structuredOutputService = pStructuredOutputService;
    }

    @Override
    public boolean supports(String profileCode) {
        return DocInsightProfiles.supportsTenderExtraction(profileCode);
    }

    @Override
    public DocumentAnalysisResult analyze(DocumentAnalysisInput input) {
        if (!DocInsightProfiles.isTenderIntake(input.profileCode())) {
            return analyzeWithFallback(input);
        }
        String focusedText = buildTenderIntakeCandidateText(input.fullText());
        DocumentChunk focusedChunk = new DocumentChunk(focusedText, List.of("人工录入候选字段"));
        DocumentAnalysisInput focusedInput = new DocumentAnalysisInput(
                input.documentId(),
                input.fileName(),
                input.fullText(),
                input.structuredMetadata(),
                List.of(focusedChunk),
                input.profileCode(),
                input.context()
        );
        String prompt = buildPrompt(focusedInput, focusedChunk, 1, 1);
        OpenAiBidAgentRequestConfig config = configurationResolver.resolveTenderIntake();
        String providerLabel = config.baseUrl().contains("deepseek") ? "DeepSeek"
                : config.baseUrl().contains("ark.cn-beijing") ? "豆包"
                : config.baseUrl().contains("dashscope") ? "通义千问"
                : config.baseUrl().contains("api.openai") ? "OpenAI"
                : "AI";
        TenderRequirementOutput output = requestAiSafe(prompt, config, providerLabel + " tender intake extraction");
        if (output == null) {
            return new DocumentAnalysisResult(
                    input.documentId(),
                    Map.of(),
                    List.of(),
                    input.fullText(),
                    List.of("AI_DOCUMENT_ANALYSIS_FAILED: AI 文档分析服务暂不可用（可能是 API 余额不足或网络异常）。"
                            + "文档已成功存储，您可以手动填写标讯信息。")
            );
        }
        return mergeAndMap(focusedInput, List.of(output));
    }

    private DocumentAnalysisResult analyzeWithFallback(DocumentAnalysisInput input) {
        try { return super.analyze(input); }
        catch (RuntimeException ex) {
            log.warn("Document analysis failed for docId={}: {}", input.documentId(), ex.getMessage());
            return new DocumentAnalysisResult(input.documentId(), Map.of(), List.of(), input.fullText(),
                    List.of("AI_DOCUMENT_ANALYSIS_FAILED: AI 服务暂不可用，文档已存储，请手动填写。"));
        }
    }

    @Override
    public TenderRequirementProfile analyze(TenderDocumentAnalysisInput input) {
        List<DocumentChunk> chunks = getStructuralChunker().chunk(
                input.extractedText(), input.structuredMetadata());
        DocumentAnalysisInput genericInput = new DocumentAnalysisInput(
                String.valueOf(input.tenderId()), input.fileName(),
                input.extractedText(), input.structuredMetadata(),
                chunks, DocInsightProfiles.TENDER, Map.of("projectId", input.projectId())
        );
        List<TenderRequirementProfile> profiles = new ArrayList<>();
        boolean anySuccess = false;
        for (int i = 0; i < chunks.size(); i++) {
            String prompt = buildPrompt(genericInput, chunks.get(i), i + 1, chunks.size());
            TenderRequirementOutput output = requestAiSafe(prompt, configurationResolver.resolve(USE_CASE), USE_CASE);
            if (output != null) {
                profiles.add(TenderRequirementProfileMapper.toTenderProfile(output, chunks.get(i).sectionPath()));
                anySuccess = true;
            }
        }
        if (!anySuccess && profiles.isEmpty()) {
            log.warn("All AI chunk analyses failed for tenderId={}, returning empty profile",
                    input.tenderId());
        }
        return TenderRequirementProfileMerger.merge(profiles);
    }

    @Override
    protected TenderRequirementOutput requestAi(String prompt) {
        OpenAiBidAgentRequestConfig config = configurationResolver.resolve(USE_CASE);
        return requestAi(prompt, config, USE_CASE);
    }

    @Override
    protected TenderRequirementOutput requestAi(String prompt, DocumentAnalysisInput input) {
        OpenAiBidAgentRequestConfig config = DocInsightProfiles.isTenderIntake(input.profileCode())
                ? configurationResolver.resolveTenderIntake()
                : configurationResolver.resolve(USE_CASE);
        String label = DocInsightProfiles.isTenderIntake(input.profileCode())
                ? "DeepSeek tender intake extraction"
                : USE_CASE;
        return requestAi(prompt, config, label);
    }

    private TenderRequirementOutput requestAi(String prompt, OpenAiBidAgentRequestConfig config, String label) {
        Instant startedAt = Instant.now();
        log.info(
                "Starting {}: model={}, baseUrl={}, timeout={}s, promptChars={}",
                label,
                config.model(),
                config.baseUrl(),
                config.timeout().toSeconds(),
                prompt.length()
        );
        try {
            TenderRequirementOutput output = structuredOutputService.request(
                    prompt, TenderRequirementOutput.class, config,
                    "AI structured response did not include tender requirements"
            );
            log.info("{} finished in {} ms",
                    label,
                    Duration.between(startedAt, Instant.now()).toMillis());
            return output;
        } catch (RuntimeException exception) {
            log.warn("{} failed after {} ms: {}",
                    label,
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    exception.getMessage());
            throw exception;
        }
    }

    /**
     * AI 请求的安全版本 —— 失败时返回 null 而不是抛出异常。
     * 用于标讯录入场景：AI 分析是增强功能，失败不应该阻塞标讯的正常创建。
     */
    private TenderRequirementOutput requestAiSafe(String prompt, OpenAiBidAgentRequestConfig config, String label) {
        long started = System.currentTimeMillis();
        try {
            return structuredOutputService.request(prompt, TenderRequirementOutput.class, config,
                    "AI structured response did not include tender requirements");
        } catch (RuntimeException ex) {
            log.warn("{} failed after {}ms: {} — returning null (fallback, not fatal)",
                    label, System.currentTimeMillis() - started, ex.getMessage());
            return null;
        }
    }

    /** Generic doc-insight path: produces DocumentAnalysisResult with all 18 fields in extractedData. */
    @Override
    protected DocumentAnalysisResult mergeAndMap(DocumentAnalysisInput input,
                                                 List<TenderRequirementOutput> results) {
        List<TenderRequirementProfile> profiles = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            profiles.add(TenderRequirementProfileMapper.toTenderProfile(
                    results.get(i), input.chunks().get(i).sectionPath()));
        }
        TenderRequirementProfile merged = TenderRequirementProfileMerger.merge(profiles);
        Map<String, Object> data = new HashMap<>();
        data.put("projectName", merged.projectName());
        data.put("tenderTitle", merged.tenderTitle());
        data.put("tenderScope", merged.tenderScope());
        data.put("purchaserName", merged.purchaserName());
        data.put("budget", merged.budget());
        data.put("region", merged.region());
        data.put("industry", merged.industry());
        data.put("publishDate", merged.publishDate());
        data.put("deadline", merged.deadline());
        data.put("qualificationRequirements", merged.qualificationRequirements());
        data.put("technicalRequirements", merged.technicalRequirements());
        data.put("commercialRequirements", merged.commercialRequirements());
        data.put("scoringCriteria", merged.scoringCriteria());
        data.put("deadlineText", merged.deadlineText());
        data.put("requiredMaterials", merged.requiredMaterials());
        data.put("riskPoints", merged.riskPoints());
        data.put("tags", merged.tags());
        if (DocInsightProfiles.isTenderIntake(input.profileCode())) {
            putTenderIntakeFields(data, results);
            // tenderInfo 是"完整招标公告原文"，AI 只能看到关键词片段，无法输出完整原文。
            // 因此 tenderInfo 由代码直接从 fullText 截断 20000 字填充，强制覆盖任何 AI 残留输出。
            String truncatedFullText = truncateForTenderInfo(input.fullText());
            if (truncatedFullText != null) {
                data.put("tenderInfo", truncatedFullText);
            }
        }
        return new DocumentAnalysisResult(
                input.documentId(), data,
                merged.items().stream().map(TenderRequirementProfileMapper::toAnalysisItem).toList(),
                input.fullText(), List.of()
        );
    }

    private void putTenderIntakeFields(Map<String, Object> data, List<TenderRequirementOutput> results) {
        for (TenderRequirementOutput item : results) {
            if (item == null) continue;
            putIfBlank(data, "tenderAgency", item.tenderAgency); putIfBlank(data, "bidOpeningTime", item.bidOpeningTime);
            putIfBlank(data, "contactName", item.contactName); putIfBlank(data, "contactPhone", item.contactPhone);
            putIfBlank(data, "contactLandline", item.contactLandline); putIfBlank(data, "contactEmail", item.contactEmail);
            putIfBlank(data, "contactName2", item.contactName2); putIfBlank(data, "contactPhone2", item.contactPhone2);
            putIfBlank(data, "contactLandline2", item.contactLandline2); putIfBlank(data, "contactEmail2", item.contactEmail2);
            putIfBlank(data, "customerType", item.customerType); putIfBlank(data, "priority", item.priority);
        }
        TenderContactSplitter.splitMultiContactNamesIfNeeded(data);
    }

    /** tenderInfo 容量上限：与 TenderRequest @Size(max=20000) 和 DB TEXT 列保持一致。 */
    private static final int TENDER_INFO_MAX_LENGTH = 20_000;

    private static String truncateForTenderInfo(String fullText) {
        if (fullText == null || fullText.isBlank()) return null;
        return fullText.length() <= TENDER_INFO_MAX_LENGTH
                ? fullText
                : fullText.substring(0, TENDER_INFO_MAX_LENGTH);
    }

    private void putIfBlank(Map<String, Object> data, String key, String value) {
        if ((data.get(key) == null || String.valueOf(data.get(key)).isBlank()) && value != null && !value.isBlank()) {
            data.put(key, value);
        }
    }

    @Override
    protected String buildPrompt(DocumentAnalysisInput input, DocumentChunk chunk,
                                 int index, int total) {
        if (DocInsightProfiles.isTenderIntake(input.profileCode())) {
            return TenderDocumentPrompts.buildTenderIntakePrompt(input, chunk);
        }
        return TenderDocumentPrompts.buildFullTenderPrompt(input, chunk, index, total,
                getSectionContext(chunk));
    }

}
