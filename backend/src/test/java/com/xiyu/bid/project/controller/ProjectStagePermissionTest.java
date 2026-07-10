package com.xiyu.bid.project.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2.4-2.7 项目阶段 @PreAuthorize 注解契约测试（飞书《投标项目·权限矩阵》2.4-2.7）。
 *
 * <p>锁定评标/结果/结项各阶段端点的角色准入。
 *
 * <p>文档角色对照（关键）：
 * <ul>
 *   <li>"投标负责人/投标辅助人员" = 投标专员（bid-Team）的项目子身份，对应 BID_TEAM</li>
 *   <li>"投标项目负责人" = bid-projectLeader（立项提交人），对应 BID_PROJECTLEADER</li>
 *   <li>两者是不同角色，不可混淆</li>
 * </ul>
 */
class ProjectStagePermissionTest {

    // ====================================================================
    // 2.4 评标中（ProjectEvaluationController）
    // 文档：管理员/组长/投标负责人/辅助（= admin/bidAdmin/bid-TeamLeader + BID_TEAM）
    // ====================================================================

    @Test
    @DisplayName("2.4 评标 advance：ADMIN/BID_TEAMLEADER/BIDADMIN/BID_TEAM（文档：含投标负责人/辅助=BID_TEAM）")
    void evaluationAdvance_preAuthorize_matchesDoc() {
        String value = findMethod(ProjectEvaluationController.class, "advance")
                .getAnnotation(PreAuthorize.class).value();
        assertThat(value).isEqualTo("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')");
    }

    // ====================================================================
    // 2.5 结果确认（ProjectResultController）
    // 文档：管理员/组长/投标负责人/辅助（含 BID_PROJECTLEADER 因项目负责人参与结果）
    // ====================================================================

    @Test
    @DisplayName("2.5 结果提交 register：含 BID_PROJECTLEADER/BID_TEAM（文档：项目负责人+投标负责人/辅助）")
    void resultSubmit_preAuthorize_matchesDoc() {
        String value = findMethod(ProjectResultController.class, "register")
                .getAnnotation(PreAuthorize.class).value();
        assertThat(value).contains("'BID_PROJECTLEADER'", "'BID_TEAM'", "'BID_TEAMLEADER'");
    }

    // ====================================================================
    // 2.7 项目结项（ProjectClosureController）
    // ====================================================================

    @Test
    @DisplayName("2.7 发起结项 submit：ADMIN/BID_PROJECTLEADER（文档：投标项目负责人发起）")
    void closureSubmit_preAuthorize_projectLeaderOnly() {
        String value = findMethod(ProjectClosureController.class, "submit")
                .getAnnotation(PreAuthorize.class).value();
        assertThat(value).isEqualTo("hasAnyRole('ADMIN', 'BID_PROJECTLEADER')");
    }

    @Test
    @DisplayName("2.7 结项审核 approve：ADMIN/BID_TEAMLEADER/BIDADMIN/BID_TEAM（文档：管理员/组长+投标负责人/辅助）")
    void closureApprove_preAuthorize_matchesDoc() {
        String value = findMethod(ProjectClosureController.class, "approve")
                .getAnnotation(PreAuthorize.class).value();
        assertThat(value).isEqualTo("hasAnyAuthority('closure.review', 'ROLE_ADMIN', 'ROLE_BID_TEAMLEADER', 'ROLE_BIDADMIN', 'ROLE_BID_TEAM')");
    }

    /** 按方法名查找（跨 Controller）。 */
    private Method findMethod(Class<?> controllerClass, String name) {
        for (Method m : controllerClass.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new AssertionError(controllerClass.getSimpleName() + "." + name + " 未找到");
    }
}
