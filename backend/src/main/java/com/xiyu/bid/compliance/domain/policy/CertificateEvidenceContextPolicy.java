// Input: 正文与目标证书身份
// Output: 同句事实判定与未具名状态复核；完整公共字符类复用，不传播跨句身份
// Pos: compliance/domain/policy - 证书文本证据纯策略
package com.xiyu.bid.compliance.domain.policy;

import java.util.List;
import java.util.regex.Pattern;

final class CertificateEvidenceContextPolicy {
    // 匹配尚未遇到分句终止符的一个字符；保留原来是否允许换页符的区别。
    private static final String CLAUSE_STOP = "[^。；;，,\\n\\f]";
    private static final String CLAUSE_STOP_WITH_PAGE_BREAK = "[^。；;，,\\n]";
    private static final Pattern CERT_IDENTIFIER =
            Pattern.compile("(?:许可证编号|证书编号|资质编号|认证编号|备案编号|证号|注册号|编号)[\\s\\u3000|:：为是]*[A-Za-z0-9][A-Za-z0-9/-]*");
    private static final String STANDARD_CERT_CODE =
            "(?:ISO\\s*\\d+|GB/?T\\s*\\d+|EDI|ICP|CMMI\\s*\\d*|ITSS\\s*\\d*|CCRC\\s*\\d*|"
                    + "CS\\s*\\d*)";
    private static final Pattern CERT_CODE =
            Pattern.compile("(?i)(?:" + STANDARD_CERT_CODE + "|[A-Z]{2,}\\d+)");
    private static final Pattern CERT_CONTEXT_CODE =
            Pattern.compile(CERT_CODE.pattern() + "(?=\\s*(?:认证|证书|许可证|资质))");
    // 否定持证、材料关联和裁决共用状态文法；同分句允许提交位置及修饰语分隔否定词、动作与材料，仍受证书、章节及业务对象边界约束。
    private static final String BIDDER_SELF = "(?:本公司|我司|我方|本投标人|投标人)";
    private static final String MISSING_MATERIAL_STATUS =
            "待补|后补|暂缺|缺失|缺少|缺页|未附|(?:暂时\\s*)?(?:无法|不能|暂无法|暂不能|未能|(?:尚)?未(?:予)?|不(?:予)?|拒绝)"
                    + "(?:随(?:本次)?(?:标书|投标文件)|在[\\h]*(?:本次[\\h]*)?(?:标书|投标文件)[\\h]*(?:中|内))?[\\h]*(?:(?:一并|"
                    + "一同|同时)[\\h]*)?(?:提供|提交|上传|报送|附具|附|出示|补充|出具)";
    // 仅用于find：含“尚未/未曾/未能”的文本已经命中“未”；到期/截止等已由前一支覆盖。
    private static final String INELIGIBLE_OR_INVALID_STATUS =
            "(?:没有|未)(?:取得|持有|获得|通过|具备|具有)?|不(?:再)?(?:由"
                    + CLAUSE_STOP
                    + "*)?(?:持有|具有|具备)|(?:已经?)?(?:于"
                    + CLAUSE_STOP_WITH_PAGE_BREAK
                    + "*)?(?:被)?(?:结束|截止|到期|逾期|过期|失效|吊销|撤销|届满|注销|作废|终止|满期|废止|中止|暂停(?:使用|有效|效力)?|停止使用|冻结)|"
                    + "(?:超过|过|超出)有效期|有效期(?:已经?)?满|失去效力|不再有效|停止有效|无效|伪造|假证|假冒|变造|虚假|不实|(?:(?:真实性|真伪|有效性|效力|"
                    + "资质|资格|持证(?:状态)?|状态|证书|认证)[\\s:：为是]*)?(?:不明|存疑|待定|待核[实验查]?|待复核|待确认|(?:无法|不能|尚未|未能|有待|"
                    + "需)(?:确认|核实|核验|查实))";
    private static final Pattern DEFECT_OR_INVALID_STATUS =
            Pattern.compile(INELIGIBLE_OR_INVALID_STATUS + "|" + MISSING_MATERIAL_STATUS);
    private static final String CERT_MATERIAL_WORDS =
            "(?:证书|认证|许可证|资质|证件|证|持证(?:状态)?|有效性|效力|复印件|扫描件|原件|证明材料)";
    private static final Pattern CERTIFICATE_MATERIAL = Pattern.compile(CERT_MATERIAL_WORDS);
    // “由…持有/取得”仅放行完整的投标人自称；具名主体无法据此确认与投标人相同。
    private static final Pattern THIRD_PARTY_OWNERSHIP =
            Pattern.compile("(?:不再|并非|未|(?:并)?不是)[\\s\\u3000]*(?:由[\\s\\u3000]*)?"
                            + BIDDER_SELF
                            + "[\\s\\u3000]*(?:持有|取得|所有|具备|具有|的)|(?:(?:不(?:再)?|并非|未|(?:并)?不是)[\\s\\u3000]*(?:属于|"
                            + "归(?:属)?(?:于)?)|非)"
                            + CLAUSE_STOP_WITH_PAGE_BREAK
                            + "*"
                            + BIDDER_SELF
                            + "|(?:属于|归属(?:于)?|持证人|持有人|(?:持证|持有)(?:单位|主体))[\\s\\u3000:：为是]*+(?!"
                            + BIDDER_SELF
                            + "[\\s\\u3000]*(?:所有|持有)?[\\s\\u3000）)]*$)|由[\\s\\u3000]*+(?!"
                            + BIDDER_SELF
                            + "[\\s\\u3000]*(?:持有|取得))"
                            + CLAUSE_STOP_WITH_PAGE_BREAK
                            + "+(?:持有|取得)|(?:供应商|合作方|合作企业|第三方|关联公司|制造商|厂家|厂商|母公司|子公司|生产商|生产厂家|代理商|原厂|其他(?:单位|企业|公司))"
                            + CLAUSE_STOP_WITH_PAGE_BREAK
                            + "*(?:所有|持有|取得)");
    private static final Pattern BIDDER_CERTIFICATE_STATEMENT =
            Pattern.compile("^\\s*" + BIDDER_SELF + "[：:\\s]*(?:已)?(?:具有|持有|取得|获得|通过)(.+)");
    // 引导语仅用于识别当前分句明确的其他业务对象，不关联前后证书。
    private static final String CERTIFICATE_INTRODUCTION =
            "(?:但是|不过|但|且|然而|经(?:现场|重新)?[核查实对审查排查复核]+(?:确认)?(?:后)?|(?:核对|核实|复核|审核|核查|审查)(?:结果|结论)"
                    + "(?:为)?)";
    // 未具名状态只触发人工复核，不携带任何跨句证书身份。
    private static final String BARE_CERTIFICATE_STATUS =
            "(?:(?:[\\p{IsHan}\\h]*(?:为|是)|已经?|被|处于|系|目前|实际|现|现已|暂时|暂停|停止|权属|状态|尚|有待|待).+|"
                    + "[\\p{IsHan}\\h]*(?:借用|借来|借自|租用|租赁).*)";
    // 只有分句主题明确写出业务对象时，才可排除这条非证书事实。
    private static final Pattern CONTEXT_BOUNDARY =
            Pattern.compile("^\\s*(?:"
                            + CERTIFICATE_INTRODUCTION
                            + "[\\h：:、]*)?(?:第[一二三四五六七八九十百0-9]+[章节篇部分](?!"
                            + CLAUSE_STOP
                            + "*(?:证书|认证|许可证|资质|证件|有效|核验|核查))|(?:"
                            + MISSING_MATERIAL_STATUS
                            + ")?(?:本|该|上述)?(?:项目经理|人员配置|团队人员|安装服务|售后服务协议|售后服务承诺|售后服务|合同|协议|报价|售后承诺|投标有效期|交货期|质保期|"
                            + "付款方式)(?![\\h]*(?:附件|附录|所附|中|内))(?!"
                            + CLAUSE_STOP
                            + "*(?:证书|认证|许可证|资质|证件)))");
    private static final Pattern WRAPPED_VALIDITY =
            Pattern.compile("((?:有效期(?:限)?|截止日期|到期日期)[：: \\t]*)[\\r\\n\\s]+(?=[0-9０-９])");
    private static final Pattern UNVERIFIED_VALIDITY =
            Pattern.compile("(?:有效期(?:限)?|截止日期|到期日期)"
                            + CLAUSE_STOP_WITH_PAGE_BREAK
                            + "*[0-9０-９]|[0-9０-９]"
                            + CLAUSE_STOP_WITH_PAGE_BREAK
                            + "*(?:到期|截止|过期|届满|失效)");
    private static final Pattern EXAMPLE_HEADING =
            Pattern.compile("^(?:[0-9一二三四五六七八九十]+[、.．:：\\s]+|(?:第[0-9一二三四五六七八九十]+[章节篇部分条款]|"
                            + "附录[0-9一二三四五六七八九十A-Za-z]*|附件[0-9一二三四五六七八九十A-Za-z]*)[、.．:：\\s]*)?(?:(?:合同(?:条款|文本|书)?|"
                            + "[\\p{IsHan}]{0,20}(?:协议书?|声明)|填写|填报|格式|参考)?(?:示例|模板|范例|样例|样本|格式样例|参考样例|参考格式|"
                            + "[（(](?:示例|模板|范例|样例|样本)[）)])(?:[：:\\s]+|[（(][^）)]*[）)]|说明)*|(?:非本公司实际声明|填报说明|填写说明|"
                            + "模板说明|格式说明))[：:\\s]*$");
    private static final Pattern
            SECTION_HEADING =
                    Pattern.compile("^第[一二三四五六七八九十百零〇0-9]+(?:章|节|篇|部分)\\h*[^。；;，,：:]{0,60}[：:]?$"),
            SAMPLE_SECTION = Pattern.compile(".*(?:示例|模板|范例|样例|样本|参考格式|填写说明|填报说明).*"), FORMAL_BODY_HEADING =
                    Pattern.compile("^(?:[一二三四五六七八九十0-9]+[、.．]\\s*)?正式(?:声明|正文)[：:]?$");
    private static final Pattern NON_BIDDER_CERTIFICATE_SENTENCE =
            Pattern.compile(".*(?:要求|须|应当|应具备|应具有|必须|采购|招标|引用|合作方|第三方|供应商|关联公司|属于|归属|持证人|所有|填写示例|填报示例|格式样例|参考格式|"
                            + "填报说明|填写说明|非本公司实际声明).*");
    private static final String CERTIFICATE_OBJECT_PREFIX_REGEX =
            "(?:有效的?|本公司(?:的)?|我司(?:的)?|我方(?:的)?)";
    private static final Pattern CERTIFICATE_OBJECT_PREFIX =
            Pattern.compile("^" + CERTIFICATE_OBJECT_PREFIX_REGEX);
    // 关联证书陈述必须完整落入已确认文法；未命中缺陷词表并不等于状态已确认。
    private static final Pattern CONFIRMED_CERTIFICATE_FACT =
            Pattern.compile("(?:"
                            + BIDDER_SELF
                            + "(?:已)?(?:具有|持有|取得|获得|通过)"
                            + CERTIFICATE_OBJECT_PREFIX_REGEX
                            + "?)?(?:质量管理体系|环境管理体系|职业健康安全管理体系|认证|证书|许可证|和|及|与)*|(?:属于|归属(?:于)?|持证人[为是]?|持有人[为是]?|"
                            + "(?:持证|持有)(?:单位|主体)[为是]?)"
                            + BIDDER_SELF
                            + "(?:所有|持有)?|由"
                            + BIDDER_SELF
                            + "(?:持有|取得)|由认证机构颁发|(?:尚)?未(?:到期|过期|失效)|(?:目前|现|现已|仍然|仍)?有效|已随(?:本次)?(?:投标文件|标书)(?:一并|"
                            + "一同|同时)?(?:提交|提供|报送)");
    private static final Pattern NORMALIZE_CHARS = Pattern.compile("[\\s\\u3000\\p{P}]");
    private static final Pattern NEGATED_INVALID_STATUS = Pattern.compile("(?:尚)?未(?:到期|过期|失效)");

