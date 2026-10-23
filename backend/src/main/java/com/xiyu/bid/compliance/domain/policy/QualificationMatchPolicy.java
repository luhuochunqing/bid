// Input: 招标资质要求列表/企业资质库/投标文件内容/参考日期
// Output: QualificationDetail 结构化比对明细列表（纯核心，FP-Java 纯函数）与第一阶段确定性核验
// Pos: compliance/domain/policy/ - 资质匹配纯核心算法与比对清单生成
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;
import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualificationDetail;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import com.xiyu.bid.entity.Qualification;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * 资质匹配纯核心算法（spec 049 / FP-Java）.
 */
public final class QualificationMatchPolicy {
    public static final int EXPIRY_WARN_DAYS = 60;
    private static final Pattern NORMALIZE_RE = Pattern.compile("[\\s\\u3000\\p{P}+/\\-_:：]+");
    private static final Pattern STANDARD_CODE_RE = Pattern.compile("(?:iso|gb|iec|cmmi|itss|ccrc|cissp|cisp)[a-z0-9]*");
    private static final Pattern ISO_STANDARD_RE = Pattern.compile("(?i)ISO\\s*[-：:]?\\s*(9001|14001|45001)");
    private static final Pattern GOV_PREFIX_RE = Pattern.compile("^(?:中华人民共和国|全国|中国|国家)+");
    private static final Pattern UNDERLINE_BLANK_PATTERN = Pattern.compile("_{3,}");
    private static final Pattern BRACKET_PLACEHOLDER_PATTERN = Pattern.compile("【(?:请|此处)?[^】]*(?:填写|上传|粘贴|提供|插入|替换)[^】]*】");
    private static final List<String> STOP_WORDS = List.of(
            "中华人民共和国", "全国", "中国", "国家", "经营许可证", "许可证", "业务经营许可证", "生产许可证",
            "资质证书", "资格证书", "资格证明", "资质证明", "认证证书", "备案证明", "登记证书",
            "管理体系认证", "服务体系认证", "服务认证", "系统认证", "投标人须具备", "供应商须具备",
            "具有有效", "有效期内", "相关资质", "国家认证", "原件复印件", "独立法人", "民事责任能力", "合法注册",
            "施工总承包", "专业承包", "总承包", "施工承包", "承包资质", "承包");
    private static final List<String> DOMAIN_TERMS = List.of(
            "质量管理体系", "环境管理体系", "职业健康安全", "信息安全管理", "信息技术服务", "信息系统建设",
            "建筑工程", "机电安装", "机电工程", "电力工程", "市政公用", "消防设施", "建筑装修装饰", "防水防腐", "钢结构",
            "安全生产许可", "营业执照", "一般纳税人", "高新技术企业", "专精特新", "重合同守信用",
            "一级建造师", "二级建造师", "造价工程师", "监理工程师", "特种作业", "增值电信", "电信业务",
            "互联网信息服务", "在线数据处理与交易", "信息安全等级保护", "等保", "等保三级");
    private static final List<String> QUAL_SECTION_KEYWORDS = List.of(
            "资质证书", "资格证书", "企业资质", "体系认证", "许可证", "资质证明", "资格证明");
    private QualificationMatchPolicy() {
    }

    public static boolean isQualificationMatch(final String req, final String certName) {
        return calculateMatchScore(req, certName) > 0;
    }

    public static boolean checkLevelCompatibility(final String req, final String cert) {
        return QualificationGradePolicy.isGradeCompatible(req, cert);
    }

    static int calculateMatchScore(final String req, final String certName) {
        if (!QualificationGradePolicy.isGradeCompatible(req, certName)) {
            return 0;
        }
        return calculateBaseMatchScore(req, certName);
    }

    static int calculateBaseMatchScore(final String req, final String certName) {
        if (req == null || req.isBlank() || certName == null || certName.isBlank()) return 0;
        String normReq = normalize(req), normCert = normalize(certName);
        if (normReq.isEmpty() || normCert.isEmpty()) return 0;
        String strippedReq = stripGovPrefix(normReq), strippedCert = stripGovPrefix(normCert);

        if (normReq.contains(normCert) || normCert.contains(normReq)
                || (!strippedCert.isBlank() && normReq.contains(strippedCert))
                || (!strippedReq.isBlank() && normCert.contains(strippedReq))
                || (!strippedReq.isBlank() && !strippedCert.isBlank()
                        && (strippedReq.contains(strippedCert) || strippedCert.contains(strippedReq)))) {
            return 1000 + (strippedCert.isBlank() ? normCert.length() : strippedCert.length()) * 10;
        }
        for (String rc : extractStandardCodes(normReq)) {
            if (extractStandardCodes(normCert).contains(rc)) return 500 + rc.length() * 10;
        }
        for (String term : DOMAIN_TERMS) {
            String normTerm = normalize(term);
            if (normReq.contains(normTerm) && normCert.contains(normTerm)) return 200 + normTerm.length() * 10;
        }
        String lcs = longestCommonSubstring(normReq, normCert);
        if (lcs.length() >= 4 && !isStopword(lcs) && !hasConflictingDomainTerms(normReq, normCert)) {
            return 100 + lcs.length();
        }
        return 0;
    }

