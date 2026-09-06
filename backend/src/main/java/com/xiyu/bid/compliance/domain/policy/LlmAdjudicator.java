// Input: 语义检查项 + 投标/招标文件 + TenderRequirements + KnowledgeMatch + LLM 通道（port，值返回）
// Output: 单项裁决 PASS/FAIL/MANUAL（reasonCode ∈ PRD §3.5）+ 真实定位证据；失败/Noop 降级 MANUAL 不阻断，兜底结论为用户可读文案（不暴露技术细节）
// Pos: compliance/domain/policy/ - LLM 混合裁决 lane（spec 049 US5 / T047-T049，research.md R6，FP-Java：值返回无异常流）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * LLM 混合裁决 lane（纯核心，spec 049 第五步）.
 *
 * <p>语义项经 {@link LlmVerdict.LlmChatPort} 交由真实 LLM 裁决，LLM 只做裁决层不做全量直出；
 * 通道侧一切失败（AI 未启用 / 调用异常 / 响应非法）由边界适配器吸收为
 * {@link LlmVerdict.ChatOutcome} 值，本 lane 无异常流：
 * 单项失败置 MANUAL + LOW_CONFIDENCE 不阻断整体，兜底结论为用户可读文案（不暴露技术细节）；
 * AI 未启用（Noop）时语义项全部 MANUAL 并注明原因，任务保持 COMPLETED（PRD §3.5 / research.md R6）.
 */
public final class LlmAdjudicator {

    /** 进度回调（参照 scoreparse OpenAiScoreAnalyzer 范式）：progress 0-100 + 当前步骤文案. */
    @FunctionalInterface
    public interface ProgressReporter extends BiConsumer<Integer, String> {
    }

    /** prompt 内知识库记录条数上限. */
    private static final int MAX_KB_MATCHES_IN_PROMPT = 20;
    /** 单条知识库记录摘要长度上限. */
    private static final int KB_MATCH_TEXT_BUDGET = 120;

    /** 裁决失败兜底结论（用户可读，不暴露技术细节）. */
    private static final String FALLBACK_INVALID_CONCLUSION =
            "AI 本次未能自动裁决该条款（返回结果异常），已转人工确认，请对照检查内容人工复核";
    private static final String FALLBACK_FAILURE_CONCLUSION =
            "AI 本次未能自动裁决该条款（调用异常），已转人工确认，请对照检查内容人工复核";
    private static final String FALLBACK_MANUAL_SUGGESTION = "请人工复核该检查项内容后确认";

    private final LlmVerdict.LlmChatPort<LlmVerdict> port;

    public LlmAdjudicator(final LlmVerdict.LlmChatPort<LlmVerdict> port) {
        this.port = port;
    }

    /**
     * 对全部 7 项语义条款执行 LLM 裁决（含进度上报）.
     *
     * @param failedKnowledgeSources 失败知识库源（供 prompt 注明数据缺口；guard 由规则 lane 负责）
     * @param progress 进度回调，每完成一项上报一次，最后一项 progress=100
     * @return itemCode → 裁决结果（覆盖全部语义项）
     */
    public Map<String, QualityCheckItemResult> adjudicate(
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final TenderRequirements requirements,
            final List<KnowledgeMatch> kbMatches,
            final Set<String> failedKnowledgeSources,
            final ProgressReporter progress) {
        List<QualityEvidence> kbEvidences = kbMatches == null ? List.of()
                : kbMatches.stream().map(QualityEvidence::fromKnowledgeMatch).toList();
        Map<String, QualityCheckItemResult> outcomes = new LinkedHashMap<>();
        List<BidQualityCheckItem> items = SemanticRulesLane.SEMANTIC_ITEMS;
        for (int i = 0; i < items.size(); i++) {
            BidQualityCheckItem item = items.get(i);
            outcomes.put(item.getCode(),
                    adjudicateItem(item, bidDoc, tenderDoc, requirements, kbMatches, kbEvidences));
            progress.accept((i + 1) * 100 / items.size(), item.getTitle());
        }
        return outcomes;
    }

