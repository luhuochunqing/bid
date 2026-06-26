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
    @DisplayName("姓名为空时只返回工号")
    void empty_full_name_returns_employee_number_only() {
        User user = new User();
        user.setFullName("");
        user.setUsername("zhangsan");
        user.setEmployeeNumber("06100");
        assertThat(OperatorDisplayName.format(user)).isEqualTo("06100");
    }

    @Test
    @DisplayName("姓名 null 时只返回工号")
    void null_full_name_returns_employee_number_only() {
        User user = new User();
        user.setFullName(null);
        user.setUsername("zhangsan");
        user.setEmployeeNumber("06100");
        assertThat(OperatorDisplayName.format(user)).isEqualTo("06100");
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

    private User user(String fullName, String employeeNumber) {
        User user = new User();
        user.setFullName(fullName);
        user.setUsername("user_login");
        user.setEmployeeNumber(employeeNumber);
        return user;
    }
}
