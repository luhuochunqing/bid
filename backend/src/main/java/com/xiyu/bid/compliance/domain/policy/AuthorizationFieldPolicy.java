// Input: 单个授权正文页的文字字段及授权签署图像实证
// Output: 授权字段FILLED/PLACEHOLDER/UNKNOWN及逐页签署实证；签署要求不作事实，明确缺陷优先拦截。
// Pos: compliance/domain/policy/ - 授权文字要素与签署图像实证判定策略 (FP-Java)
// 一旦我被更新，务必更新我的开头注释。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuthorizationFieldPolicy {
    public enum State {
        FILLED, PLACEHOLDER, UNKNOWN
    }

    public record Assessment(State principal, State agent, State scope, State term) {
        public boolean filled() {
            return principal == State.FILLED && agent == State.FILLED && scope == State.FILLED && term == State.FILLED;
        }

        public boolean hasPlaceholder() {
            return List.of(principal, agent, scope, term).contains(State.PLACEHOLDER);
        }

    }
    private static final String PRINCIPAL = "法定代表人|委托人";
    private static final String AGENT = "委托代理人|受托人|被授权人|授权代表";
    private static final String SCOPE = "授权范围|授权事项|代理权限|委托事项|委托权限";
    private static final String TERM = "授权期限|委托期限";
    private static final String ROLE_SUFFIX = "(?:[（(](?:单位负责人|签字|盖章|姓名)[）)])?";
    private static final Pattern FIELD =
            Pattern.compile("(" + PRINCIPAL + "|" + AGENT + "|" + SCOPE + "|" + TERM + "|姓名|性别|年龄|职务|身份证号码|身份证号)"
                            + ROLE_SUFFIX + "[ \\t]*[：:]");
    private static final Pattern FIELD_BOUNDARY =
            Pattern.compile("[\\s,，]+(?:投标人|单位|公司|日期|落款)[：:]");
    private static final Pattern PLACEHOLDER =
            Pattern.compile("[【】\\[\\]{}_＿某]|(?:此处|请|待|未).*(?:填|输入|补充|确定)|姓名");
    private static final String ACTION = "(?:签署|签订|递交|提交|澄清确认|澄清|撤回|修改)";
    private static final String OBJECT = "(?:(?:本项目|本次|该项目)?(?:投标文件|标书|合同))";
    private static final String ACTION_CLAUSE = ACTION + "(?:[、，,及和与并]" + ACTION + ")*" + OBJECT;
    // 代表公司参加投标类写法也属明确授权范围（如「全权代表本公司参加投标」或「参与投标」）。
    private static final String REPRESENTATION =
            "(?:全权)?(?:代表(?:我方|本公司|公司|本单位)?)?(?:参加|参与|出席|办理|处理)?(?:本项目|本次|该项目)?(?:投标|招投标|开标)(?:活动|事宜)?";
    private static final Pattern ACTION_SCOPE =
            Pattern.compile("(?:" + ACTION_CLAUSE + ")(?:[、，,及和与并](?:" + ACTION_CLAUSE + "))*|处理本项目投标事宜|"
                            + REPRESENTATION);
    private static final Pattern VALIDITY_TERM =
            Pattern.compile(
                    "(?:自(?:本授权(?:委托)?书)?(?:签字盖章|签字|签署)(?:之日|日)?起?(?:生效)?(?:至|"
                + "到))(?:本项目)?投标有效期(?:结束|届满)(?:之日)?止?|(?:覆盖|至|到)(?:本项目)?投标有效期(?:结束|"
                + "届满)?|(?:长期|永久)(?:有效)?");
    // 仅覆盖明确的人名候选文法；未覆盖姓氏/写法为UNKNOWN，不宣称真实身份已核验。
    private static final String FAMILIES =
            "赵钱孙李周吴郑王冯陈卫蒋沈韩杨朱秦许何吕张孔曹严华金魏姜谢邹柏苏潘葛范彭韦马袁柳鲍史唐薛雷贺倪汤殷罗毕郝安常于傅齐康伍余"
                + "顾孟黄尹姚邵汪毛戴宋庞熊舒屈董梁杜阮贾江童颜郭梅林钟徐邱高夏蔡田胡霍万卢莫宗丁邓石崔龚程裴陆翁甄段侯刘景詹龙叶黎乔白蒲赖"
                + "卓蒙商谭申冉桑牛尚温庄柴阎连艾向古易廖耿文寇欧曾关聂游鞠丰沙";
    private static final String PERSON_BODY =
            "(?:[" + FAMILIES + "][\\p{IsHan}]{1,2}|(?:欧阳|司马|上官|诸葛|司徒|夏侯|东方|皇甫|尉迟|公孙|慕容)[\\p{IsHan}]{1,2})";
    private static final Pattern PERSON_CANDIDATE = Pattern.compile(PERSON_BODY);
    // 无冒号、跨行书写（如「委托代理人\n王五」）也要能识别出已填写当事人。
    private static final Pattern PARTY_DECLARATION =
            Pattern.compile("(" + PRINCIPAL + "|" + AGENT + ")" + ROLE_SUFFIX + "\\s*[:：]?\\s*" + PERSON_BODY);
    private static final Pattern ID_CARD =
            Pattern.compile("[1-9][0-9]{5}(?:[0-9]{8}|[*＊]{8})[0-9]{3}[0-9Xx]");
    private static final String DATE = "([0-9]{4})-([0-9]{1,2})-([0-9]{1,2})";
    private static final Pattern DATE_INTERVAL =
            Pattern.compile("自?" + DATE + "(?:至|到|—|~)" + DATE + "止?");
    private AuthorizationFieldPolicy() {}
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
            if (fields.group(1).matches(PRINCIPAL + "|" + AGENT + "|" + SCOPE + "|" + TERM))
                return true;
        }

        return false;
    }

    /** 该页是否声明了可用于冲突比对的授权事实：结构化字段、无冒号的当事人声明， 或任一要素已非 UNKNOWN。用于避免「第二份授权正文」因写法差异被静默忽略。 */
    public static boolean declaresAuthorizationFacts(final String text) {
        String source = text == null ? "" : text;
        Assessment fields = assessAuthorizationFields(source);
        return hasStructuredFields(source) || PARTY_DECLARATION.matcher(source).find()
                || List.of(fields.principal(), fields.agent(), fields.scope(), fields.term()).stream()
                        .anyMatch(state -> state != State.UNKNOWN);
    }

    public static boolean legalRepresentativeProofFilled(final String text) {
        if (text == null || !text.contains("法定代表人")) return false;
        boolean nameFilled =
                classify(values(text, "姓名"), AuthorizationFieldPolicy::isName) == State.FILLED
                        || classify(values(text, PRINCIPAL), AuthorizationFieldPolicy::isName)
                                == State.FILLED;
        return nameFilled && ID_CARD.matcher(text).find();
    }

    private static List<String> values(final String text, final String labels) {
        List<String> result = new ArrayList<>();
        Matcher field = FIELD.matcher(text);
        char[] separators =
                (labels.contains(PRINCIPAL) || labels.contains(AGENT))
                        ? new char[] {'\n', '\r', '。', '；', ';', '，', ','}
                        : new char[] {'\n', '\r', '。', '；', ';'};
        while (field.find()) {
            if (!field.group(1).matches(labels)) continue;
            int end = text.length();
            for (char separator : separators) {
                int position = text.indexOf(separator, field.end());
                if (position >= 0) end = Math.min(end, position);
            }

            Matcher boundary = FIELD_BOUNDARY.matcher(text);
            if (boundary.find(field.end())) end = Math.min(end, boundary.start());
            Matcher next = FIELD.matcher(text);
            if (next.find(field.end())) end = Math.min(end, next.start());
            result.add(trim(text.substring(field.end(), end)));
        }

        return result;
    }

    private static List<String> scopeValues(final String text) {
        List<String> fields = values(text, SCOPE);
        if (!fields.isEmpty()) return fields;
        Matcher declaration =
                Pattern.compile("(?:代理人|受托人)全权代表(?:我方|本公司)([^。；\\r\\n]+?)(?=授权期限|委托期限|[。；\\r\\n"
                                    + "]|$)").matcher(text);
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
        if (values.stream().anyMatch(v -> v.isBlank() || PLACEHOLDER.matcher(v).find()))
            return State.PLACEHOLDER;
        if (values.stream().map(AuthorizationFieldPolicy::trim).distinct().count() != 1)
            return State.UNKNOWN;
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
        for (int index = 0; index < parts.length; index++)
            parts[index] = Integer.parseInt(dates.group(index + 1));
        if (!validDate(parts[0], parts[1], parts[2]) || !validDate(parts[3], parts[4], parts[5]))
            return false;
        return parts[0] * 10000 + parts[1] * 100 + parts[2]
                <= parts[3] * 10000 + parts[4] * 100 + parts[5];
    }

    private static boolean validDate(final int year, final int month, final int day) {
        return year >= 1900 && year <= 2200 && month >= 1 && month <= 12 && day >= 1
                && YearMonth.of(year, month).isValidDay(day);
    }

    private static final String SIGN_ROLES =
            "(?:法定代表人|委托代理人|代理人|授权人|被授权人|受托人|单位负责人|负责人|签字人|签署人|投标人|单位|公司)";
    private static final String SIGN_WORDS = "(?:签字|签名|签署|盖章|公章|印章|印鉴|印文)";
    private static final String PARTIAL_SIGNATURE = "(?:仅|只)?部分(?:完成|签字|签名|签署|盖章)";
    private static final String SIGN_PLACEHOLDERS =
            "(?:无|没有|未提供|空白|未见|不可见|缺失|缺少|待补|空缺|未签|未盖|待签|待盖|待定|待签署|未签署|待确认|待填|未填|不清晰|模糊|无法辨认|无法识别|" + PARTIAL_SIGNATURE
                    + "|尚待确认|不完整|未完成|暂未完成|尚未完成|不齐全|未齐全|(?:尚未|暂未|未|没有|待|不)(?:填写|填|加盖|签署|"
                + "盖章|确认|完成|完整|齐全|全)|_{2,}|＿{2,}|\\[\\s*\\]|【\\s*】)";
    private static final String SIGN_FIELD =
            SIGN_ROLES + "?[（(]?" + SIGN_WORDS + "[）)]?(?:栏|处|位|框|位置)?";
    private static final String SIGN_FIELD_END =
            "(?=$|[。；;，,\\r\\n|｜]|" + FIELD.pattern() + "|(?:投标人|单位|公司|日期|落款)[：:])";
    private static final Pattern UNSIGNED =
            Pattern.compile("(?:未|没有|无|缺少|缺失|尚未|暂未|空缺|待|疑似|是否|不|无法|未能)[^。；\\n]{0,8}" + SIGN_WORDS + "|" + SIGN_WORDS
                            + "[^。；\\n"
                    + "]{0,8}(?:不可见|未见|未附|缺失|缺少|空缺|未签|未盖|未加盖|未签署|不清晰|模糊|无法辨认|无法识别|无法看清|看不清|存疑|待核|待查|待确认|不明|无法确认|尚未确认|"
                            + PARTIAL_SIGNATURE + "|尚待确认|不完整|未完成|暂未完成|尚未完成|不齐全|未齐全)|" + SIGN_FIELD + "[：:\\s]*"
                            + SIGN_PLACEHOLDERS + "|" + SIGN_FIELD
                            + "[：:\\h]*[^。；\\n]{0,6}(?:暂未|尚未|未|没有|待|不|存疑|不明|无法|未能)|" + SIGN_FIELD + "[\\h]*[：:][\\h]*"
                            + SIGN_FIELD_END + "|(?:^|[。；;\\r\\n|｜])\\h*" + SIGN_FIELD + "\\h*" + SIGN_FIELD_END
                            + "|(?:此处|待)[^。；\\n]{0,6}" + SIGN_WORDS + "|" + SIGN_FIELD
                            + "[^。；\\n]{0,6}(?:空缺|缺少|缺失|未见|不可见|待补|未签|未盖|待签|待盖|待签署|未签署|待确认|" + PARTIAL_SIGNATURE
                            + "|尚待确认|不完整|未完成|暂未完成|尚未完成)|(?:真实性|真伪|签字|签名|签署|盖章|公章|印章)[^。；\\n"
                            + "]{0,10}(?:待核|待查|待确认|存疑|不明|无法确认|尚未确认|未经核实|未予核实)|(?:疑似|是否)[^。；\\n]{0,8}"
                            + SIGN_ROLES);
    private static final Pattern BLANK_UNDERSCORE =
            Pattern.compile("_{2,}|＿{2,}|\\[(?:此处|请|待|空缺|缺少|未)[^\\]]*\\]|【(?:此处|请|待|空缺|缺少|未)[^】]*】");
    // 只接受完整的肯定观察分句，不能从疑问、否定或待核实描述中截取“可见”。
    private static final String OBSERVED_MARK = "(?:手写签(?:名|字)?|公章|印章|印鉴|印文)";
    private static final Pattern OBSERVATION_TRACE =
            Pattern.compile("(?:^|[。；;\\r\\n】])\\h*(?:图像|页面图像|扫描图像)?(?:(?:清晰可见|可见|盖有|附有|加盖有)(?:鲜红|红色|清晰)?"
                            + OBSERVED_MARK + "|" + OBSERVED_MARK + "(?:清晰)?可见)\\h*(?=$|[。；;\\r\\n])");
    // 同行多个签署字段须整体符合具名文法；空白不能作为跳过未知前缀的入口。
    private static final String NAMED_SIGNATURE =
            SIGN_ROLES + "[（(]?(?:签字|签名|签署)[）)]?[：:\\h]*+(?!" + SIGN_ROLES + "(?:$|[\\h：:（(]))" + PERSON_BODY;
    private static final Pattern SIGNED_TEXT =
            Pattern.compile("(?:^|[。；;\\r\\n】])\\h*(?:" + NAMED_SIGNATURE + ")(?:\\h+(?:" + NAMED_SIGNATURE
                            + "))*(?:\\h+投标人[：:]?[（(]盖章[）)])?\\h*(?=$|[。；;\\r\\n])|(?<=^|[。；;\\r\\n"
                            + "】])\\h*(?:已签字|已签名|已盖章|已加盖公章)\\h*(?=$|[。；;\\r\\n])");
    public static boolean hasSignedAuthorizationEvidence(final DocumentContent bidDoc) {
        if (bidDoc == null || bidDoc.pages() == null || bidDoc.images().isEmpty()) return false;
        var grants =
                bidDoc.pages().stream().filter(p ->
                                        p.text() != null && !BidLetterCheckPolicy.isCatalogOrOutlinePage(p.text()))
                        .filter(p ->
                                        BasicInfoCheckPolicy.containsAnyKeyword(p.text(), AuthorizationLetterCheckPolicy
                                                        .AUTH_LETTER_KEYWORDS))
                        .filter(p -> declaresAuthorizationFacts(p.text())).toList();
        return !grants.isEmpty() && grants.stream().allMatch(p ->
                                        hasImageOnPage(bidDoc, p.pageNumber()) && p.text().contains(ScanPageTextPolicy
                                                                        .TRANSCRIPTION_MARKER)
                                                && !UNSIGNED.matcher(p.text()).find()
                                                && !BLANK_UNDERSCORE.matcher(p.text()).find()
                                                && hasValidAuthorizationContent(p.text().substring(p.text().indexOf(
                                                                                        ScanPageTextPolicy
                    .TRANSCRIPTION_MARKER))));
    }

    public static boolean hasValidAuthorizationContent(final String transcription) {
        if (transcription == null || UNSIGNED.matcher(transcription).find()
                || BLANK_UNDERSCORE.matcher(transcription).find()) return false;
        return OBSERVATION_TRACE.matcher(transcription).find() || (SIGNED_TEXT.matcher(transcription).find()
                        && assessAuthorizationFields(transcription).filled());
    }

    public static boolean hasImageOnPage(final DocumentContent bidDoc, final int pageNumber) {
        return bidDoc != null && bidDoc.images().stream().anyMatch(img ->
                                        Integer.valueOf(pageNumber).equals(img.pageHint()) && img.base64() != null
                                                && !img.base64().isBlank());
    }

    private static String trim(final String value) {
        return value.trim().replaceAll("[，,。；;]+$", "").trim();
    }
}
