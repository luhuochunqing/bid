// Input: 招标资质要求列表/企业资质库/投标文件内容/参考日期
// Output: QualificationDetail 结构化比对明细列表（纯核心，FP-Java 纯函数）
// Pos: compliance/domain/policy/ - 资质匹配纯核心算法与比对清单生成
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualificationDetail;
import com.xiyu.bid.entity.Qualification;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 资质匹配纯核心算法（spec 049 / FP-Java）.
 *
 * <p>提供大模型提取要求与企业资质库证书之间的双向智能模糊匹配，
 * 并结合投标文件真实页码定位，生成结构化比对清单。
 */
public final class QualificationMatchPolicy {

    /** 证书临期预警阈值（天）. */
    public static final int EXPIRY_WARN_DAYS = 60;

    /** 归一化正则：剔除所有空白字符与标点符号. */
    private static final Pattern NORMALIZE_RE = Pattern.compile("[\\s\\u3000\\p{P}+/\\-_:：]+");

    /** 标准代号正则（如 iso9001, cmmi3, itss 等）. */
    private static final Pattern STANDARD_CODE_RE = Pattern.compile(
            "(?:iso|gb|iec|cmmi|itss|ccrc|cissp|cisp)[a-z0-9]*");

    /** 常见通用停用词（排除在 LCS 有效重叠外，严禁仅凭通用证件后缀或行政国字头触发错配）. */
    private static final List<String> STOP_WORDS = List.of(
            "中华人民共和国", "全国", "中国", "国家", "经营许可证", "许可证", "业务经营许可证", "生产许可证",
            "资质证书", "资格证书", "资格证明", "资质证明", "认证证书", "备案证明", "登记证书",
            "管理体系认证", "服务体系认证", "服务认证", "系统认证", "投标人须具备", "供应商须具备",
            "具有有效", "有效期内", "相关资质", "国家认证", "原件复印件", "独立法人", "民事责任能力", "合法注册");

    /** 常见行政/国家级前缀正则（用于归一化剥离，以便核心资质名穿透包含）. */
    private static final Pattern GOV_PREFIX_RE = Pattern.compile("^(?:中华人民共和国|全国|中国|国家)+");

    /** 行业核心专业资质关键词. */
    private static final List<String> DOMAIN_TERMS = List.of(
            "质量管理体系", "环境管理体系", "职业健康安全", "信息安全管理", "信息技术服务", "信息系统建设",
            "建筑工程", "机电安装", "机电工程", "电力工程", "市政公用", "消防设施", "建筑装修装饰", "防水防腐", "钢结构",
            "安全生产许可", "营业执照", "一般纳税人", "高新技术企业", "专精特新", "重合同守信用",
            "一级建造师", "二级建造师", "造价工程师", "监理工程师", "特种作业", "增值电信", "电信业务",
            "互联网信息服务", "在线数据处理与交易", "信息安全等级保护", "等保", "等保三级");

    private QualificationMatchPolicy() {
    }

    /**
     * 智能判定招标要求与资质证书是否匹配（双向模糊匹配）.
     */
    public static boolean isQualificationMatch(final String req, final String certName) {
        return calculateMatchScore(req, certName) > 0;
    }

