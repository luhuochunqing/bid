// Input: 规则/LLM裁决结果与投标文件分页文本及图像实证
// Output: 复用投标人主体文法，保留实证通过与具体缺陷（含项目经理业绩及项目管理机构配置完整字段，证书正反向扫描均排除样例正文，普通章节目标题恢复正式正文扫描）；标题、样例块、分页及核验描述不重置正式证书关联，明确人员、服务等业务对象切换才清理上下文，前置未关联缺陷在身份确定后拦截，裸状态、系词判断、借用租赁与权属续句不依赖缺陷词表关联，证据不足时转人工复核
// Pos: compliance/domain/policy/ - 语义证据边界，按证书身份、正文语境及分页关联裁决（spec079）
package com.xiyu.bid.compliance.domain.policy;
import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/** 同一边界用于LLM入口及引擎最终输出，不能用标题或检索未命中证明全篇缺失。 */
final class SemanticEvidenceBoundary {
    private static final Set<String> GUARDED_ITEMS = Set.of(BidQualityCheckItem.AUTHORIZATION_LETTER.getCode(),
            BidQualityCheckItem.ID_AUTH_LETTER.getCode(), BidQualityCheckItem.FRONT_TABLE_RESPONSE.getCode(), BidQualityCheckItem.QUALIFICATION_REVIEW.getCode(), BidQualityCheckItem.SCORING_RESPONSE.getCode());
    private static final List<String> ABSENCE_CLAIMS = List.of("缺失", "缺少", "未提供", "未包含", "未附", "未检测到", "未检索到", "未见", "正文中没有", "原文没有", "缺页");
    private static final List<String> FINANCIAL_CUES = List.of("财务", "审计", "资产负债表", "利润表", "现金流量表");
    private static final Pattern SELF_ADMISSION = Pattern.compile("^(?:(?:财务材料说明|材料说明|说明)[：:])?\\s*(?:我司|本公司|本投标人|我方|本投标文件)\\s*(?:确实|确认|本次)?\\s*(?:未|不)(?:提供|提交|附具|报送|附)(.+)");
    private static final Pattern CONCRETE_BLANK = Pattern.compile("(?:(授权期限|授权范围|受托人|法定代表人|交货期|质保期|付款方式|投标有效期|技术方案|实施方案|施工组织|项目经理(?:业绩(?:证明)?)?|项目管理(?:机构(?:配置)?)?|团队人员|服务方案|进度计划|技术架构|人员配置)[：:]\\s*(?:_{3,}|[【\\[（(](?:\\s*|(?:请\\s*)?(?:在此处|在此|此处)?(?:填写|输入|插入|粘贴|待填)[^】\\]）)]{0,30})[】\\]）)]))"
            + "|(?:[【\\[（(](?:请\\s*)?(?:在此处|在此|此处)?(?:填写|输入|插入|粘贴|待填)[^】\\]）)]{0,10}(技术方案|实施方案|施工组织|项目经理(?:业绩(?:证明)?)?|项目管理(?:机构(?:配置)?)?|团队人员|服务方案|进度计划|技术架构|人员配置)[^】\\]）)]{0,20}[】\\]）)])");
    private static final Pattern CERT_NUMBER_FACT = Pattern.compile("(?:许可证编号|证书编号|资质编号|认证编号|备案编号|证号|注册号)[\\s\\u3000|:：为是]*[A-Za-z0-9\\u4e00-\\u9fa5-]{4,}|合字[A-Z0-9-]{4,}");
    private static final Pattern CERT_VALIDITY_FACT = Pattern.compile("(?:有效期限|有效期)[^。；\\n]{0,40}(?:20[2-9]\\d\\s*(?:年|[./-])|长期)");
    private static final Pattern CERT_IDENTIFIER = Pattern.compile("(?:许可证编号|证书编号|资质编号|认证编号|备案编号|证号|注册号|编号)[\\s\\u3000|:：为是]*[A-Za-z0-9][A-Za-z0-9/-]*");
    private static final String STANDARD_CERT_CODE = "(?:ISO\\s*\\d+|GB/?T\\s*\\d+|EDI|ICP|CMMI\\s*\\d*|ITSS\\s*\\d*|CCRC\\s*\\d*|CS\\s*\\d*)";
    private static final Pattern CERT_CODE = Pattern.compile("(?i)(?:" + STANDARD_CERT_CODE + "|[A-Z]{2,}\\d+)");
    private static final Pattern CERT_CONTEXT_CODE = Pattern.compile(CERT_CODE.pattern() + "(?=\\s*(?:认证|证书|许可证|资质))");
    // 否定持证、材料关联和裁决共用状态文法；同分句允许提交位置及修饰语分隔否定词、动作与材料，仍受证书、章节及业务对象边界约束。
    private static final String BIDDER_SELF = "(?:本公司|我司|我方|本投标人|投标人)";
    private static final String MISSING_MATERIAL_STATUS = "待补|后补|暂缺|缺失|缺少|缺页|未附|(?:暂时\\s*)?(?:无法|不能|暂无法|暂不能|未能|(?:尚)?未(?:予)?|不(?:予)?|拒绝)(?:随(?:本次)?(?:标书|投标文件)|在[\\h]*(?:本次[\\h]*)?(?:标书|投标文件)[\\h]*(?:中|内))?[\\h]*(?:(?:一并|一同|同时)[\\h]*)?(?:提供|提交|上传|报送|附具|附|出示|补充|出具)";
    private static final Pattern INELIGIBLE_OR_INVALID_STATUS = Pattern.compile(
            "(?:没有|未|尚未|未曾|未能)(?:取得|持有|获得|通过|具备|具有)?|不(?:再)?(?:由[^。；;，,\\n\\f]*)?(?:持有|具有|具备)"
                    + "|(?:已经?|已)?(?:于[^。；;，,\\n]*)?(?:被)?(?:结束|截止|到期|逾期|过期|失效|吊销|撤销|届满|注销|作废|终止|满期|废止|中止|暂停(?:使用|有效|效力)?|停止使用|冻结)"
                    + "|(?:超过|过|超出)有效期|有效期(?:已经?|已)?(?:结束|届满|到期|满|截止)|失去效力|不再有效|不再具有|未生效|停止有效|无效|伪造|假证|假冒|变造|虚假|不实|(?:(?:真实性|真伪|有效性|效力|资质|资格|持证(?:状态)?|状态|证书|认证)[\\s:：为是]*)?(?:不明|存疑|待定|待核[实验查]?|待复核|待确认|(?:无法|不能|尚未|未能|有待|需)(?:确认|核实|核验|查实))");
    private static final Pattern DEFECT_OR_INVALID_STATUS = Pattern.compile(INELIGIBLE_OR_INVALID_STATUS.pattern() + "|" + MISSING_MATERIAL_STATUS);
    private static final String CERT_MATERIAL_WORDS = "(?:证书|认证|许可证|资质|证件|证|持证(?:状态)?|有效性|效力|复印件|扫描件|原件|证明材料)";
    private static final Pattern CERTIFICATE_MATERIAL = Pattern.compile(CERT_MATERIAL_WORDS), MISSING_CERTIFICATE_MATERIAL = Pattern.compile(
            CERT_MATERIAL_WORDS + "[^。；;，,\\n\\f]*(?:" + MISSING_MATERIAL_STATUS + ")"
                    + "|(?:" + MISSING_MATERIAL_STATUS + ")[^。；;，,\\n\\f]*" + CERT_MATERIAL_WORDS);
    // “由…持有/取得”仅放行完整的投标人自称；具名主体无法据此确认与投标人相同。
    private static final Pattern THIRD_PARTY_OWNERSHIP = Pattern.compile(
            "(?:不再|并非|未|(?:并)?不是)[\\s\\u3000]*(?:由[\\s\\u3000]*)?" + BIDDER_SELF + "[\\s\\u3000]*(?:持有|取得|所有|具备|具有|的)|(?:(?:不(?:再)?|并非|未|(?:并)?不是)[\\s\\u3000]*(?:属于|归(?:属)?(?:于)?)|非)[^。；;，,\\n]*" + BIDDER_SELF
                    + "|(?:属于|归属(?:于)?|持证人|持有人|(?:持证|持有)(?:单位|主体))[\\s\\u3000:：为是]*+"
                    + "(?!" + BIDDER_SELF + "[\\s\\u3000]*(?:所有|持有)?[\\s\\u3000）)]*$)"
                    + "|由[\\s\\u3000]*+(?!" + BIDDER_SELF + "[\\s\\u3000]*(?:持有|取得))[^。；;，,\\n]+(?:持有|取得)"
                    + "|(?:供应商|合作方|合作企业|第三方|关联公司|制造商|厂家|厂商|母公司|子公司|生产商|生产厂家|代理商|原厂|其他(?:单位|企业|公司))[^。；;，,\\n]*(?:所有|持有|取得)");
    private static final Pattern BIDDER_CERTIFICATE_STATEMENT = Pattern.compile("^\\s*" + BIDDER_SELF + "[：:\\s]*(?:已)?(?:具有|持有|取得|获得|通过)(.+)");
    // 引导语有无标点均可独立换行；仅合并行内排版空白，保留换页及业务对象边界。
    private static final String CERTIFICATE_INTRODUCTION = "(?:但是|不过|但|且|然而|经(?:现场|重新)?[核查实对审查排查复核]+(?:确认)?(?:后)?|(?:核对|核实|复核|审核|核查|审查)(?:结果|结论)(?:为)?)";
    private static final Pattern INTRODUCTORY_CONNECTOR = Pattern.compile("(" + CERTIFICATE_INTRODUCTION + ")(?:[，,：:]\\h*(?:[\\r\\n]\\h*)*|\\h*[\\r\\n](?:\\h|[\\r\\n])*)");
    // 省略对象的状态谓语沿用证书上下文；是否可通过仍须完整匹配肯定事实，不能依赖缺陷词表穷举。
    private static final String BARE_CERTIFICATE_PREDICATE = "(?:(?:[\\p{IsHan}\\h]*(?:为|是)|已经?|被|处于|系|目前|实际|现|现已|暂时|暂停|停止|权属|状态|尚|有待|待).+|[\\p{IsHan}\\h]*(?:借用|借来|借自|租用|租赁).*)";
    private static final Pattern CERTIFICATE_CONTINUATION = Pattern.compile(
            "^\\s*(?:" + BIDDER_SELF + "[\\h：:、]*+)?(?:" + CERTIFICATE_INTRODUCTION + "[\\h：:、]*+)?(?:" + BIDDER_SELF + "[\\h：:、]*+)?\\s*(?:目前|现|现已|暂时)?\\s*(?:(?:(?:该|此|本|上述)?\\s*(?:" + CERT_MATERIAL_WORDS + "|有效期限|有效期|截止日期|到期日期).+)|(?:(?:" + DEFECT_OR_INVALID_STATUS.pattern() + ")\\s*(?:(?:该|此|本|上述)?\\s*" + CERT_MATERIAL_WORDS + ")?)|(?:" + BARE_CERTIFICATE_PREDICATE + "))\\s*$");
    private static final Pattern EXPLICIT_CERTIFICATE_REFERENCE = Pattern.compile("(?:该|此|本|上述)\\s*" + CERT_MATERIAL_WORDS);
    private static final Pattern NAMED_CERTIFICATE = Pattern.compile("[\\p{IsHan}]{2,}(?:证书|许可证|资质)|营业执照|[\\p{IsHan}]{2,}体系认证");
    // 业务对象须作为分句主题出现；“资料随合同装订”等普通提及不切断证书关联。
    private static final Pattern CONTEXT_BOUNDARY = Pattern.compile(
            "^\\s*(?:" + CERTIFICATE_INTRODUCTION + "[\\h：:、]*)?(?:第[一二三四五六七八九十百0-9]+[章节篇部分](?![^。；;，,\\n\\f]*(?:证书|认证|许可证|资质|证件|有效|核验|核查))|(?:" + MISSING_MATERIAL_STATUS + ")?(?:本|该|上述)?(?:项目经理|人员配置|团队人员|安装服务|售后服务协议|售后服务承诺|售后服务|合同|协议|报价|售后承诺|投标有效期|交货期|质保期|付款方式)(?![\\h]*(?:附件|附录|所附|中|内))(?![^。；;，,\\n\\f]*(?:证书|认证|许可证|资质|证件)))");
    // 核验动作修饰通用“证书”不构成另一张证书的身份，不能据此清除已关联缺陷。
    private static final Pattern NON_CERT_VERB = Pattern.compile(".*(?:复核|审核|核查|审查|查验|确认|发现|持有|取得|具备|具有|通过|提供|获得|核对|核实|本公司|我司|说明|附件|无|没有|未|不|该|此|本|上述|相关|对应|有关|此类).*");
    private static final Pattern WRAPPED_VALIDITY = Pattern.compile("((?:有效期(?:限)?|截止日期|到期日期)[：: \\t]*)[\\r\\n\\s]+(?=[0-9０-９])");
    private static final Pattern UNVERIFIED_VALIDITY = Pattern.compile("(?:有效期(?:限)?|截止日期|到期日期)[^。；;，,\\n]*[0-9０-９]|[0-9０-９][^。；;，,\\n]*(?:到期|截止|过期|届满|失效)");
    private static final Pattern EXAMPLE_HEADING = Pattern.compile("^(?:[0-9一二三四五六七八九十]+[、.．:：\\s]+|(?:第[0-9一二三四五六七八九十]+[章节篇部分条款]|附录[0-9一二三四五六七八九十A-Za-z]*|附件[0-9一二三四五六七八九十A-Za-z]*)[、.．:：\\s]*)?(?:(?:合同(?:条款|文本|书)?|[\\p{IsHan}]{0,20}(?:协议书?|声明)|填写|填报|格式|参考)?(?:示例|模板|范例|样例|样本|格式样例|参考样例|参考格式|[（(](?:示例|模板|范例|样例|样本)[）)])(?:[：:\\s]+|[（(][^）)]*[）)]|说明)*|(?:非本公司实际声明|填报说明|填写说明|模板说明|格式说明))[：:\\s]*$");
    private static final Pattern SECTION_HEADING = Pattern.compile("^第[一二三四五六七八九十百零〇0-9]+(?:章|节|篇|部分)\\h*[^。；;，,：:]{0,60}[：:]?$"), SAMPLE_SECTION = Pattern.compile(".*(?:示例|模板|范例|样例|样本|参考格式|填写说明|填报说明).*"), FORMAL_BODY_HEADING = Pattern.compile("^(?:[一二三四五六七八九十0-9]+[、.．]\\s*)?正式(?:声明|正文)[：:]?$");
    private static final Pattern NON_BIDDER_CERTIFICATE_SENTENCE = Pattern.compile(".*(?:要求|须|应当|应具备|应具有|必须|采购|招标|引用|合作方|第三方|供应商|关联公司|属于|归属|持证人|所有|填写示例|填报示例|格式样例|参考格式|填报说明|填写说明|非本公司实际声明).*");
    private static final String CERTIFICATE_OBJECT_PREFIX_REGEX = "(?:有效的?|本公司(?:的)?|我司(?:的)?|我方(?:的)?)";
    private static final Pattern CERTIFICATE_OBJECT_PREFIX = Pattern.compile("^" + CERTIFICATE_OBJECT_PREFIX_REGEX);
    // 关联证书陈述必须完整落入已确认文法；未命中缺陷词表并不等于状态已确认。
    private static final Pattern CONFIRMED_CERTIFICATE_FACT = Pattern.compile(
            "(?:" + BIDDER_SELF + "(?:已)?(?:具有|持有|取得|获得|通过)" + CERTIFICATE_OBJECT_PREFIX_REGEX + "?)?(?:质量管理体系|环境管理体系|职业健康安全管理体系|认证|证书|许可证|和|及|与)*"
                    + "|(?:属于|归属(?:于)?|持证人[为是]?|持有人[为是]?|(?:持证|持有)(?:单位|主体)[为是]?)" + BIDDER_SELF + "(?:所有|持有)?"
                    + "|由" + BIDDER_SELF + "(?:持有|取得)|由认证机构颁发|(?:尚)?未(?:到期|过期|失效)|(?:目前|现|现已|仍然|仍)?有效"
                    + "|已随(?:本次)?(?:投标文件|标书)(?:一并|一同|同时)?(?:提交|提供|报送)");
    private static final Pattern NORMALIZE_CHARS = Pattern.compile("[\\s\\u3000\\p{P}]");
    private static final Pattern NEGATED_INVALID_STATUS = Pattern.compile("(?:尚)?未(?:到期|过期|失效)");
    private SemanticEvidenceBoundary() { }
    static QualityCheckItemResult reconcileQualificationApplicability(final QualityCheckItemResult result, final DocumentContent bidDoc, final TenderRequirements requirements) {
        if (!BidQualityCheckItem.QUALIFICATION_MATCH.getCode().equals(result.itemCode())
                || !requirements.hasUnresolvedCertificateRequirements()) return result;
        var firstStage = QualificationMatchPolicy.checkFirstStage(bidDoc, requirements);
        if (firstStage.filter(v -> v.status() == QualityCheckStatus.FAIL).isPresent()) return firstStage.orElseThrow();
        return QualityCheckItemResult.manualReview(BidQualityCheckItem.QUALIFICATION_MATCH,
                "证书条件的强制性或作用对象尚未确认，资料检索结果不能直接证明招标资格满足或缺失",
                "请按结构化条件的原文依据确认适用范围，再核对企业资质与本次投标材料", result.evidences());
    }
    static QualityCheckItemResult reconcile(final QualityCheckItemResult input, final DocumentContent bidDoc) {
        QualityCheckItemResult passCheck = reconcilePass(input, bidDoc);
        if (passCheck != null) return passCheck;
        return downgradeGuardedFail(requireQualificationConfirmation(input, bidDoc), bidDoc);
    }
    private static QualityCheckItemResult reconcilePass(final QualityCheckItemResult input, final DocumentContent bidDoc) {
        if (input == null || input.status() != QualityCheckStatus.PASS) return null;
        if (BidQualityCheckItem.ID_AUTH_LETTER.getCode().equals(input.itemCode()) && !hasCompleteBothSidesIdFacts(bidDoc))
            return ManualReasons.manual(BidQualityCheckItem.ID_AUTH_LETTER, ManualReasons.EVIDENCE_INSUFFICIENT,
                    "文本及图片标记不能证明证件正反面齐全、身份一致或授权签署有效",
                    "请核对扫描页中的实际身份证正反面、人员一致性及签署信息；文字转写不等于图像附件核验通过", input.evidences());
        if (BidQualityCheckItem.AUTHORIZATION_LETTER.getCode().equals(input.itemCode()) && bidDoc != null
                && bidDoc.pages().stream().anyMatch(p -> p.text().contains(ScanPageTextPolicy.TRANSCRIPTION_MARKER)
                        && BasicInfoCheckPolicy.containsAnyKeyword(p.text(), AuthorizationLetterCheckPolicy.AUTH_LETTER_KEYWORDS))
                && !AuthorizationFieldPolicy.hasSignedAuthorizationEvidence(bidDoc))
            return ManualReasons.manual(BidQualityCheckItem.AUTHORIZATION_LETTER, ManualReasons.EVIDENCE_INSUFFICIENT,
                    "授权委托书未检测到签字盖章痕迹或签署图像，文字字段不能单独证明授权已生效",
                    "请核对原始授权书的签字盖章及签署有效性后确认", input.evidences());
        return null;
    }
    private static QualityCheckItemResult downgradeGuardedFail(final QualityCheckItemResult result, final DocumentContent bidDoc) {
        if (result == null || result.status() != QualityCheckStatus.FAIL || !GUARDED_ITEMS.contains(result.itemCode())) return result;
        boolean financialClaim = result.itemCode().equals(BidQualityCheckItem.QUALIFICATION_REVIEW.getCode())
                && FINANCIAL_CUES.stream().anyMatch(result.conclusion()::contains);
        boolean scoring = BidQualityCheckItem.SCORING_RESPONSE.getCode().equals(result.itemCode());
        List<QualityEvidence> body = result.evidences().stream().filter(e -> isBody(e, bidDoc))
                .filter(e -> !scoring || bidDoc != null && bidDoc.pages().stream()
                        .anyMatch(p -> e.pageNumber().equals(p.pageNumber()) && p.text() != null && p.text().contains(e.quote())))
                .filter(e -> !financialClaim || FINANCIAL_CUES.stream().anyMatch(e.quote()::contains)).toList();
        boolean absenceClaim = ABSENCE_CLAIMS.stream().anyMatch(result.conclusion()::contains);
        List<String> topics = proofTopics(result.itemCode(), financialClaim);
        boolean provenSpecificDefect = body.stream().anyMatch(e -> explicitDefect(sourceText(e, bidDoc), topics, scoring ? bidDoc.pages().stream().filter(p -> p.pageNumber() < e.pageNumber() && p.text() != null).sorted(java.util.Comparator.comparingInt(DocumentContent.PageContent::pageNumber)).map(DocumentContent.PageContent::text).collect(java.util.stream.Collectors.joining("\n")) : ""));
        if (!body.isEmpty() && ((!scoring && !absenceClaim) || provenSpecificDefect)) return result;
        return new QualityCheckItemResult(result.itemCode(), result.category(), result.title(), result.description(),
                QualityCheckStatus.MANUAL_REVIEW, result.severity(), result.order(), "[" + ManualReasons.EVIDENCE_INSUFFICIENT + "] 未定位到足以证明该缺陷的正文事实，目录、标题或检索缺口不能证明原文件缺失",
                "请对照原文件完整页面及图片附件人工复核后确认，不要仅凭检索未命中补删材料",
                result.evidences(), result.details());
    }
    static QualityCheckItemResult requireQualificationConfirmation(final QualityCheckItemResult result) { return requireQualificationConfirmation(result, null); }
    static QualityCheckItemResult requireQualificationConfirmation(final QualityCheckItemResult result, final DocumentContent bidDoc) {
        if (result == null || result.status() != QualityCheckStatus.PASS || !(BidQualityCheckItem.QUALIFICATION_REVIEW.getCode().equals(result.itemCode())
                || BidQualityCheckItem.QUALIFICATION_VETO.getCode().equals(result.itemCode()))) return result;
        boolean veto = BidQualityCheckItem.QUALIFICATION_VETO.getCode().equals(result.itemCode());
        if (veto && (hasSamePageSubstantiveCertificateFacts(bidDoc) || hasValidKbEvidenceCoveringBid(result, bidDoc))) return result;
        return new QualityCheckItemResult(result.itemCode(), result.category(), result.title(), result.description(),
                QualityCheckStatus.MANUAL_REVIEW, result.severity(), result.order(),
                veto ? "完整资格条件及证明材料尚需逐项核验，证书名称或资料库匹配不能证明资格全部满足"
                        : "财务要求适用性及资格材料尚需人工确认，系统暂不自动判定资格性评审通过",
                veto ? "请对照招标资格审查清单逐项复核主体、财务、信用、业绩、纳税社保及本次投标证明材料"
                        : "请对照招标原文逐项确认财务条款、豁免条件及原始报表；已有定位证据仅供复核",
                result.evidences(), result.details());
    }
    static boolean hasSubstantiveCertificateFacts(final DocumentContent bidDoc, final Integer pageNumber) {
        return bidDoc != null && pageNumber != null && bidDoc.pages().stream().filter(p -> p.pageNumber() == pageNumber).map(DocumentContent.PageContent::text).anyMatch(SemanticEvidenceBoundary::hasSubstantiveCertificateFacts);
    }
    static boolean hasSubstantiveCertificateFacts(final String text) { return text != null && !text.isBlank() && CERT_NUMBER_FACT.matcher(text).find() && CERT_VALIDITY_FACT.matcher(text).find(); }
    private static boolean hasSamePageSubstantiveCertificateFacts(final DocumentContent bidDoc) {
        return bidDoc != null && bidDoc.pages() != null && bidDoc.pages().stream().filter(p -> p.text() != null && !BidLetterCheckPolicy.isCatalogOrOutlinePage(p.text()))
                .anyMatch(p -> hasSubstantiveCertificateFacts(p.text()));
    }
    private static boolean hasValidKbEvidenceCoveringBid(final QualityCheckItemResult result, final DocumentContent bidDoc) {
        if (result == null || result.evidences() == null || bidDoc == null || bidDoc.pages() == null) return false;
        List<QualityEvidence> kbEvidences = result.evidences().stream().filter(e -> "KNOWLEDGE_BASE".equals(e.sourceType())
                && KnowledgeMatch.SOURCE_QUALIFICATION.equals(e.knowledgeSource())
                && Boolean.TRUE.equals(e.matched()) && e.quote() != null && !e.quote().isBlank()).toList();
        String bodyText = bidDoc.pages().stream().filter(p -> p.text() != null && !BidLetterCheckPolicy.isCatalogOrOutlinePage(p.text())).map(DocumentContent.PageContent::text).collect(java.util.stream.Collectors.joining("\n\f\n"));
        return !kbEvidences.isEmpty() && kbEvidences.stream().allMatch(kb -> hasPositiveCertificateStatement(bodyText, certificateIdentity(kb.quote())));
    }
    static boolean hasPositiveCertificateStatement(final String text, final String certificate) {
        if (text == null || certificate == null) return false;
        List<String> codes = certificateCodes(certificate);
        String name = NORMALIZE_CHARS.matcher(certificate).replaceAll("");
        String bodyText = WRAPPED_VALIDITY.matcher(QualificationConditionExtractor.stripHtml(text)).replaceAll("$1");
        bodyText = INTRODUCTORY_CONNECTOR.matcher(bodyText).replaceAll("$1：");
        List<String> clauses = java.util.Arrays.stream(bodyText.split("(?=\f)|(?<=\f)|[。；;，,\n]")).filter(c -> !c.isBlank() || c.contains("\f")).toList();
        return !hasCertificateDefect(clauses, name, codes) && hasFormalBidderStatement(bodyText, name, codes);
    }
    private static boolean hasCertificateDefect(final List<String> clauses, final String name, final List<String> codes) {
        boolean previousRelated = false, lastCertificateRelated = false, pendingDefect = false, certificateContextKnown = false, inExampleContext = false;
        for (String clause : clauses) {
            // 分页只是排版边界；说明、页眉页脚及空白页不解除关联，明确对象切换仍由下方清理。
            if (clause.contains("\f")) continue;
            boolean formalBodyHeading = isFormalBodyHeading(clause);
            if (isExampleHeading(clause) || (inExampleContext && formalBodyHeading) || FORMAL_BODY_HEADING.matcher(cleanHeading(clause)).matches()) {
                inExampleContext = !formalBodyHeading;
                // 样例正文不参与扫描；标题不清除正式证据身份，避免保留声明却丢弃其后矛盾。
                continue;
            }
            if (inExampleContext) continue;
            List<String> clauseCodes = certificateCodes(clause);
            boolean directlyRelated = mentionsCertificate(clause, clauseCodes, codes, name);
            boolean explicitRef = EXPLICIT_CERTIFICATE_REFERENCE.matcher(clause).find();
            if (!directlyRelated && !explicitRef && CONTEXT_BOUNDARY.matcher(clause).find()) {
                previousRelated = false; pendingDefect &= certificateContextKnown;
            }
            boolean otherCert = CERT_CONTEXT_CODE.matcher(CERT_IDENTIFIER.matcher(clause).replaceAll("")).results()
                    .map(m -> NORMALIZE_CHARS.matcher(m.group().toUpperCase(java.util.Locale.ROOT)).replaceAll("")).anyMatch(c -> !codes.contains(c));
            boolean switchesContext = otherCert || (NAMED_CERTIFICATE.matcher(clause).results().anyMatch(m -> !NON_CERT_VERB.matcher(m.group()).matches()) && !explicitRef && !directlyRelated);
            if (directlyRelated && pendingDefect) return true;
            if (directlyRelated || switchesContext) certificateContextKnown = true;
            if (switchesContext) pendingDefect = false;
            if (directlyRelated) lastCertificateRelated = true; else if (switchesContext) lastCertificateRelated = false;
            boolean related = directlyRelated, effectivePrevious = explicitRef ? lastCertificateRelated : previousRelated;
            boolean continuation = explicitRef || CERTIFICATE_CONTINUATION.matcher(clause).matches();
            if (!related && !otherCert && continuation) related = effectivePrevious;
            String statusClause = NEGATED_INVALID_STATUS.matcher(clause).replaceAll("有效");
            boolean thirdParty = THIRD_PARTY_OWNERSHIP.matcher(clause).find();
            related = related || (effectivePrevious && !switchesContext && ((CERTIFICATE_MATERIAL.matcher(clause).find() && INELIGIBLE_OR_INVALID_STATUS.matcher(statusClause).find()) || thirdParty || MISSING_CERTIFICATE_MATERIAL.matcher(clause).find()));
            previousRelated = related || (previousRelated && !switchesContext);
            boolean defective = thirdParty || UNVERIFIED_VALIDITY.matcher(clause).find() || DEFECT_OR_INVALID_STATUS.matcher(statusClause).find() || !hasConfirmedCertificateClause(clause, name, codes);
            // 前言、说明及分页不应丢弃前置缺陷；身份明确前暂存，明确对象切换时清理。
            if (!certificateContextKnown && continuation && defective) pendingDefect = true;
            if (related && defective) return true;
        }
        return pendingDefect && lastCertificateRelated;
    }
    private static boolean hasFormalBidderStatement(final String bodyText, final String name, final List<String> codes) {
        boolean inExampleContext = false;
        for (String rawLine : bodyText.split("\n")) {
            String line = rawLine.trim();
            if (isFormalBodyHeading(line)) inExampleContext = false;
            if (isExampleHeading(line) || (NON_BIDDER_CERTIFICATE_SENTENCE.matcher(line).matches() && (line.endsWith("：") || line.endsWith(":")))) inExampleContext = true;
            if (inExampleContext) continue;
            if (hasBidderStatementInLine(line, name, codes)) return true;
        }
        return false;
    }
    private static boolean hasBidderStatementInLine(final String line, final String name, final List<String> codes) {
        return java.util.Arrays.stream(line.split("[。；;]"))
                .filter(sentence -> !NON_BIDDER_CERTIFICATE_SENTENCE.matcher(sentence).matches())
                .flatMap(sentence -> java.util.Arrays.stream(sentence.split("[，,]")))
                .anyMatch(clause -> isConfirmedBidderStatement(clause, name, codes));
    }
    private static boolean isConfirmedBidderStatement(final String clause, final String name, final List<String> codes) {
        var response = BIDDER_CERTIFICATE_STATEMENT.matcher(clause);
        if (!response.find()) return false;
        String asserted = CERTIFICATE_OBJECT_PREFIX.matcher(NORMALIZE_CHARS.matcher(response.group(1)).replaceAll(""))
                .replaceFirst("").toUpperCase(java.util.Locale.ROOT);
        return matchesCertificateIdentity(asserted, name, codes) && hasConfirmedCertificateClause(clause, name, codes);
    }
    private static boolean matchesCertificateIdentity(final String asserted, final String name, final List<String> codes) {
        boolean startsWithName = asserted.startsWith(name.toUpperCase(java.util.Locale.ROOT));
        return codes.isEmpty() ? startsWithName : certificateCodes(asserted).containsAll(codes) && (startsWithName || codes.stream().anyMatch(asserted::startsWith));
    }
    private static String certificateIdentity(final String quote) {
        return !quote.startsWith("资质证书: ") ? quote : quote.substring("资质证书: ".length()).split("（等级: |，有效期至 ", 2)[0].trim();
    }
    private static boolean hasConfirmedCertificateClause(final String clause, final String name, final List<String> codes) {
        // 编号字段归一为材料名称，剩余整句仍须确认持证状态；有效期不替代状态确认。
        String fact = NORMALIZE_CHARS.matcher(CERT_IDENTIFIER.matcher(clause).replaceAll("证书")).replaceAll("").toUpperCase(java.util.Locale.ROOT).replace(name.toUpperCase(java.util.Locale.ROOT), "");
        for (String code : codes) fact = fact.replace(code, "");
        // 材料标题仅用于定位；正式持证声明仍须单独匹配投标人及持证动作。
        if (fact.matches("(?:认证|证书|许可证|资质)?(?:证明材料|复印件|扫描件)")) return true;
        return CONFIRMED_CERTIFICATE_FACT.matcher(fact.replaceFirst("^(?:该|此|本|上述)?" + CERT_MATERIAL_WORDS, "")).matches();
    }
    private static boolean mentionsCertificate(final String clause, final List<String> clauseCodes, final List<String> codes, final String name) {
        return codes.isEmpty() ? NORMALIZE_CHARS.matcher(clause).replaceAll("").contains(name) : clauseCodes.stream().anyMatch(codes::contains);
    }
    private static List<String> certificateCodes(final String text) {
        return text == null ? List.of() : CERT_CODE.matcher(CERT_IDENTIFIER.matcher(text).replaceAll("")).results()
                .map(m -> NORMALIZE_CHARS.matcher(m.group().toUpperCase(java.util.Locale.ROOT)).replaceAll("")).distinct().toList();
    }
    private static boolean hasCompleteBothSidesIdFacts(final DocumentContent bidDoc) {
        if (bidDoc == null || bidDoc.pages() == null || bidDoc.images().isEmpty()) return false;
        return bidDoc.pages().stream().filter(p -> p.text() != null && !BidLetterCheckPolicy.isCatalogOrOutlinePage(p.text()))
                .filter(p -> AuthorizationFieldPolicy.hasImageOnPage(bidDoc, p.pageNumber())).anyMatch(p -> p.text().contains(ScanPageTextPolicy.TRANSCRIPTION_MARKER)
                        && IdAuthLetterCheckPolicy.hasCompleteBothSidesIdFacts(p.text().substring(p.text().indexOf(ScanPageTextPolicy.TRANSCRIPTION_MARKER))));
    }
    private static boolean isBody(final QualityEvidence evidence, final DocumentContent bidDoc) {
        return "BID".equals(evidence.sourceType()) && evidence.pageNumber() != null && evidence.pageNumber() > 0 && !evidence.quote().isBlank() && !BidLetterCheckPolicy.isCatalogOrOutlinePage(sourceText(evidence, bidDoc));
    }
    private static String sourceText(final QualityEvidence evidence, final DocumentContent bidDoc) {
        if (bidDoc == null || evidence == null || evidence.pageNumber() == null) return evidence == null ? "" : evidence.quote();
        return bidDoc.pages().stream().filter(p -> evidence.pageNumber().equals(p.pageNumber())).findFirst()
                .map(DocumentContent.PageContent::text).orElse(evidence.quote());
    }
    private static List<String> proofTopics(final String itemCode, final boolean financialClaim) {
        if (financialClaim) return FINANCIAL_CUES;
        if (BidQualityCheckItem.SCORING_RESPONSE.getCode().equals(itemCode)) return List.of("技术方案", "实施方案", "施工组织", "项目经理", "项目管理", "团队人员", "服务方案", "进度计划", "技术架构", "人员配置");
        if (BidQualityCheckItem.AUTHORIZATION_LETTER.getCode().equals(itemCode)) return List.of("授权委托书", "法定代表人身份证明", "授权期限", "授权范围", "受托人", "法定代表人");
        if (BidQualityCheckItem.ID_AUTH_LETTER.getCode().equals(itemCode)) return List.of("身份证", "公民身份号码");
        if (BidQualityCheckItem.FRONT_TABLE_RESPONSE.getCode().equals(itemCode)) return List.of("前附表", "条款响应", "商务响应表", "交货期", "质保期", "付款方式", "投标有效期", "质量标准", "交货地点", "工期");
        return List.of("资格", "纳税", "社保", "信用", "信誉", "业绩");
    }
    private static String cleanHeading(final String rawLine) { return rawLine == null ? "" : rawLine.trim().replaceFirst("^#{1,6}\\h+", "").replaceFirst("\\h+#+$", "").trim(); }
    private static boolean isExampleHeading(final String rawLine) { return EXAMPLE_HEADING.matcher(cleanHeading(rawLine)).matches(); }
    private static boolean isFormalBodyHeading(final String rawLine) { String heading = cleanHeading(rawLine); return FORMAL_BODY_HEADING.matcher(heading).matches() || (SECTION_HEADING.matcher(heading).matches() && !SAMPLE_SECTION.matcher(heading).matches()); }
    private static boolean scoringExampleContext(final String rawClause, final boolean previous) {
        return !isFormalBodyHeading(rawClause) && (isExampleHeading(rawClause) || previous);
    }
    private static boolean explicitDefect(final String text, final List<String> topics, final String precedingText) {
        boolean scoring = topics.contains("技术方案"), exampleContext = false;
        if (scoring) for (String clause : precedingText.split("[。；;\\r\\n]+")) exampleContext = scoringExampleContext(clause.trim(), exampleContext);
        if (!scoring) {
            Matcher blank = CONCRETE_BLANK.matcher(text);
            while (blank.find()) if (topics.stream().anyMatch((blank.group(1) != null ? blank.group(1) : blank.group(2))::contains)) return true;
            if (List.of("示例", "模板", "填写", "范例", "参考").stream().anyMatch(text::contains)) return false;
        }
        for (String rawClause : text.split("[。；;\\r\\n]+")) {
            String clause = rawClause.trim();
            if (scoring) {
                // 样例标题约束后续字段；普通称谓和其他分句的参考/填写说明不抹掉真实缺陷。
                exampleContext = scoringExampleContext(clause, exampleContext);
                if (exampleContext || clause.matches(".*(?:示例|模板|范例|样例|样本|参考格式).*")) continue;
                Matcher blank = CONCRETE_BLANK.matcher(clause);
                while (blank.find()) if (topics.stream().anyMatch((blank.group(1) != null ? blank.group(1) : blank.group(2))::contains)) return true;
            }
            Matcher admission = SELF_ADMISSION.matcher(clause);
            if (admission.matches() && topics.stream().anyMatch(admission.group(1)::contains)) return true;
        }
        return false;
    }
}
