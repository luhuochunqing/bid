// Input: 保证金退款正文页
// Output: 是否具有唯一可识别的企业户名、银行名称及账号文字（不验证账户所有权）
// Pos: compliance/domain/policy/ - 退款字段完整性正向判定
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RefundAccountEvidencePolicy {
    private static final String HOLDER = "户名|账户名称|收款单位|开户名称";
    private static final String BANK = "开户银行|开户行";
    private static final String ACCOUNT = "银行账号|银行账户|账户号码|账号";
    private static final Pattern FIELD = Pattern.compile("(?m)^[ \\t]*(" + HOLDER + "|" + BANK + "|" + ACCOUNT + ")[：:][ \\t]*([^\\r\\n]*)$");
    // 名称主体必须位于括号外；括号只承载附加信息，不能代替主体。
    private static final String NAME_STEM = "[\\p{IsHan}A-Za-z0-9·]{2,80}";
    private static final Pattern ORGANIZATION = Pattern.compile(NAME_STEM
            + "(?:[（(][\\p{IsHan}A-Za-z0-9·]{1,30}[）)][\\p{IsHan}A-Za-z0-9·]{0,20})?(?:公司|合作社|合伙企业)");
    private static final Pattern BANK_NAME = Pattern.compile(NAME_STEM + "(?:银行|信用社|农商行)"
            + "(?:股份有限公司)?(?:[（(][\\p{IsHan}A-Za-z0-9·]{1,30}[）)])?"
            + "(?:[\\p{IsHan}A-Za-z0-9·]{0,40}(?:分行|支行|营业部))?");
    private static final Pattern FIELD_DESCRIPTION = Pattern.compile("(?:单位|公司|企业|银行|开户行|账户)(?:名称|全称)");
    private static final Pattern UNRESOLVED = Pattern.compile("[【】\\[\\]{}_＿某]|[Xx×]{2,}|^(?:此处|请|待|未|未知|不详)");

    private RefundAccountEvidencePolicy() { }

    public static boolean hasCompleteFacts(final String text) {
        String holder = uniqueValue(text, HOLDER);
        String bank = uniqueValue(text, BANK);
        String account = uniqueValue(text, ACCOUNT).replace("-", "");
        return ORGANIZATION.matcher(holder).matches() && BANK_NAME.matcher(bank).matches()
                && account.matches("[0-9]{9,32}") && !account.matches("([0-9])\\1{8,31}");
    }

    private static String uniqueValue(final String text, final String labels) {
        Matcher field = FIELD.matcher(text == null ? "" : text);
        List<String> values = new ArrayList<>();
        while (field.find()) {
            if (!field.group(1).matches(labels)) continue;
            String value = field.group(2).replaceAll("\\s+", "");
            if (value.isEmpty() || UNRESOLVED.matcher(value).find() || FIELD_DESCRIPTION.matcher(value).find()) return "";
            values.add(value);
        }
        return values.stream().distinct().count() == 1 ? values.getFirst() : "";
    }
}
