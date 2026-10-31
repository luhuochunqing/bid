// Input: 偏离状态、投标响应、说明或全文命中的原始文本
// Output: 证据来源、极性及不确定性；不改写原文和定位
// Pos: compliance/domain/policy/ - 响应性证据角色纯核心
package com.xiyu.bid.compliance.domain.policy;

import java.text.Normalizer;
import java.util.regex.Pattern;

final class DeviationEvidencePolicy {
    enum Origin { STATUS_COLUMN, RESPONSE_COLUMN, DESCRIPTION_COLUMN, BIDDER_TEXT, RULE }
    enum Polarity { NEGATIVE, NONE, POSITIVE, RULE_ONLY, UNKNOWN, EMPTY }
    record Fact(Origin origin, Polarity polarity, String text) { }

    private static final Pattern SELF = Pattern.compile("我方|我司|本企业|本公司|本单位|我单位|本投标人");
    private static final Pattern RULE_FRAME = Pattern.compile("评审标准|评分标准|评分规则|评审规则|招标文件要求|采购文件要求");
    private static final Pattern NEGATIVE_FACT = Pattern.compile("负偏离|负偏差|部分满足|部分响应|达不到|低于|不足|(?:未能|无法|不能|未|不)[^，,。；;]{0,20}(?:满足|符合|达到|履约)");
    private static final Pattern ACKNOWLEDGEMENT = Pattern.compile("(?:无偏离|无负偏离|不存在负偏离|完全响应|全面响应|全部响应|符合要求|满足要求|满足招标要求|符合招标文件要求|遵照执行|严格遵从|响应|满足|符合|无|/|-)(?:[，,。；;].*)?");

    private DeviationEvidencePolicy() { }

    static int ambiguousOwnFactIndex(final String text) {
        var sentences = Pattern.compile("[^。；;\\n]+").matcher(text);
        while (sentences.find()) {
            String sentence = sentences.group();
            if (!SELF.matcher(sentence).find()) continue;
            for (String part : sentence.split("[，,]")) {
                String local = compact(part);
                if (hasUnnegatedNegative(local)) return sentences.start();
            }
        }
        return -1;
    }

    static Fact assess(final Origin origin, final String raw) {
        String text = raw == null ? "" : raw;
        String compact = compact(text);
        if (compact.isBlank()) return new Fact(origin, Polarity.EMPTY, text);
        String[] parts = compact.split("[，,；;。]");
        boolean inheritedSelf = false;
        boolean uncertain = false;
        for (String part : parts) {
            if (SELF.matcher(part).find()) inheritedSelf = true;
            if (hasUnnegatedNegative(part)) {
                if (origin == Origin.STATUS_COLUMN) return new Fact(origin, Polarity.NEGATIVE, text);
                if ((SELF.matcher(part).find() || inheritedSelf && part.contains("实际")) && !isRuleFrame(part)
                        && !part.matches("(?s).*(?:引用|遵照执行|投标人).*"))
                    return new Fact(origin, Polarity.NEGATIVE, text);
                uncertain = true;
            }
        }
        if (uncertain) return new Fact(origin, Polarity.UNKNOWN, text);
        if (isRuleFrame(compact)) return new Fact(Origin.RULE, Polarity.RULE_ONLY, text);
        if (origin != Origin.STATUS_COLUMN && SELF.matcher(compact).find() && hasUnnegatedNegative(compact))
            return new Fact(origin, Polarity.NEGATIVE, text);
        if (origin == Origin.STATUS_COLUMN && isExplicitNegative(compact)) return new Fact(origin, Polarity.NEGATIVE, text);
        if (containsUnresolvedNegative(compact)) return new Fact(origin, Polarity.UNKNOWN, text);
        if (compact.contains("正偏离") || compact.contains("优于招标要求")) return new Fact(origin, Polarity.POSITIVE, text);
        if (ACKNOWLEDGEMENT.matcher(compact).matches() || compact.startsWith("响应"))
            return new Fact(origin, Polarity.NONE, text);
        return new Fact(origin, Polarity.UNKNOWN, text);
    }

    static boolean isOwnLow(final String text, final int index) {
        String prefix = text.substring(Math.max(0, index - 160), index);
        int boundary = Math.max(Math.max(prefix.lastIndexOf('。'), prefix.lastIndexOf('，')),
                Math.max(prefix.lastIndexOf('；'), prefix.lastIndexOf(';')));
        String local = compact(prefix.substring(boundary + 1));
        if (isRuleFrame(local) || isNegatedOrConditional(local)) return false;
        if (local.matches("(?s).*(?:引用|遵照执行|投标人).*")) return false;
        if (local.matches("(?s).*(?:知悉|知晓|理解|确认评审|确认招标|承诺遵循).*")
                && !local.matches("(?s).*(?:我方|我司|本企业|本公司|本单位|我单位)实际.*")) return false;
        if (SELF.matcher(local).find()) return true;
        String preceding = compact(prefix.substring(0, boundary + 1));
        return SELF.matcher(preceding).find() && !isRuleFrame(preceding)
                && !preceding.matches("(?s).*(?:引用|遵照执行|投标人).*");
    }

    private static boolean isRuleFrame(final String text) {
        int frame = firstMatch(RULE_FRAME, text);
        if (frame < 0) return false;
        String before = text.substring(0, frame);
        return !SELF.matcher(before).find() || before.matches("(?s).*(?:知悉|知晓|理解|已理解|确认|承诺遵循).*");
    }

    private static boolean isNegatedOrConditional(final String text) {
        return text.matches("(?s).*(?:不得|不应|不应当|不可以|不可|不允许|不低于|无负偏离|不存在负偏离|未发生负偏离).*")
                || text.matches("(?s).*(?:并未|未对|未作|未曾|不存在|没有|无任何|绝不).{0,35}(?:负偏离|负偏差).*")
                || text.matches("(?s).*(?:如果|假如|若|如|一旦).{0,50}(?:低于|不满足|未达到).*");
    }

    private static boolean isExplicitNegative(final String text) {
        return hasUnnegatedNegative(text);
    }

    private static boolean containsUnresolvedNegative(final String text) {
        return hasUnnegatedNegative(text);
    }

    private static boolean hasUnnegatedNegative(final String text) {
        var matcher = NEGATIVE_FACT.matcher(text);
        while (matcher.find()) {
            String prefix = text.substring(Math.max(0, matcher.start() - 20), matcher.start());
            if (prefix.matches("(?s).*(?:不得|不应|不|无(?:任何|实质性)?|不存在(?:任何)?|没有|并非|绝不)$")) continue;
            if (matcher.group().startsWith("负偏")
                    && prefix.matches("(?s).*(?:并未对[^，,。；;]{0,12}作出|绝不具有(?:任何)?)$")) continue;
            if (prefix.matches("(?s).*(?:如果|假如|若|如|一旦).{0,15}")) continue;
            return true;
        }
        return false;
    }

    private static int firstMatch(final Pattern pattern, final String text) {
        var matcher = pattern.matcher(text);
        return matcher.find() ? matcher.start() : -1;
    }

    private static String compact(final String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).replaceAll("[\\s\\p{Z}\\p{Cf}]+", "");
    }
}
