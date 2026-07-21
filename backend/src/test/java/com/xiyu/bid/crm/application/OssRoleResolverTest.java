package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.domain.policy.OssMenuPermissionMapper;
import com.xiyu.bid.integration.organization.infrastructure.mapper.PositionToRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link OssRoleResolver} 根因行为测试。
 * <p>
 * 覆盖 04569 登录失败 bug 的根因：OssRoleResolver 遍历 sysRoleList 时
 * 只用 roleName 不用 roleCode，导致 OSS 端已分配 bid-administration 角色
 * 但 roleName "投标-行政专员" 不在映射表的用户被误拒。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OssRoleResolverTest {

    @Mock private OssMenuPermissionMapper ossMenuPermissionMapper;
    @Mock private PositionToRoleMapper positionToRoleMapper;
    @Mock private OrganizationIntegrationProperties orgProperties;

    private OssRoleResolver resolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        resolver = new OssRoleResolver(ossMenuPermissionMapper, positionToRoleMapper, orgProperties);
        // 白名单已删除，OssRoleResolver 不再读取 personToRoleMappings
        // PositionToRoleMapper 对"投标-行政专员"和"行政专员"都返回 null（不在岗位映射表）
        when(positionToRoleMapper.map(anyString())).thenReturn(null);
        // 06234 回归：非投标 roleName"销售主管"若走 positionToRoleMapper 会命中 bid-projectLeader
        when(positionToRoleMapper.map("销售主管")).thenReturn("bid-projectLeader");
    }

    @Test
    @DisplayName("根因复现：sysRoleList 有 roleCode=bid-administration 但 roleName 不在映射表时，通过 roleCode 解析成功")
    void resolveRoleCodeFromJobList_usesRoleCodeWhenRoleNameNotInMapping() {
        // 04569 的真实数据：roleName="投标-行政专员"（不在 OSS_ROLE_NAME_TO_INTERNAL），roleCode="bid-administration"
        CrmJobListResponse.JobInfo jobInfo = new CrmJobListResponse.JobInfo();
        jobInfo.setJobName("行政专员");
        jobInfo.setJobNumber("04569");
        jobInfo.setUsername("沈樱娇");
        CrmJobListResponse.SysRole role = new CrmJobListResponse.SysRole();
        role.setRoleName("投标-行政专员");
        role.setRoleCode("bid-administration");
        role.setStatus("1");
        role.setDel(false);
        jobInfo.setSysRoleList(List.of(role));

        CrmJobListResponse jobList = new CrmJobListResponse();
        jobList.setData(java.util.Map.of("04569", jobInfo));

        String result = resolver.resolveRoleCodeFromJobList(jobList, "04569", "04569");

        // 修复前：返回 null（roleName "投标-行政专员" 不在映射表）
        // 修复后：通过 roleCode "bid-administration" 解析成功
        assertThat(result).isEqualTo("bid-administration");
    }

    @Test
    @DisplayName("roleCode 为 null 时 fallback 到 roleName 映射")
    void resolveRoleCodeFromJobList_fallsBackToRoleNameWhenRoleCodeNull() {
        CrmJobListResponse.JobInfo jobInfo = new CrmJobListResponse.JobInfo();
        jobInfo.setJobName("投标项目负责人");
        jobInfo.setJobNumber("03668");
        CrmJobListResponse.SysRole role = new CrmJobListResponse.SysRole();
        role.setRoleName("投标管理员");
        role.setRoleCode(null); // roleCode 为空
        role.setStatus("1");
        role.setDel(false);
        jobInfo.setSysRoleList(List.of(role));

        CrmJobListResponse jobList = new CrmJobListResponse();
        jobList.setData(java.util.Map.of("03668", jobInfo));

        String result = resolver.resolveRoleCodeFromJobList(jobList, "03668", "03668");

        // roleCode 为空 → fallback 到 roleName "投标管理员" → BID_ADMIN_CODE
        assertThat(result).isEqualTo("/bidAdmin");
    }

    @Test
    @DisplayName("OSS 系统角色码（如 SE/PE）不会被误认为 bid 角色码")
    void resolveRoleCodeFromJobList_ignoresOssSystemRoleCodes() {
        CrmJobListResponse.JobInfo jobInfo = new CrmJobListResponse.JobInfo();
        jobInfo.setJobName("采购工程师");
        jobInfo.setJobNumber("04569");
        CrmJobListResponse.SysRole seRole = new CrmJobListResponse.SysRole();
        seRole.setRoleName("SE");
        seRole.setRoleCode("SE"); // OSS 系统角色码，不是 bid 角色码
        seRole.setStatus("1");
        seRole.setDel(false);
        CrmJobListResponse.SysRole peRole = new CrmJobListResponse.SysRole();
        peRole.setRoleName("PE");
        peRole.setRoleCode("PE");
        peRole.setStatus("1");
        peRole.setDel(false);
        jobInfo.setSysRoleList(List.of(seRole, peRole));

        CrmJobListResponse jobList = new CrmJobListResponse();
        jobList.setData(java.util.Map.of("04569", jobInfo));

        String result = resolver.resolveRoleCodeFromJobList(jobList, "04569", "04569");

        // SE/PE 不在 RoleProfileCatalog 中 → 返回 null（fail-closed）
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolveRoleCodeFromEmployeeInfo：从 getUserInfo roleList 解析 bid-administration 成功")
    void resolveRoleCodeFromEmployeeInfo_resolvesBidRoleCode() throws Exception {
        // 04569 的 getUserInfo 返回（含 roleList，其中有 bid-administration）
        String json = "{\"username\":\"04569\",\"nickName\":\"沈樱娇\",\"jobName\":\"行政专员\","
                + "\"roleList\":["
                + "{\"roleName\":\"默认角色\",\"roleCode\":null,\"status\":\"1\",\"del\":false},"
                + "{\"roleName\":\"SE\",\"roleCode\":\"SE\",\"status\":\"1\",\"del\":false},"
                + "{\"roleName\":\"投标-行政专员\",\"roleCode\":\"bid-administration\",\"status\":\"1\",\"del\":false},"
                + "{\"roleName\":\"普通用户\",\"roleCode\":null,\"status\":\"1\",\"del\":true}"
                + "]}";
        JsonNode employeeInfo = objectMapper.readTree(json);

        String result = resolver.resolveRoleCodeFromEmployeeInfo(employeeInfo, "04569");

        assertThat(result).isEqualTo("bid-administration");
    }

    @Test
    @DisplayName("resolveRoleCodeFromEmployeeInfo：roleList 只有 OSS 系统角色码时返回 null")
    void resolveRoleCodeFromEmployeeInfo_returnsNullForNonBidRoles() throws Exception {
        String json = "{\"username\":\"04569\",\"roleList\":["
                + "{\"roleName\":\"SE\",\"roleCode\":\"SE\",\"status\":\"1\",\"del\":false},"
                + "{\"roleName\":\"PE\",\"roleCode\":\"PE\",\"status\":\"1\",\"del\":false}"
                + "]}";
        JsonNode employeeInfo = objectMapper.readTree(json);

        String result = resolver.resolveRoleCodeFromEmployeeInfo(employeeInfo, "04569");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolveRoleCodeFromEmployeeInfo：跳过 status=0 和 del=true 的角色")
    void resolveRoleCodeFromEmployeeInfo_skipsInactiveRoles() throws Exception {
        String json = "{\"username\":\"04569\",\"roleList\":["
                + "{\"roleName\":\"投标-行政专员\",\"roleCode\":\"bid-administration\",\"status\":\"0\",\"del\":false},"
                + "{\"roleName\":\"投标-行政专员\",\"roleCode\":\"bid-administration\",\"status\":\"1\",\"del\":true}"
                + "]}";
        JsonNode employeeInfo = objectMapper.readTree(json);

        String result = resolver.resolveRoleCodeFromEmployeeInfo(employeeInfo, "04569");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("06234 回归：sysRoleList 中非投标 roleName 排在 bid 角色前时不应误匹配")
    void resolveRoleCodeFromJobList_skipsNonBidRoleNameBeforeBidRoleCode() {
        // 06234 的真实数据：id=27 "销售主管"(roleCode="53") 排在 id=199 "/bidAdmin" 之前
        CrmJobListResponse.SysRole salesSupervisor = new CrmJobListResponse.SysRole();
        salesSupervisor.setRoleName("销售主管");
        salesSupervisor.setRoleCode("53"); // 非 bid 角色码
        salesSupervisor.setStatus("1");
        salesSupervisor.setDel(false);

        CrmJobListResponse.SysRole bidAdmin = new CrmJobListResponse.SysRole();
        bidAdmin.setRoleName("客户开发管理员");
        bidAdmin.setRoleCode("/bidAdmin");
        bidAdmin.setStatus("1");
        bidAdmin.setDel(false);

        CrmJobListResponse.JobInfo jobInfo = new CrmJobListResponse.JobInfo();
        jobInfo.setJobName("高级投标经理");
        jobInfo.setJobNumber("06234");
        jobInfo.setSysRoleList(List.of(salesSupervisor, bidAdmin));

        CrmJobListResponse jobList = new CrmJobListResponse();
        jobList.setData(java.util.Map.of("06234", jobInfo));

        String result = resolver.resolveRoleCodeFromJobList(jobList, "06234", "06234");

        // 修复前："销售主管" 匹配 positionToRoleMapper 返回 bid-projectLeader
        // 修复后：跳过非投标 roleCode/roleName，正确解析到 /bidAdmin
        assertThat(result).isEqualTo("/bidAdmin");
    }

    @Test
    @DisplayName("resolveRoleCodeFromEmployeeInfo：employeeInfo 为 null 时返回 null")
    void resolveRoleCodeFromEmployeeInfo_returnsNullForNullInput() {
        assertThat(resolver.resolveRoleCodeFromEmployeeInfo(null, "04569")).isNull();
    }

    @Test
    @DisplayName("04569 完整场景：jobList 解析失败 → employeeInfo fallback 解析 bid-administration")
    void fullScenario_04569_jobListFailsEmployeeInfoFallbackSucceeds() throws Exception {
        // jobList: sysRoleList 为空（模拟 getUserJobList 返回的 sysRoleList 无 bid 角色的情况）
        CrmJobListResponse.JobInfo jobInfo = new CrmJobListResponse.JobInfo();
        jobInfo.setJobName("行政专员");
        jobInfo.setJobNumber("04569");
        jobInfo.setSysRoleList(List.of()); // 空 sysRoleList
        CrmJobListResponse jobList = new CrmJobListResponse();
        jobList.setData(java.util.Map.of("04569", jobInfo));

        // jobList 解析 → null（jobName "行政专员" 不在映射表，sysRoleList 为空）
        String fromJobList = resolver.resolveRoleCodeFromJobList(jobList, "04569", "04569");
        assertThat(fromJobList).isNull();

        // employeeInfo fallback → bid-administration
        String json = "{\"username\":\"04569\",\"roleList\":["
                + "{\"roleName\":\"投标-行政专员\",\"roleCode\":\"bid-administration\",\"status\":\"1\",\"del\":false}"
                + "]}";
        JsonNode employeeInfo = objectMapper.readTree(json);
        String fromEmployeeInfo = resolver.resolveRoleCodeFromEmployeeInfo(employeeInfo, "04569");
        assertThat(fromEmployeeInfo).isEqualTo("bid-administration");
    }

    // ── lessons-learned.md §78：覃超颖（bid-SystemAdmin）403 案例回归 ──

    @Test
    @DisplayName("§78 覃超颖回归：sysRoleList 中其他系统 admin 排在 bid-SystemAdmin 前，应跳过 admin 解析到 bid-SystemAdmin")
    void resolveRoleCodeFromJobList_skipsOtherSystemAdmin_resolvesBidSystemAdmin_qinchaoYing() {
        // 覃超颖（OSS username=09118，投标系统管理员）真实场景：
        // OSS 返回的 sysRoleList 中可能包含其他系统（Home/CRM/SCM 等）的 admin 角色码
        // 修复前：admin 被错误识别为我们系统的 admin，写入 Redis 缓存（oss:perm:09118）
        //        → 颁发 ROLE_ADMIN → @PreAuthorize 列表不含 BID_SYSTEMADMIN 时 403
        // 修复后：canonicalOssCode 对 admin 返回 null，跳过 admin，继续找到 bid-SystemAdmin
        CrmJobListResponse.SysRole otherSystemAdmin = new CrmJobListResponse.SysRole();
        otherSystemAdmin.setRoleName("Home 系统管理员"); // 其他系统的角色名
        otherSystemAdmin.setRoleCode("admin"); // 其他系统的 admin 角色码
        otherSystemAdmin.setStatus("1");
        otherSystemAdmin.setDel(false);

        CrmJobListResponse.SysRole bidSystemAdmin = new CrmJobListResponse.SysRole();
        bidSystemAdmin.setRoleName("投标系统管理员");
        bidSystemAdmin.setRoleCode("bid-SystemAdmin");
        bidSystemAdmin.setStatus("1");
        bidSystemAdmin.setDel(false);

        CrmJobListResponse.JobInfo jobInfo = new CrmJobListResponse.JobInfo();
        jobInfo.setJobNumber("09118");
        jobInfo.setSysRoleList(List.of(otherSystemAdmin, bidSystemAdmin));

        CrmJobListResponse jobList = new CrmJobListResponse();
        jobList.setData(java.util.Map.of("09118", jobInfo));

        String result = resolver.resolveRoleCodeFromJobList(jobList, "09118", "09118");

        // 修复后：跳过其他系统的 admin，正确解析到 bid-SystemAdmin
        assertThat(result).isEqualTo("bid-SystemAdmin");
    }

    @Test
    @DisplayName("§78: sysRoleList 只含其他系统 admin（无 bid-* 角色）时返回 null（fail-closed）")
    void resolveRoleCodeFromJobList_onlyOtherSystemAdmin_returnsNull() {
        // OSS sysRoleList 只含其他系统的 admin，没有 bid-* 角色码
        // 修复前：错误识别为 admin，写入缓存
        // 修复后：admin 返回 null，无其他 bid-* 角色，整体返回 null（fail-closed）
        CrmJobListResponse.SysRole otherSystemAdmin = new CrmJobListResponse.SysRole();
        otherSystemAdmin.setRoleName("CRM 系统管理员");
        otherSystemAdmin.setRoleCode("admin");
        otherSystemAdmin.setStatus("1");
        otherSystemAdmin.setDel(false);

        CrmJobListResponse.JobInfo jobInfo = new CrmJobListResponse.JobInfo();
        jobInfo.setJobNumber("99999");
        jobInfo.setSysRoleList(List.of(otherSystemAdmin));

        CrmJobListResponse jobList = new CrmJobListResponse();
        jobList.setData(java.util.Map.of("99999", jobInfo));

        String result = resolver.resolveRoleCodeFromJobList(jobList, "99999", "99999");

        // 修复后：fail-closed，不识别其他系统的 admin，返回 null
        assertThat(result).isNull();
    }
}
