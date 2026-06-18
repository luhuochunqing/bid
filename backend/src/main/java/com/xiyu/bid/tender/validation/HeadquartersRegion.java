// Input: 标讯总部所在地字符串
// Output: Bean Validation 注解，校验值在 TenderRegionCatalog 白名单内
// Pos: tender/validation - 总部所在地格式校验注解

package com.xiyu.bid.tender.validation;

import com.xiyu.bid.tender.core.TenderRegionCatalog;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 校验标讯「总部所在地」为全局统一的省+市格式（直辖市仅市、港澳台仅本级行政区名）。
 * <p>null 值不校验（与其他字段一致，由 @NotBlank 等控制必填）。
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HeadquartersRegion.Validator.class)
@Documented
public @interface HeadquartersRegion {

    String message() default "总部所在地须为省+市格式（直辖市仅市，如\"北京市\"、\"广东省深圳市\"）";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<HeadquartersRegion, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return TenderRegionCatalog.isValid(value);
        }
    }
}
