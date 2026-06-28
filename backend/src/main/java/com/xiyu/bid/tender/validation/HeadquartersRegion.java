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
 * 校验标讯「总部所在地」为全局统一的一级+二级格式（与前端 chinaRegionData.js 一致）。
 * <p>格式口径：
 * <ul>
 *   <li>普通省/自治区：省+市（如 广东省深圳市）</li>
 *   <li>直辖市：一级+二级拼接（如 北京市北京市），兼容旧市-市格式与单名</li>
 *   <li>港澳台：一级+二级拼接（如 台湾省台北市、香港特别行政区中西区），兼容旧单名</li>
 * </ul>
 * <p>null 值不校验（与其他字段一致，由 @NotBlank 等控制必填）。
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HeadquartersRegion.Validator.class)
@Documented
public @interface HeadquartersRegion {

    String message() default "总部所在地须为一级+二级格式（如\"广东省深圳市\"、\"北京市北京市\"、\"台湾省台北市\"）";

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
