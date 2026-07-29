// Input: @Auditable 切点、操作上下文、IAuditLogService
// Output: 操作日志记录与环绕增强
// Pos: Aspect/操作日志切面层
// 维护声明: 仅维护横切操作记录逻辑；不要在此层承载业务规则.
package com.xiyu.bid.aspect;

import com.xiyu.bid.audit.service.AuditLogService;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.audit.core.AuditActionPolicy;
import com.xiyu.bid.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 操作日志切面
 * 自动记录带有 @Auditable 注解的方法调用
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditableAspect {

    private final IAuditLogService auditLogService;
    private final AuditActionPolicy auditActionPolicy;

    @Around("@annotation(com.xiyu.bid.annotation.Auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 获取注解
        com.xiyu.bid.annotation.Auditable auditable =
            method.getAnnotation(com.xiyu.bid.annotation.Auditable.class);

        // 获取当前用户信息
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth != null ? auth.getName() : "system";
        String username = auth != null ? auth.getName() : "system";

        // 记录开始时间
        long startTime = System.currentTimeMillis();

        Object result = null;
        boolean success = false;
        String errorMessage = null;

        try {
            result = joinPoint.proceed();
            success = true;
            return result;
        } catch (RuntimeException e) {
            success = false;
            errorMessage = e.getMessage();
            throw e;
        } catch (Error e) {
            success = false;
            errorMessage = e.getMessage();
            throw e;
        } finally {
            // 计算执行时间
            long duration = System.currentTimeMillis() - startTime;

            // 记录关键操作
            if (auditable != null && auditActionPolicy.shouldRecord(auditable.action())) {
                // CO-XXX 修复：仅 projectScoped=true 时才提取 projectId，避免非项目操作
                // （Performance/Template/Collaboration 等）的实体 id 被错当作 projectId。
                Long projectId = auditable.projectScoped()
                        ? extractProjectId(joinPoint.getArgs(), result)
                        : null;
                AuditLogService.AuditLogEntry entry = AuditLogService.AuditLogEntry.builder()
                    .userId(userId)
                    .username(username)
                    .action(auditable.action())
                    .entityType(auditable.entityType())
                    .entityId(extractEntityId(joinPoint.getArgs(), result))
                    .projectId(projectId)
                    .description(auditable.description().isEmpty() ?
                        method.getName() : auditable.description())
                    .success(success)
                    .errorMessage(errorMessage)
                    .build();

                auditLogService.log(entry);

                log.debug("Audited method call: {} - {} ({}ms)",
                    method.getName(), success ? "SUCCESS" : "FAILED", duration);
            }
        }
    }

    /**
     * 从方法参数中提取实体ID
     */
    private String extractEntityId(Object[] args, Object result) {
        String resultId = extractIdFromObject(result, 0);
        if (resultId != null) {
            return resultId;
        }

        if (args == null || args.length == 0) {
            return null;
        }

        for (Object arg : args) {
            String argId = extractIdFromObject(arg, 0);
            if (argId != null) {
                return argId;
            }
            if (arg instanceof Number || arg instanceof CharSequence) {
                String str = arg.toString();
                if (str.length() <= 100) {
                    return str;
                }
            }
        }
        return null;
    }

    /**
     * CO-324: 从方法参数/返回值提取项目 ID（项目动态按项目查询）。
     * 顺序：入参 getProjectId() -> 返回值 getProjectId()。
     * 非项目操作（projectScoped=false）直接返回 null。
     *
     * <p>CO-XXX 修复：移除了两条 bug 路径——
     * <ul>
     *   <li>移除 Long args-first：原实现把任意 Long 入参当 projectId，导致 performanceId/feeId/templateId
     *       被错写为 project_id（用户报告的 /project/31 污染根因）</li>
     *   <li>移除 getId() fallback：原实现 fallback 到 getId()，对 FeeDTO.getId() 返回 feeId 也会被错当 projectId</li>
     * </ul>
     * 现在只通过显式的 getProjectId() 方法提取，确保只有真正携带 projectId 的对象才能被提取。
     * 对 ProjectDTO（id 即 projectId），通过 ProjectDTO.getProjectId() 显式返回 id。
     */
    private Long extractProjectId(Object[] args, Object result) {
        // args-first：扫描入参对象的 getProjectId()
        if (args != null) {
            for (Object arg : args) {
                String pid = extractProjectIdFromObject(arg, 0);
                if (pid != null) {
                    return parseLongId(pid);
                }
            }
        }
        // 回退：返回值对象的 getProjectId()
        return parseLongId(extractProjectIdFromObject(result, 0));
    }

    private Long parseLongId(String pid) {
        if (pid == null) {
            return null;
        }
        try {
            return Long.valueOf(pid);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractProjectIdFromObject(Object value, int depth) {
        if (value == null || depth > 4) {
            return null;
        }
        if (value instanceof ResponseEntity<?> response) {
            return extractProjectIdFromObject(response.getBody(), depth + 1);
        }
        if (value instanceof ApiResponse<?> apiResponse) {
            return extractProjectIdFromObject(apiResponse.getData(), depth + 1);
        }
        // CO-XXX 修复：移除 Long args-first 路径（原 L170-172）——
        // 任意 Long 入参（performanceId/feeId/templateId）被错当 projectId 是污染根因。
        // 只通过显式的 getProjectId() 方法提取，确保只有真正携带 projectId 的对象才能被提取。
        return invokeNoArgIdMethod(value, "getProjectId");
    }

    private String extractIdFromObject(Object value, int depth) {
        if (value == null || depth > 4) {
            return null;
        }

        if (value instanceof ResponseEntity<?> response) {
            return extractIdFromObject(response.getBody(), depth + 1);
        }

        if (value instanceof ApiResponse<?> apiResponse) {
            return extractIdFromObject(apiResponse.getData(), depth + 1);
        }

        if (value instanceof Number || value instanceof CharSequence) {
            String str = value.toString();
            return str.length() <= 100 ? str : null;
        }

        String beanId = invokeNoArgIdMethod(value, "getId");
        if (beanId != null) {
            return beanId;
        }

        return invokeNoArgIdMethod(value, "id");
    }

    private String invokeNoArgIdMethod(Object value, String methodName) {
        try {
            Method getId = findNoArgMethod(value, methodName);
            if (getId != null && getId.getParameterCount() == 0) {
                if (!getId.canAccess(value)) {
                    getId.setAccessible(true);
                }
                Object id = getId.invoke(value);
                if (id != null) {
                    String str = id.toString();
                    return str.length() <= 100 ? str : null;
                }
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Fall through to no ID extracted.
        }

        return null;
    }

    private Method findNoArgMethod(Object value, String methodName) {
        try {
            return value.getClass().getMethod(methodName);
        } catch (NoSuchMethodException ignored) {
            try {
                return value.getClass().getDeclaredMethod(methodName);
            } catch (NoSuchMethodException missing) {
                return null;
            }
        }
    }
}
