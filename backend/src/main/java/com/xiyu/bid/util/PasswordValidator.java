package com.xiyu.bid.util;

import java.util.regex.Pattern;

/**
 * 密码强度验证工具类
 * 确保用户密码符合安全要求
 */
public class PasswordValidator {

    // 最小密码长度
    private static final int MIN_LENGTH = 8;

    // 最大密码长度
    private static final int MAX_LENGTH = 128;

    // 正则表达式模式
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");

    /**
     * 验证密码强度
     *
     * @param password 待验证的密码
     * @return 验证结果对象
     */
    public static ValidationResult validate(String password) {
        if (password == null) {
            return ValidationResult.failure("密码不能为空");
        }

        if (password.length() < MIN_LENGTH) {
            return ValidationResult.failure("密码长度不能少于" + MIN_LENGTH + "个字符");
        }

        if (password.length() > MAX_LENGTH) {
            return ValidationResult.failure("密码长度不能超过" + MAX_LENGTH + "个字符");
        }

        boolean hasLowercase = LOWERCASE_PATTERN.matcher(password).find();
        boolean hasUppercase = UPPERCASE_PATTERN.matcher(password).find();
        boolean hasDigit = DIGIT_PATTERN.matcher(password).find();
        boolean hasSpecialChar = SPECIAL_CHAR_PATTERN.matcher(password).find();

        int strengthScore = 0;
        if (hasLowercase) strengthScore++;
        if (hasUppercase) strengthScore++;
        if (hasDigit) strengthScore++;
        if (hasSpecialChar) strengthScore++;

        if (strengthScore < 3) {
            return ValidationResult.failure("密码强度不足：必须包含大写字母、小写字母、数字和特殊字符中的至少3种");
        }

        // 检查常见弱密码
        if (isCommonWeakPassword(password)) {
            return ValidationResult.failure("密码过于简单，请使用更复杂的密码");
        }

        return ValidationResult.success();
    }

    /**
     * 计算密码强度得分（0-100）
     *
     * @param password 密码
     * @return 强度得分
     */
    public static int calculateStrength(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }

        int score = 0;

        // 长度得分（最多40分）
        int length = Math.min(password.length(), MAX_LENGTH);
        score += Math.min(length * 2, 40);

        // 字符类型得分（每种类型10分，最多40分）
        if (LOWERCASE_PATTERN.matcher(password).find()) score += 10;
        if (UPPERCASE_PATTERN.matcher(password).find()) score += 10;
        if (DIGIT_PATTERN.matcher(password).find()) score += 10;
        if (SPECIAL_CHAR_PATTERN.matcher(password).find()) score += 10;

        // 复杂度加分（最多20分）
        int uniqueChars = (int) password.chars().distinct().count();
        score += Math.min(uniqueChars, 20);

        return Math.min(score, 100);
    }

    /**
     * 检查是否为常见弱密码
     */
    private static boolean isCommonWeakPassword(String password) {
        String[] weakPasswords = {
            "password", "Password123", "Admin123",
            "12345678", "87654321", "qwerty123",
            "abcd1234", "test1234", "temp1234"
        };

        String lowerPassword = password.toLowerCase();
        for (String weak : weakPasswords) {
            if (lowerPassword.contains(weak)) {
                return true;
            }
        }

        // 检查重复字符（如 aaaaaaaa）
        if (password.matches("(.)\\1{7,}")) {
            return true;
        }

        // 检查连续字符（如 12345678）
        for (int i = 0; i < password.length() - 3; i++) {
            char c = password.charAt(i);
            if (c + 1 == password.charAt(i + 1) &&
                c + 2 == password.charAt(i + 2) &&
                c + 3 == password.charAt(i + 3)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 密码验证结果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean pValid, String pMessage) {
            this.valid = pValid;
            this.message = pMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }
}
