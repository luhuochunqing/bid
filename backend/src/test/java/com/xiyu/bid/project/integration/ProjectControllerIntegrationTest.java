package com.xiyu.bid.project.integration;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class ProjectControllerIntegrationTest extends AbstractProjectControllerIntegrationTest {

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getAllProjects_ShouldSerializeTeamMembersAndReturnList() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.name=='真实项目列表回归')].teamMembers[0]").value(hasItem(staffUser.getId().intValue())))
                .andExpect(jsonPath("$.data[?(@.name=='真实项目列表回归')].teamMembers[1]").value(hasItem(602)));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void createProject_ShouldPersistSourceMetadata() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "来源线索项目",
                                  "tenderId": 201,
                                  "status": "INITIATED",
                                  "managerId": 501,
                                  "teamMembers": [501],
                                  "startDate": "2026-03-18T09:00:00",
                                  "endDate": "2026-03-28T18:00:00",
                                  "sourceModule": "customer-opportunity-center",
                                  "sourceCustomerId": "CUST-001",
                                  "sourceCustomer": "华东某集团",
                                  "sourceOpportunityId": "OPP-001",
                                  "sourceReasoningSummary": "根据客户采购节奏建议提前立项"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sourceModule").value("customer-opportunity-center"))
                .andExpect(jsonPath("$.data.sourceCustomerId").value("CUST-001"))
                .andExpect(jsonPath("$.data.sourceCustomer").value("华东某集团"))
                .andExpect(jsonPath("$.data.sourceOpportunityId").value("OPP-001"))
                .andExpect(jsonPath("$.data.sourceReasoningSummary").value("根据客户采购节奏建议提前立项"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void createProject_ShouldPersistAllBusinessFields() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "全字段业务项目",
                                  "tenderId": 301,
                                  "status": "INITIATED",
                                  "managerId": 501,
                                  "teamMembers": [501],
                                  "startDate": "2026-04-01T09:00:00",
                                  "endDate": "2026-05-15T18:00:00",
                                  "customer": "西部某能源集团",
                                  "customerType": "央国企客户",
                                  "budget": 12500000.50,
                                  "industry": "能源",
                                  "region": "新疆乌鲁木齐",
                                  "platform": "外部标讯聚合平台",
                                  "deadline": "2026-05-10",
                                  "description": "项目背景: 风电场二期建设",
                                  "remark": "甲方要求增项响应",
                                  "tagsJson": "[\\"风电\\",\\"重点\\"]"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.customer").value("西部某能源集团"))
                .andExpect(jsonPath("$.data.customerType").value("央国企客户"))
                .andExpect(jsonPath("$.data.budget").value(12500000.50))
                .andExpect(jsonPath("$.data.industry").value("能源"))
                .andExpect(jsonPath("$.data.region").value("新疆乌鲁木齐"))
                .andExpect(jsonPath("$.data.platform").value("外部标讯聚合平台"))
                .andExpect(jsonPath("$.data.deadline").value("2026-05-10"))
                .andExpect(jsonPath("$.data.description").value("项目背景: 风电场二期建设"))
                .andExpect(jsonPath("$.data.remark").value("甲方要求增项响应"))
                .andExpect(jsonPath("$.data.tagsJson").value("[\"风电\",\"重点\"]"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void updateProject_ShouldUpdateBusinessFields() throws Exception {
        Project existing = projectRepository.save(Project.builder()
                .name("待更新项目")
                .tenderId(401L)
                .status(Project.Status.INITIATED)
                .managerId(managerUser.getId())
                .teamMembers(List.of(managerUser.getId()))
                .startDate(LocalDateTime.of(2026, 4, 1, 9, 0))
                .endDate(LocalDateTime.of(2026, 5, 1, 18, 0))
                .customer("初始客户")
                .customerType("民营客户")
                .industry("制造")
                .build());

        mockMvc.perform(put("/api/projects/{id}", existing.getId())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "已更新项目",
                                  "tenderId": 401,
                                  "managerId": %d,
                                  "teamMembers": [%d],
                                  "startDate": "2026-04-01T09:00:00",
                                  "endDate": "2026-05-30T18:00:00",
                                  "customer": "更新后客户",
                                  "customerType": "政府客户",
                                  "budget": 8800000.00,
                                  "industry": "智慧城市",
                                  "region": "北京",
                                  "platform": "央采平台",
                                  "deadline": "2026-05-25",
                                  "description": "更新后描述",
                                  "remark": "更新后备注",
                                  "tagsJson": "[\\"智慧\\",\\"城市\\"]"
                                }
                                """.formatted(managerUser.getId(), managerUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("已更新项目"))
                .andExpect(jsonPath("$.data.customer").value("更新后客户"))
                .andExpect(jsonPath("$.data.customerType").value("政府客户"))
                .andExpect(jsonPath("$.data.budget").value(8800000.00))
                .andExpect(jsonPath("$.data.industry").value("智慧城市"))
                .andExpect(jsonPath("$.data.region").value("北京"))
                .andExpect(jsonPath("$.data.platform").value("央采平台"))
                .andExpect(jsonPath("$.data.deadline").value("2026-05-25"))
                .andExpect(jsonPath("$.data.description").value("更新后描述"))
                .andExpect(jsonPath("$.data.remark").value("更新后备注"))
                .andExpect(jsonPath("$.data.tagsJson").value("[\"智慧\",\"城市\"]"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getAllProjects_ShouldRejectNegativePage() throws Exception {
        mockMvc.perform(get("/api/projects").param("page", "-1").param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getAllProjects_ShouldRejectOversizedSize() throws Exception {
        mockMvc.perform(get("/api/projects").param("size", "99999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getAllProjects_ShouldRejectMaxIntegerPageWithoutOverflow() throws Exception {
        // Regression: page * size used to overflow int → 500. Now must fail @Max validation → 400.
        mockMvc.perform(get("/api/projects").param("page", "2147483647").param("size", "200"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

}
