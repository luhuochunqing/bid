package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.policy.JobRoleLookupResolver;
import com.xiyu.bid.integration.organization.domain.policy.JobRoleLookupResolver.ResolvedRole;
import com.xiyu.bid.integration.organization.domain.OrganizationSyncPolicy;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSyncPlan;
import com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto;
import com.xiyu.bid.security.domain.LoginRoleWhitelist;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationUserSyncWriter {
    /**
     * 锁定密码的 BCrypt 编码。OSS 同步用户不存储本地密码，使用此哈希确保
     * 本地密码验证永远失败，强制走 OSS 统一认证。
     */
    public static final String LOCKED_PASSWORD_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOHIhi4YhML26vP7Hk1UR93E1Vda8yI9W";

    private final UserRepository userRepository;
    private final RoleProfileRepository roleProfileRepository;
    private final OrganizationDepartmentRepository organizationDepartmentRepository;
    private final OrganizationIntegrationProperties properties;
    private final JobRoleLookupResolver jobRoleLookupResolver;
    private final OssRoleMenuPermissionAutoSync ossRoleMenuPermissionAutoSync;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CacheEvict(value = "users:enabled", key = "'all'")
    public Optional<User> upsert(String sourceApp, String eventKey, OrganizationUserSnapshot snapshot) {
        return upsert(sourceApp, eventKey, snapshot, Map.of());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CacheEvict(value = "users:enabled", key = "'all'")
    public Optional<User> upsert(
            String sourceApp,
            String eventKey,
            OrganizationUserSnapshot snapshot,
            Map<String, OssUserJobAndRoleDto> jobRoleLookupMap
    ) {
        validateRequiredContact(snapshot);
        Optional<User> existingUser = userRepository.findByExternalOrgSourceAppAndExternalOrgUserId(sourceApp, snapshot.externalUserId());
        // Fallback: 若按 externalOrgSourceApp+externalOrgUserId 未查到，但按 username 已存在
        // 通过登录自动创建的 OSS 用户（externalOrgSourceApp="oss-login"），应复用而非重复创建。
        // 背景：AuthService 本地无记录时 OssUserAutoCreator 自动创建标记为 "oss-login" 的 User，
        // 后续 Kafka 同步事件到达时应更新该记录而非新建。
        if (existingUser.isEmpty()) {
            existingUser = userRepository.findByUsername(snapshot.username())
                    .filter(User::isOssUser);
            if (existingUser.isPresent()) {
                log.info("Org sync fallback to username lookup: externalUserId={}, username={}, current sourceApp={}",
                        snapshot.externalUserId(), snapshot.username(),
                        existingUser.get().getExternalOrgSourceApp());
            }
        }
        User user = existingUser.orElseGet(User::new);

        OrganizationUserSnapshot enrichedSnapshot = enrichDepartmentName(sourceApp, snapshot);
        ResolvedRole resolvedRole = jobRoleLookupResolver.resolve(enrichedSnapshot, jobRoleLookupMap);
        String resolvedRoleCode = resolvedRole.roleCode();

        if (!LoginRoleWhitelist.isAllowed(resolvedRoleCode) && properties.isSkipUnmappedUsers()) {
            // 白名单模式（skipUnmappedUsers=true）：未匹配角色的用户不创建，已存在则刷新在职状态
            // 非白名单模式（skipUnmappedUsers=false）：继续创建无角色用户，登录时由 OssPermissionCache 决定角色
            handleUserWithoutResolvedRole(sourceApp, eventKey, snapshot, existingUser);
            return Optional.empty();
        }

        // allowAdminElevation=false：OSS 同步用户不应该被提升为 admin（admin 是本地超级管理员，和 OSS 无关）。
        // 历史上只有 person-to-role-mappings 白名单来源允许 admin elevation，白名单已删除。
        boolean allowAdminElevation = false;
        OrganizationUserSyncPlan plan = OrganizationSyncPolicy.planUserSync(
                snapshot,
                // SAFE: OSS 同步场景特有 — 同步时新用户的 DB roleCode 是上一次同步的快照值，
                // 不存在"OSS 缓存未命中"的场景（同步本身就是要更新 DB）。这里读取的是"待同步 DB 当前值"
                // 用于对比决策，不是权限判定。CO-373 治理范围外。
                user.getRoleCode(),
                normalizeSet(properties.getAdminRoleCodes()),
                normalizeSet(properties.getManagerRoleCodes()),
                resolvedRoleCode,
                allowAdminElevation
        );
        user.setUsername(plan.username());
        // spec 037 Review H1：username 三用（登录账号/工号/CRM salesNo）的防御性校验。
        // OSS 同步事件约定 username 字段为工号（如 "04503"），但若上游改推邮箱前缀或 AD 账号，
        // 三字段会被同一非工号值污染 → TenderAutoAssignmentService 按 employee_number 查询失败（CO-441 再现）
        // + generateToken 缺 salesNo → 标讯无法关联商机。不匹配时只 setUsername，跳过工号字段填充。
        if (looksLikeEmployeeNumber(plan.username())) {
            // CO-441: OSS 同步用户的工号同时写入 employee_number，保持与 username 一致。
            // 历史上只写 username，导致 TenderAutoAssignmentService.resolveManagerNameByEmployeeNumber
            // 按 employee_number 查询时返回 null，CRM 自动分配失败。V1126 迁移脚本回填历史数据。
            user.setEmployeeNumber(plan.username());
            // spec 037: OSS 工号即 CRM salesNo（已生产验证），填充后 generateToken 不再依赖 OSS token。
            // 历史上 users.crm_sales_no 全表 NULL，导致 PM 未登录时无法换 CRM JWT → 标讯无法关联商机。
            user.setCrmSalesNo(plan.username());
        } else {
            log.warn("OSS 同步事件 username 不像工号，跳过 employee_number/crm_sales_no 填充：username={}, externalUserId={}",
                    plan.username(), snapshot.externalUserId());
        }
        user.setPassword(user.getPassword() == null ? LOCKED_PASSWORD_HASH : user.getPassword());
        user.setEmail(plan.email());
        user.setFullName(plan.fullName());
        user.setPhone(plan.phone());
        user.setDepartmentCode(plan.departmentCode());
        user.setDepartmentName(plan.departmentName());
        user.setEnabled(plan.enabled());
        user.setExternalOrgUserId(snapshot.externalUserId());
        user.setExternalOrgSourceApp(sourceApp);
        user.setLastOrgEventKey(eventKey);
        user.setLastOrgSyncedAt(LocalDateTime.now());
        applyRole(user, plan.roleCode());
        User saved = userRepository.save(user);
        if (properties.getDirectory().isAutoSyncMenuPermissions()) {
            autoSyncMenuPermissions(saved);
        }
        return Optional.of(saved);
    }

    private void autoSyncMenuPermissions(User user) {
        RoleProfile role = user.getRoleProfile();
        if (role == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return;
        }
        try {
            ossRoleMenuPermissionAutoSync.mergeUserMenuPermissionsIntoRole(user.getUsername(), role);
        } catch (RuntimeException ex) {
            log.warn("自动同步用户 OSS 菜单权限失败: userId={}, roleCode={}, error={}",
                user.getId(), role.getCode(), ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CacheEvict(value = "users:enabled", key = "'all'")
    public void disableByExternalId(String sourceApp, String eventKey, String externalUserId) {
        userRepository.findByExternalOrgSourceAppAndExternalOrgUserId(sourceApp, externalUserId)
                .ifPresent(user -> {
                    user.setEnabled(false);
                    user.setLastOrgEventKey(eventKey);
                    user.setLastOrgSyncedAt(LocalDateTime.now());
                    userRepository.save(user);
                });
    }

    private OrganizationUserSnapshot enrichDepartmentName(String sourceApp, OrganizationUserSnapshot snapshot) {
        String deptName = snapshot.departmentName();
        if ((deptName == null || deptName.isBlank())
                && snapshot.departmentCode() != null && !snapshot.departmentCode().isBlank()) {
            deptName = organizationDepartmentRepository
                    .findBySourceAppAndExternalDeptId(sourceApp, snapshot.departmentCode())
                    .map(OrganizationDepartmentEntity::getDepartmentName)
                    .orElse(deptName);
            if (deptName != null && !deptName.isBlank()) {
                return new OrganizationUserSnapshot(
                        snapshot.externalUserId(),
                        snapshot.username(),
                        snapshot.fullName(),
                        snapshot.email(),
                        snapshot.phone(),
                        snapshot.departmentCode(),
                        deptName,
                        snapshot.jobId(),
                        snapshot.externalRoleCode(),
                        snapshot.enabled()
                );
            }
        }
        return snapshot;
    }

    private void validateRequiredContact(OrganizationUserSnapshot snapshot) {
        if (snapshot.email() == null || snapshot.email().isBlank()) {
            throw new IllegalArgumentException("组织架构用户邮箱不能为空");
        }
        if (snapshot.phone() == null || snapshot.phone().isBlank()) {
            throw new IllegalArgumentException("组织架构用户手机号不能为空");
        }
    }

    private void applyRole(User user, String roleCode) {
        RoleProfile roleProfile = roleProfileRepository.findByCodeIgnoreCase(roleCode).orElse(null);
        user.setRoleProfile(roleProfile);
        user.setRole(RoleProfileCatalog.legacyRoleForCode(roleProfile == null ? roleCode : roleProfile.getCode()));
    }

    /**
     * spec 037 Review H1：判断 username 是否像工号（OSS 同步约定为纯数字或数字为主的字符串）。
     * <p>规则：
     * <ul>
     *   <li>非空且长度 ≤ 20（工号一般不会超过 20 字符）</li>
     *   <li>至少包含一个数字</li>
     *   <li>不包含 {@code @}（排除邮箱）和 {@code .}（排除邮箱前缀如 john.doe）</li>
     * </ul>
     * <p>宽松设计：只要不含明显非工号特征就通过，避免误拒合法工号（如 E00123、04503）。
     */
    private static boolean looksLikeEmployeeNumber(String username) {
        if (username == null || username.isBlank() || username.length() > 20) {
            return false;
        }
        if (username.contains("@") || username.contains(".")) {
            return false;
        }
        // 至少包含一个数字（纯字母的 AD 账号如 wangx 不算工号）
        return username.chars().anyMatch(c -> c >= '0' && c <= '9');
    }

    private Set<String> normalizeSet(java.util.List<String> values) {
        return values.stream().map(value -> value.trim().toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 角色未匹配白名单的 OSS 用户处理：本地已存在则按 OSS 在职状态刷新 enabled 与基础信息
     * （不分配角色），不存在则跳过（不创建无角色记录）。
     * <p>enabled 反映 OSS 在职状态（由 {@link com.xiyu.bid.integration.organization.infrastructure.client.UserEnabledDetector} 判定），
     * 不再因"角色未匹配"强制禁用——登录由 {@code AuthService.requireOssRole} + {@code UserDetailsServiceImpl}
     * 的白名单校验独立拦截，与 enabled 解耦。
     */
    private void handleUserWithoutResolvedRole(String sourceApp, String eventKey, OrganizationUserSnapshot snapshot, Optional<User> existingUser) {
        existingUser.ifPresent(user -> {
            user.setEnabled(snapshot.enabled());
            user.setFullName(snapshot.fullName());
            user.setEmail(snapshot.email());
            user.setPhone(snapshot.phone());
            user.setDepartmentCode(snapshot.departmentCode());
            user.setDepartmentName(snapshot.departmentName());
            user.setLastOrgEventKey(eventKey);
            user.setLastOrgSyncedAt(LocalDateTime.now());
            userRepository.save(user);
        });
    }
}