    private CertificateEvidenceContextPolicy() {}

    static boolean hasPositiveCertificateStatement(final String text, final String certificate) {
        if (text == null || certificate == null) return false;
        List<String> codes = certificateCodes(certificate);
        String name = NORMALIZE_CHARS.matcher(certificate).replaceAll("");
        String bodyText =
                WRAPPED_VALIDITY.matcher(QualificationConditionExtractor.stripHtml(text)).replaceAll("$1");
        List<String> clauses =
                java.util.Arrays.stream(bodyText.split("[。；;，,\n\f]")).filter(c -> !c.isBlank()).toList();
        return !hasCertificateDefect(clauses, name, codes) && hasFormalBidderStatement(bodyText, name, codes);
    }

    private static boolean hasCertificateDefect(
            final List<String> clauses, final String name, final List<String> codes) {
        boolean inExampleContext = false;
        for (String clause : clauses) {
            boolean formalBody = isFormalBodyHeading(clause);
            if (isExampleHeading(clause) || (inExampleContext && formalBody)
                    || FORMAL_BODY_HEADING.matcher(cleanHeading(clause)).matches()) {
                inExampleContext = !formalBody;
                continue;
            }

            if (inExampleContext) continue;
            if (hasUnconfirmedClause(clause, name, codes)) return true;
        }
        return false;
    }

