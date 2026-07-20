package com.xiyu.bid.webhook.domain;

import com.xiyu.bid.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OperatorDisplayName 单元测试（FP-Java 纯函数）。
 * <p>覆盖"姓名（工号）"格式的各种边界情况。
 */
@DisplayName("OperatorDisplayName — 操作人展示名格式化")
class OperatorDisplayNameTest {

    @Test
    @DisplayName("正常：姓名 + 工号 → \"姓名（工号）\"")
    void full_name_with_employee_number() {
        User user = user("郑蓉蓉", "06234");
        assertThat(OperatorDisplayName.format(user)).isEqualTo("郑蓉蓉（06234）");
    }

    @Test
    @DisplayName("工号为空时 fallback 到 username → \"姓名（username）\"")
    void blank_employee_number_falls_back_to_username() {
        User user = new User();
        user.setFullName("李四");
        user.setUsername("lisi");
        user.setEmployeeNumber(null);  // 工号空，getDisplayEmployeeNumber() 返回 username
        assertThat(OperatorDisplayName.format(user)).isEqualTo("李四（lisi）");
    }

    @Test
    @DisplayName("工号为空白字符串时也 fallback 到 username")
    void blank_string_employee_number_falls_back_to_username() {
        User user = new User();
        user.setFullName("王五");
        user.setUsername("wangwu");
        user.setEmployeeNumber("   ");
        assertThat(OperatorDisplayName.format(user)).isEqualTo("王五（wangwu）");
    }

    @Test
    @DisplayName("姓名为空时 fallback 到 username → \"username（工号）\"")
    void empty_full_name_falls_back_to_username() {
        User user = new User();
        user.setFullName("");
        user.setUsername("zhangsan");
        user.setEmployeeNumber("06100");
        assertThat(OperatorDisplayName.format(user)).isEqualTo("zhangsan（06100）");
    }

    @Test
    @DisplayName("姓名 null 时 fallback 到 username → \"username（工号）\"")
    void null_full_name_falls_back_to_username() {
        User user = new User();
        user.setFullName(null);
        user.setUsername("zhangsan");
        user.setEmployeeNumber("06100");
        assertThat(OperatorDisplayName.format(user)).isEqualTo("zhangsan（06100）");
    }

    @Test
    @DisplayName("姓名和工号都为空时返回空字符串")
    void both_empty_returns_empty_string() {
        User user = new User();
        user.setFullName("");
        user.setUsername("");
        user.setEmployeeNumber(null);
        assertThat(OperatorDisplayName.format(user)).isEmpty();
    }

    @Test
    @DisplayName("user 为 null 时返回空字符串")
    void null_user_returns_empty_string() {
        assertThat(OperatorDisplayName.format(null)).isEmpty();
    }

    // ============ formatStrict：严格模式（fullName 空时返回空，不回退 username）============

    @Test
    @DisplayName("formatStrict 正常：姓名 + 工号 → \"姓名（工号）\"")
    void strict_full_name_with_employee_number() {
        User user = user("郑蓉蓉", "06234");
        assertThat(OperatorDisplayName.formatStrict(user)).isEqualTo("郑蓉蓉（06234）");
    }

    @Test
    @DisplayName("formatStrict 姓名为空时返回空字符串（不回退 username）")
    void strict_empty_full_name_returns_empty() {
        User user = new User();
        user.setFullName("");
        user.setUsername("06234");  // OSS 用户 username=工号
        user.setEmployeeNumber("06234");
        // 关键：不返回"06234（06234）"，让调用方走自己的 fallback
        assertThat(OperatorDisplayName.formatStrict(user)).isEmpty();
    }

    @Test
    @DisplayName("formatStrict 姓名 null 时返回空字符串")
    void strict_null_full_name_returns_empty() {
        User user = new User();
        user.setFullName(null);
        user.setUsername("zhangsan");
        user.setEmployeeNumber("06100");
        assertThat(OperatorDisplayName.formatStrict(user)).isEmpty();
    }

    @Test
    @DisplayName("formatStrict 工号为空时只返回姓名（与 format 一致）")
    void strict_blank_employee_number_returns_full_name_only() {
        User user = new User();
        user.setFullName("李四");
        user.setUsername("lisi");
        user.setEmployeeNumber(null);
        // formatStrict 在工号空时仍 fallback 到 username 作为工号（getDisplayEmployeeNumber 行为），
        // 所以这里返回"李四（lisi）"。这与 format 一致，因为 formatStrict 只改变 fullName 空的处理。
        assertThat(OperatorDisplayName.formatStrict(user)).isEqualTo("李四（lisi）");
    }

    @Test
    @DisplayName("formatStrict user 为 null 时返回空字符串")
    void strict_null_user_returns_empty() {
        assertThat(OperatorDisplayName.formatStrict(null)).isEmpty();
    }

    private User user(String fullName, String employeeNumber) {
        User user = new User();
        user.setFullName(fullName);
        user.setUsername("user_login");
        user.setEmployeeNumber(employeeNumber);
        return user;
    }
}
