// Input: 招标文件分页内容 (DocumentContent)
// Output: 电子标 CA 证书结构化要求契约 (CaCertificateRequirement)
// Pos: compliance/domain/policy/ - 确定性电子标 CA 证书提取器（纯核心 FP-Java）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招标文件电子标 CA 数字证书 / 电子签章确定性双轨提取器 (FP-Java 纯核心).
 */
public final class DeterministicCaCertificateExtractor {

    private static final Pattern PLATFORM_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9]{2,20}(?:电子招投标交易平台|公共资源交易中心|公共资源交易平台|电子商务平台|电子招标采购系统|交易平台|交易系统|公共服务平台|采购网|政采云|招采进宝|优质采))"
    );

    private static final Pattern CA_SPECIFIC_LOCK_PATTERN = Pattern.compile(
            "(?:使用|办理|持有|注册|通过)?(?:企业|单位)?([\\u4e00-\\u9fa5]{2,8}(?:省|市)?\\s*CA(?:锁|数字证书|数字认证)?)"
    );

    private static final Pattern REGIONAL_CA_SIGNAL = Pattern.compile(
            "(?i)[\\u4e00-\\u9fa5]{2,6}\\s*CA"
    );

    private static final String[] EXPLICIT_EXEMPT_SIGNALS = {
            "无需CA", "不要求CA", "无需数字证书", "无需电子签章", "不使用电子签章"
    };

    private static final String[] PAPER_SIGNALS = {
            "纸质标", "线下投标", "现场递交", "纸质投标文件递交"
    };

    private static final String[] CA_REQUIRED_KEYWORDS = {
            "电子招投标", "电子投标", "CA证书", "CA数字证书", "电子签章", "电子印章", "数字证书认证", "数字证书", "数字认证",
            "加密投标文件", "全流程电子化", "CA锁", "CA加密", "CA注册", "办理CA", "使用CA", "企业CA", "电子标"
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
        return extract(sb.toString());
    }

    public static CaCertificateRequirement extract(final String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return null;
        }

        String explicitExemption = null;
        for (String exempt : EXPLICIT_EXEMPT_SIGNALS) {
            if (fullText.contains(exempt)) {
                explicitExemption = exempt;
                break;
            }
        }

        // 1. 汇总电子标/CA强证据，避免负向模板词先命中后短路
        boolean hasCaKeyword = false;
        for (String kw : CA_REQUIRED_KEYWORDS) {
            if (fullText.contains(kw)) {
                hasCaKeyword = true;
                break;
            }
        }
        if (!hasCaKeyword && explicitExemption == null && REGIONAL_CA_SIGNAL.matcher(fullText).find()) {
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
        Matcher mPlatform = PLATFORM_PATTERN.matcher(fullText);
        if (mPlatform.find()) {
            platform = mPlatform.group(1).trim();
        } else {
            Matcher mCaLock = CA_SPECIFIC_LOCK_PATTERN.matcher(fullText);
            if (mCaLock.find()) {
                platform = mCaLock.group(1).replaceAll("\\s+", "");
            }
        }

        boolean requireCopy = fullText.contains("证书复印件")
                || fullText.contains("有效性证明")
                || fullText.contains("证书截图")
                || fullText.contains("证书扫描件")
                || fullText.contains("CA证明");

        boolean requireSeal = fullText.contains("电子签章")
                || fullText.contains("电子印章")
                || fullText.contains("数字证书签名")
                || fullText.contains("CA签名");

        String desc = platform != null
                ? "本项目要求使用 " + platform + " 实行全流程电子招投标，须提供有效 CA 证书及电子签章"
                : "本项目要求使用 CA 数字证书及电子签章响应电子招投标";

        return new CaCertificateRequirement(true, platform, requireCopy, requireSeal, desc);
    }
}
