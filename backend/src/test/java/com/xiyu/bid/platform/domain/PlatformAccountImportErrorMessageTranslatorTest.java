package com.xiyu.bid.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * PlatformAccountImportErrorMessageTranslator 单元测试（CO-560 补强）。
 *
 * <p>验证底层 DB 异常消息被正确翻译成业务可读消息，不暴露表结构/列名。
 */
@DisplayName("PlatformAccountImportErrorMessageTranslator 异常翻译器")
class PlatformAccountImportErrorMessageTranslatorTest {

    @Nested
    @DisplayName("translate — Data too long")
    class DataTooLong {

        @Test
        @DisplayName("register_phone 超长：翻译成「注册手机长度超过数据库限制」")
        void registerPhoneTooLong_translated() {
            Exception e = new RuntimeException(
                    "could not execute statement; SQL [n/a]; nested exception is " +
                    "org.hibernate.exception.DataException: Data too long for column 'register_phone'");
            assertThat(PlatformAccountImportErrorMessageTranslator.translate(e))
                    .isEqualTo("注册手机长度超过数据库限制");
        }

        @Test
        @DisplayName("未知列超长：原样返回列名")
        void unknownColumnTooLong_returnsColumnName() {
            Exception e = new RuntimeException("Data too long for column 'some_new_field'");
            assertThat(PlatformAccountImportErrorMessageTranslator.translate(e))
                    .isEqualTo("some_new_field长度超过数据库限制");
        }
    }

    @Nested
    @DisplayName("translate — NOT NULL 约束")
    class NotNullConstraint {

        @Test
        @DisplayName("account_name 为空：翻译成「平台名称不能为空」")
        void accountNameNull_translated() {
            Exception e = new DataIntegrityViolationException(
                    "could not execute statement; Column 'account_name' cannot be null");
            assertThat(PlatformAccountImportErrorMessageTranslator.translate(e))
                    .isEqualTo("平台名称不能为空");
        }
    }

    @Nested
    @DisplayName("translate — 唯一约束冲突")
    class DuplicateEntry {

        @Test
        @DisplayName("重复值：翻译成「数据重复（值「xxx」已存在）」")
        void duplicateEntry_translated() {
            Exception e = new DataIntegrityViolationException(
                    "Duplicate entry 'test_platform' for key 'platform_accounts.account_name_unique'");
            String result = PlatformAccountImportErrorMessageTranslator.translate(e);
            assertThat(result).contains("数据重复");
            assertThat(result).contains("test_platform");
        }
    }

    @Nested
    @DisplayName("translate — 外键约束")
    class ForeignKeyConstraint {

        @Test
        @DisplayName("外键失败：翻译成「关联数据不存在」")
        void foreignKeyFail_translated() {
            Exception e = new DataIntegrityViolationException(
                    "Cannot add or update a child row: a foreign key constraint fails");
            assertThat(PlatformAccountImportErrorMessageTranslator.translate(e))
                    .isEqualTo("关联数据不存在（外键约束失败）");
        }
    }

    @Nested
    @DisplayName("translate — 异常链穿透")
    class ExceptionChainTraversal {

        @Test
        @DisplayName("三层包装：提取最内层根因消息")
        void threeLayerWrapping_extractsRootCause() {
            Exception root = new RuntimeException("Data too long for column 'register_email'");
            Exception middle = new RuntimeException("persist failed", root);
            Exception outer = new RuntimeException("import task error", middle);
            assertThat(PlatformAccountImportErrorMessageTranslator.translate(outer))
                    .isEqualTo("注册邮箱长度超过数据库限制");
        }

        @Test
        @DisplayName("根因 message 为 null：使用外层 message")
        void rootMessageNull_usesOuterMessage() {
            Exception root = new RuntimeException();
            Exception outer = new RuntimeException("Duplicate entry 'foo' for key", root);
            String result = PlatformAccountImportErrorMessageTranslator.translate(outer);
            assertThat(result).contains("数据重复");
        }
    }

    @Nested
    @DisplayName("translate — 兜底")
    class Fallback {

        @Test
        @DisplayName("未知异常：返回通用「数据保存失败」，不暴露原始消息")
        void unknownException_returnsGenericMessage() {
            Exception e = new RuntimeException("org.hibernate.AssertionFailure: null id");
            assertThat(PlatformAccountImportErrorMessageTranslator.translate(e))
                    .isEqualTo("数据保存失败");
        }

        @Test
        @DisplayName("空消息：返回通用「数据保存失败」")
        void emptyMessage_returnsGenericMessage() {
            Exception e = new RuntimeException("");
            assertThat(PlatformAccountImportErrorMessageTranslator.translate(e))
                    .isEqualTo("数据保存失败");
        }

        @Test
        @DisplayName("null 消息：返回通用「数据保存失败」")
        void nullMessage_returnsGenericMessage() {
            Exception e = new RuntimeException((String) null);
            assertThat(PlatformAccountImportErrorMessageTranslator.translate(e))
                    .isEqualTo("数据保存失败");
        }
    }
}
