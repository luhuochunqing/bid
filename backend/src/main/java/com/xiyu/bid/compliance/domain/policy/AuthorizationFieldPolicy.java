// Input: 单个授权正文页的文字字段
// Output: 人员、权限、期限的FILLED/PLACEHOLDER/UNKNOWN状态（不验证身份与签章）
// Pos: compliance/domain/policy/ - 授权文字要素的保守正向文法
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuthorizationFieldPolicy {
    public enum State { FILLED, PLACEHOLDER, UNKNOWN }
    public record Assessment(State principal, State agent, State scope, State term) {
        public boolean filled() {
            return principal == State.FILLED && agent == State.FILLED
                    && scope == State.FILLED && term == State.FILLED;
        }
        public boolean hasPlaceholder() {
            return List.of(principal, agent, scope, term).contains(State.PLACEHOLDER);
        }
    }
    private static final String PRINCIPAL = "法定代表人|委托人";
    private static final String AGENT = "委托代理人|受托人|被授权人|授权代表";
    private static final String SCOPE = "授权范围|授权事项|代理权限|委托事项|委托权限";
    private static final String TERM = "授权期限|委托期限";
    private static final Pattern FIELD = Pattern.compile("(" + PRINCIPAL + "|" + AGENT + "|" + SCOPE + "|" + TERM + "|姓名|性别|年龄|职务|身份证号码|身份证号"
            + ")(?:[（(](?:单位负责人|签字|盖章|姓名)[）)])?[ \\t]*[：:]");
    private static final Pattern PLACEHOLDER = Pattern.compile("[【】\\[\\]{}_＿某]|(?:此处|请|待|未).*(?:填|输入|补充|确定)|姓名");
    private static final String ACTION = "(?:签署|签订|递交|提交|澄清确认|澄清|撤回|修改)";
    private static final String OBJECT = "(?:(?:本项目|本次|该项目)?(?:投标文件|标书|合同))";
    private static final String ACTION_CLAUSE = ACTION + "(?:[、，,及和与并]" + ACTION + ")*" + OBJECT;
    private static final Pattern ACTION_SCOPE = Pattern.compile("(?:" + ACTION_CLAUSE
            + ")(?:[、，,及和与并](?:" + ACTION_CLAUSE + "))*|处理本项目投标事宜");
    private static final Pattern VALIDITY_TERM = Pattern.compile(
            "(?:自(?:本授权(?:委托)?书)?(?:签字盖章|签字|签署)(?:之日|日)?起?(?:生效)?(?:至|到))"
                    + "(?:本项目)?投标有效期(?:结束|届满)(?:之日)?止?"
                    + "|(?:覆盖|至|到)(?:本项目)?投标有效期(?:结束|届满)?");
    // 仅覆盖明确的人名候选文法；未覆盖姓氏/写法为UNKNOWN，不宣称真实身份已核验。
    private static final String FAMILIES = "赵钱孙李周吴郑王冯陈卫蒋沈韩杨朱秦许何吕张孔曹严华金魏姜谢邹柏苏潘葛范彭韦马袁柳鲍史唐薛雷贺倪汤殷罗毕郝安常于傅齐康伍余顾孟黄尹姚邵汪毛戴宋庞熊舒屈董梁杜阮贾江童颜郭梅林钟徐邱高夏蔡田胡霍万卢莫宗丁邓石崔龚程裴陆翁甄段侯刘景詹龙叶黎乔白蒲赖卓蒙商谭申冉桑牛尚温庄柴阎连艾向古易廖耿文寇欧曾关聂游鞠丰沙";
    private static final Pattern PERSON_CANDIDATE = Pattern.compile(
            "(?:[" + FAMILIES + "][\\p{IsHan}]{1,2}|(?:欧阳|司马|上官|诸葛|司徒|夏侯|东方|皇甫|尉迟|公孙|慕容)[\\p{IsHan}]{1,2})");

    private static final String DATE = "([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})";
    private static final Pattern DATE_INTERVAL = Pattern.compile("自?" + DATE + "(?:至|到|—|~)" + DATE + "止?");

    private AuthorizationFieldPolicy() { }

    public static Assessment assessAuthorizationFields(final String text) {
        String source = text == null ? "" : text;
        return new Assessment(classify(values(source, PRINCIPAL), AuthorizationFieldPolicy::isName),
                classify(values(source, AGENT), AuthorizationFieldPolicy::isName),
                classify(scopeValues(source), AuthorizationFieldPolicy::isScope),
                classify(termValues(source), AuthorizationFieldPolicy::isTerm));
    }

    public static boolean hasStructuredFields(final String text) {
        Matcher fields = FIELD.matcher(text == null ? "" : text);
        while (fields.find()) {
            if (fields.group(1).matches(PRINCIPAL + "|" + AGENT + "|" + SCOPE + "|" + TERM)) return true;
        }
        return false;
    }

    public static boolean legalRepresentativeProofFilled(final String text) {
        return text != null && text.contains("法定代表人")
                && classify(values(text, "姓名"), AuthorizationFieldPolicy::isName) == State.FILLED
                && Pattern.compile("[1-9][0-9]{16}[0-9Xx]").matcher(text).find();
    }

    private static List<String> values(final String text, final String labels) {
        List<String> result = new ArrayList<>();
        Matcher field = FIELD.matcher(text);
        while (field.find()) {
            if (!field.group(1).matches(labels)) continue;
            int end = text.length();
            for (char separator : new char[] {'\n', '\r', '。', '；', ';'}) {
                int position = text.indexOf(separator, field.end());
                if (position >= 0) end = Math.min(end, position);
            }
            Matcher next = FIELD.matcher(text);
            if (next.find(field.end())) end = Math.min(end, next.start());
            result.add(trim(text.substring(field.end(), end)));
        }
        return result;
    }

    private static List<String> scopeValues(final String text) {
        List<String> fields = values(text, SCOPE);
        if (!fields.isEmpty()) return fields;
        Matcher declaration = Pattern.compile("(?:代理人|受托人)全权代表(?:我方|本公司)([^。；\\r\\n]+?)(?=授权期限|委托期限|[。；\\r\\n]|$)").matcher(text);
        List<String> result = new ArrayList<>();
        while (declaration.find()) result.add(trim(declaration.group(1)));
        return result;
    }

    private static List<String> termValues(final String text) {
        List<String> fields = values(text, TERM);
        if (!fields.isEmpty()) return fields;
        Matcher prose = Pattern.compile("(?:" + TERM + ")((?:覆盖|至|到|自)[^。；\\r\\n]+)").matcher(text);
        List<String> result = new ArrayList<>();
        while (prose.find()) result.add(trim(prose.group(1)));
        return result;
    }

    private static State classify(final List<String> values, final Predicate<String> recognizer) {
        if (values.isEmpty()) return State.UNKNOWN;
        if (values.stream().anyMatch(v -> v.isBlank() || PLACEHOLDER.matcher(v).find())) return State.PLACEHOLDER;
        if (values.stream().map(AuthorizationFieldPolicy::trim).distinct().count() != 1) return State.UNKNOWN;
        return values.stream().allMatch(recognizer) ? State.FILLED : State.UNKNOWN;
    }

    private static boolean isName(final String value) {
        return PERSON_CANDIDATE.matcher(trim(value)).matches();
    }

    private static boolean isScope(final String value) {
        return ACTION_SCOPE.matcher(trim(value).replaceAll("\\s+", "")).matches();
    }

    private static boolean isTerm(final String value) {
        String normalized = trim(value).replaceAll("\\s+", "");
        if (VALIDITY_TERM.matcher(normalized).matches()) return true;
        normalized = normalized.replace('年', '-').replace('月', '-').replace("日", "");
        Matcher dates = DATE_INTERVAL.matcher(normalized);
        if (!dates.matches()) return false;
        int[] parts = new int[6];
        for (int index = 0; index < parts.length; index++) parts[index] = Integer.parseInt(dates.group(index + 1));
        if (!validDate(parts[0], parts[1], parts[2]) || !validDate(parts[3], parts[4], parts[5])) return false;
        return parts[0] * 10000 + parts[1] * 100 + parts[2] <= parts[3] * 10000 + parts[4] * 100 + parts[5];
    }

    private static boolean validDate(final int year, final int month, final int day) {
        return year >= 1900 && year <= 2200 && month >= 1 && month <= 12 && day >= 1
                && YearMonth.of(year, month).isValidDay(day);
    }

    private static String trim(final String value) {
        return value.trim().replaceAll("[，,。；;]+$", "").trim();
    }
}