    /**
     * 计算招标要求与资质证书的相关度打分（分数越高代表匹配度越精确）.
     */
    static int calculateMatchScore(final String req, final String certName) {
        if (req == null || req.isBlank() || certName == null || certName.isBlank()) {
            return 0;
        }
        String normReq = normalize(req);
        String normCert = normalize(certName);
        if (normReq.isEmpty() || normCert.isEmpty()) {
            return 0;
        }
        String strippedReq = stripGovPrefix(normReq);
        String strippedCert = stripGovPrefix(normCert);

        // 1. 双向直接包含（最高优先级：核心证书名穿透包含）
        if (normReq.contains(normCert) || normCert.contains(normReq)
                || (!strippedCert.isBlank() && normReq.contains(strippedCert))
                || (!strippedReq.isBlank() && normCert.contains(strippedReq))
                || (!strippedReq.isBlank() && !strippedCert.isBlank()
                        && (strippedReq.contains(strippedCert) || strippedCert.contains(strippedReq)))) {
            return 1000 + (strippedCert.isBlank() ? normCert.length() : strippedCert.length()) * 10;
        }

        // 2. 标准代号精确重合（如 ISO9001, ISO27001, CMMI3 等）
        List<String> reqCodes = extractStandardCodes(normReq);
        List<String> certCodes = extractStandardCodes(normCert);
        for (String rc : reqCodes) {
            if (certCodes.contains(rc)) {
                return 500 + rc.length() * 10;
            }
        }

        // 3. 行业专有领域词重合
        for (String term : DOMAIN_TERMS) {
            String normTerm = normalize(term);
            if (normReq.contains(normTerm) && normCert.contains(normTerm) && checkLevelCompatibility(normReq, normCert)) {
                return 200 + normTerm.length() * 10;
            }
        }

        // 4. 最长公共子串 (LCS) 判定：有效重叠 >= 4 字符且非通用停用词
        String lcs = longestCommonSubstring(normReq, normCert);
        if (lcs.length() >= 4 && !isStopword(lcs) && checkLevelCompatibility(normReq, normCert)) {
            return 100 + lcs.length();
        }

        return 0;
    }

    /**
     * 批量生成三方比对结构化明细列表（招标要求 vs 我司证书 vs 标书页码）.
     */
    public static List<QualificationDetail> buildDetails(
            final List<String> requirements,
            final List<Qualification> allCertificates,
            final DocumentContent bidDoc,
            final LocalDate today) {
        if (requirements == null || requirements.isEmpty()) {
            return Collections.emptyList();
        }
        List<Qualification> certs = allCertificates == null ? Collections.emptyList() : allCertificates;
        LocalDate refDate = today == null ? LocalDate.now() : today;
        List<QualificationDetail> details = new ArrayList<>();

        for (String req : requirements) {
            if (req == null || req.isBlank() || !DeterministicQualificationExtractor.isSpecificCertificate(req.trim())) {
                continue;
            }
            details.add(buildSingleDetail(req.trim(), certs, bidDoc, refDate));
        }
        return Collections.unmodifiableList(details);
    }

    private static QualificationDetail buildSingleDetail(
            final String req,
            final List<Qualification> certs,
            final DocumentContent bidDoc,
            final LocalDate today) {
        Qualification bestCert = findBestMatchingCert(req, certs);

        if (bestCert != null) {
            String certName = bestCert.getName() != null ? bestCert.getName() : "";
            Integer remainingDays = computeRemainingDays(bestCert.getExpiryDate(), today);
            String status;
            String remark;

            if (bestCert.getExpiryDate() != null && bestCert.getExpiryDate().isBefore(today)) {
                status = "EXPIRED";
                remark = "证书已过期（" + bestCert.getExpiryDate() + "），存在废标风险";
            } else if (remainingDays != null && remainingDays <= EXPIRY_WARN_DAYS) {
                status = "ATTENTION";
                remark = "证书即将到期（剩余 " + remainingDays + " 天，至 " + bestCert.getExpiryDate() + "），建议复核";
            } else {
                status = "MATCHED";
                remark = "已匹配企业资质库有效证书「" + certName + "」"
                        + (bestCert.getExpiryDate() != null ? "（有效期至 " + bestCert.getExpiryDate() + "）" : "");
            }

            // 检索标书页码（包含证书原名、剥离国字头名称、归一化名称及招标要求）
            List<String> cues = List.of(certName, stripGovPrefix(normalize(certName)), normalize(certName), req);
            Optional<EvidenceHit> hit = locateInBid(bidDoc, cues);
            Integer bidPage = hit.map(EvidenceHit::pageNumber).orElse(null);
            String bidQuote = hit.map(EvidenceHit::quote).orElse("");

            if (bidPage == null) {
                remark += "；但在投标文件中未检索到明确证明页码";
            }
            return new QualificationDetail(req, certName, status, bidPage, bidQuote, remainingDays, remark);
        }

        // 资质库未匹配：检查标书中是否包含证明文本
        List<String> cues = List.of(req, normalize(req));
        return locateInBid(bidDoc, cues).map(h -> new QualificationDetail(
                req, "", "ATTENTION", h.pageNumber(), h.quote(), null,
                "资质库未维护该证书，但在标书第 " + h.pageNumber() + " 页检索到证明材料，请人工核查有效性"))
                .orElseGet(() -> new QualificationDetail(
                        req, "", "UNMATCHED", null, "", null,
                        "企业资质库及投标文件中均未检索到对应资质证明材料，存在偏离或废标风险"));
    }