    private static boolean hasUnconfirmedClause(final String clause, final String name, final List<String> codes) {
        String statusClause = NEGATED_INVALID_STATUS.matcher(clause).replaceAll("有效");
        if (mentionsCertificate(clause, certificateCodes(clause), codes, name)) {
            return hasInvalidStatus(clause, statusClause) || !hasConfirmedCertificateClause(clause, name, codes);
        }
        // 只排除当前分句明确写出的其他对象，不继承前后证书身份。
        boolean anotherCertificate =
                CERT_CONTEXT_CODE.matcher(CERT_IDENTIFIER.matcher(clause).replaceAll("")).results()
                        .anyMatch(match -> !codes.contains(normalizeCode(match.group())));
        if (anotherCertificate || CONTEXT_BOUNDARY.matcher(clause).find()) return false;
        // 未具名材料、状态和权属只能触发复核，不能归属到某张证书。
        return CERTIFICATE_MATERIAL.matcher(clause).find() || hasInvalidStatus(clause, statusClause)
                || clause.trim().matches(BARE_CERTIFICATE_STATUS)
                || CONFIRMED_CERTIFICATE_FACT.matcher(clause.trim()).matches();
    }

    private static boolean hasInvalidStatus(final String clause, final String statusClause) {
        return DEFECT_OR_INVALID_STATUS.matcher(statusClause).find() || THIRD_PARTY_OWNERSHIP.matcher(clause).find()
                || UNVERIFIED_VALIDITY.matcher(clause).find();
    }

