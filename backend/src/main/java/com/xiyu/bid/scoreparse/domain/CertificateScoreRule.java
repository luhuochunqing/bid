package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Closed, whole-clause grammar: any unconsumed substantive text requires human review. */
public record CertificateScoreRule(List<Requirement> requirements, Map<Integer, BigDecimal> scores, boolean supported) {
    public record Requirement(String identity, String level) {}
    private static final String POINTS = "([0-9]+(?:\\.[0-9]+)?)";
    private static final Map<String, String> STANDARDS = Map.of(
            "ISO9001", "(?:ISO9001|GB/T19001|GBT19001)",
            "ISO14001", "(?:ISO14001|GB/T24001|GBT24001)",
            "ISO45001", "(?:ISO45001|GB/T45001|GBT45001)");
    private static final Map<String, String> DESCRIPTIONS = Map.of(
            "ISO9001", "质量(?:管理(?:体系)?|体系)", "ISO14001", "环境(?:管理(?:体系)?|体系)?",
            "ISO45001", "职业健康(?:安全)?管理(?:体系)?");
    private static final Map<String, String> HONORS = Map.of(
            "品牌信誉", "(?:中国)?品牌信誉评价", "质量信用", "企业质量信用等级评价",
            "售后服务", "商品售后(?:服务)?(?:评价)?", "标准化", "企业标准化管理体系");
    private static final String[] HONOR_ORDER = {"品牌信誉", "质量信用", "售后服务", "标准化"};
    private static final String CERT_SUFFIX = "(?:认证(?:证书)?|证书)";
    private static final String CERT_END = CERT_SUFFIX + "?";
    private static final String AWARD_SUBJECT = "(?:投标人)?(?:具有|具备|持有)";
    private static final Pattern INDEPENDENT_AWARD = Pattern.compile(
            AWARD_SUBJECT + "(?:在?有效期内的)?(.+?)的?得" + POINTS + "分");
    private static final Pattern DECLARED_MAXIMUM = Pattern.compile("(?:满分|合计|共)" + POINTS + "分");
    private static final String SEPARATOR = "[、,及和]";

