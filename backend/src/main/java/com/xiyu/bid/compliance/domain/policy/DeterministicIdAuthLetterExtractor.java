package com.xiyu.bid.compliance.domain.policy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 招标文件法定代表人及代理人身份证明/身份证要求的纯函数确定性提取器 (FP-Java).
 * 零框架依赖，毫秒级从招标文件文本中提取正反面、社保证明、公章与原件要求硬事实.
 */
public final class DeterministicIdAuthLetterExtractor {

    private static final Pattern FRONT_AND_BACK_PATTERN = Pattern.compile(
            "(?:身份证(?:正反面|双面|正\\s*反\\s*面|正面及反面|正背面|正、反面|人像面和国徽面)|双面复印件|正反双面)");

    private static final Pattern SOCIAL_SECURITY_PATTERN = Pattern.compile(
            "(?:近[一二三四五六七八九十\\d]+个?月.*?(?:社保|社会保险)|(?:社保|社会保险)(?:缴纳)?证明|社保缴纳凭证|参保证明)");

    private DeterministicIdAuthLetterExtractor() {
    }

    public static IdAuthLetterRequirement extract(final String tenderText) {
        if (tenderText == null || tenderText.isBlank()) {
            return IdAuthLetterRequirement.standard();
        }

        boolean mentionsIdOrAuth = tenderText.contains("身份证")
                || tenderText.contains("身份证明")
                || tenderText.contains("社保");

        if (!mentionsIdOrAuth) {
            return IdAuthLetterRequirement.empty();
        }

        boolean requireFrontAndBack = FRONT_AND_BACK_PATTERN.matcher(tenderText).find();
        boolean requireSocialSecurity = SOCIAL_SECURITY_PATTERN.matcher(tenderText).find();

        String description = null;
        String requireOriginalOrCopy = null;
        for (String line : tenderText.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.contains("身份证") || trimmed.contains("身份证明") || trimmed.contains("代理人") || trimmed.contains("社保")) {
                boolean isSpecificIdCard = trimmed.contains("身份证") && !trimmed.contains("身份证明");
                if (isSpecificIdCard) {
                    description = trimmed;
                } else if (description == null) {
                    description = trimmed;
                }
                if (trimmed.contains("公章")) {
                    requireOriginalOrCopy = "复印件加盖公章";
                } else if (trimmed.contains("原件") && requireOriginalOrCopy == null) {
                    requireOriginalOrCopy = "原件";
                } else if (trimmed.contains("复印件") && requireOriginalOrCopy == null) {
                    requireOriginalOrCopy = "复印件";
                } else if (trimmed.contains("扫描件") && requireOriginalOrCopy == null) {
                    requireOriginalOrCopy = "扫描件";
                }
            }
        }
        if (description != null && description.length() > 200) {
            description = description.substring(0, 200) + "...";
        }

        return new IdAuthLetterRequirement(
                true,
                requireFrontAndBack,
                requireSocialSecurity,
                requireOriginalOrCopy,
                description
        );
    }
}
