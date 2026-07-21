package com.xiyu.bid.integration.organization.domain.policy;

import com.xiyu.bid.entity.RoleProfileCatalog;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * OSS 角色识别辅助类——区分"属于投标系统的角色"与"OSS 平台其他系统的角色"。
 * <p>
 * 背景（lessons-learned.md §78）：OSS 是多系统共用的角色管理平台（Home/CRM/SCM/投标等），
 * 返回的 sysRoleList 混合多系统角色。其中属于本系统的只有 7 个 bid-* 角色码。
 * {@code admin} 是本地独有的超级管理员（{@link RoleProfileCatalog#ADMIN_CODE}），与 OSS 无关——
 * OSS 返回的 admin 是其他系统的 admin，不应被识别为我们系统的 admin 写入缓存。
 * <p>
 * 详见 lessons-learned.md §78（覃超颖 bidding/60 403 案例根因）。
 *
 * @see RoleProfileCatalog#canonicalCode(String)
 */
public final class OssRoleEligibility {

    private OssRoleEligibility() {
    }

    /**
     * OSS 角色解析路径可识别的角色码集合（7 个 bid-* 角色码，不含 admin）。
     * <p>使用 case-insensitive TreeSet 支持大小写不敏感查找。
     */
    public static final Set<String> OSS_ELIGIBLE_CODES;
    static {
        TreeSet<String> ossSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        ossSet.add(RoleProfileCatalog.BID_ADMIN_CODE);
        ossSet.add(RoleProfileCatalog.BID_LEAD_CODE);
        ossSet.add(RoleProfileCatalog.BID_SYSTEM_ADMIN_CODE);
        ossSet.add(RoleProfileCatalog.BID_SPECIALIST_CODE);
        ossSet.add(RoleProfileCatalog.SALES_CODE);
        ossSet.add(RoleProfileCatalog.ADMIN_STAFF_CODE);
        ossSet.add(RoleProfileCatalog.BID_OTHER_DEPT_CODE);
        OSS_ELIGIBLE_CODES = Collections.unmodifiableSet(ossSet);
    }

    /**
     * 返回 roleCode 在 OSS 解析路径下的规范形式（排除 admin）。
     * <p>
     * 与 {@link RoleProfileCatalog#canonicalCode(String)} 的关键区别：本方法对 {@code admin} 返回 null。
     * <p>
     * OSS 返回的 sysRoleList 中可能包含其他系统（Home/CRM/SCM 等）的 admin 角色码——
     * 这些 admin 不属于本系统，不应被识别为我们系统的 admin 写入 Redis 缓存，
     * 否则会引发 403（@PreAuthorize 列表不含 BID_SYSTEMADMIN 时）或越权（hasAdminAccess 短路）。
     * <p>
     * 仅以下 7 个 bid-* 角色码会被识别并返回规范码（见 {@link #OSS_ELIGIBLE_CODES}）：
     * /bidAdmin、bid-TeamLeader、bid-SystemAdmin、bid-Team、bid-projectLeader、
     * bid-administration、bid-otherDept。
     * <p>
     * 未注册或为 admin 时返回 null（fail-closed），调用方应继续尝试其他映射或返回 null。
     *
     * @param roleCode 待归一化的角色码
     * @return 规范角色码；admin 或未注册返回 null
     */
    public static String canonicalOssCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        String trimmed = roleCode.trim();
        // admin 是本地独有的超级管理员，OSS 返回的 admin 是其他系统的，不应识别
        if (trimmed.equalsIgnoreCase(RoleProfileCatalog.ADMIN_CODE)) {
            return null;
        }
        return RoleProfileCatalog.canonicalCode(trimmed);
    }
}
