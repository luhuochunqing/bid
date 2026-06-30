package com.xiyu.bid.resources.service;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.resources.dto.CaCertificateDTO;
import com.xiyu.bid.resources.dto.CaCertificateRequest;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.entity.CaCertificatePlatformEntity;
import com.xiyu.bid.resources.repository.CaCertificatePlatformRepository;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import com.xiyu.bid.security.EffectiveRoleResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
public class CaCertificateService {

    private final CaCertificateRepository certificateRepository;
    private final CaCertificatePlatformRepository platformLinkRepository;
    private final PasswordEncryptionUtil passwordEncryptionUtil;
    private final EffectiveRoleResolver effectiveRoleResolver;
    private final UserRepository userRepository;

    // ========== CA 证书 CRUD ==========

    @Transactional
    public CaCertificateDTO create(CaCertificateRequest request) {
        String storedPassword = passwordEncryptionUtil.encrypt(request.getCaPassword());
        CaCertificateEntity entity = CaCertificateEntity.builder()
                .caType(request.getCaType())
                .sealType(request.getSealType())
                .electronicAccount(request.getElectronicAccount())
                .caPassword(storedPassword)
                .issuer(request.getIssuer())
                .holderName(request.getHolderName())
                .expiryDate(request.getExpiryDate())
                .caPlatformUrl(request.getCaPlatformUrl())
                .custodianId(request.getCustodianId())
                .custodianName(request.getCustodianName())
                .borrowStatus("IN_STOCK")
                .status(computeStatus(request.getExpiryDate()))
                .remarks(request.getRemarks())
                .build();
        CaCertificateEntity saved = certificateRepository.save(entity);
        List<Long> platformIds = persistPlatformLinks(saved.getId(), request.getPlatformIds());
        return CaCertificateDTO.from(saved, platformIds);
    }

    @Transactional
    public CaCertificateDTO update(Long id, CaCertificateRequest request) {
        CaCertificateEntity entity = certificateRepository.findById(id)
                .orElseThrow(() -> new CaBusinessException("CA证书不存在: " + id));
        entity.setCaType(request.getCaType());
        entity.setSealType(request.getSealType());
        entity.setElectronicAccount(request.getElectronicAccount());
        if (request.getCaPassword() != null && !request.getCaPassword().isEmpty()) {
            String storedPassword = passwordEncryptionUtil.encrypt(request.getCaPassword());
            entity.setCaPassword(storedPassword);
        }
        entity.setIssuer(request.getIssuer());
        entity.setHolderName(request.getHolderName());
        entity.setExpiryDate(request.getExpiryDate());
        entity.setCaPlatformUrl(request.getCaPlatformUrl());
        entity.setCustodianId(request.getCustodianId());
        entity.setCustodianName(request.getCustodianName());
        entity.setStatus(computeStatus(request.getExpiryDate()));
        entity.setRemarks(request.getRemarks());
        CaCertificateEntity saved = certificateRepository.save(entity);
        List<Long> platformIds = persistPlatformLinks(saved.getId(), request.getPlatformIds());
        return CaCertificateDTO.from(saved, platformIds);
    }

    /**
     * CO-409: 下架 CA 证书，按保管员差异化校验.
     *
     * <p>授权矩阵（与前端操作项对齐）：
     * <ul>
     *   <li>管理员（admin/bidAdmin/bid-TeamLeader）→ 可下架任意 CA</li>
     *   <li>投标专员（bid-Team）→ 仅可下架自己保管的 CA（custodianId == currentUser.id）</li>
     *   <li>其他角色 → 拒绝</li>
     * </ul>
     *
     * <p>复刻 {@code PlatformAccountService.returnAccount} 的 Policy 范式（lessons §28）：
     * Controller 类级 @PreAuthorize 放宽后，细粒度校验下沉到 Service 层。
     * roleCode 统一走 EffectiveRoleResolver，不直调 User.getRoleCode()（CO-373）。
     */
    @Transactional
    public void deactivate(Long id, UserDetails userDetails) {
        User currentUser = resolveUser(userDetails);
        CaCertificateEntity entity = certificateRepository.findById(id)
                .orElseThrow(() -> new CaBusinessException("CA证书不存在: " + id));
        String roleCode = effectiveRoleResolver.resolveRoleCode(currentUser);
        if (!canDeactivate(roleCode, entity, currentUser)) {
            throw new AccessDeniedException("仅管理员或该 CA 保管员可下架");
        }
        entity.setStatus("INACTIVE");
        certificateRepository.save(entity);
    }

