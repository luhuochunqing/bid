// Input: source/target 字符串
// Output: boolean 智能匹配结果
// Pos: biddraftagent/domain/validation — 共享智能匹配工具（纯核心）

package com.xiyu.bid.biddraftagent.domain.validation;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 智能匹配工具函数集合。
 * 供 QualificationMatcher、PersonnelCertMatcher、BrandAuthMatcher、PerformanceMatcher 共用。
 */
public final class SmartMatchUtils {

    private static final Map<String, Pattern> SHORT_NAME_PATTERN_CACHE = new ConcurrentHashMap<>();

    private SmartMatchUtils() {}

    /**
     * 智能匹配：
     * - 长名称（length > 5 或含非 ASCII）：不区分大小写子串匹配
     * - 短纯 ASCII 名称：词边界正则匹配防止误匹配
     */
    public static boolean isSmartMatch(String source, String target) {
        if (source == null || target == null || target.length() < 2) return false;
        if (target.length() > 5 || !target.matches("^[a-zA-Z]+$")) {
            return source.toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT));
        }
        Pattern pattern = SHORT_NAME_PATTERN_CACHE.computeIfAbsent(
                target,
                t -> Pattern.compile("\\b" + Pattern.quote(t) + "\\b", Pattern.CASE_INSENSITIVE));
        return pattern.matcher(source).find();
    }
}
