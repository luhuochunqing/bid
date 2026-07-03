package com.xiyu.bid.project.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2.2 项目立项 @PreAuthorize 注解契约测试（飞书《投标项目·权限矩阵》2.2）。
 *
 * <p>锁定立项各端点的角色准入，防止未来重构放行/收紧错误角色。
 *
 * <p>文档核心约束：
 * <ul>
 *   <li>提交/更新/AI 评估：仅项目负责人（BID_PROJECTLEADER）+ 管理员</li>
 *   <li>审核（approve/reject）：仅管理员/组长（ADMIN/BID_TEAMLEADER/BIDADMIN）</li>
 * </ul>
 */
class ProjectInitiationPermissionTest {

    @Test
    @DisplayName("2.2 提交立项 submit：ADMIN/BID_PROJECTLEADER（文档：仅项目负责人）")
    void submit_preAuthorize_allowsProjectLeaderOnly() {
        PreAuthorize annotation = findMethod("submit").getAnnotation(PreAuthorize.class);
        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN','BID_PROJECTLEADER')");
    }

    @Test
    @DisplayName("2.2 审核通过 approve：ADMIN/BID_TEAMLEADER/BIDADMIN（文档：管理员/组长，不含项目负责人/专员）")
    void approve_preAuthorize_adminBidLeadBidAdminOnly() {
        String value = findMethod("approve").getAnnotation(PreAuthorize.class).value();
        assertThat(value).isEqualTo("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')");
        // 用完整 token 检查（避免 BIDADMIN 误含 BID_TEAM 子串）
        assertThat(value).doesNotContain("'BID_PROJECTLEADER'", "'BID_TEAM'", "'SALES'");
    }

    @Test
    @DisplayName("2.2 审核驳回 reject：ADMIN/BID_TEAMLEADER/BIDADMIN（文档：管理员/组长）")
    void reject_preAuthorize_adminBidLeadBidAdminOnly() {
        String value = findMethod("reject").getAnnotation(PreAuthorize.class).value();
        assertThat(value).isEqualTo("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')");
        assertThat(value).doesNotContain("'BID_PROJECTLEADER'", "'BID_TEAM'", "'SALES'");
    }

    @Test
    @DisplayName("2.2 AI 风险评估 assessRisk：ADMIN/BID_PROJECTLEADER（文档：项目负责人触发+查看）")
    void assessRisk_preAuthorize_allowsProjectLeader() {
        PreAuthorize annotation = findMethod("assessRisk").getAnnotation(PreAuthorize.class);
        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'BID_PROJECTLEADER')");
    }

    /** 按方法名查找（避免参数类型不匹配问题）。 */
    private Method findMethod(String name) {
        for (Method m : ProjectInitiationController.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new AssertionError("方法 " + name + " 未找到");
    }
}