    /**
     * 单项 LLM 语义裁决：任何失败分支都降级 MANUAL，不抛异常、不阻断整体.
     *
     * <p>用 instanceof 序贯判定而非 switch 模式匹配：后者编译产物含 MatchException 构造，
     * 会触发 FP 门禁（纯核心禁止构造 Throwable）.
     */
    public QualityCheckItemResult adjudicateItem(
            final BidQualityCheckItem item,
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final TenderRequirements requirements,
            final List<KnowledgeMatch> kbMatches,
            final List<QualityEvidence> kbEvidences) {
        List<QualityEvidence> evidences =
                EvidenceAssembler.semanticEvidences(item, bidDoc, tenderDoc, requirements, kbEvidences);
        LlmVerdict.ChatOutcome<LlmVerdict> outcome =
                port.chat(buildPrompt(item, bidDoc, tenderDoc, requirements, kbMatches));
        if (outcome instanceof LlmVerdict.Success<LlmVerdict> success) {
            return toResult(item, success.verdict(), evidences);
        }
        if (outcome instanceof LlmVerdict.Noop<LlmVerdict>) {
            return ManualReasons.manual(item,
                    ManualReasons.MANUAL_REVIEW_REQUIRED, "AI 未启用，语义条款未经 LLM 裁决，请人工确认",
                    "请在系统设置中启用 AI 后重新核查", evidences);
        }
        if (outcome instanceof LlmVerdict.Failure<LlmVerdict>) {
            return ManualReasons.manual(item,
                    ManualReasons.LOW_CONFIDENCE, FALLBACK_FAILURE_CONCLUSION,
                    FALLBACK_MANUAL_SUGGESTION, evidences);
        }
        return ManualReasons.manual(item,
                ManualReasons.LOW_CONFIDENCE, FALLBACK_INVALID_CONCLUSION,
                FALLBACK_MANUAL_SUGGESTION, evidences);
    }

    /**
     * 裁决结论 → 核查结果；结论未通过结构校验时降级 LOW_CONFIDENCE.
     */
    private QualityCheckItemResult toResult(
            final BidQualityCheckItem item, final LlmVerdict verdict, final List<QualityEvidence> evidences) {
        if (verdict == null || verdict.validated().isEmpty()) {
            return ManualReasons.manual(item, ManualReasons.LOW_CONFIDENCE,
                    FALLBACK_INVALID_CONCLUSION, FALLBACK_MANUAL_SUGGESTION, evidences);
        }
        return switch (verdict.status()) {
            case "PASS" -> QualityCheckItemResult.pass(item, verdict.conclusion(), evidences);
            case "FAIL" -> QualityCheckItemResult.fail(item, verdict.conclusion(),
                    verdict.suggestion().isBlank() ? "请按招标要求整改对应条款" : verdict.suggestion(), evidences);
            default -> ManualReasons.manual(item,
                    verdict.reasonCode().isBlank() ? ManualReasons.AMBIGUOUS : verdict.reasonCode(),
                    verdict.conclusion(),
                    verdict.suggestion().isBlank() ? "请人工复核该语义条款" : verdict.suggestion(), evidences);
        };
    }

