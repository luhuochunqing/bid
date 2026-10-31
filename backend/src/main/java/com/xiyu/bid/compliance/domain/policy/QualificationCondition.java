// Input: 完整资格条件原文及可核实的来源
// Output: 不可变条件、证书子项、作用对象与适用性；缺失来源保持未知
// Pos: compliance/domain/policy/ - 资格条件结构化模型
package com.xiyu.bid.compliance.domain.policy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record QualificationCondition(String text, Source source, List<CertificateRequirement> certificates) {
    public enum Applicability { MANDATORY, OPTIONAL, UNKNOWN }
    public enum Target { BIDDER, PRODUCT, PERSON, UNKNOWN }

    /** pageNumber 沿用文档逻辑页；不宣称是PDF物理页。 */
    public record Source(String fileName, String fileHash, Integer pageNumber, String quote) {
        public Source {
            fileName = fileName == null ? "" : fileName;
            fileHash = fileHash == null ? "" : fileHash;
            quote = quote == null ? "" : quote;
        }
    }

    public record CertificateRequirement(String requirementText, Target target, Applicability applicability,
                                         String basis, Integer cellIndex) { }

    public QualificationCondition {
        text = text == null ? "" : text;
        source = source == null ? new Source("", "", null, text) : source;
        certificates = certificates == null ? CertificateRequirementPolicy.classify(text, source.quote()) : List.copyOf(certificates);
    }

    static List<String> cleanTexts(final List<String> raw) {
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String value : raw == null ? List.<String>of() : raw) {
            String item = QualificationConditionExtractor.cleanCondition(value);
            if (!item.isBlank() && !QualificationConditionExtractor.isHeading(item)
                    && !item.matches("无|不适用|不要求|/")) cleaned.add(item);
        }
        return List.copyOf(cleaned);
    }

    /** 旧文本未被结构化提取覆盖时仍保留，禁止把未知解释为没有要求。 */
    static List<QualificationCondition> complete(final List<QualificationCondition> structured, final List<String> raw) {
        List<QualificationCondition> conditions = new ArrayList<>(structured == null ? List.of() : structured);
        for (String text : cleanTexts(raw)) {
            if (conditions.stream().noneMatch(c -> c.text().equals(text))) {
                conditions.add(new QualificationCondition(text, null, null));
            }
        }
        return conditions.stream().distinct().toList();
    }

    static List<String> certificateQueries(final List<QualificationCondition> conditions) {
        return conditions.stream().flatMap(c -> c.certificates().stream())
                .filter(c -> c.applicability() != Applicability.OPTIONAL)
                .filter(c -> c.target() == Target.BIDDER || c.target() == Target.UNKNOWN)
                .map(CertificateRequirement::requirementText).distinct().toList();
    }
}
