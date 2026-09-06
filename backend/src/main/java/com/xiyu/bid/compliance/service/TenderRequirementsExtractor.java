// Input: 招标文件 DocumentContent（真实文件解析产物）
// Output: TenderRequirements 结构化招标要求；全部失败场景降级 missing 哨兵，禁止阻断任务
// Pos: compliance/service/ - 招标要求 LLM 结构化提取（spec 049 US2，contracts/tender-requirements-extraction.md）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.policy.TenderRequirements;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 招标要求 LLM 结构化提取器（spec 049 第二步）.
 *
 * <p>经 {@link AiProvider}（运行时路由；未启用时抛异常走降级）按
 * contracts/tender-requirements-extraction.md 的 JSON 契约提取评分项/硬性要求/
 * 否决条款/组成要求/格式要求。长文本按页窗口分段送入；任何失败（AI 未启用、
 * 调用失败、JSON 非法）一律降级 {@link TenderRequirements#missingSentinel()}，
 * 比对项 MANUAL + REFERENCE_MISSING、独立项照常，禁止阻断任务.
 */
@Component
@Slf4j
public class TenderRequirementsExtractor {

    /** 单段字符预算（对齐模型上下文，按页窗口打包）. */
    static final int MAX_CHUNK_CHARS = 30000;
    /** 单段提取 max_tokens. */
    static final int MAX_TOKENS = 4096;

    /** AI 通道（RoutingAiProvider 运行时路由）. */
    private final AiProvider aiProvider;

    public TenderRequirementsExtractor(final AiProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    /**
     * 从招标文件结构化内容提取招标要求.
     *
     * @param tenderDoc 招标文件解析内容；为空视为招标未上传
     * @return 提取结果；全部降级场景返回 {@link TenderRequirements#missingSentinel()}
     */
    public TenderRequirements extract(final DocumentContent tenderDoc) {
        if (tenderDoc == null || tenderDoc.fullText() == null || tenderDoc.fullText().isBlank()) {
            log.warn("招标文件未上传或解析为空，招标要求提取降级为 missing");
            return TenderRequirements.missingSentinel();
        }
        List<String> chunks = chunkByPageWindow(tenderDoc);
        List<TenderRequirements> parts = new ArrayList<>();
        for (String chunk : chunks) {
            String response;
            try {
                response = aiProvider.chatCompletion(buildPrompt(chunk), MAX_TOKENS);
            } catch (RuntimeException e) {
                // AI 未启用（Noop）/ LLM 调用失败 / 超时：降级不阻断（Constitution VII §3 打堆栈）
                log.error("招标要求 LLM 提取失败，降级为 missing: {}", e.getMessage(), e);
                return TenderRequirements.missingSentinel();
            }
            TenderRequirements part = TenderRequirementsParser.parse(response);
            if (part.missing()) {
                log.warn("招标要求 LLM 响应解析失败，该段降级为 missing。响应摘要: {}", summarize(response));
            }
            parts.add(part);
        }
        return TenderRequirements.merge(parts);
    }

    /**
     * 按页窗口分段：优先使用 Sidecar 解析出的 PageContent，单段不超过字符预算；
     * 无分页信息时退回 \f 分段与硬切.
     */
    static List<String> chunkByPageWindow(final DocumentContent tenderDoc) {
        List<String> segments = new ArrayList<>();
        if (tenderDoc.pages() != null && !tenderDoc.pages().isEmpty()) {
            for (DocumentContent.PageContent page : tenderDoc.pages()) {
                if (page.text() != null && !page.text().isBlank()) {
                    segments.add(page.text());
                }
            }
        }
        if (segments.isEmpty()) {
            for (String part : tenderDoc.fullText().split("\f")) {
                if (!part.isBlank()) {
                    segments.add(part);
                }
            }
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String segment : segments) {
            for (String piece : splitToFit(segment)) {
                if (buffer.length() + piece.length() > MAX_CHUNK_CHARS && buffer.length() > 0) {
                    chunks.add(buffer.toString());
                    buffer.setLength(0);
                }
                buffer.append(piece).append('\n');
            }
        }
        if (buffer.length() > 0) {
            chunks.add(buffer.toString());
        }
        return chunks.isEmpty() ? List.of(tenderDoc.fullText()) : chunks;
    }

    private static List<String> splitToFit(final String segment) {
        if (segment.length() <= MAX_CHUNK_CHARS) {
            return List.of(segment);
        }
        List<String> pieces = new ArrayList<>();
        for (int start = 0; start < segment.length(); start += MAX_CHUNK_CHARS) {
            pieces.add(segment.substring(start, Math.min(start + MAX_CHUNK_CHARS, segment.length())));
        }
        return pieces;
    }

    private static String buildPrompt(final String chunkText) {
        return """
                你是招投标领域的招标文件分析专家。请仅从下面给定的招标文件文本中提取结构化要求，\
                禁止臆造任何文本中不存在的内容；无法确定的类别一律返回空数组或null。
                输出必须是严格 JSON（不要包含 Markdown 代码块或解释文字），结构如下：
                {
                  "scoringItems": [ { "name": "评分项名称", "maxScore": 数值或null, "criteria": "评分标准描述" } ],
                  "mandatoryQualifications": [ "硬性资格要求/资质要求原文表述" ],
                  "vetoClauses": [ { "clause": "否决条款原文", "keywords": ["否决关键词"] } ],
                  "compositionRequirements": [ "投标文件组成要求条目" ],
                  "formatRequirements": [ "格式要求条目" ],
                  "depositRequirement": {
                    "required": true或false或null,
                    "amount": "保证金金额或null",
                    "forms": "保证金形式或null",
                    "description": "招标文件关于投标保证金要求的原文章节或前附表条款摘录（若明确免收、不收取、不涉及、无保证金、不适用或为0元，必须记录原文并将required设为false）"
                  },
                  "bidLetterRequirement": {
                    "letterType": "投标函或报价函或响应函等指定名称或null",
                    "recipientName": "招标文件要求的致招标人/采购人称谓全称或null",
                    "validityDays": 投标有效期天数整数或null,
                    "description": "招标文件关于投标函格式、致采购人称谓、有效期等具体要求的原文条款摘录或null"
                  },
                  "authorizationRequirement": {
                    "required": true或false或null,
                    "allowLegalRepresentativeOnly": true或false或null,
                    "templateTitle": "招标文件指定的授权书模板标题或null",
                    "description": "招标文件关于授权委托书与法定代表人身份证明的具体要求条款摘录或null"
                  },
                  "businessLicenseRequirement": {
                    "required": true或false或null,
                    "allowCertificates": [ "允许的证书类型列表，如营业执照、事业单位法人证书等" ],
                    "requireOriginalOrCopy": "原件扫描件或复印件加盖公章等要求或null",
                    "description": "招标文件关于营业执照及主体资格证明的具体要求条款摘录或null"
                  },
                  "idAuthLetterRequirement": {
                    "required": true或false或null,
                    "requireFrontAndBack": true或false或null,
                    "requireSocialSecurity": true或false或null,
                    "requireOriginalOrCopy": "原件或加盖公章的复印件等要求或null",
                    "description": "招标文件关于法定代表人及委托代理人身份证正反面、社保证明等具体要求条款摘录或null"
                  },
                  "financialRequirement": {
                    "required": true或false或null,
                    "auditReportRequired": true或false或null,
                    "requiredYears": [ "招标文件要求的审计或报表年份，如2021、2022、2023或近三年等" ],
                    "requiredStatements": [ "要求的报表，如资产负债表、利润表、现金流量表、附注等" ],
                    "requireCpaSeal": true或false或null,
                    "description": "招标文件关于财务状况证明、经审计的财务会计报告、三表及审计印章等具体要求条款摘录或null"
                  },
                  "priceRequirement": {
                    "required": true或false或null,
                    "maxPrice": 最高限价/招标控制价/采购预算的数字金额（元，不含汉字单位）或null,
                    "maxPriceRawText": "最高投标限价/招标控制价/采购预算原文表述或null",
                    "pricingMethod": "总价或单价或费率等报价方式或null",
                    "requireItemizedList": true或false或null,
                    "requireBidOpeningTable": true或false或null,
                    "description": "招标文件关于最高投标限价、开标一览表、报价明细表等具体要求的条款摘录或null"
                  },
                  "caCertificateRequirement": {
                    "required": true或false或null,
                    "platformName": "招标文件指定的电子招投标交易平台名称或null",
                    "requireCertificateCopy": true或false或null,
                    "requireElectronicSeal": true或false或null,
                    "description": "招标文件关于电子标 CA 数字证书、电子签章等具体要求或免收说明的条款摘录或null"
                  }
                }
                顶层键必须全部存在。

                招标文件文本：
                """ + chunkText;
    }

    private static String summarize(final String response) {
        if (response == null) {
            return "<null>";
        }
        return response.length() <= 200 ? response : response.substring(0, 200) + "...(已截断)";
    }
}
