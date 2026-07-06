package com.xiyu.bid.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiyu.bid.platform.domain.PlatformAccountImportPolicy.ParsedAccountRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PlatformAccountImportPolicy 平台账户导入策略")
class PlatformAccountImportPolicyTest {

    @Nested
    @DisplayName("validateHeader — 表头校验")
    class ValidateHeader {

        @Test
        @DisplayName("新表头（账号保管员工号*）校验通过")
        void newHeader_valid() {
            String[] header = {
                    "平台名称*", "平台网址*", "登录账号*", "登录密码*",
                    "账号保管员工号*", "是否有CA", "备注",
                    "注册人", "注册手机", "注册邮箱"
            };
            assertThat(PlatformAccountImportPolicy.validateHeader(header)).isEmpty();
        }

        @Test
        @DisplayName("旧表头（账号保管员userId）校验失败")
        void oldHeader_invalid() {
            String[] header = {
                    "平台名称*", "平台网址*", "登录账号*", "登录密码*",
                    "账号保管员userId", "是否有CA", "备注",
                    "注册人", "注册手机", "注册邮箱"
            };
            assertThat(PlatformAccountImportPolicy.validateHeader(header))
                    .anyMatch(s -> s.contains("账号保管员工号"));
        }
    }

    @Nested
    @DisplayName("parseRow — 行解析")
    class ParseRow {

        private String[] validCells() {
            return new String[]{
                    "测试平台", "https://example.com", "admin", "pass123",
                    "EMP001", "否", "",
                    "", "", ""
            };
        }

        @Test
        @DisplayName("有效行：工号正确解析，employeeNumber 字段有值")
        void validRow_employeeNumberParsed() {
            ParsedAccountRow row = PlatformAccountImportPolicy.parseRow(2, validCells());
            assertThat(row.valid()).isTrue();
            assertThat(row.employeeNumber()).isEqualTo("EMP001");
        }

        @Test
        @DisplayName("工号为空时，valid() 为 false，错误包含提示")
        void emptyEmployeeNumber_invalid() {
            String[] cells = validCells();
            cells[4] = "";
            ParsedAccountRow row = PlatformAccountImportPolicy.parseRow(2, cells);
            assertThat(row.valid()).isFalse();
            assertThat(row.errors()).contains("请填入账号保管员工号");
        }

        @Test
        @DisplayName("工号为空白字符时，视为空，报错")
        void blankEmployeeNumber_invalid() {
            String[] cells = validCells();
            cells[4] = "   ";
            ParsedAccountRow row = PlatformAccountImportPolicy.parseRow(2, cells);
            assertThat(row.valid()).isFalse();
            assertThat(row.errors()).contains("请填入账号保管员工号");
        }

        @Test
        @DisplayName("工号会自动 trim 前后空白")
        void employeeNumberTrimmed() {
            String[] cells = validCells();
            cells[4] = "  EMP001  ";
            ParsedAccountRow row = PlatformAccountImportPolicy.parseRow(2, cells);
            assertThat(row.valid()).isTrue();
            assertThat(row.employeeNumber()).isEqualTo("EMP001");
        }

        @Test
        @DisplayName("工号支持纯数字格式（兼容旧习惯）")
        void numericEmployeeNumber_valid() {
            String[] cells = validCells();
            cells[4] = "20260509";
            ParsedAccountRow row = PlatformAccountImportPolicy.parseRow(2, cells);
            assertThat(row.valid()).isTrue();
            assertThat(row.employeeNumber()).isEqualTo("20260509");
        }
    }
}
