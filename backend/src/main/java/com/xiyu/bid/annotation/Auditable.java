package com.xiyu.bid.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作记录注解
 * 标记需要记录操作日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /**
     * 操作类型
     */
    String action() default "OPERATION";

    /**
     * 实体类型
     */
    String entityType() default "";

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否把本次操作记录关联到项目动态（写入 audit_logs.project_id）。
     * <p>默认 false，避免非项目操作（业绩/模板/协作等）的实体 id 被错当作 projectId。
     * <p>项目相关方法（Project/Tender/Fee/CalendarEvent/ProjectClosure 等）需显式声明 {@code projectScoped = true}。
     * <p>背景：CO-324 引入 audit_logs.project_id 后，AuditableAspect 原实现对所有
     * 第一参为 Long 的 @Auditable 方法都提取 projectId，导致 Performance/Template 等
     * 非项目实体的 id 被错写为 project_id，污染项目动态。
     */
    boolean projectScoped() default false;
}
