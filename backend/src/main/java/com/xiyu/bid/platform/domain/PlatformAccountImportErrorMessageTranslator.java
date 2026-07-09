package com.xiyu.bid.platform.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯核心：平台账号导入异常翻译器（CO-560 补强）。
 *
 * <p>将底层 DB/Hibernate 异常消息翻译成业务可读消息，避免向用户暴露表结构、列名等技术细节。
 * 原始异常仍由调用方记录日志，此翻译器仅负责面向用户的错误消息。
 *
 * <p>覆盖的异常类型（基于 MySQL 8.0 + Hibernate 6.x + Spring Data JPA）：
 * <ul>
 *   <li>Data too long for column → 字段长度超限（已提取列名）</li>
 *   <li>NOT NULL 约束违反 → 必填字段为空</li>
 *   <li>Duplicate entry → 唯一约束冲突</li>
 *   <li>Foreign key 约束 → 关联数据不存在</li>
 *   <li>其他 DataAccessException → 通用"数据保存失败"</li>
 * </ul>
 */
public final class PlatformAccountImportErrorMessageTranslator {

    /** "Data too long for column 'register_phone'" → 提取 register_phone */
    private static final Pattern DATA_TOO_LONG_PATTERN =
            Pattern.compile("Data too long for column '([^']+)'", Pattern.CASE_INSENSITIVE);

    /** "Column 'xxx' cannot be null" → 提取 xxx */
    private static final Pattern CANNOT_NULL_PATTERN =
            Pattern.compile("Column '([^']+)' cannot be null", Pattern.CASE_INSENSITIVE);

    /** "Duplicate entry 'xxx' for key" → 提取 xxx */
    private static final Pattern DUPLICATE_ENTRY_PATTERN =
            Pattern.compile("Duplicate entry '([^']*)'", Pattern.CASE_INSENSITIVE);

    private PlatformAccountImportErrorMessageTranslator() {}

    /**
     * 将异常翻译成业务可读消息（不暴露表结构）。
     *
     * @param e persist 层抛出的异常（通常是 DataAccessException 或其子类）
     * @return 面向用户的错误消息
     */
    public static String translate(Exception e) {
        String message = extractRootMessage(e);
        if (message == null || message.isBlank()) {
            return "数据保存失败";
        }
        String lower = message.toLowerCase();

        // Data too long for column 'xxx'
        Matcher m = DATA_TOO_LONG_PATTERN.matcher(message);
        if (m.find()) {
            return fieldLabel(m.group(1)) + "长度超过数据库限制";
        }

        // Column 'xxx' cannot be null
        m = CANNOT_NULL_PATTERN.matcher(message);
        if (m.find()) {
            return fieldLabel(m.group(1)) + "不能为空";
        }

        // Duplicate entry 'xxx' for key
        m = DUPLICATE_ENTRY_PATTERN.matcher(message);
        if (m.find()) {
            return "数据重复（值「" + m.group(1) + "」已存在）";
        }

        // Foreign key constraint
        if (lower.contains("foreign key") || lower.contains("child row")
                || lower.contains("a foreign key constraint fails")) {
            return "关联数据不存在（外键约束失败）";
        }

        // 通用 DataAccessException
        if (lower.contains("constraint") || lower.contains("integrity")) {
            return "数据约束校验失败";
        }

        // 默认：不暴露原始消息
        return "数据保存失败";
    }

    /**
     * 提取异常链最根因的 message（穿透 Spring/Hibernate 包装层）。
     */
    private static String extractRootMessage(Throwable e) {
        Throwable current = e;
        String lastMessage = e.getMessage();
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            if (current.getMessage() != null) {
                lastMessage = current.getMessage();
            }
        }
        return lastMessage;
    }

    /**
     * 将数据库列名翻译成业务字段标签。
     * 未识别的列名原样返回（用户至少能看到列名，不比"数据保存失败"更差）。
     */
    private static String fieldLabel(String columnName) {
        return switch (columnName.toLowerCase()) {
            case "register_phone" -> "注册手机";
            case "register_email" -> "注册邮箱";
            case "registrant" -> "注册人";
            case "account_name" -> "平台名称";
            case "username" -> "登录账号";
            case "url" -> "平台网址";
            case "remarks" -> "备注";
            default -> columnName;
        };
    }
}
