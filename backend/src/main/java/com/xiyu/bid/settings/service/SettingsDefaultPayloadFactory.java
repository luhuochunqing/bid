package com.xiyu.bid.settings.service;

import com.xiyu.bid.settings.dto.SettingsResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class SettingsDefaultPayloadFactory {

    SettingsResponse createDefaultSettings() {
        return SettingsResponse.builder()
                .systemConfig(defaultSystemConfig())
                .roles(defaultRoles())
                .deptDataScope(defaultDeptScopes())
                .projectGroupScope(defaultProjectScopes())
                .integrationConfig(defaultIntegrationConfig())
                .flowMappings(defaultFlowMappings())
                .apiList(defaultApiSettings())
                .build();
    }

    private SettingsResponse.SystemConfig defaultSystemConfig() {
        return SettingsResponse.SystemConfig.builder()
                .sysName("西域数智化投标管理平台")
                .depositWarnDays(7)
                .qualWarnDays(30)
                .enableAI(true)
                .build();
    }

    private SettingsResponse.IntegrationConfig defaultIntegrationConfig() {
        return SettingsResponse.IntegrationConfig.builder()
                .orgEnabled(false)
                .orgSystem("dingtalk")
                .orgAppKey("")
                .orgAppSecret("")
                .oaEnabled(false)
                .oaUrl("")
                .ssoEnabled(false)
                .callbackUrl("")
                .apiKey("sk_xiyu_bid_server_default")
                .aiBaseUrl("")
                .aiModel("")
                .ipWhitelist("")
                .orgDirectoryBaseUrl("")
                .orgDirectoryAuthClientId("")
                .orgDirectoryAuthClientSecret("")
                .orgDirectoryUserDetailPath("/users/{userId}")
                .orgDirectoryDeptDetailPath("/departments/{deptId}")
                .orgDirectoryUserWindowPath("")
                .orgDirectoryDeptWindowPath("")
                .orgEventSdkEnabled(false)
                .orgEventConsumerGroup("")
                .orgEventServerRegisterUrl("")
                .build();
    }

    private List<SettingsResponse.RoleSetting> defaultRoles() {
        return List.of(
                SettingsResponse.RoleSetting.builder().code("admin").name("管理员").description("系统管理员，拥有所有权限").userCount(1).dataScope("all").menuPermissions(List.of("all")).allowedProjects(List.of("pg1", "pg2", "pg3")).allowedDepts(List.of("dept1", "dept2", "dept3", "dept4", "dept5")).build(),
                SettingsResponse.RoleSetting.builder().code("manager").name("经理").description("部门经理，可查看报表和审批").userCount(1).dataScope("dept").menuPermissions(List.of("dashboard", "project", "ai-center", "analytics")).allowedProjects(List.of("pg1", "pg2", "pg3")).allowedDepts(List.of("dept1", "dept5")).build(),
                SettingsResponse.RoleSetting.builder().code("bid-projectLeader").name("销售").description("销售人员，可创建项目和查看数据").userCount(5).dataScope("self").menuPermissions(List.of("dashboard", "project", "bidding", "ai-center")).allowedProjects(List.of("pg1")).allowedDepts(List.of("dept1", "dept2")).build(),
                SettingsResponse.RoleSetting.builder().code("tech").name("技术人员").description("技术人员，可参与项目任务").userCount(10).dataScope("self").menuPermissions(List.of("dashboard", "project", "ai-center")).allowedProjects(List.of("pg2")).allowedDepts(List.of("dept3")).build()
        );
    }

    private List<SettingsResponse.DeptDataScopeSetting> defaultDeptScopes() {
        return List.of(
                SettingsResponse.DeptDataScopeSetting.builder().deptName("华南销售部").dataScope("dept").canViewOtherDepts(false).allowedDepts(List.of("dept1")).build(),
                SettingsResponse.DeptDataScopeSetting.builder().deptName("华东销售部").dataScope("dept").canViewOtherDepts(false).allowedDepts(List.of("dept2")).build(),
                SettingsResponse.DeptDataScopeSetting.builder().deptName("技术部").dataScope("dept").canViewOtherDepts(false).allowedDepts(List.of("dept3")).build(),
                SettingsResponse.DeptDataScopeSetting.builder().deptName("投标管理部").dataScope("all").canViewOtherDepts(true).allowedDepts(List.of("dept1", "dept2", "dept3")).build()
        );
    }

    private List<SettingsResponse.ProjectGroupScopeSetting> defaultProjectScopes() {
        return List.of(
                SettingsResponse.ProjectGroupScopeSetting.builder().groupName("央企项目组").manager("张经理").memberCount(5).visibility("members").allowedRoles(List.of("admin", "manager", "bid-projectLeader")).build(),
                SettingsResponse.ProjectGroupScopeSetting.builder().groupName("政府项目组").manager("李经理").memberCount(3).visibility("members").allowedRoles(List.of("admin", "manager", "bid-projectLeader")).build(),
                SettingsResponse.ProjectGroupScopeSetting.builder().groupName("军队项目组").manager("王经理").memberCount(2).visibility("manager").allowedRoles(List.of("admin", "manager")).build()
        );
    }

    private List<SettingsResponse.FlowMappingSetting> defaultFlowMappings() {
        return List.of(
                SettingsResponse.FlowMappingSetting.builder().systemFlow("项目立项审批").oaFlow("project_start").oaFlowCode("FLOW_001").oaFlowName("项目立项流程").description("新建项目时的审批流程").build(),
                SettingsResponse.FlowMappingSetting.builder().systemFlow("投标审批").oaFlow("bidding_approval").oaFlowCode("FLOW_002").oaFlowName("投标审批流程").description("投标前的审批流程").build(),
                SettingsResponse.FlowMappingSetting.builder().systemFlow("合同审批").oaFlow("contract_approval").oaFlowCode("FLOW_003").oaFlowName("合同签署流程").description("合同签署的审批流程").build(),
                SettingsResponse.FlowMappingSetting.builder().systemFlow("用印申请").oaFlow("seal_application").oaFlowCode("FLOW_004").oaFlowName("用印申请流程").description("公章使用申请流程").build()
        );
    }

    private List<SettingsResponse.ApiSetting> defaultApiSettings() {
        return List.of(
                SettingsResponse.ApiSetting.builder().name("获取项目列表").path("/api/projects").method("GET").description("查询投标项目列表").status("enabled").enabled(true).build(),
                SettingsResponse.ApiSetting.builder().name("创建项目").path("/api/projects").method("POST").description("创建新的投标项目").status("enabled").enabled(true).build(),
                SettingsResponse.ApiSetting.builder().name("获取项目详情").path("/api/projects/{id}").method("GET").description("获取指定项目详情").status("enabled").enabled(true).build(),
                SettingsResponse.ApiSetting.builder().name("更新项目").path("/api/projects/{id}").method("PUT").description("更新项目信息").status("enabled").enabled(true).build(),
                SettingsResponse.ApiSetting.builder().name("数据看板总览").path("/api/analytics/overview").method("GET").description("获取核心看板指标").status("enabled").enabled(true).build()
        );
    }
}
