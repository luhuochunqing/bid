// Input: 表单提交上下文
// Output: 审计记录持久化
// Pos: Application 层
// 维护声明: 编排审计日志持久化，无复杂业务规则.
package com.xiyu.bid.formengine.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.formengine.infrastructure.persistence.FormDefinitionRegistryRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.FormSubmissionAuditRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormDefinitionRegistryEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormSubmissionAuditEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 表单提交审计服务。
 * 记录每次提交（成功 / 验证失败 / 处理异常）的完整上下文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormSubmissionAuditService {

    private final FormSubmissionAuditRepository auditRepository;
    private final FormDefinitionRegistryRepository definitionRepository;
    private final ObjectMapper objectMapper;

    /**
     * 记录成功提交。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(String scope, Map<String, Object> formData, String operatorUsername, Long orgId) {
        persistAudit(scope, formData, operatorUsername, orgId, FormSubmissionAuditEntity.STATUS_SUCCESS, null);
    }

    /**
     * 记录验证失败提交。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logValidationFailure(String scope, Map<String, Object> formData,
            String operatorUsername, Long orgId, String errorMessage) {
        persistAudit(scope, formData, operatorUsername, orgId, FormSubmissionAuditEntity.STATUS_VALIDATION_FAILED, errorMessage);
    }

    /**
     * 记录处理异常。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logProcessingError(String scope, Map<String, Object> formData,
            String operatorUsername, Long orgId, String errorMessage) {
        persistAudit(scope, formData, operatorUsername, orgId, FormSubmissionAuditEntity.STATUS_PROCESSING_ERROR, errorMessage);
    }

    private void persistAudit(String scope, Map<String, Object> formData,
            String operatorUsername, Long orgId,
            String status, String errorMessage) {
        try {
            var definitionOpt = definitionRepository.findByScopeAndOrgIdIsNullAndEnabledTrue(scope);
            if (definitionOpt.isEmpty()) {
                log.warn("Cannot audit: form definition not found for scope={}", scope);
                return;
            }

            FormSubmissionAuditEntity entity = new FormSubmissionAuditEntity();
            entity.setDefinition(definitionOpt.get());
            entity.setScope(scope);
            entity.setOperatorUsername(operatorUsername);
            entity.setOrgId(orgId);
            entity.setStatus(status);
            entity.setErrorMessage(errorMessage);
            entity.setCreatedAt(LocalDateTime.now());

            // 计算数据哈希（用于去重）
            String json = objectMapper.writeValueAsString(formData);
            entity.setFormDataHash(sha256(json));
            entity.setFormDataSnapshot(json);

            auditRepository.save(entity);
            log.debug("Audit logged: scope={}, operator={}, status={}", scope, operatorUsername, status);
        } catch (Exception e) {
            // 审计失败不应影响主事务
            log.error("Failed to persist form audit: scope={}, operator={}, error={}",
                    scope, operatorUsername, e.getMessage());
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
