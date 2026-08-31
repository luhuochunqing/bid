package com.xiyu.bid.project.integration;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.support.NoOpPasswordEncryptionTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 项目搜索框 placeholder"搜索项目名称、招标主体"的后端契约回归：
 * GET /api/projects?name= 关键词必须同时匹配项目名、客户名与招标主体
 * （招标主体经 projects.tender_id 关联 tenders.purchaser_name 回填到 DTO.ownerUnit）。
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class ProjectSearchByOwnerUnitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TenderRepository tenderRepository;

    private User admin;

    @BeforeEach
    void setUp() {
        tenderRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(User.builder()
                .username("search-admin")
                .password("XiyuDemo!2026")
                .email("search-admin@example.com")
                .fullName("搜索管理员")
                .role(User.Role.ADMIN)
                .enabled(true)
                .departmentName("投标管理部")
                .build());
    }

    private Project saveProject(String name, Long tenderId) {
        return projectRepository.save(Project.builder()
                .name(name)
                .tenderId(tenderId)
                .status(Project.Status.BIDDING)
                .managerId(admin.getId())
                .build());
    }

    private Tender saveTender(String purchaserName) {
        return tenderRepository.save(Tender.builder()
                .title(purchaserName + "标讯")
                .sourcePlatform("人工录入")
                .status(Tender.Status.TRACKING)
                .purchaserName(purchaserName)
                .budget(java.math.BigDecimal.valueOf(100000))
                .build());
    }

    @Test
    @WithMockUser(username = "search-admin", roles = {"ADMIN"})
    void nameKeywordShouldMatchTenderOwnerUnit() throws Exception {
        Tender gridTender = saveTender("国家电网新疆电力公司");
        Tender bureauTender = saveTender("市大数据局");
        saveProject("数据中心建设项目", gridTender.getId());
        saveProject("云计算平台采购", bureauTender.getId());

        mockMvc.perform(get("/api/projects").param("name", "国家电网"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].name").value(hasItem("数据中心建设项目")))
                .andExpect(jsonPath("$.data[?(@.name=='云计算平台采购')]").isEmpty());
    }

    @Test
    @WithMockUser(username = "search-admin", roles = {"ADMIN"})
    void nameKeywordShouldStillMatchProjectName() throws Exception {
        Tender unrelated = saveTender("某采购方");
        saveProject("国家电网配套项目", unrelated.getId());
        saveProject("无关项目", unrelated.getId());

        mockMvc.perform(get("/api/projects").param("name", "国家电网"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].name").value(hasItem("国家电网配套项目")))
                .andExpect(jsonPath("$.data[?(@.name=='无关项目')]").isEmpty());
    }

    @Test
    @WithMockUser(username = "search-admin", roles = {"ADMIN"})
    void nameKeywordMatchAgainstOwnerUnitShouldIgnoreCase() throws Exception {
        // 锚定 containsIgnoreCase 语义：小写关键词命中大写招标主体（前端 .includes 大小写敏感，两侧语义不同）
        Tender tender = saveTender("State Grid Xinjiang Power");
        saveProject("外文主体项目", tender.getId());

        mockMvc.perform(get("/api/projects").param("name", "state grid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].name").value(hasItem("外文主体项目")));
    }
}
