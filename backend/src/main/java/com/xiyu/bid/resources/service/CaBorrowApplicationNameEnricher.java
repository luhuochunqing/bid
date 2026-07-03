package com.xiyu.bid.resources.service;

import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.resources.dto.CaBorrowApplicationDTO;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.entity.CaCertificatePlatformEntity;
import com.xiyu.bid.resources.repository.CaCertificatePlatformRepository;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CO-466: CA 借用申请列表 caName enricher.
 *
 * <p>从 CaBorrowService 拆出，避免 Service 超出 300 行预算。
 * 负责批量查询 CaCertificate + PlatformAccount，按前端 caLabel 契约
 * 拼装 caName 字符串填入 DTO。
 *
 * <p>拼装规则（与前端 CABorrowDialog.vue caLabel 一致）：
 * <pre>caName = [holderName, platforms.join(', '), sealTypeLabel].filter(nonEmpty).join(' / ')</pre>
 */
@Component
@RequiredArgsConstructor
public class CaBorrowApplicationNameEnricher {

    private final CaCertificateRepository certificateRepository;
    private final CaCertificatePlatformRepository platformLinkRepository;
    private final PlatformAccountRepository platformAccountRepository;

    /**
     * 批量为借用申请 enrich caName 字段。
     *
     * <p>使用批量查询避免 N+1：
     * <ol>
     *   <li>collect 所有 caCertificateId</li>
     *   <li>一次性 findAllById 查 CaCertificateEntity</li>
     *   <li>一次性 findByCaCertificateIdIn 查 platformLinks</li>
     *   <li>一次性 findAllById 查 PlatformAccount</li>
     * </ol>
     */
    public List<CaBorrowApplicationDTO> enrich(List<CaBorrowApplicationEntity> apps) {
        if (apps == null || apps.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> caIds = apps.stream()
                .map(CaBorrowApplicationEntity::getCaCertificateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (caIds.isEmpty()) {
            return apps.stream().map(e -> CaBorrowApplicationDTO.from(e, null)).collect(Collectors.toList());
        }

        Map<Long, CaCertificateEntity> certMap = certificateRepository.findAllById(caIds).stream()
                .collect(Collectors.toMap(CaCertificateEntity::getId, c -> c,
                        (a, b) -> a)); // CO-027: merge function 防止 Duplicate key 异常

        List<CaCertificatePlatformEntity> links = platformLinkRepository.findByCaCertificateIdIn(caIds);
        Set<Long> platformIds = links.stream()
                .map(CaCertificatePlatformEntity::getPlatformAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> accountNameById = platformIds.isEmpty()
                ? Collections.emptyMap()
                : platformAccountRepository.findAllById(platformIds).stream()
                    .collect(Collectors.toMap(PlatformAccount::getId, PlatformAccount::getAccountName,
                            (a, b) -> a)); // CO-027: merge function 防止 Duplicate key 异常
        Map<Long, List<Long>> platformIdsByCaId = links.stream().collect(Collectors.groupingBy(
                CaCertificatePlatformEntity::getCaCertificateId,
                LinkedHashMap::new,
                Collectors.mapping(CaCertificatePlatformEntity::getPlatformAccountId, Collectors.toList())
        ));

        return apps.stream().map(app -> {
            Long caId = app.getCaCertificateId();
            CaCertificateEntity cert = certMap.get(caId);
            String caName = cert == null ? null
                    : buildCaName(cert, platformIdsByCaId.getOrDefault(caId, List.of()), accountNameById);
            return CaBorrowApplicationDTO.from(app, caName);
        }).collect(Collectors.toList());
    }

    /**
     * 拼装 CA 显示名：[持有人, 关联平台(逗号分隔), 印章中文].filter(nonEmpty).join(' / ')
     */
    private static String buildCaName(CaCertificateEntity cert, List<Long> platformIds,
                                       Map<Long, String> accountNameById) {
        List<String> parts = new ArrayList<>(3);
        if (cert.getHolderName() != null && !cert.getHolderName().isEmpty()) {
            parts.add(cert.getHolderName());
        }
        if (platformIds != null && !platformIds.isEmpty()) {
            String platforms = platformIds.stream()
                    .map(accountNameById::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(", "));
            if (!platforms.isEmpty()) {
                parts.add(platforms);
            }
        }
        String sealLabel = sealTypeLabel(cert.getSealType());
        if (sealLabel != null && !sealLabel.isEmpty()) {
            parts.add(sealLabel);
        }
        return parts.isEmpty() ? null : String.join(" / ", parts);
    }

    /**
     * 印章类型 code → 中文标签，与前端 SEAL_TYPE_MAP 保持一致。
     * 支持多值（英文逗号分隔），如 "OFFICIAL_SEAL,LEGAL_PERSON_SEAL" → "公章,法人章"。
     */
    private static String sealTypeLabel(String sealType) {
        if (sealType == null || sealType.isEmpty()) return "";
        return Arrays.stream(sealType.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> switch (s) {
                    case "OFFICIAL_SEAL" -> "公章";
                    case "LEGAL_PERSON_SEAL" -> "法人章";
                    case "LEGAL_SIGN" -> "法人签字";
                    case "CONTACT_SIGN" -> "联系人签字";
                    default -> s;
                })
                .collect(Collectors.joining(","));
    }
}