    public static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value.replace("\\n", ""), Normalizer.Form.NFKC)
                .toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    /** Named identities for rough estimates; Chinese names also constrain relevance. */
    public static List<String> identitiesForEstimate(String dim, String detail) {
        String clause = scoringClause(detail, false);
        String text = normalize(dim) + ";" + (clause == null ? "" : clause);
        Set<String> identities = new java.util.LinkedHashSet<>();
        STANDARDS.keySet().stream().sorted().filter(id -> identifies(text, id)).forEach(identities::add);
        for (String id : HONOR_ORDER) if (identifies(text, id)) identities.add(id);
        var standard = Pattern.compile("ISO[0-9]{4,5}(?![0-9])|CMMI[1-5]?").matcher(text);
        while (standard.find()) identities.add(standard.group());
        var named = Pattern.compile("[\\p{IsHan}]{2,30}?(?:许可证|证书)").matcher(text);
        while (named.find()) {
            String before = text.substring(0, named.start());
            if (STANDARDS.values().stream().anyMatch(alias -> before.matches("(?s).*" + alias + "$"))
                    || before.matches("(?s).*(?:ISO[0-9]{4,5}|CMMI[1-5]?)$")) continue;
            String label = named.group().replaceFirst("^(?:(?:投标人|供应商|应当|应|须|需|具有|具备|持有|提供|取得|获得|在有效期内的|有效期内的|有效的|有效|相关|相应|以及|和|及))+", "");
            if (!Set.of("证书", "认证证书", "资质证书").contains(label)
                    && !label.matches("[一二两三四五六七八九十]+(?:项|份)?(?:认证)?证书")) {
                String canonical = java.util.stream.Stream.concat(STANDARDS.keySet().stream(), java.util.Arrays.stream(HONOR_ORDER))
                        .filter(id -> identifies(label, id)).findFirst().orElse(label);
                identities.add(canonical);
            }
        }
        return List.copyOf(identities);
    }

    /** Closed additive rule for separate management-system certificate awards in stage 1. */
    static Map<String, BigDecimal> independentAwards(String dim, String detail, BigDecimal weight) {
        String clause = scoringClause(detail, false);
        if (clause == null) return Map.of();
        var header = Pattern.compile("^(?:商务|技术)?" + Pattern.quote(normalize(dim))
                + POINTS + "(?=" + AWARD_SUBJECT + ")").matcher(clause);
        if (header.find() && new BigDecimal(header.group(1)).compareTo(weight) == 0) {
            clause = clause.substring(header.end());
        }
        var awards = new LinkedHashMap<String, BigDecimal>();
        BigDecimal total = BigDecimal.ZERO;
        String[] sentences = clause.replaceFirst("[;。]$", "").split("[;。]", -1);
        for (int i = 0; i < sentences.length; i++) {
            var maximum = DECLARED_MAXIMUM.matcher(sentences[i]);
            if (i == sentences.length - 1 && maximum.matches()) {
                if (new BigDecimal(maximum.group(1)).compareTo(weight) != 0) return Map.of();
                break;
            }
            var award = INDEPENDENT_AWARD.matcher(sentences[i]);
            if (!award.matches()) return Map.of();
            String identity = estimateSystemIdentity(award.group(1), false);
            BigDecimal points = new BigDecimal(award.group(2));
            if (identity == null || points.signum() <= 0 || awards.putIfAbsent(identity, points) != null) return Map.of();
            total = total.add(points);
        }
        return awards.size() >= 2 && total.compareTo(weight) == 0 ? awards : Map.of();
    }

    static String ledgerSystemIdentity(String name) {
        return estimateSystemIdentity(name, true);
    }

    private static String estimateSystemIdentity(String name, boolean allowBareTrailingStandard) {
        String text = normalize(name);
        for (String identity : List.of("ISO9001", "ISO14001", "ISO45001", "ISO27001")) {
            String alias = STANDARDS.getOrDefault(identity, "(?:ISO(?:/IEC)?27001)");
            String description = DESCRIPTIONS.getOrDefault(identity, "信息安全管理体系");
            String label = "(?:" + description + ")" + CERT_SUFFIX;
            String labelWithStandard = "ISO27001".equals(identity) ? label + "|信息安全认证(?:证书)?" : label;
            String standard = alias + "(?::[0-9]{4})?";
            if (text.matches("(?:" + standard + "(?:" + labelWithStandard + "|" + CERT_END + ")|"
                    + label + "(?:\\(" + standard + "\\))?)")) return identity;
            if (allowBareTrailingStandard && text.matches("(?:" + labelWithStandard + ")" + standard)) {
                return identity;
            }
        }
        return null;
    }

    static Requirement estimateRequirement(String identity, String detail) {
        String text = normalize(detail);
        String base = honorBase(identity);
        if (base == null) return new Requirement(identity, "");
        var match = Pattern.compile(base + "[^;。|]{0,15}?(五星|5星|3A|AAA)").matcher(text);
        if (!match.find()) return new Requirement(identity, "");
        return new Requirement(identity, match.group(1).matches("五星|5星") ? "五星" : "3A");
    }

    static boolean matchesEstimateIdentity(String name, String identity) {
        return identifies(name, identity)
                || Pattern.compile(Pattern.quote(identity) + "(?![0-9])").matcher(normalize(name)).find();
    }

    /** Recall is separate from verifying that a record really is a corporate certificate. */
    public static boolean identifies(String text, String identity) {
        String pattern = STANDARDS.getOrDefault(identity, HONORS.get(identity));
        return pattern != null && Pattern.compile(pattern + "(?![0-9])").matcher(normalize(text)).find();
    }

    /** Routing owns dimension identity; closed parsing below only decides automatic judgement. */
    public static boolean isCertificateScore(String dim, String detail) {
        String title = normalize(dim), text = normalize(detail)
                .replaceFirst("^[0-9]+\\|[^|]*\\|", "").replaceFirst("^1、评审标准:", "").replaceFirst("^[1-3]\\)", "");
        if (title.matches(".*(方案|人员|团队|培训|实施计划).*")) return false;
        if (title.contains("企业荣誉") || KnowledgeCategoryPolicy.CATEGORY_CERT.equals(
                new KnowledgeCategoryPolicy().categorize(dim, detail))) return true;
        return text.matches("^(?:具有|具备|持有|获得|取得).*(?:认证|证书|ISO[0-9]|GB/T[0-9]).*");
    }

    public static CertificateScoreRule parse(String dim, String detail, BigDecimal weight) {
        String text = scoringClause(detail, true);
        if (text == null || weight == null || weight.signum() < 0 || !isCertificateScore(dim, detail)) return unsupported();
        String certificate = isoCertificatePattern();
        var single = Pattern.compile("(?:具有|具备|持有|获得|取得)?(" + certificate + ")"
                + "(?:[,。;]?的?得" + POINTS + "分)?[。;]?").matcher(text);
        if (single.matches()) {
            if (single.group(2) != null && new BigDecimal(single.group(2)).compareTo(weight) != 0) return unsupported();
            return new CertificateScoreRule(List.of(new Requirement(standardIdentity(single.group(1)), "")), Map.of(1, weight), true);
        }
        String triplePattern = "(?:具有|具备|持有|获得|取得)?(?:(" + certificate + ")" + SEPARATOR + "(" + certificate + ")"
                + SEPARATOR + ")?(" + certificate + ")的?得" + POINTS + "分[。;]?"
                + "三项齐全得满分[,、]?缺少一项[,、]?不得分[。;]?"
                + "(?:不满足或未提供证明文件者[,、]?不得分[。;]?)?";
        var triple = Pattern.compile(triplePattern).matcher(text);
        if (triple.matches()) {
            Set<String> identities = new HashSet<>();
            for (int n = 1; n <= 3; n++) {
                String cert = triple.group(n);
                if (cert != null) {
                    identities.add(standardIdentity(cert));
                }
            }
            if (identities.size() == 1) {
                identities.addAll(STANDARDS.keySet());
            }
            if (identities.size() != 3 || new BigDecimal(triple.group(4)).compareTo(weight) != 0) return unsupported();
            return new CertificateScoreRule(identities.stream().sorted().map(id -> new Requirement(id, "")).toList(), Map.of(3, weight), true);
        }
        StringBuilder honorPattern = new StringBuilder("具有");
        for (String identity : HONOR_ORDER) {
            String level = identity.equals("品牌信誉") || identity.equals("售后服务") ? "(?:五星|5星)(?:级)?" : "(?:3A|AAA)(?:级)?";
            honorPattern.append(HONORS.get(identity)).append("(?:认证)?").append(level).append(CERT_END).append(';');
        }
        honorPattern.append("以上4项,");
        for (int n = 4; n >= 1; n--) {
            honorPattern.append("具备任意").append(n).append("项(?:则|的)?得").append(POINTS).append("分,");
        }
        honorPattern.append("不满足任意1项或未提供证明文件者,?不得分[。;]?");
        var honor = Pattern.compile(honorPattern.toString()).matcher(text);
        if (!honor.matches()) return unsupported();
        Map<Integer, BigDecimal> scores = new TreeMap<>();
        for (int n = 4; n >= 1; n--) scores.put(n, new BigDecimal(honor.group(5 - n)));
        for (int n = 1; n <= 4; n++) {
            if (scores.get(n).signum() < 0 || scores.get(n).compareTo(weight) > 0
                    || n > 1 && scores.get(n).compareTo(scores.get(n - 1)) < 0) return unsupported();
        }
        List<Requirement> requirements = new ArrayList<>();
        for (String identity : HONOR_ORDER) requirements.add(new Requirement(identity,
                identity.equals("品牌信誉") || identity.equals("售后服务") ? "五星" : "3A"));
        return new CertificateScoreRule(List.copyOf(requirements), Map.copyOf(scores), true);
    }

    /** Only known wrappers and a closed proof-material sentence may be removed. */
    private static String scoringClause(String detail, boolean rejectUnrecognizedProofMaterials) {
        String text = normalize(detail);
        if (text.contains("|")) {
            String[] cells = text.split("\\|", -1);
            if (cells.length == 5 && cells[0].matches("[0-9]+")
                    && cells[3].matches("[0-9]+(?:\\.[0-9]+)?") && cells[4].matches("是|否")) {
                text = cells[2];
            } else if (cells.length >= 3 && cells[cells.length - 2].matches("[0-9]+(?:\\.[0-9]+)?")
                    && cells[cells.length - 1].matches("是|否")) {
                int start = (cells.length > 3 && cells[0].matches("[0-9]+")) ? 1 : 0;
                text = String.join("|", java.util.Arrays.copyOfRange(cells, start, cells.length - 2));
            } else {
                return null;
            }
        }
        text = text.replaceFirst("^1、评审标准:", "");
        var proofHeading = Pattern.compile("(?:2[、.．]?)?证明(?:文件|材料):").matcher(text);
        if (proofHeading.find()) {
            String materials = text.substring(proofHeading.end());
            if (rejectUnrecognizedProofMaterials && !materials.matches(
                    "投标人须提供(?:在有效期内的)?认证证书(?:复印件或扫描件|复印件|扫描件)(?:加盖投标人公章)?(?:[,。;]?原件备查)?[。;]?")) return null;
            text = text.substring(0, proofHeading.start());
        }
        return text.replaceAll("(^|[。;]|分)[1-3]\\)", "$1");
    }

    static String isoCertificatePattern() {
        return "(?:" + STANDARDS.keySet().stream().sorted().map(id -> STANDARDS.get(id)
                + "(?:" + DESCRIPTIONS.get(id) + ")?" + CERT_END)
                .reduce((left, right) -> left + "|" + right).orElse("") + ")";
    }

    private static String standardIdentity(String name) {
        return STANDARDS.keySet().stream().filter(id -> identifies(name, id)).findFirst().orElse("");
    }

    static String honorBase(String identity) { return HONORS.get(identity); }
    private static CertificateScoreRule unsupported() { return new CertificateScoreRule(List.of(), Map.of(), false); }
}