    private static String normalizeCode(final String code) {
        return NORMALIZE_CHARS.matcher(code.toUpperCase(java.util.Locale.ROOT)).replaceAll("");
    }

    private static boolean hasFormalBidderStatement(
            final String bodyText, final String name, final List<String> codes) {
        boolean inExampleContext = false;
        for (String rawLine : bodyText.split("\n")) {
            String line = rawLine.trim();
            if (isFormalBodyHeading(line)) inExampleContext = false;
            if (isExampleHeading(line) || (NON_BIDDER_CERTIFICATE_SENTENCE.matcher(line).matches()
                            && (line.endsWith("：") || line.endsWith(":")))) inExampleContext = true;
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

    private static boolean isConfirmedBidderStatement(
            final String clause, final String name, final List<String> codes) {
        var response = BIDDER_CERTIFICATE_STATEMENT.matcher(clause);
        if (!response.find()) return false;
        String asserted =
                CERTIFICATE_OBJECT_PREFIX.matcher(NORMALIZE_CHARS.matcher(response.group(1)).replaceAll(""))
                        .replaceFirst("").toUpperCase(java.util.Locale.ROOT);
        return matchesCertificateIdentity(asserted, name, codes) && hasConfirmedCertificateClause(clause, name, codes);
    }

    private static boolean matchesCertificateIdentity(
            final String asserted, final String name, final List<String> codes) {
        boolean startsWithName = asserted.startsWith(name.toUpperCase(java.util.Locale.ROOT));
        return codes.isEmpty()
                ? startsWithName
                : certificateCodes(asserted).containsAll(codes)
                        && (startsWithName || codes.stream().anyMatch(asserted::startsWith));
    }

    static String certificateIdentity(final String quote) {
        return !quote.startsWith("资质证书: ")
                ? quote
                : quote.substring("资质证书: ".length()).split("（等级: |，有效期至 ", 2)[0].trim();
    }

    private static boolean hasConfirmedCertificateClause(
            final String clause, final String name, final List<String> codes) {
        // 编号字段归一为材料名称，剩余整句仍须确认持证状态；有效期不替代状态确认。
        String fact =
                NORMALIZE_CHARS.matcher(CERT_IDENTIFIER.matcher(clause).replaceAll("证书")).replaceAll("")
                        .toUpperCase(java.util.Locale.ROOT).replace(name.toUpperCase(java.util.Locale.ROOT), "");
        for (String code : codes) fact = fact.replace(code, "");
        // 材料标题仅用于定位；正式持证声明仍须单独匹配投标人及持证动作。
        if (fact.matches("(?:认证|证书|许可证|资质)?(?:证明材料|复印件|扫描件)")) return true;
        return CONFIRMED_CERTIFICATE_FACT.matcher(fact.replaceFirst("^(?:该|此|本|上述)?" + CERT_MATERIAL_WORDS, ""))
                .matches();
    }

    private static boolean mentionsCertificate(final String clause, final List<String> clauseCodes,
            final List<String> codes, final String name) {
        return codes.isEmpty()
                ? NORMALIZE_CHARS.matcher(clause).replaceAll("").contains(name)
                : clauseCodes.stream().anyMatch(codes::contains);
    }

    private static List<String> certificateCodes(final String text) {
        return text == null
                ? List.of()
                : CERT_CODE.matcher(CERT_IDENTIFIER.matcher(text).replaceAll("")).results().map(m ->
                                        NORMALIZE_CHARS.matcher(m.group().toUpperCase(java.util.Locale.ROOT))
                                                .replaceAll("")).distinct().toList();
    }

    private static String cleanHeading(final String rawLine) {
        return rawLine == null
                ? ""
                : rawLine.trim().replaceFirst("^#{1,6}\\h+", "").replaceFirst("\\h+#+$", "").trim();
    }

    static boolean isExampleHeading(final String rawLine) {
        return EXAMPLE_HEADING.matcher(cleanHeading(rawLine)).matches();
    }

    static boolean isFormalBodyHeading(final String rawLine) {
        String heading = cleanHeading(rawLine);
        return FORMAL_BODY_HEADING.matcher(heading).matches() || (SECTION_HEADING.matcher(heading).matches()
                        && !SAMPLE_SECTION.matcher(heading).matches());
    }
}
