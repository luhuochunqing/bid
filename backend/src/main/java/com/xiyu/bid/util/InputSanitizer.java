package com.xiyu.bid.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 输入清洗工具类
 * 防止XSS攻击和SQL注入
 */
public class InputSanitizer {

    // SQL注入检测模式
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "('(''|[^'])*')|(;)|(\b(ALTER|CREATE|DELETE|DROP|EXEC(UTE){0,1}|INSERT( +INTO){0,1}|MERGE|SELECT|UPDATE|UNION( +ALL){0,1})\b)",
        Pattern.CASE_INSENSITIVE
    );

    // 路径遍历检测模式
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
        ".*(\\.\\.[/\\\\]).*"
    );

    // XSS危险字符检测
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "<(script|iframe|object|embed|form|input|button).*?>.*?</\\1>|javascript:|vbscript:|onload=|onerror=|onclick=",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 清洗HTML输入，移除潜在危险的标签和属性
     *
     * @param html 可能包含HTML的输入
     * @return 清洗后的安全HTML
     */
    public static String sanitizeHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        // 使用JSoup清洗HTML，只保留安全的标签
        Safelist safelist = Safelist.relaxed();

        // 移除所有协议，只允许https
        safelist.removeProtocols("a", "href", "ftp", "http");
        safelist.removeProtocols("img", "src", "ftp", "http");

        return Jsoup.clean(html, safelist);
    }

    /**
     * 清洗纯文本输入，移除所有HTML标签
     *
     * @param text 可能包含HTML的文本
     * @return 纯文本
     */
    public static String stripHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        return Jsoup.clean(text, Safelist.none());
    }

    /**
     * 检测SQL注入尝试
     *
     * @param input 待检测的输入
     * @return 如果检测到SQL注入特征返回true
     */
    public static boolean detectSqlInjection(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        return SQL_INJECTION_PATTERN.matcher(input).find();
    }

    /**
     * 检测路径遍历攻击
     *
     * @param path 文件路径
     * @return 如果检测到路径遍历返回true
     */
    public static boolean detectPathTraversal(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        return PATH_TRAVERSAL_PATTERN.matcher(path).find();
    }

    /**
     * 检测XSS攻击
     *
     * @param input 待检测的输入
     * @return 如果检测到XSS特征返回true
     */
    public static boolean detectXss(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        return XSS_PATTERN.matcher(input).find();
    }

    /**
     * 清洗文件名，移除危险字符
     *
     * @param filename 原始文件名
     * @return 安全的文件名
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "unnamed";
        }

        // 移除路径分隔符和特殊字符
        String sanitized = filename.replaceAll("[/\\\\:*?\"<>|]", "_");

        // 限制长度
        if (sanitized.length() > 255) {
            String extension = "";
            int dotIndex = sanitized.lastIndexOf('.');
            if (dotIndex > 0) {
                extension = sanitized.substring(dotIndex);
                sanitized = sanitized.substring(0, Math.min(200, dotIndex));
            }
            sanitized = sanitized + extension;
        }

        // 防止保留文件名
        if (sanitized.equals("CON") || sanitized.equals("PRN") ||
            sanitized.equals("AUX") || sanitized.equals("NUL") ||
            sanitized.matches("COM[1-9]") || sanitized.matches("LPT[1-9]")) {
            sanitized = "_" + sanitized;
        }

        return sanitized;
    }

    /**
     * 转义SQL特殊字符（用于LIKE语句）
     *
     * @param input 原始输入
     * @return 转义后的字符串
     */
    public static String escapeSqlLike(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        return input
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }

    /**
     * 验证并清洗字符串输入
     *
     * @param input 原始输入
     * @param maxLength 最大允许长度
     * @return 清洗后的字符串，如果输入为null则返回空字符串
     */
    public static String sanitizeString(String input, int maxLength) {
        if (input == null) {
            return "";
        }

        // 移除控制字符
        String sanitized = input.replaceAll("[\\x00-\\x1F\\x7F]", "");

        // 限制长度
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }

        // 去除首尾空白
        return sanitized.trim();
    }

    /**
     * 清洗 Markdown 等对空白敏感的文本：
     * <ul>
     *   <li>保留 {@code \t}（0x09）、{@code \n}（0x0A）、{@code \r}（0x0D），
     *       让标题、列表、代码块等 Markdown 结构得以存活；</li>
     *   <li>仅剥离真正危险的控制字符（0x00-0x08、0x0B、0x0C、0x0E-0x1F、0x7F）；</li>
     *   <li>不调用 {@link String#trim()}，因为 Markdown 中尾随换行具有语义；</li>
     *   <li>按字符长度截断（调用方负责协调字符数与字节数，例如 UTF-8 CJK
     *       场景下 DB 列以字节为单位的容量上限）。</li>
     * </ul>
     *
     * @param input 原始输入
     * @param maxLen 最大允许字符数
     * @return 清洗后的字符串；输入为 {@code null} 时原样返回 {@code null}
     */
    public static String sanitizeMarkdown(String input, int maxLen) {
        if (input == null) {
            return null;
        }
        String cleaned = input.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        if (cleaned.length() > maxLen) {
            cleaned = cleaned.substring(0, maxLen);
        }
        return cleaned;
    }

    /**
     * 验证电子邮件格式
     *
     * @param email 电子邮件地址
     * @return 如果格式有效返回true
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }

        // 简单的邮箱验证（实际应用中可能需要更复杂的验证）
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        return Pattern.matches(emailRegex, email);
    }

    /**
     * 验证手机号格式（中国大陆）
     *
     * @param phone 手机号
     * @return 如果格式有效返回true
     */
    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }

        // 中国大陆手机号：1开头，第二位3-9，共11位
        String phoneRegex = "^1[3-9]\\d{9}$";
        return Pattern.matches(phoneRegex, phone);
    }

    /**
     * 将字符串转换为安全的UTF-8编码
     *
     * @param input 原始输入
     * @return 安全的UTF-8字符串
     */
    public static String toSafeUtf8(String input) {
        if (input == null) {
            return "";
        }

        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 清洗字符串输入（默认最大长度1000）
     *
     * @param input 原始输入
     * @return 清洗后的字符串
     */
    public static String sanitize(String input) {
        return sanitizeString(input, 1000);
    }
}
