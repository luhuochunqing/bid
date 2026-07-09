package com.xiyu.bid.resources.service;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.audit.event.EntityUpdatedEvent;
import com.xiyu.bid.resources.core.CaFieldDiffCalculator;
import com.xiyu.bid.resources.dto.CaCertificateDTO;
import com.xiyu.bid.resources.dto.CaCertificateRequest;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
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
import org.springframework.context.ApplicationEventPublisher;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class CaCertificateService {

    private final CaCertificateRepository certificateRepository;
    private final PasswordEncryptionUtil passwordEncryptionUtil;
    private final EffectiveRoleResolver effectiveRoleResolver;
    private final UserRepository userRepository;
    private final CustodianEmployeeNumberResolver custodianEmployeeNumberResolver;
    /** CO-515: 事件发布器，用于发布 CA 编辑事件（audit 模块监听写日志，避免 Service 直接注入 IAuditLogService） */
    private final ApplicationEventPublisher eventPublisher;

    // ========== CA 证书 CRUD ==========

    @Transactional
    public CaCertificateDTO create(CaCertificateRequest request) {
        // CO-566: CA 密码改为非必填（实体CA/电子CA均不强制密码）
        String rawPassword = request.getCaPassword();
        String storedPassword = (rawPassword == null || rawPassword.isBlank())
                ? null : passwordEncryptionUtil.encrypt(rawPassword);
        String normalizedSealType = SealTypeNormalizer.normalize(request.getSealType());
        String custodianEmployeeNumber = custodianEmployeeNumberResolver.fetchEmployeeNumber(request.getCustodianId());
        CaCertificateEntity entity = CaCertificateEntity.builder()
                .caType(request.getCaType())
                .sealType(normalizedSealType)
                .electronicAccount(request.getElectronicAccount())
                .caPassword(storedPassword)
                .issuer(request.getIssuer())
                .holderName(request.getHolderName())
                .expiryDate(request.getExpiryDate())
                .caPlatformUrl(request.getCaPlatformUrl())
                .relatedPlatforms(request.getRelatedPlatforms())
                .custodianId(request.getCustodianId())
                .custodianName(request.getCustodianName())
                .borrowStatus("IN_STOCK")
                .status(computeStatus(request.getExpiryDate()))
                .remarks(request.getRemarks())
                .build();
        CaCertificateEntity saved = certificateRepository.save(entity);
        return CaCertificateDTO.from(saved, false, null, custodianEmployeeNumber);
    }

    @Transactional
    public CaCertificateDTO update(Long id, CaCertificateRequest request) {
        CaCertificateEntity entity = certificateRepository.findById(id)
                .orElseThrow(() -> new CaBusinessException("CA证书不存在: " + id));
        // CO-515: 更新前快照（用于审计日志 diff 变更字段）
        CaCertificateEntity beforeSnapshot = snapshotForDiff(entity);
        entity.setCaType(request.getCaType());
        entity.setSealType(SealTypeNormalizer.normalize(request.getSealType()));
        entity.setElectronicAccount(request.getElectronicAccount());
        // CO-566: CA 密码非必填；留空表示不修改密码（保留原值）
        if (request.getCaPassword() != null && !request.getCaPassword().isEmpty()) {
            String storedPassword = passwordEncryptionUtil.encrypt(request.getCaPassword());
            entity.setCaPassword(storedPassword);
        }
        entity.setIssuer(request.getIssuer());
        entity.setHolderName(request.getHolderName());
        entity.setExpiryDate(request.getExpiryDate());
        entity.setCaPlatformUrl(request.getCaPlatformUrl());
        entity.setRelatedPlatforms(request.getRelatedPlatforms());
        entity.setCustodianId(request.getCustodianId());
        entity.setCustodianName(request.getCustodianName());
        entity.setStatus(computeStatus(request.getExpiryDate()));
        entity.setRemarks(request.getRemarks());
        CaCertificateEntity saved = certificateRepository.save(entity);
        // CO-451: 从 User 表获取保管员工号
        String custodianEmployeeNumber = custodianEmployeeNumberResolver.fetchEmployeeNumber(request.getCustodianId());

        // CO-515: 计算 diff 并发布事件（audit 模块监听写日志，
        // 避免 Service 直接注入 IAuditLogService 违反 RULE 12）
        List<String> changes = CaFieldDiffCalculator.diff(beforeSnapshot, saved);
        if (!changes.isEmpty()) {
            String summary = CaFieldDiffCalculator.formatSummary(changes);
            eventPublisher.publishEvent(new EntityUpdatedEvent(
                    saved.getId(), "CaCertificate", "UPDATE", summary));
        }

        return CaCertificateDTO.from(saved, false, null, custodianEmployeeNumber);
    }

    /**
     * CO-515: 构造变更前快照（仅复制 diff 关心的字段，避免 JPA 脏检查干扰）。
     */
    private CaCertificateEntity snapshotForDiff(CaCertificateEntity source) {
        return CaCertificateEntity.builder()
                .caType(source.getCaType())
                .sealType(source.getSealType())
                .electronicAccount(source.getElectronicAccount())
                .caPassword(source.getCaPassword())
                .issuer(source.getIssuer())
                .holderName(source.getHolderName())
                .expiryDate(source.getExpiryDate())
                .caPlatformUrl(source.getCaPlatformUrl())
                .relatedPlatforms(source.getRelatedPlatforms())
                .custodianId(source.getCustodianId())
                .custodianName(source.getCustodianName())
                .remarks(source.getRemarks())
                .build();
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
        // CO-477: 读时刷新 status（INACTIVE 下架状态不被覆盖），避免持久化 status 陈旧
        refreshStatusInMemory(entity);
        // CO-451: 从 User 表获取保管员工号
        String custodianEmployeeNumber = custodianEmployeeNumberResolver.fetchEmployeeNumber(entity.getCustodianId());
        return CaCertificateDTO.from(entity, false, null, custodianEmployeeNumber);
    }

    /**
     * Reveal the decrypted CA password.
     * 权限：投标管理员（ADMIN/MANAGER）、投标组长（bid-TeamLeader），
     * 或 CA 保管员（custodianId == 当前用户）。
     */
    public CaCertificateDTO revealPassword(Long id, UserDetails currentUser) {
        CaCertificateEntity entity = certificateRepository.findById(id)
                .orElseThrow(() -> new CaBusinessException("CA证书不存在: " + id));
        User user = resolveUser(currentUser);
        String roleCode = effectiveRoleResolver.resolveRoleCode(user);
        boolean isManager = RoleProfileCatalog.GLOBAL_ACCESS_ROLES.contains(roleCode);
        boolean isCustodian = entity.getCustodianId() != null
                && entity.getCustodianId().equals(user.getId());
        if (!isManager && !isCustodian) {
            throw new AccessDeniedException("无权查看此 CA 证书的密码");
        }
        String decrypted = passwordEncryptionUtil.decrypt(entity.getCaPassword());
        // CO-451: 从 User 表获取保管员工号
        String custodianEmployeeNumber = custodianEmployeeNumberResolver.fetchEmployeeNumber(entity.getCustodianId());
        return CaCertificateDTO.from(entity, true, decrypted, custodianEmployeeNumber);
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
                predicates.add(cb.like(root.get("sealType"), "%" + sealType + "%"));
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
        Page<CaCertificateEntity> entityPage = certificateRepository.findAll(spec, pageable);
        // CO-477: 读时刷新 status（INACTIVE 下架状态不被覆盖），避免持久化 status 陈旧
        entityPage.forEach(this::refreshStatusInMemory);
        // CO-451: 批量获取保管员工号
        Map<Long, String> employeeNumberMap = custodianEmployeeNumberResolver.batchFetchEmployeeNumbers(
                entityPage.stream().map(CaCertificateEntity::getCustodianId).toList()
        );
        return entityPage.map(entity -> CaCertificateDTO.from(
                entity, false, null,
                employeeNumberMap.get(entity.getCustodianId())
        ));
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

    /**
     * CO-477: 读时按 expiryDate 实时刷新内存中的 status.
     * <p>下架状态（INACTIVE）是管理员显式操作，不应被到期计算覆盖。
     * 其他状态（ACTIVE/EXPIRING/EXPIRED）按到期日实时重算，避免持久化字段陈旧
     * （例：创建时为 EXPIRING，过到期日后仍显示 EXPIRING）。
     * <p>仅修改内存 entity，不持久化；持久化由 CaExpiryScanService 定时回写。
     */
    private void refreshStatusInMemory(CaCertificateEntity entity) {
        if (entity == null) return;
        if ("INACTIVE".equals(entity.getStatus())) return;
        entity.setStatus(computeStatus(entity.getExpiryDate()));
    }
}
