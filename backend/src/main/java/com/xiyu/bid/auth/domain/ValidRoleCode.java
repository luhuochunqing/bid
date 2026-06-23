package com.xiyu.bid.auth.domain;

import com.xiyu.bid.security.domain.LoginRoleWhitelist;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 校验角色 code 必须是系统允许的 7 个业务角色之一，且不能是 staff 等已移除角色。
 *
 * <p>用于命令对象/DTO 的角色字段校验，与 {@link LoginRoleWhitelist} 保持一致。
 */
@Constraint(validatedBy = ValidRoleCode.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidRoleCode {

    String message() default "无效或已移除的角色";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<ValidRoleCode, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return LoginRoleWhitelist.isAllowed(value);
        }
    }
}