    public static List<QualificationDetail> buildDetails(
            final List<String> requirements, final List<Qualification> allCertificates,
            final DocumentContent bidDoc, final LocalDate today) {
        if (requirements == null || requirements.isEmpty()) return Collections.emptyList();
        List<Qualification> certs = allCertificates == null ? Collections.emptyList() : allCertificates;
        LocalDate refDate = today == null ? LocalDate.now() : today;
        List<QualificationDetail> details = new ArrayList<>();
        for (String req : expandCombinedIsoRequirements(requirements)) {
            if (req == null || req.isBlank() || !DeterministicQualificationExtractor.isSpecificCertificate(req.trim())) {
                continue;
            }
            details.add(buildSingleDetail(req.trim(), certs, bidDoc, refDate));
        }
        return Collections.unmodifiableList(details);
    }

    public static List<String> expandCombinedIsoRequirements(final List<String> requirements) {
        if (requirements == null || requirements.isEmpty()) return List.of();
        Set<String> expanded = new LinkedHashSet<>();
        for (String requirement : requirements) {
            if (requirement == null || requirement.isBlank()) continue;
            Matcher matcher = ISO_STANDARD_RE.matcher(requirement);
            List<String> isoCodes = new ArrayList<>();
            while (matcher.find()) isoCodes.add("ISO" + matcher.group(1));
            if (isoCodes.size() > 1) expanded.addAll(isoCodes);
            else expanded.add(requirement);
        }
        return List.copyOf(expanded);
    }

    private static QualificationDetail buildSingleDetail(
            final String req, final List<Qualification> certs, final DocumentContent bidDoc, final LocalDate today) {
        Qualification bestCert = findBestMatchingCert(req, certs);
        if (bestCert != null) {
            String certName = bestCert.getName() != null ? bestCert.getName() : "";
            Integer remainingDays = computeRemainingDays(bestCert.getExpiryDate(), today);
            String status = "MATCHED", remark;
            if (bestCert.getExpiryDate() != null && bestCert.getExpiryDate().isBefore(today)) {
                status = "EXPIRED";
                remark = "证书已过期（" + bestCert.getExpiryDate() + "），存在废标风险";
            } else if (remainingDays != null && remainingDays <= EXPIRY_WARN_DAYS) {
                status = "ATTENTION";
                remark = "证书即将到期（剩余 " + remainingDays + " 天，至 " + bestCert.getExpiryDate() + "），建议复核";
            } else {
                QualificationGradePolicy.GradeAssessment ga =
                        QualificationGradePolicy.assessGrade(req, certName, bestCert.getLevel());
                String gradeNote = (ga.compatible() && !ga.remark().isBlank()) ? "；" + ga.remark() : "";
                remark = "已匹配企业资质库有效证书「" + certName + "」"
                        + (bestCert.getExpiryDate() != null ? "（有效期至 " + bestCert.getExpiryDate() + "）" : "") + gradeNote;
            }
            Optional<EvidenceHit> hit = locateInBid(bidDoc, List.of(certName, stripGovPrefix(normalize(certName)), normalize(certName), req));
            if (hit.isEmpty()) remark += "；但在投标文件中未检索到明确证明页码";
            return new QualificationDetail(req, certName, status, hit.map(EvidenceHit::pageNumber).orElse(null),
                    hit.map(EvidenceHit::quote).orElse(""), remainingDays, remark);
        }
        Qualification underGraded = findUnderGradedCert(req, certs);
        if (underGraded != null) {
            String certName = underGraded.getName() != null ? underGraded.getName() : "";
            QualificationGradePolicy.GradeAssessment ga =
                    QualificationGradePolicy.assessGrade(req, certName, underGraded.getLevel());
            String note = !ga.remark().isBlank() ? ga.remark() : "资质等级不足";
            Optional<EvidenceHit> hit = locateInBid(bidDoc, List.of(certName, req));
            return new QualificationDetail(req, certName, "UNMATCHED", hit.map(EvidenceHit::pageNumber).orElse(null),
                    hit.map(EvidenceHit::quote).orElse(""), null,
                    "企业资质库中证书「" + certName + "」" + note + "，存在实质性偏离或废标风险");
        }
        return locateInBid(bidDoc, QualificationDetailReconciler.buildBidSearchCues(req)).map(h -> new QualificationDetail(
                req, "", "ATTENTION", h.pageNumber(), h.quote(), null,
                "资质库未维护该证书，但在标书第 " + h.pageNumber() + " 页检索到证明材料，请人工核查有效性"))
                .orElseGet(() -> new QualificationDetail(
                        req, "", "UNMATCHED", null, "", null,
                        "企业资质库及投标文件中均未检索到对应资质证明材料，存在偏离或废标风险"));
    }