    private static Qualification findBestMatchingCert(final String req, final List<Qualification> certs) {
        Qualification best = null;
        int maxScore = 0;
        LocalDate latestExpiry = null;
        for (Qualification q : certs) {
            if (q == null || q.getName() == null) {
                continue;
            }
            int score = calculateMatchScore(req, q.getName());
            if (score <= 0) {
                continue;
            }
            LocalDate expiry = q.getExpiryDate() == null ? LocalDate.MAX : q.getExpiryDate();
            if (best == null || score > maxScore || (score == maxScore && expiry.isAfter(latestExpiry))) {
                best = q;
                maxScore = score;
                latestExpiry = expiry;
            }
        }
        return best;
    }

    private static Optional<EvidenceHit> locateInBid(final DocumentContent bidDoc, final List<String> cues) {
        if (bidDoc == null || bidDoc.pages() == null || bidDoc.pages().isEmpty() || cues == null) {
            return Optional.empty();
        }
        List<String> cleanCues = cues.stream().filter(c -> c != null && !c.isBlank()).distinct().toList();
        return cleanCues.isEmpty() ? Optional.empty() : EvidenceLocator.locate(bidDoc.pages(), cleanCues);
    }

    private static Integer computeRemainingDays(final LocalDate expiryDate, final LocalDate today) {
        if (expiryDate == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(today, expiryDate);
    }

    private static String normalize(final String text) {
        return text == null ? "" : NORMALIZE_RE.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static String stripGovPrefix(final String normText) {
        return normText == null || normText.isBlank() ? "" : GOV_PREFIX_RE.matcher(normText).replaceFirst("");
    }

    private static List<String> extractStandardCodes(final String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        List<String> codes = new ArrayList<>();
        Matcher m = STANDARD_CODE_RE.matcher(text);
        while (m.find()) {
            codes.add(m.group());
        }
        return codes;
    }

    private static boolean checkLevelCompatibility(final String req, final String cert) {
        String[] levels = {"特级", "一级", "二级", "三级", "甲级", "乙级", "丙级"};
        String reqLevel = null, certLevel = null;
        for (String lvl : levels) {
            if (reqLevel == null && req.contains(lvl)) {
                reqLevel = lvl;
            }
            if (certLevel == null && cert.contains(lvl)) {
                certLevel = lvl;
            }
        }
        return reqLevel == null || certLevel == null || reqLevel.equals(certLevel);
    }

    private static boolean isStopword(final String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        for (String term : DOMAIN_TERMS) {
            if (text.contains(normalize(term))) {
                return false;
            }
        }
        for (String sw : STOP_WORDS) {
            if (sw.equals(text) || sw.contains(text)) {
                return true;
            }
        }
        return false;
    }

    private static String longestCommonSubstring(final String s1, final String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) {
            return "";
        }
        int m = s1.length(), n = s2.length(), maxLen = 0, endIndex = 0;
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    if (dp[i][j] > maxLen) {
                        maxLen = dp[i][j];
                        endIndex = i;
                    }
                }
            }
        }
        return maxLen == 0 ? "" : s1.substring(endIndex - maxLen, endIndex);
    }
}