    /**
     * CO-409: 下架权限判定（纯函数，便于单测）.
     *
     * <p>特权角色放行；投标专员要求为该 CA 的保管员；其他角色不放行。
     * 对称于 {@code PlatformAccountViewerPolicy.canReturnAccount} 的授权语义。
     */
    private static boolean canDeactivate(String roleCode, CaCertificateEntity entity, User currentUser) {
        if (RoleProfileCatalog.GLOBAL_ACCESS_ROLES.contains(roleCode)) {
            return true;
        }
        if (RoleProfileCatalog.BID_SPECIALIST_CODE.equalsIgnoreCase(roleCode)) {
            return entity.getCustodianId() != null
                    && entity.getCustodianId().equals(currentUser.getId());
        }
        return false;
    }

    // CO-409: 复刻 CaBorrowService#resolveUser，Controller 传 UserDetails、Service 解析成 User 实体做 custodian 校验
    // （strict-module Controller 不能直接依赖 ..entity../..repository..，下沉到 Service 层合规）。
    private User resolveUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new AccessDeniedException("下架 CA 证书需要登录");
        }
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new AccessDeniedException("当前用户不存在: " + userDetails.getUsername()));
    }

    public CaCertificateDTO getById(Long id) {
        CaCertificateEntity entity = certificateRepository.findById(id)
                .orElseThrow(() -> new CaBusinessException("CA证书不存在: " + id));
        return CaCertificateDTO.from(entity, loadPlatformIds(id));
    }

    /**
     * Reveal the decrypted CA password. Intended for ADMIN / custodian flows
     * where the secret value must be displayed.
     */
    public CaCertificateDTO revealPassword(Long id) {
        CaCertificateEntity entity = certificateRepository.findById(id)
                .orElseThrow(() -> new CaBusinessException("CA证书不存在: " + id));
        String decrypted = passwordEncryptionUtil.decrypt(entity.getCaPassword());
        return CaCertificateDTO.from(entity, loadPlatformIds(id), true, decrypted);
    }

    private List<Long> persistPlatformLinks(Long caId, List<Long> platformIds) {
        if (platformIds == null) return loadPlatformIds(caId);
        platformLinkRepository.deleteByCaCertificateId(caId);
        List<Long> normalized = platformIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        for (Long pid : normalized) {
            platformLinkRepository.save(CaCertificatePlatformEntity.builder()
                    .caCertificateId(caId)
                    .platformAccountId(pid)
                    .build());
        }
        return normalized;
    }

    private List<Long> loadPlatformIds(Long caId) {
        return platformLinkRepository.findByCaCertificateId(caId).stream()
                .map(CaCertificatePlatformEntity::getPlatformAccountId)
                .collect(Collectors.toList());
    }

    public Page<CaCertificateDTO> list(String status, String borrowStatus, String keyword,
                                        String caType, String sealType, Pageable pageable) {
        Specification<CaCertificateEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("status"), "INACTIVE"));
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (borrowStatus != null && !borrowStatus.isEmpty()) {
                predicates.add(cb.equal(root.get("borrowStatus"), borrowStatus));
            }
            if (caType != null && !caType.isEmpty()) {
                predicates.add(cb.equal(root.get("caType"), caType));
            }
            if (sealType != null && !sealType.isEmpty()) {
                predicates.add(cb.equal(root.get("sealType"), sealType));
            }
            if (keyword != null && !keyword.isEmpty()) {
                String pattern = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("holderName"), pattern),
                        cb.like(root.get("issuer"), pattern),
                        cb.like(root.get("custodianName"), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return certificateRepository.findAll(spec, pageable)
                .map(entity -> CaCertificateDTO.from(entity, loadPlatformIds(entity.getId())));
    }

    public Map<String, Long> getOverview() {
        Map<String, Long> result = certificateRepository.getOverviewAggregated();
        if (result == null || result.get("total") == null) {
            return Map.of("total", 0L, "expiring", 0L, "expired", 0L, "borrowed", 0L);
        }
        return result;
    }

    // ========== 辅助 ==========

    private String computeStatus(LocalDate expiryDate) {
        if (expiryDate == null) return "ACTIVE";
        long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
        if (daysUntil < 0) return "EXPIRED";
        if (daysUntil <= 30) return "EXPIRING";
        return "ACTIVE";
    }
}
