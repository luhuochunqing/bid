// Utility: convert Chinese characters to pinyin for search indexing
// Pos: common/util - shared utility, no framework dependency beyond pinyin4j
package com.xiyu.bid.common.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.Locale;

/**
 * Utility for converting Chinese characters (Hanzi) to Hanyu Pinyin.
 * <p>
 * Used primarily to populate {@code users.full_name_pinyin} on persist/update,
 * enabling the user search API to match by pinyin in addition to the raw name.
 * <p>
 * Non-Chinese characters (letters, digits, punctuation) are preserved as-is.
 */
public final class PinyinUtils {

    private static final HanyuPinyinOutputFormat FORMAT;

    static {
        FORMAT = new HanyuPinyinOutputFormat();
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    private PinyinUtils() {
    }

    /**
     * Converts the Chinese characters in {@code input} to lowercase pinyin
     * without tone marks. All pinyin syllables are concatenated without spaces
     * so that {@code LIKE '%zhangsan%'} matches on any substring of the full
     * pinyin. Non-Chinese characters are kept as-is.
     * <p>
     * Examples:
     * <pre>
     *   toPinyin("张三")       → "zhangsan"
     *   toPinyin("张三123")    → "zhangsan123"
     *   toPinyin("hello")     → "hello"
     *   toPinyin(null)        → ""
     *   toPinyin("")          → ""
     *   toPinyin("欧阳小明")   → "ouyangxiaoming"
     * </pre>
     *
     * @param input the source string, may be null
     * @return the pinyin-converted string, never null
     */
    public static String toPinyin(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(input.length() * 2);
        char[] chars = input.trim().toCharArray();
        for (char ch : chars) {
            if (Character.toString(ch).matches("\\p{Script=Han}")) {
                try {
                    String[] pinyins = PinyinHelper.toHanyuPinyinStringArray(ch, FORMAT);
                    if (pinyins != null && pinyins.length > 0) {
                        result.append(pinyins[0]);
                    } else {
                        result.append(ch);
                    }
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    result.append(ch);
                }
            } else {
                result.append(ch);
            }
        }
        return result.toString().toLowerCase(Locale.ROOT);
    }
}