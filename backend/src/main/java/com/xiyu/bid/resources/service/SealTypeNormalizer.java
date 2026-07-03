package com.xiyu.bid.resources.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 印章类型归一化器：将逗号分隔的多选印章类型输入清洗为标准格式。
 *
 * <p>从 CaCertificateService 提取，以满足 300 行单文件行数预算（RULES.md §单一职责）。
 */
final class SealTypeNormalizer {

    private static final Set<String> VALID_SEAL_TYPES = Set.of(
            "OFFICIAL_SEAL", "LEGAL_PERSON_SEAL", "LEGAL_SIGN", "CONTACT_SIGN");

    private SealTypeNormalizer() {
    }

    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CaBusinessException("印章类型不能为空");
        }
        String normalized = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(VALID_SEAL_TYPES::contains)
                .distinct()
                .collect(Collectors.joining(","));
        if (normalized.isEmpty()) {
            throw new CaBusinessException("印章类型必须包含有效选项：OFFICIAL_SEAL/LEGAL_PERSON_SEAL/LEGAL_SIGN/CONTACT_SIGN");
        }
        return normalized;
    }
}
