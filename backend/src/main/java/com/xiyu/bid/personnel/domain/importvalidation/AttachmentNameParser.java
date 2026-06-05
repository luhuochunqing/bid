package com.xiyu.bid.personnel.domain.importvalidation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 附件文件名解析器（纯核心，无副作用）
 *
 * 负责将用户上传的文件名解析为结构化信息，并判断是否符合蓝图规范。
 */
public final class AttachmentNameParser {

    // 匹配 PER_姓名_工号_序号_证书名.扩展名
    // 允许姓名和证书名包含常见字符，但工号和序号严格
    private static final Pattern STANDARD_PATTERN = Pattern.compile(
            "^PER_(.+?)_([A-Za-z0-9_-]+)_(\\d{1,3})_(.+)\\.([a-zA-Z0-9]+)$"
    );

    private AttachmentNameParser() {}

    /**
     * 尝试解析文件名。
     * 即使格式不完全标准，也尽量提取可用信息（容错设计）。
     */
    public static ParsedAttachmentName parse(String rawFileName) {
        if (rawFileName == null || rawFileName.isBlank()) {
            return new ParsedAttachmentName(rawFileName, null, null, null, null, null);
        }

        String trimmed = rawFileName.trim();
        Matcher matcher = STANDARD_PATTERN.matcher(trimmed);

        if (matcher.matches()) {
            String name = matcher.group(1);
            String empNo = matcher.group(2);
            Integer seq = parseSequence(matcher.group(3));
            String certPart = matcher.group(4);
            String ext = matcher.group(5).toLowerCase();

            return new ParsedAttachmentName(trimmed, name, empNo, seq, certPart, ext);
        }

        // 格式不标准时，尝试做最小降级解析（只提取工号和扩展名，如果可能）
        return fallbackParse(trimmed);
    }

    private static Integer parseSequence(String seqStr) {
        // 由调用处正则 ^PER_..._(\\d{1,3})_ 保证此处一定是数字，无需异常控制流
        // 符合 FP-Java / ArchTest 纯核心不得用异常做业务分支的要求
        int val = Integer.parseInt(seqStr);
        return val > 0 ? val : null;
    }

    private static ParsedAttachmentName fallbackParse(String raw) {
        // 极简降级逻辑：提取最后一个点后的扩展名 + 尝试找类似工号的段
        String ext = null;
        int dot = raw.lastIndexOf('.');
        if (dot > 0 && dot < raw.length() - 1) {
            ext = raw.substring(dot + 1).toLowerCase();
        }

        // 简单启发：找包含字母数字且较长的段作为可能的工号
        String possibleEmpNo = null;
        String[] parts = raw.split("[_\\-./\\\\]");
        for (String p : parts) {
            if (p.matches("[A-Za-z0-9_-]{3,}") && !p.toLowerCase().startsWith("per")) {
                possibleEmpNo = p;
                break;
            }
        }

        return new ParsedAttachmentName(raw, null, possibleEmpNo, null, null, ext);
    }

    /**
     * 判断是否严格符合蓝图要求的标准命名格式
     */
    public static boolean isStandardFormat(String rawFileName) {
        if (rawFileName == null) return false;
        return STANDARD_PATTERN.matcher(rawFileName.trim()).matches();
    }
}
