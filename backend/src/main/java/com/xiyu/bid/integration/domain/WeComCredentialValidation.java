package com.xiyu.bid.integration.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure domain validation for WeComCredential.
 * No Spring, no side effects — returns a ValidationResult.
 *
 * Design: Bean Validation on WeComIntegrationRequest covers field-format rules at the HTTP boundary
 * and returns field-level 400 errors. This class enforces the same invariants at the domain layer
 * as defense-in-depth (e.g. when the credential is constructed outside the controller path).
 * Duplication is intentional; each layer owns its boundary.
 */
public final class WeComCredentialValidation {

    private static final int CORP_ID_MAX_LENGTH = 64;
    private static final int AGENT_ID_MAX_LENGTH = 32;

    private WeComCredentialValidation() {
    }

    public static ValidationResult validate(WeComCredential credential) {
        List<String> errors = new ArrayList<>();

        validateCorpId(credential.corpId(), errors);
        validateAgentId(credential.agentId(), errors);
        validateCorpSecret(credential.corpSecret(), errors);

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.failed(errors);
    }

    private static void validateCorpId(String corpId, List<String> errors) {
        if (corpId == null || corpId.isBlank()) {
            errors.add("corpId 不能为空");
            return;
        }
        if (corpId.length() > CORP_ID_MAX_LENGTH) {
            errors.add("corpId 长度不能超过 " + CORP_ID_MAX_LENGTH + " 个字符");
        }
    }

    private static void validateAgentId(String agentId, List<String> errors) {
        if (agentId == null || agentId.isBlank()) {
            errors.add("agentId 不能为空");
            return;
        }
        if (agentId.length() > AGENT_ID_MAX_LENGTH) {
            errors.add("agentId 长度不能超过 " + AGENT_ID_MAX_LENGTH + " 个字符");
            return;
        }
        if (!agentId.matches("\\d+")) {
            errors.add("agentId 必须为纯数字字符串");
        }
    }

    private static void validateCorpSecret(String corpSecret, List<String> errors) {
        if (corpSecret == null || corpSecret.isBlank()) {
            errors.add("corpSecret 不能为空");
        }
    }
}
