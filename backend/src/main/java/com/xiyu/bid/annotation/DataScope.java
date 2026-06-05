package com.xiyu.bid.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Data Scope Permission Annotation
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * The alias of the department field in the query (e.g., "d.dept_id")
     */
    String deptAlias() default "";

    /**
     * The alias of the user field in the query (e.g., "u.user_id")
     */
    String userAlias() default "";
}
