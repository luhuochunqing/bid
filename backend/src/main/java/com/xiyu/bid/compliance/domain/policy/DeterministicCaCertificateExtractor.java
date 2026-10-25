// Input: 招标文件分页内容 (DocumentContent)
// Output: 电子标 CA 要求、唯一具体平台及招标原文页码证据
// Pos: compliance/domain/policy/ - 确定性电子标 CA 证书提取器（纯核心 FP-Java）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityEvidence;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招标文件电子标 CA 数字证书 / 电子签章确定性双轨提取器 (FP-Java 纯核心).
 */
public final class DeterministicCaCertificateExtractor {

    private static final String CA_OR_SEAL = "(?:电子\\s*)?(?:CA(?:\\s*(?:数字证书|证书|锁))?|数字证书|电子签章|电子印章)";
    private static final Pattern EXPLICIT_EXEMPT_CA = Pattern.compile(
            "(?:无需|不要求|不使用|免于使用)\\s*(?:缴纳[^。；，\\r\\n]{0,20}?与\\s*)?" + CA_OR_SEAL
                    + "(?:\\s*(?:与|及|或|和)\\s*" + CA_OR_SEAL + ")*", Pattern.CASE_INSENSITIVE);

    private static final String[] PAPER_SIGNALS = {
            "纸质标", "线下投标", "现场递交", "纸质投标文件递交"
    };

    private static final String[] CA_REQUIRED_KEYWORDS = {
            "电子招投标", "电子投标", "CA证书", "CA数字证书", "电子签章", "电子印章", "数字证书认证", "数字证书", "数字认证",
            "加密投标文件", "全流程电子化", "网上开标", "远程开标", "远程解密", "在线解密",
            "CA锁", "CA加密", "CA注册", "办理CA", "使用CA", "企业CA", "电子标"
    };

    private DeterministicCaCertificateExtractor() {
    }

    // REUSE-OK: * — 电子标 CA 证书确定性提取入口，业务域独立
    public static CaCertificateRequirement extract(final DocumentContent tenderDoc) {
        if (tenderDoc == null || tenderDoc.pages().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (DocumentContent.PageContent page : tenderDoc.pages()) {
            if (page.text() != null) {
                sb.append(page.text()).append('\n');
            }
        }
        CaCertificateRequirement requirement = extract(sb.toString());
        if (requirement == null || !requirement.required()) return requirement;
        List<QualityEvidence> evidence = new ArrayList<>();
        for (DocumentContent.PageContent page : tenderDoc.pages()) {
            for (String line : page.text().split("[\\n\\r。；]")) {
                List<String> names = CaPlatformNamePolicy.candidates(EXPLICIT_EXEMPT_CA.matcher(line).replaceAll(""));
                if (names.isEmpty() || (requirement.platformName() != null && names.stream().noneMatch(
                        name -> CaPlatformNamePolicy.identity(name).equals(CaPlatformNamePolicy.identity(requirement.platformName()))))) continue;
                evidence.add(QualityEvidence.tender("招标文件交易平台原文", page.pageNumber(), line.trim()));
            }
        }
        return new CaCertificateRequirement(requirement.required(), requirement.platformName(),
                requirement.requireCertificateCopy(), requirement.requireElectronicSeal(),
                requirement.description(), evidence);
    }

    public static CaCertificateRequirement extract(final String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return null;
        }

        Matcher exemption = EXPLICIT_EXEMPT_CA.matcher(fullText);
        String explicitExemption = exemption.find() ? exemption.group() : null;
        // 只移除明确否定的 CA/签章短语；其它位置真实电子投标/CA要求仍参与强证据判断。
        String positiveText = EXPLICIT_EXEMPT_CA.matcher(fullText).replaceAll("");

        // 1. 汇总电子标/CA强证据，避免负向模板词先命中后短路
        boolean hasCaKeyword = false;
        for (String kw : CA_REQUIRED_KEYWORDS) {
            if (positiveText.contains(kw)) {
                hasCaKeyword = true;
                break;
            }
        }
        if (!hasCaKeyword && CaPlatformNamePolicy.hasRegionalCa(positiveText)) {
            hasCaKeyword = true;
        }

        if (!hasCaKeyword && explicitExemption != null) {
            String mode = fullText.contains("纸质标") || fullText.contains("纸质投标") ? "纸质标且" : "";
            return CaCertificateRequirement.notRequired("招标文件明确为" + mode + "未要求 CA 电子签章/数字证书（" + explicitExemption + "）");
        }

        if (!hasCaKeyword) {
            for (String paperSignal : PAPER_SIGNALS) {
                if (fullText.contains(paperSignal)) {
                    return CaCertificateRequirement.notRequired("招标文件明确为纸质或线下投标（" + paperSignal + "）");
                }
            }
        }

        if (!hasCaKeyword) {
            // 未检索到任何电子标或 CA 关键词，返回 null 供大模型或后续判定
            return null;
        }

        // 2. 提取平台名称或省份 CA 锁名称
        String platform = null;
        List<String> candidates = CaPlatformNamePolicy.candidates(positiveText);
        if (candidates.size() == 1) {
            platform = candidates.getFirst();
        }

        boolean requireCopy = positiveText.contains("证书复印件")
                || positiveText.contains("有效性证明")
                || positiveText.contains("证书截图")
                || positiveText.contains("证书扫描件")
                || positiveText.contains("CA证明");

        boolean requireSeal = positiveText.contains("电子签章")
                || positiveText.contains("电子印章")
                || positiveText.contains("数字证书签名")
                || positiveText.contains("CA签名");

        String desc = platform != null
                ? "本项目要求使用 " + platform + " 实行全流程电子招投标，须提供有效 CA 证书及电子签章"
                : "本项目要求使用 CA 数字证书及电子签章响应电子招投标";

        return new CaCertificateRequirement(true, platform, requireCopy, requireSeal, desc);
    }
}
