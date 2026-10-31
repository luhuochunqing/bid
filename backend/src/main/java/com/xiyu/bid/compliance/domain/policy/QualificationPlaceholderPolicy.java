// Input: 单页文本
// Output: 与资质证明明确关联的占位符及其页面内位置
// Pos: compliance/domain/policy/ - 资质材料占位纯核心
package com.xiyu.bid.compliance.domain.policy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class QualificationPlaceholderPolicy {
    private static final Pattern PLACEHOLDER = Pattern.compile("_{3,}|【(?:请|此处)?[^】]*(?:填写|上传|粘贴|提供|插入|替换)[^】]*】");
    private static final Pattern QUALIFICATION = Pattern.compile("资质证书|资格证书|企业资质|体系认证|许可证|资质证明|资格证明");

    enum Certainty { CONFIRMED, UNKNOWN }
    private static final Pattern OTHER_MATERIAL = Pattern.compile("延保|协议|合同|甲方|乙方");

    record Hit(String value, int offset, Certainty certainty, String context) { }
    record Scan(List<Hit> hits, boolean inQualificationSection, String certificateContext) { }
    private static final Pattern CERTIFICATE_NAME = Pattern.compile("(?i)ISO[ \\t-]*[0-9]{3,5}(?![0-9])|[\\p{IsHan}]{2,20}许可证");
    private static final Pattern ISO_LIST = Pattern.compile("(?i)ISO[ \\t-]*[0-9]{3,5}(?![0-9])[ \\t]*[、,，/或][ \\t]*ISO[ \\t-]*[0-9]{3,5}(?![0-9])");
    private QualificationPlaceholderPolicy() { }

    static boolean requiresManual(final TenderRequirements requirements, final Hit hit) {
        if (hit.certainty() == Certainty.UNKNOWN) return true;
        if (requirements == null) return true;
        var certificates = requirements.qualificationConditions().stream()
                .flatMap(condition -> condition.certificates().stream()).toList();
        if (certificates.isEmpty()) return true;
        Set<String> names = certificateNames(hit.context());
        if (names.size() != 1) return true;
        String name = names.iterator().next();
        var matched = certificates.stream().filter(c -> name.startsWith("ISO")
                ? certificateNames(c.requirementText()).contains(name)
                : c.requirementText().replace(" ", "").contains(name.replace(" ", ""))).toList();
        return matched.isEmpty() || matched.stream()
                .anyMatch(c -> c.applicability() != QualificationCondition.Applicability.MANDATORY
                        || c.target() != QualificationCondition.Target.BIDDER);
    }

    static Scan scan(final String text, final boolean previousSection, final String previousContext) {
        int offset = 0;
        boolean inQualificationSection = previousSection;
        String certificateContext = previousContext;
        List<Hit> hits = new ArrayList<>();
        for (String line : text.split("\\n", -1)) {
            Matcher placeholder = PLACEHOLDER.matcher(line);
            while (placeholder.find()) {
                String prefix = line.substring(0, placeholder.start());
                int sentenceBoundary = Math.max(Math.max(prefix.lastIndexOf('；'), prefix.lastIndexOf(';')),
                        Math.max(prefix.lastIndexOf('。'), prefix.lastIndexOf('.')));
                int boundary = Math.max(sentenceBoundary, Math.max(Math.max(prefix.lastIndexOf('，'), prefix.lastIndexOf(',')),
                        prefix.lastIndexOf('、')));
                String localName = certificateName(prefix.substring(boundary + 1));
                String ownName = certificateName(placeholder.group());
                String priorName = localName.isBlank() ? certificateName(certificateContext) : localName;
                boolean conflict = certificateNames(prefix.substring(boundary + 1)).size() > 1
                        || certificateNames(placeholder.group()).size() > 1
                        || hasAmbiguousIsoList(prefix.substring(sentenceBoundary + 1))
                        || localName.isBlank() && certificateNames(certificateContext).size() > 1
                        || !ownName.isBlank() && !priorName.isBlank() && !ownName.equals(priorName);
                String context = !ownName.isBlank() ? ownName : !localName.isBlank() ? localName : certificateContext;
                if (isQualificationContext(line, placeholder.start(), inQualificationSection)
                        || inQualificationSection && isCertificatePlaceholder(placeholder.group())) {
                    hits.add(new Hit(placeholder.group(), offset + placeholder.start(),
                            conflict ? Certainty.UNKNOWN : Certainty.CONFIRMED, context));
                } else if (hasCertificateField(line, placeholder.start()) && !context.isBlank()) {
                    hits.add(new Hit(placeholder.group(), offset + placeholder.start(), Certainty.CONFIRMED, context));
                } else if (hasCertificateField(line, placeholder.start())
                        || inQualificationSection && !OTHER_MATERIAL.matcher(line).find()) {
                    hits.add(new Hit(placeholder.group(), offset + placeholder.start(), Certainty.UNKNOWN, context));
                }
            }
            if (!line.isBlank()) {
                String trimmed = line.trim();
                if (trimmed.matches("(?:证书名称|资质名称|许可证名称)[：:].+") && CERTIFICATE_NAME.matcher(trimmed).find()) {
                    certificateContext = trimmed;
                    inQualificationSection = true;
                } else if (isHeading(trimmed) || isCertificateTitle(trimmed)) {
                    inQualificationSection = true;
                    certificateContext = trimmed;
                } else if (isOtherHeading(trimmed)) {
                    inQualificationSection = false;
                    certificateContext = "";
                }
            }
            offset += line.length() + 1;
        }
        return new Scan(List.copyOf(hits), inQualificationSection, certificateContext);
    }

    private static String certificateName(final String text) {
        var names = certificateNames(text);
        return names.size() == 1 ? names.iterator().next() : "";
    }

    private static Set<String> certificateNames(final String text) {
        var matcher = CERTIFICATE_NAME.matcher(text == null ? "" : text);
        Set<String> result = new LinkedHashSet<>();
        while (matcher.find()) {
            String candidate = matcher.group().replaceFirst("^(?:(?:请|须|应|此处|上传|提供|提交|粘贴))+", "");
            if (candidate.regionMatches(true, 0, "ISO", 0, 3))
                candidate = candidate.toUpperCase(Locale.ROOT).replaceAll("[ \\t-]+", "");
            if (!candidate.equals("许可证")) result.add(candidate);
        }
        return result;
    }

    private static boolean hasAmbiguousIsoList(final String text) {
        var matcher = ISO_LIST.matcher(text);
        while (matcher.find()) if (certificateNames(matcher.group()).size() > 1) return true;
        return false;
    }

    private static boolean isCertificateTitle(final String line) {
        String title = line.replaceFirst("[（(][^（）()\\n]{0,40}[）)]$", "");
        return (title.matches("[^，,。；;：:]{2,35}许可证")
                || title.matches("(?i)ISO[ \\t-]*[0-9]{3,5}[^，,。；;：:]{0,30}(?:认证|证书)"))
                && !title.matches(".*(?:已提供|已提交|须|应|必须|持有|取得|具备|具有|未提供).*");
    }

    private static boolean isQualificationContext(final String line, final int start, final boolean inQualificationSection) {
        String prefix = line.substring(0, start);
        int boundary = Math.max(Math.max(prefix.lastIndexOf('，'), prefix.lastIndexOf('。')),
                Math.max(Math.max(prefix.lastIndexOf('；'), prefix.lastIndexOf(',')), prefix.lastIndexOf(';')));
        String local = prefix.substring(boundary + 1);
        return isDirectQualificationContext(local)
                || (boundary < 0 && inQualificationSection && hasCertificateField(line, start));
    }

    private static boolean isCertificatePlaceholder(final String placeholder) {
        return placeholder.startsWith("【") && !OTHER_MATERIAL.matcher(placeholder).find()
                && placeholder.matches("(?i).*(?:证书|许可证|资质|ISO[ \\t-]*[0-9]+).*");
    }

    private static boolean hasCertificateField(final String line, final int start) {
        String prefix = line.substring(0, start).trim();
        return prefix.matches("(?:请填写)?(?:证书|资质|许可证)(?:名称|编号|有效期(?:至)?)[：:]?");
    }

    private static boolean isDirectQualificationContext(final String text) {
        var qualification = QUALIFICATION.matcher(text);
        int lastQualification = -1;
        while (qualification.find()) lastQualification = qualification.start();
        var other = OTHER_MATERIAL.matcher(text);
        int lastOther = -1;
        while (other.find()) lastOther = other.start();
        return lastQualification >= 0 && lastQualification > lastOther;
    }

    private static boolean isOtherHeading(final String line) {
        return line.matches(".*(?:协议书|合同书|授权书|承诺书|一览表|联系人信息|报价表)$")
                || line.matches("(?:第[一二三四五六七八九十0-9]+[章节部分]+|附件[一二三四五六七八九十0-9]+)\\s*.+");
    }

    private static boolean isHeading(final String line) {
        if (line.matches(".*(?:已提供|已提交|须|应|必须|持有|取得|具备|具有|不满足|未提供).*")) return false;
        return line.matches("(?:(?:第[一二三四五六七八九十0-9]+[章节部分]+|附件[一二三四五六七八九十0-9]+)\\s*)?"
                + "(?:[^，,。；;：:]{0,30}(?:许可证|资质证书|资格证书|认证证书)|企业资质(?:证明)?|体系认证|资质证明|资格证明)"
                + "(?:证明材料|材料|复印件|扫描件)?(?:[：:]\\s*(?:ISO[- ]?[0-9]+(?:证书)?)?)?");
    }
}
