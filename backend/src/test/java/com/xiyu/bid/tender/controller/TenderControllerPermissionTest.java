package com.xiyu.bid.tender.controller;

import com.xiyu.bid.tender.dto.TenderRequest;
import com.xiyu.bid.tender.dto.TenderTransferRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TenderController @PreAuthorize 注解契约测试。
 *
 * <p>2.2 标讯录入（飞书《标讯中心·权限矩阵》）：
 * 锁定各录入端点的角色准入，特别是"批量导入/下载模板不含项目负责人"。
 */
class TenderControllerPermissionTest {

    @Test
    @DisplayName("2.2 手动录入 createTender：5 个业务角色 + 管理员（文档：含项目负责人）")
    void createTender_allowsSalesStaffToSubmitTenderInformation() throws NoSuchMethodException {
        PreAuthorize annotation = TenderController.class
                .getMethod("createTender", TenderRequest.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM', 'SALES')");
    }

    @Test
    @DisplayName("2.2 批量导入 importTenders：不含项目负责人（文档：批量导入排除项目负责人）")
    void importTenders_excludesProjectLeader() throws NoSuchMethodException {
        PreAuthorize annotation = TenderController.class
                .getMethod("importTenders", org.springframework.web.multipart.MultipartFile.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class);

        String value = annotation.value();
        assertThat(value).isEqualTo("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')");
        // 显式断言不含项目负责人（BID_PROJECTLEADER 和 SALES）
        assertThat(value).doesNotContain("BID_PROJECTLEADER", "SALES");
    }

    @Test
    @DisplayName("2.2 下载模板 downloadImportTemplate：不含项目负责人")
    void downloadImportTemplate_excludesProjectLeader() throws NoSuchMethodException {
        PreAuthorize annotation = TenderController.class
                .getMethod("downloadImportTemplate")
                .getAnnotation(PreAuthorize.class);

        String value = annotation.value();
        assertThat(value).isEqualTo("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')");
        assertThat(value).doesNotContain("BID_PROJECTLEADER", "SALES");
    }

    @Test
    void updateTender_allowsAdminAndBidTeamRoles() throws NoSuchMethodException {
        PreAuthorize annotation = TenderController.class
                .getMethod("updateTender", Long.class, TenderRequest.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'SALES')");
    }

    @Test
    void deleteTender_allowsAdminAndBidTeamRoles() throws NoSuchMethodException {
        PreAuthorize annotation = TenderController.class
                .getMethod("deleteTender", Long.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'SALES')");
    }

    @Test
    void transferTender_allowsAdminBidLeadAndBidAdminOnly() throws NoSuchMethodException {
        PreAuthorize annotation = TenderTransferController.class
                .getMethod("transferTender", Long.class, TenderTransferRequest.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')");
    }
}
