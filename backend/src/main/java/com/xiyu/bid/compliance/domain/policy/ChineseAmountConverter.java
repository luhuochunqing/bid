package com.xiyu.bid.compliance.domain.policy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 中文金额大写转换与解析工具 (FP-Java).
 */
public final class ChineseAmountConverter {

    private static final String[] CN_NUMS = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
    private static final String[] CN_UNITS = {"", "拾", "佰", "仟"};
    private static final String[] CN_BIG_UNITS = {"", "万", "亿"};

    private static final Map<Character, Long> CN_DIGIT_MAP = new HashMap<>();
    private static final Map<Character, Long> CN_UNIT_MAP = new HashMap<>();

    static {
        CN_DIGIT_MAP.put('零', 0L);
        CN_DIGIT_MAP.put('壹', 1L);
        CN_DIGIT_MAP.put('贰', 2L);
        CN_DIGIT_MAP.put('叁', 3L);
        CN_DIGIT_MAP.put('肆', 4L);
        CN_DIGIT_MAP.put('伍', 5L);
        CN_DIGIT_MAP.put('陆', 6L);
        CN_DIGIT_MAP.put('柒', 7L);
        CN_DIGIT_MAP.put('捌', 8L);
        CN_DIGIT_MAP.put('玖', 9L);

        CN_UNIT_MAP.put('拾', 10L);
        CN_UNIT_MAP.put('佰', 100L);
        CN_UNIT_MAP.put('仟', 1000L);
        CN_UNIT_MAP.put('万', 10000L);
        CN_UNIT_MAP.put('亿', 100000000L);
    }

    private ChineseAmountConverter() {
    }

    /**
     * 将数字金额转换为标准中文大写金额.
     */
    public static String toChineseAmount(final BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        long val = amount.longValue();
        if (val == 0) {
            return "零元整";
        }
        StringBuilder sb = new StringBuilder();
        int sectionIndex = 0;
        while (val > 0) {
            int section = (int) (val % 10000);
            if (section > 0) {
                String sectionStr = sectionToChinese(section);
                sb.insert(0, sectionStr + CN_BIG_UNITS[sectionIndex]);
            }
            val /= 10000;
            sectionIndex++;
        }
        return sb.toString() + "元整";
    }

    private static String sectionToChinese(final int section) {
        StringBuilder sb = new StringBuilder();
        int temp = section;
        int unitIndex = 0;
        boolean zeroFlag = true;

        while (temp > 0) {
            int digit = temp % 10;
            if (digit == 0) {
                if (!zeroFlag) {
                    zeroFlag = true;
                    sb.insert(0, CN_NUMS[0]);
                }
            } else {
                zeroFlag = false;
                sb.insert(0, CN_NUMS[digit] + CN_UNITS[unitIndex]);
            }
            unitIndex++;
            temp /= 10;
        }
        return sb.toString();
    }

    /**
     * 将中文大写金额转换为 BigDecimal 数字.
     */
    public static BigDecimal convertChineseToNumber(final String chinese) {
        if (chinese == null || chinese.isBlank()) {
            return BigDecimal.ZERO;
        }
        String s = chinese.replace("元", "").replace("整", "").replace("正", "").trim();
        long total = 0;
        long section = 0;
        long number = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (CN_DIGIT_MAP.containsKey(c)) {
                number = CN_DIGIT_MAP.get(c);
            } else if (CN_UNIT_MAP.containsKey(c)) {
                long unit = CN_UNIT_MAP.get(c);
                if (unit == 100000000L) {
                    section = (section + number) * unit;
                    total += section;
                    section = 0;
                    number = 0;
                } else if (unit == 10000L) {
                    section = (section + number) * unit;
                    total += section;
                    section = 0;
                    number = 0;
                } else {
                    if (number == 0 && unit == 10L) {
                        number = 1;
                    }
                    section += number * unit;
                    number = 0;
                }
            }
        }
        total += section + number;
        return BigDecimal.valueOf(total);
    }
}
