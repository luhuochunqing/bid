package com.xiyu.bid.integration.organization.domain.policy;

import com.xiyu.bid.entity.RoleProfileCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OssRoleEligibility} 单元测试。
 * <p>
 * 覆盖 lessons-learned.md §78 覃超颖 bidding/60 403 案例的根因：
 * OSS 返回的多系统 sysRoleList 中包含其他系统（Home/CRM/SCM 等）的 admin 角色码，
 * 不应被识别为我们系统的 admin 写入 Redis 缓存。
 * <p>
 * 修复方案：OSS 解析路径仅识别 7 个 bid-* 角色码（{@link OssRoleEligibility#OSS_ELIGIBLE_CODES}），
 * 对 admin 返回 null（fail-closed），与本地路径 {@link RoleProfileCatalog#canonicalCode(String)} 形成对比。
 */
class OssRoleEligibilityTest {

    @Test
    @DisplayName("§78: canonicalOssCode 对 admin 返回 null（OSS 返回的 admin 是其他系统的）")
    void canonicalOssCode_admin_returnsNull() {
        // 核心根因修复：OSS 返回的 admin 是其他系统（Home/CRM/SCM）的，不应被识别为我们系统的 admin
        assertThat(OssRoleEligibility.canonicalOssCode("admin")).isNull();
    }

    @Test
    @DisplayName("§78: canonicalOssCode 对 ADMIN/Admin/AdMiN 大小写不敏感均返回 null")
    void canonicalOssCode_admin_caseInsensitive_returnsNull() {
        assertThat(OssRoleEligibility.canonicalOssCode("ADMIN")).isNull();
        assertThat(OssRoleEligibility.canonicalOssCode("Admin")).isNull();
        assertThat(OssRoleEligibility.canonicalOssCode("AdMiN")).isNull();
        assertThat(OssRoleEligibility.canonicalOssCode("  admin  ")).isNull();
    }

    @Test
    @DisplayName("§78: canonicalOssCode 识别 7 个 bid-* 角色码并返回规范码")
    void canonicalOssCode_sevenBidRoles_returnCanonicalCode() {
        // 7 个 bid-* 角色码必须被识别（OSS 解析路径唯一真相来源）
        assertThat(OssRoleEligibility.canonicalOssCode("/bidAdmin")).isEqualTo("/bidAdmin");
        assertThat(OssRoleEligibility.canonicalOssCode("bid-TeamLeader")).isEqualTo("bid-TeamLeader");
        assertThat(OssRoleEligibility.canonicalOssCode("bid-SystemAdmin")).isEqualTo("bid-SystemAdmin");
        assertThat(OssRoleEligibility.canonicalOssCode("bid-Team")).isEqualTo("bid-Team");
        assertThat(OssRoleEligibility.canonicalOssCode("bid-projectLeader")).isEqualTo("bid-projectLeader");
        assertThat(OssRoleEligibility.canonicalOssCode("bid-administration")).isEqualTo("bid-administration");
        assertThat(OssRoleEligibility.canonicalOssCode("bid-otherDept")).isEqualTo("bid-otherDept");
    }

    @Test
    @DisplayName("§78: canonicalOssCode 对大小写混合的 bid-* 角色码返回规范码（与 RoleProfileCatalog.canonicalCode 一致）")
    void canonicalOssCode_mixedCase_returnsCanonicalCode() {
        // 大小写不敏感查找，返回 RoleProfileCatalog 中注册的规范码
        assertThat(OssRoleEligibility.canonicalOssCode("/BIDADMIN")).isEqualTo("/bidAdmin");
        assertThat(OssRoleEligibility.canonicalOssCode("BID-TEAMLEADER")).isEqualTo("bid-TeamLeader");
        assertThat(OssRoleEligibility.canonicalOssCode("bid-systemadmin")).isEqualTo("bid-SystemAdmin");
        assertThat(OssRoleEligibility.canonicalOssCode("  bid-Team  ")).isEqualTo("bid-Team");
    }

    @Test
    @DisplayName("§78: canonicalOssCode 对未注册角色码返回 null（fail-closed）")
    void canonicalOssCode_unregisteredCode_returnsNull() {
        // OSS 返回的其他系统角色码（SE/PE/CRM_ADMIN/HomeReadonly 等）不应被识别
        assertThat(OssRoleEligibility.canonicalOssCode("SE")).isNull();
        assertThat(OssRoleEligibility.canonicalOssCode("PE")).isNull();
        assertThat(OssRoleEligibility.canonicalOssCode("CRM_ADMIN")).isNull();
        assertThat(OssRoleEligibility.canonicalOssCode("HomeReadonly")).isNull();
        assertThat(OssRoleEligibility.canonicalOssCode("manager")).isNull();
        assertThat(OssRoleEligibility.canonicalOssCode("unknown_role")).isNull();
    }

    @Test
    @DisplayName("§78: OSS_ELIGIBLE_CODES 包含 7 个 bid-* 角色码且不含 admin")
    void ossEligibleCodes_containsSevenBidRoles_excludesAdmin() {
        Set<String> codes = OssRoleEligibility.OSS_ELIGIBLE_CODES;

        // 必须包含 7 个 bid-* 角色码
        assertThat(codes).containsExactlyInAnyOrder(
                "/bidAdmin",
                "bid-TeamLeader",
                "bid-SystemAdmin",
                "bid-Team",
                "bid-projectLeader",
                "bid-administration",
                "bid-otherDept"
        );
        // 必须不含 admin（本地独有的超级管理员，与 OSS 无关）
        assertThat(codes).doesNotContain("admin");
        assertThat(codes).doesNotContain("ADMIN");
        assertThat(codes).doesNotContain("Admin");
    }
}