    /**
     * 组装裁决 prompt：招标结构化要求 + 知识库真实记录 + 文档按语义检索相关页（固定 JSON 输出契约）.
     */
    public static String buildPrompt(
            final BidQualityCheckItem item,
            final DocumentContent bidDoc,
            final DocumentContent tenderDoc,
            final TenderRequirements requirements,
            final List<KnowledgeMatch> kbMatches) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是投标文件质量核查专家。请仅依据下列材料裁决检查项「").append(item.getTitle())
                .append("」，输出且仅输出 JSON 对象：{\"status\":\"PASS|FAIL|MANUAL\",\"conclusion\":\"...\","
                        + "\"suggestion\":\"...\",\"reasonCode\":\"EVIDENCE_INSUFFICIENT|AMBIGUOUS|LOW_CONFIDENCE"
                        + "|REFERENCE_MISSING|MANUAL_REVIEW_REQUIRED\"}（status=MANUAL 时 reasonCode 必填）。\n")
                .append("裁决准则：\n")
                .append("1. 若项目为单标段或标书正文已包含清晰独立章节划分，不得判定为缺失分标段独立章节；\n")
                .append("2. 标书中对否决条款的确认与正向履约承诺（如承诺遵守违约罚则）属于合规响应，不得判定为废标/违规；\n")
                .append("3. 若检查项为【投标保证金回单】：若招标文件明确免收/不涉及保证金，必须判定为 PASS；若要求缴纳，必须核查标书是否包含真实有效的银行汇款回单或电子保函凭证（具银行/机构、金额、流水号或保函编号等事实要素）；若仅在目录中列出标题、仅在附件清单中列出占位条目、或仅为合规声明而无实际凭证内容，绝对不得判定为 PASS，必须判定为 FAIL！\n")
                .append("4. 若检查项为【投标函/报价函格式规范】：必须核查标书是否包含实质性投标函/报价函正文章节（具备正文承诺内容、致采购人称谓与落款签章）；若招标文件指定了特定函件名称（如报价函、响应函）或明确要求了致采购人抬头称谓、投标有效期，标书必须实质性响应；若仅在目录页列出标题、仅为占位模板（如括号占位符未填）或无实际正文，绝对不得判定为 PASS！\n")
                .append("5. 若检查项为【法定代表人授权委托书】：必须核查标书是否包含真实完备的法定代表人证明书及授权委托书正文（具备委托人与受托人姓名、身份证号、明确授权权限与有效期限，且包含签字盖章要素）；若招标文件允许法定代表人亲自参加仅提供身份证明且标书符合，可判定为 PASS；若仅在目录页列出条目、仅为占位模板未填（如【被授权人姓名】、下划线留白）或无实际正文，绝对不得判定为 PASS！\n")
                .append("6. 若检查项为【营业执照】：必须核查标书是否包含真实有效的营业执照（或事业单位法人证书/社会团体法人登记证书/执业许可证等合法主体资格证明）；必须核验是否存在有效完整的统一社会信用代码及登记主体要素（法定代表人、经营范围、有效期限等）；若仅在目录页列出索引条目、仅为占位模板未填（如【统一社会信用代码】、下划线留白）或未附有效正文，绝对不得判定为 PASS！\n")
                .append("7. 若检查项为【委托人身份证原件 + 授权委托书/法定代表人身份证明】：必须核查标书是否包含真实清晰的身份证扫描件或复印件正反面；核查是否包含有效 18 位公民身份号码及人像面/国徽面要素；若招标文件明确要求社保证明，必须核验是否包含社保缴纳凭据；若仅在目录页列出条目、仅为占位模板未填（如【此处粘贴身份证正反面】、下划线留白）或未附实际证件，绝对不得判定为 PASS！\n")
                .append("8. 若检查项为【资格性评审条款响应 / 财务状况证明与财务审计报告】：必须核查标书是否包含真实完备的财务会计报表（资产负债表、利润表、现金流量表）及会计师事务所出具的审计报告（包含无保留审计意见、会计师事务所印章及注册会计师签章）；若招标文件指定了具体年份要求，必须核验标书报表年份是否完全覆盖；若仅在目录页列出索引条目、仅为占位模板未填（如【此处插入审计报告】、下划线留白）或未附实际财务报表正文，绝对不得判定为 PASS！财务部分齐全不得短路资格性条款整体响应（必须覆盖企业信誉、类似项目业绩门槛、纳税、人员配备等其他资格性条款），不得仅因财务报表齐全就忽略业绩、信誉、纳税等。\n")
                .append("9. 若检查项为【开标一览表/报价表】：必须核查标书是否包含真实完备的开标一览表或报价表正文（具备规范表头如序号、名称、规格型号、数量、单价、合价、投标总价等及实际填报数据）；若仅在目录页列出索引条目（含省略号/点线）、仅为占位模板未填（如【此处粘贴开标一览表】、下划线留白）或未附实际表格正文，绝对不得判定为 PASS！\n")
                .append("10. 若检查项为【报价一致性】：必须核查投标总报价大写金额与小写阿拉伯数字是否相符；若招标文件设定了最高投标限价/招标控制价/采购预算，投标报价高于最高限价属于法定废标红线，绝对不得判定为 PASS，必须判定为 FAIL！若开标一览表总价与投标函总价不一致，必须判定为 FAIL！\n\n");
        sb.append("【招标要求（LLM 结构化提取）】\n").append(summarizeRequirements(requirements)).append("\n\n");
        sb.append("【企业知识库真实记录】\n").append(summarizeKbMatches(kbMatches)).append("\n\n");
        sb.append("【招标文件相关内容】\n")
                .append(SemanticPageRetriever.retrieveContext(
                        item, requirements, tenderDoc, SemanticPageRetriever.DEFAULT_TENDER_BUDGET))
                .append("\n\n");
        sb.append("【投标文件相关内容】\n")
                .append(SemanticPageRetriever.retrieveContext(
                        item, requirements, bidDoc, SemanticPageRetriever.DEFAULT_BID_BUDGET))
                .append("\n");
        return sb.toString();
    }

    private static String summarizeRequirements(final TenderRequirements requirements) {
        if (requirements.missing()) {
            return "（招标要求不可用）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("硬性资格要求: ").append(String.join("；", requirements.mandatoryQualifications())).append('\n');
        sb.append("评分项: ").append(requirements.scoringItems().stream()
                .map(TenderRequirements.ScoringItem::name).collect(Collectors.joining("；"))).append('\n');
        sb.append("否决条款: ").append(requirements.vetoClauses().stream()
                .map(TenderRequirements.VetoClause::clause).collect(Collectors.joining("；"))).append('\n');
        if (requirements.depositRequirement() != null) {
            String desc = requirements.depositRequirement().description();
            sb.append("保证金要求: ").append(desc != null && !desc.isBlank()
                    ? desc
                    : (Boolean.FALSE.equals(requirements.depositRequirement().required()) ? "免收/不涉及" : "需缴纳")).append('\n');
        }
        if (requirements.bidLetterRequirement() != null) {
            String desc = requirements.bidLetterRequirement().description();
            sb.append("投标函要求: ").append(desc != null && !desc.isBlank()
                    ? desc
                    : "类型: " + requirements.bidLetterRequirement().letterType()
                            + ", 抬头: " + requirements.bidLetterRequirement().recipientName()
                            + ", 有效期: " + requirements.bidLetterRequirement().validityDays() + "天").append('\n');
        }
        if (requirements.authorizationRequirement() != null) {
            String desc = requirements.authorizationRequirement().description();
            sb.append("授权书要求: ").append(desc != null && !desc.isBlank()
                    ? desc
                    : (Boolean.TRUE.equals(requirements.authorizationRequirement().allowLegalRepresentativeOnly())
                            ? "需提供法定代表人证明书；法定代表人亲自参加免授权书"
                            : "需提供法定代表人证明书及授权委托书")).append('\n');
        }
        if (requirements.businessLicenseRequirement() != null) {
            String desc = requirements.businessLicenseRequirement().description();
            sb.append("营业执照要求: ").append(desc != null && !desc.isBlank()
                    ? desc
                    : "须提供有效的营业执照或主体资格证明文件（复印件加盖公章）").append('\n');
        }
        if (requirements.idAuthLetterRequirement() != null) {
            String desc = requirements.idAuthLetterRequirement().description();
            sb.append("身份证件要求: ").append(desc != null && !desc.isBlank()
                    ? desc
                    : "须提供法定代表人及委托代理人身份证复印件或扫描件"
                            + (requirements.idAuthLetterRequirement().requireSocialSecurity() ? "，并提供社保证明" : "")).append('\n');
        }
        if (requirements.financialRequirement() != null) {
            String desc = requirements.financialRequirement().description();
            sb.append("财务状况要求: ").append(desc != null && !desc.isBlank()
                    ? desc
                    : "须提供经会计师事务所审计的财务审计报告（含资产负债表、利润表、现金流量表）").append('\n');
        }
        if (requirements.priceRequirement() != null) {
            String desc = requirements.priceRequirement().description();
            sb.append("报价要求: ").append(desc != null && !desc.isBlank()
                    ? desc
                    : (requirements.priceRequirement().maxPrice() != null
                            ? "最高投标限价: " + requirements.priceRequirement().maxPrice() + "元"
                            : (requirements.priceRequirement().maxPriceRawText() != null
                                    ? "最高限价: " + requirements.priceRequirement().maxPriceRawText()
                                    : "按规定填报报价"))).append('\n');
        }
        return sb.toString();
    }

    private static String summarizeKbMatches(final List<KnowledgeMatch> kbMatches) {
        if (kbMatches == null || kbMatches.isEmpty()) {
            return "（知识库无匹配记录）";
        }
        return kbMatches.stream()
                .limit(MAX_KB_MATCHES_IN_PROMPT)
                .map(m -> "[" + m.knowledgeSource() + (m.matched() ? "/已匹配" : "/未匹配") + "] "
                        + truncate(m.content(), KB_MATCH_TEXT_BUDGET))
                .collect(Collectors.joining("\n"));
    }

    private static String truncate(final String text, final int budget) {
        if (text == null) {
            return "";
        }
        return text.length() <= budget ? text : text.substring(0, budget) + "…";
    }
}