    // REUSE-OK: * — 资质匹配专职资质章节占位反假核验，业务域独立
    public static Optional<QualityCheckItemResult> checkFirstStage(
            final DocumentContent bidDoc, final TenderRequirements requirements) {
        if (bidDoc == null || bidDoc.pages() == null || bidDoc.pages().isEmpty()) return Optional.empty();
        for (DocumentContent.PageContent page : bidDoc.pages()) {
            String text = page.text();
            if (text == null || BidLetterCheckPolicy.isCatalogOrOutlinePage(text) || !hasAnyKeyword(text, QUAL_SECTION_KEYWORDS)) {
                continue;
            }
            String placeholder = findPlaceholder(text);
            if (placeholder != null) {
                QualityEvidence evidence = EvidenceAssembler.locateBid(bidDoc, "资质证书证明材料占位留白", List.of(placeholder));
                return Optional.of(QualityCheckItemResult.fail(
                        BidQualityCheckItem.QUALIFICATION_MATCH,
                        "投标文件资质证书证明章节存在未填写的占位模板或空白留白（" + placeholder + "）",
                        "请在标书中完整上传对应资质证书扫描件并核验有效信息", List.of(evidence)));
            }
        }
        return Optional.empty();
    }

    private static Qualification findBestMatchingCert(final String req, final List<Qualification> certs) {
        Qualification best = null;
        int maxScore = 0;
        LocalDate latestExpiry = null;
        for (Qualification q : certs) {
            if (q == null || q.getName() == null) continue;
            int score = calculateMatchScore(req, q.getName());
            if (score <= 0) continue;
            LocalDate expiry = q.getExpiryDate() == null ? LocalDate.MAX : q.getExpiryDate();
            if (best == null || score > maxScore || (score == maxScore && expiry.isAfter(latestExpiry))) {
                best = q;
                maxScore = score;
                latestExpiry = expiry;
            }
        }
        return best;
    }

    private static Qualification findUnderGradedCert(final String req, final List<Qualification> certs) {
        if (certs == null) return null;
        for (Qualification q : certs) {
            if (q == null || q.getName() == null) continue;
            if (calculateBaseMatchScore(req, q.getName()) > 0
                    && !QualificationGradePolicy.isGradeCompatible(req, q.getName())) {
                return q;
            }
        }
        return null;
    }

    private static Optional<EvidenceHit> locateInBid(final DocumentContent bidDoc, final List<String> cues) {
        if (bidDoc == null || bidDoc.pages() == null || bidDoc.pages().isEmpty() || cues == null) return Optional.empty();
        List<String> cleanCues = cues.stream().filter(c -> c != null && !c.isBlank()).distinct().toList();
        if (cleanCues.isEmpty()) return Optional.empty();
        List<DocumentContent.PageContent> nonCatalog = bidDoc.pages().stream()
                .filter(p -> p.text() != null && !BidLetterCheckPolicy.isCatalogOrOutlinePage(p.text())).toList();
        List<DocumentContent.PageContent> target = nonCatalog.isEmpty() ? bidDoc.pages() : nonCatalog;
        return EvidenceLocator.locate(target, cleanCues);
    }

    private static Integer computeRemainingDays(final LocalDate expiryDate, final LocalDate today) {
        return expiryDate == null ? null : (int) ChronoUnit.DAYS.between(today, expiryDate);
    }

    private static String normalize(final String text) {
        return text == null ? "" : NORMALIZE_RE.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static String stripGovPrefix(final String normText) {
        return normText == null || normText.isBlank() ? "" : GOV_PREFIX_RE.matcher(normText).replaceFirst("");
    }

    private static List<String> extractStandardCodes(final String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        List<String> codes = new ArrayList<>();
        Matcher m = STANDARD_CODE_RE.matcher(text);
        while (m.find()) codes.add(m.group());
        return codes;
    }

    private static boolean isStopword(final String text) {
        if (text == null || text.isBlank()) return true;
        for (String term : DOMAIN_TERMS) {
            if (text.contains(normalize(term))) return false;
        }
        for (String sw : STOP_WORDS) {
            if (sw.equals(text) || sw.contains(text)) return true;
        }
        return false;
    }

    private static String longestCommonSubstring(final String s1, final String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) return "";
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

    private static boolean hasAnyKeyword(final String text, final List<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private static boolean hasConflictingDomainTerms(final String normReq, final String normCert) {
        String reqDomain = null, certDomain = null;
        for (String term : DOMAIN_TERMS) {
            String normTerm = normalize(term);
            if (reqDomain == null && normReq.contains(normTerm)) reqDomain = normTerm;
            if (certDomain == null && normCert.contains(normTerm)) certDomain = normTerm;
        }
        return reqDomain != null && certDomain != null && !reqDomain.equals(certDomain);
    }

    private static String findPlaceholder(final String text) {
        Matcher m1 = UNDERLINE_BLANK_PATTERN.matcher(text);
        if (m1.find()) return m1.group();
        Matcher m2 = BRACKET_PLACEHOLDER_PATTERN.matcher(text);
        return m2.find() ? m2.group() : null;
    }
}
