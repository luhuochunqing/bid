package com.xiyu.bid.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.xiyu.bid.common.util.PinyinUtils;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    @JsonIgnore
    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(length = 32)
    private String phone;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = true)
    private RoleProfile roleProfile;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "department_code", length = 100)
    private String departmentCode;

    @Column(name = "department_name", length = 100)
    private String departmentName;

    @Column(name = "employee_number", length = 32)
    private String employeeNumber;

    /** CRM 工号，用于按用户维度换取 CRM JWT token（CO-152）。null 时回退到全局共享 token。 */
    @Column(name = "crm_sales_no", length = 64)
    private String crmSalesNo;

    @Column(name = "full_name_pinyin", length = 255)
    private String fullNamePinyin;

    @Column(name = "employee_number_pinyin", length = 255)
    private String employeeNumberPinyin;

    @Column(name = "wecom_user_id", length = 64)
    private String wecomUserId;

    @Column(name = "external_org_user_id", length = 128)
    private String externalOrgUserId;

    @Column(name = "external_org_source_app", length = 100)
    private String externalOrgSourceApp;

    @Column(name = "last_org_event_key", length = 128)
    private String lastOrgEventKey;

    @Column(name = "last_org_synced_at")
    private LocalDateTime lastOrgSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (fullNamePinyin == null && fullName != null && !fullName.isBlank()) {
            fullNamePinyin = PinyinUtils.toPinyin(fullName);
        }
        if (employeeNumberPinyin == null && employeeNumber != null && !employeeNumber.isBlank()) {
            employeeNumberPinyin = PinyinUtils.toPinyin(employeeNumber);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        // Always refresh pinyin when fullName or employeeNumber changes (org sync, admin edit, etc.)
        if (fullName != null && !fullName.isBlank()) {
            fullNamePinyin = PinyinUtils.toPinyin(fullName);
        }
        if (employeeNumber != null && !employeeNumber.isBlank()) {
            employeeNumberPinyin = PinyinUtils.toPinyin(employeeNumber);
        }
    }

    public enum Role {
        ADMIN,
        MANAGER;

        public static Role fromCode(String code) {
            if (code == null || code.isBlank()) {
                return MANAGER;
            }
            return switch (code.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "admin" -> ADMIN;
                default -> MANAGER;
            };
        }
    }

    /**
     * 返回用户的角色码。
     *
     * <p><b>⚠️ DEPRECATED — 业务权限判定请勿直接调用本方法。</b>
     * <p>本方法在 OSS 同步用户（{@code role_id=NULL}）时 fallback 返回
     * {@code "manager"}，会导致业务权限判定误判。这是 CO-361 / CO-373 的根因之一。
     *
     * <p>业务权限判定（出现在 {@code if} / 白名单 / 黑名单 / {@code equals} 的场景）
     * <b>必须</b>走下列统一入口之一：
     * <ul>
     *   <li>{@link com.xiyu.bid.security.EffectiveRoleResolver#resolveRoleCode(User)}
     *       — 服务层权限校验首选入口，OSS-cache-aware + fail-closed</li>
     *   <li>{@link com.xiyu.bid.admin.service.DataScopeConfigService#getRoleCode(User)}
     *       — 数据范围配置场景（与 {@code getAccessProfile} 配套使用）</li>
     * </ul>
     *
     * <p>保留直调的合法场景（需在调用点加 {@code // SAFE: <理由>} 注释）：
     * <ol>
     *   <li>登录响应装配（{@code AuthResponse}）— 仅作为 SSO/登录契约字段</li>
     *   <li>MDC 日志上下文（{@code TraceFilter}）— 仅用于链路追踪</li>
     *   <li>{@code DataScopeConfigService.isLocalSystemAccount} 内部判定 —
     *       通过 {@code externalOrgSourceApp} + {@code ADMIN_CODE} 双重条件确认本地账户</li>
     *   <li>{@code DataScopeConfigService.getRoleCode} 自身实现 — admin 本地账户 cache miss 时回退</li>
     *   <li>{@code EffectiveRoleResolver} 内部读取 entityRoleCode 用于缓存对比决策</li>
     * </ol>
     *
     * <p>新增直调会被 {@code scripts/check-rolecode-direct-calls.mjs} pre-push 检查拦截。
     *
     * @see com.xiyu.bid.security.EffectiveRoleResolver
     * @see com.xiyu.bid.admin.service.DataScopeConfigService#getRoleCode(User)
     * @see <a href="https://linear.app/ericforai/issue/CO-373">CO-373</a>
     */
    @Deprecated
    public String getRoleCode() {
        if (roleProfile != null && roleProfile.getCode() != null && !roleProfile.getCode().isBlank()) {
            // 保留 roleCode 原始大小写：OSS 角色码大小写敏感（如 bidAdmin、bid-TeamLeader）
            // RoleProfileCatalog 用 case-insensitive TreeMap 查找，但 User.roleCode 字段存原值
            return roleProfile.getCode().trim();
        }
        return role == null ? "manager" : role.name().toLowerCase(java.util.Locale.ROOT);
    }

    public String getRoleName() {
        if (roleProfile != null && roleProfile.getName() != null && !roleProfile.getName().isBlank()) {
            return roleProfile.getName();
        }
        return switch (getRole()) {
            case ADMIN -> "管理员";
            case MANAGER -> "经理";
        };
    }

    /**
     * Returns the employee number if present, falling back to username
     * when the employee number column is blank (e.g. org-synced users whose
     * visible job number was historically stored in the username field).
     * <p>
     * This is the single source of truth for display-oriented employee-number
     * resolution used by user search, candidate lists, and similar features.
     */
    public String getDisplayEmployeeNumber() {
        if (employeeNumber != null && !employeeNumber.isBlank()) {
            return employeeNumber;
        }
        return username;
    }
}
