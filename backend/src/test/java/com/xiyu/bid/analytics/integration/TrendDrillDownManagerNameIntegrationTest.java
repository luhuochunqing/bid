// Input: MockMvc + 种子项目/标讯/立项明细（pid 负责人空串 vs 非空）
// Output: 下钻明细 managerName/techLeaderName 回退口径与项目列表/详情页（populateFromTender）一致的回归锁
// Pos: Test/数据分析下钻集成测试（H2）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.integration;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
@DisplayName("M1 下钻明细负责人口径：空串必须回退标讯（与 populateFromTender 的回退口径一致）")
class TrendDrillDownManagerNameIntegrationTest extends AbstractAuditOperationalAnalyticsIntegrationTest {

    @Autowired
    private ProjectInitiationDetailsRepository detailsRepository;

    @Test
    @WithMockUser(username = "audit-admin", authorities = {"ROLE_ADMIN", "dashboard", "all", "analytics-dashboard"})
    @DisplayName("pid 负责人为空串时回退标讯 project_manager_name / bidding_person_name")
    void drillDownShouldFallbackToTenderWhenPidNamesAreEmptyString() throws Exception {
        Tender tender = tenderRepository.findById(project.getTenderId()).orElseThrow();
        tender.setProjectManagerName("标讯负责人");
        tender.setBiddingPersonName("标讯投标人");
        tenderRepository.save(tender);

        detailsRepository.save(ProjectInitiationDetails.builder()
                .projectId(project.getId())
                .projectLeaderName("")
                .biddingLeaderName("")
                .build());

        mockMvc.perform(get("/api/analytics/trends/drilldown")
                        .param("dimension", "projectStatus")
                        .param("axisValue", "BIDDING")
                        .param("seriesName", "投标数"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].managerName").value("标讯负责人"))
                .andExpect(jsonPath("$.data.items[0].techLeaderName").value("标讯投标人"));
    }

    @Test
    @WithMockUser(username = "audit-admin", authorities = {"ROLE_ADMIN", "dashboard", "all", "analytics-dashboard"})
    @DisplayName("pid 与标讯负责人都为空时不回退为 raw 空串（JSON 省略字段，前端渲染 '-'）")
    void drillDownShouldNotReturnRawEmptyStringWhenBothSourcesAreEmpty() throws Exception {
        detailsRepository.save(ProjectInitiationDetails.builder()
                .projectId(project.getId())
                .projectLeaderName("")
                .biddingLeaderName("")
                .build());

        mockMvc.perform(get("/api/analytics/trends/drilldown")
                        .param("dimension", "projectStatus")
                        .param("axisValue", "BIDDING")
                        .param("seriesName", "投标数"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].managerName").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].techLeaderName").doesNotExist());
    }

    @Test
    @WithMockUser(username = "audit-admin", authorities = {"ROLE_ADMIN", "dashboard", "all", "analytics-dashboard"})
    @DisplayName("pid 负责人非空时优先取 pid，不回退标讯")
    void drillDownShouldKeepPidNamesWhenNonBlank() throws Exception {
        Tender tender = tenderRepository.findById(project.getTenderId()).orElseThrow();
        tender.setProjectManagerName("标讯负责人");
        tender.setBiddingPersonName("标讯投标人");
        tenderRepository.save(tender);

        detailsRepository.save(ProjectInitiationDetails.builder()
                .projectId(project.getId())
                .projectLeaderName("李四")
                .biddingLeaderName("王五")
                .build());

        mockMvc.perform(get("/api/analytics/trends/drilldown")
                        .param("dimension", "projectStatus")
                        .param("axisValue", "BIDDING")
                        .param("seriesName", "投标数"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].managerName").value("李四"))
                .andExpect(jsonPath("$.data.items[0].techLeaderName").value("王五"));
    }
}
