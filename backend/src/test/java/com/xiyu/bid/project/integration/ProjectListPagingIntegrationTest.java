package com.xiyu.bid.project.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 项目列表服务端分页的契约回归。
 *
 * <p>被测改动把"只为取 10 行却富化全表"改成"排序 / 分页 / 权限下推 SQL，只富化当页"，所以这里要钉住
 * 四件事：① 分页既不丢行也不重行，且页内顺序确定；② 快路径与全量路径对同一输入返回**逐位相同**的
 * 列表；③ 契约不变——不传 {@code size} 时仍返回全量；④ 筛选语义不变——空白值视作未传、
 * 大小写仍然敏感（这两条是"S05 评审"抓出的两处等价性反例，见 Entry Gate §5）。</p>
 *
 * <p><b>所有断言都用有序列表。</b>顺序不是实现细节：{@code created_at} 并列时页的划分完全由兜底键
 * 决定，而两条路径的兜底键来源不同（旧路径是 {@code findAll()} 的物理序，新路径是显式 {@code id ASC}），
 * 所以"顺序等价"必须被断言，且夹具要**显式固定** {@code created_at}，不能让断言依赖时钟精度
 * （{@code Project.onCreate} 用 {@code LocalDateTime.now()} 无条件覆写 {@code createdAt}，
 * 同一微秒内的两行会并列）。</p>
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@Import(NoOpPasswordEncryptionTestConfig.class)
class ProjectListPagingIntegrationTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TenderRepository tenderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private User admin;

    /** 所有项目共用一个标讯：projects.tender_id 是 NOT NULL，且富化要靠它回填 ownerUnit 等字段。 */
    private Tender tender;

    @BeforeEach
    void setUp() {
        tenderRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(User.builder()
                .username("paging-admin")
                .password("XiyuDemo!2026")
                .email("paging-admin@example.com")
                .fullName("分页管理员")
                .role(User.Role.ADMIN)
                .enabled(true)
                .departmentName("投标管理部")
                .build());

        tender = tenderRepository.save(Tender.builder()
                .title("分页用例标讯")
                .sourcePlatform("人工录入")
                .status(Tender.Status.TRACKING)
                .purchaserName("某采购方")
                .budget(java.math.BigDecimal.valueOf(100000))
                .build());
    }

    private User saveUser(String username, User.Role role) {
        return userRepository.save(User.builder()
                .username(username)
                .password("XiyuDemo!2026")
                .email(username + "@example.com")
                .fullName(username)
                .role(role)
                .enabled(true)
                .build());
    }

    private Project saveProject(String name, String stage, Long managerId) {
        return projectRepository.save(Project.builder()
                .name(name)
                .tenderId(tender.getId())
                .stage(stage)
                .status(Project.Status.BIDDING)
                .managerId(managerId)
                .build());
    }

    /**
     * 显式固定 {@code created_at}，让顺序断言不依赖插入时刻的时钟精度。
     *
     * <p>走 JDBC 而不是实体回调：{@code Project.onCreate} 会用 {@code now()} 无条件覆写该列，
     * 实体侧没有任何办法设成指定的过去时刻。</p>
     */
    private void pinCreatedAt(Long projectId, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE projects SET created_at = ? WHERE id = ?", createdAt, projectId);
    }

    /**
     * 发一次 {@code GET /api/projects}，按**响应顺序**返回该页的项目名。
     *
     * <p>刻意返回 {@code List} 而不是 {@code Set}：页内顺序是契约的一部分（见类注释）。</p>
     */
    private List<String> requestNames(String... params) throws Exception {
        var request = get("/api/projects");
        for (int i = 0; i + 1 < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = objectMapper.readTree(body).path("data");
        List<String> names = new ArrayList<>();
        data.forEach(node -> names.add(node.path("name").asText()));
        return names;
    }

    @Test
    @WithMockUser(username = "paging-admin", roles = {"ADMIN"})
    void pagesShouldPartitionTheWholeListWithoutLosingOrRepeatingRows() throws Exception {
        // 7 行的 created_at 依次递增 ⇒ 期望顺序是"最新在前"，且不存在并列，断言完全确定。
        for (int i = 1; i <= 7; i++) {
            Project p = saveProject("项目-" + i, "INITIATED", admin.getId());
            pinCreatedAt(p.getId(), BASE_TIME.plusMinutes(i));
        }
        List<String> expected = List.of(
                "项目-7", "项目-6", "项目-5", "项目-4", "项目-3", "项目-2", "项目-1");

        List<String> page0 = requestNames("page", "0", "size", "3");
        List<String> page1 = requestNames("page", "1", "size", "3");
        List<String> page2 = requestNames("page", "2", "size", "3");

        // 逐页比对，而不只是比集合：若页的划分落在错误的边界上，集合相等也可能成立。
        assertThat(page0).containsExactly("项目-7", "项目-6", "项目-5");
        assertThat(page1).containsExactly("项目-4", "项目-3", "项目-2");
        assertThat(page2).containsExactly("项目-1");

        List<String> union = new ArrayList<>(page0);
        union.addAll(page1);
        union.addAll(page2);
        assertThat(union).containsExactlyElementsOf(expected);

        // 越界页返回空数组：既不报错，也不把最后一页重发一遍
        assertThat(requestNames("page", "9", "size", "3")).isEmpty();

        // 只传 size 不传 page 时 page 默认 0（前端的首页请求就是这一种）：
        // 若 page 默认值取错，首页会变成"从半途开始"，而按页遍历的用例看不出来。
        assertThat(requestNames("size", "3")).containsExactlyElementsOf(page0);
    }

    @Test
    @WithMockUser(username = "paging-admin", roles = {"ADMIN"})
    void sizeAbsentShouldStillReturnTheWholeList() throws Exception {
        for (int i = 1; i <= 5; i++) {
            Project p = saveProject("全量-" + i, "INITIATED", admin.getId());
            pinCreatedAt(p.getId(), BASE_TIME.plusMinutes(i));
        }

        // 契约回归：不传 size 必须仍是全量（前端列表与导出都走这条路径）
        assertThat(requestNames()).hasSize(5);
        assertThat(requestNames("stage", "INITIATED")).hasSize(5);
    }

    @Test
    @WithMockUser(username = "paging-admin", roles = {"ADMIN"})
    void noFilterShouldReturnTheSameOrderOnBothPaths() throws Exception {
        for (int i = 1; i <= 5; i++) {
            Project p = saveProject("等价-" + i, "INITIATED", admin.getId());
            pinCreatedAt(p.getId(), BASE_TIME.plusMinutes(i));
        }

        // 同样的输入、同样的期望顺序，只有 size 不同：传 size 走 SQL 分页快路径，
        // 不传 size 走全量内存筛选慢路径。两条路径必须给出**逐位相同**的列表——这是本次改动的核心断言。
        List<String> fastPath = requestNames("size", "100");
        List<String> slowPath = requestNames();

        assertThat(fastPath).containsExactly("等价-5", "等价-4", "等价-3", "等价-2", "等价-1");
        assertThat(fastPath).containsExactlyElementsOf(slowPath);
    }

    @Test
    @WithMockUser(username = "paging-admin", roles = {"ADMIN"})
    void stageFilterShouldStayCaseSensitiveAndTreatBlankAsAbsent() throws Exception {
        saveProject("立项中的-1", "INITIATED", admin.getId());
        saveProject("立项中的-2", "INITIATED", admin.getId());
        Project p3 = saveProject("标书编制的-1", "DRAFTING", admin.getId());
        Project p4 = saveProject("标书编制的-2", "DRAFTING", admin.getId());
        pinCreatedAt(p3.getId(), BASE_TIME.plusMinutes(4));
        pinCreatedAt(p4.getId(), BASE_TIME.plusMinutes(3));

        // 非空 stage 一律回落全量路径（原因见 ProjectListQuery#hasNoEffectiveFilter：SQL 的 `=`
        // 在 utf8mb4_unicode_ci 下大小写不敏感，与 String.equals 不等价）。回落不等于可以不管语义：
        assertThat(requestNames("stage", "DRAFTING", "size", "100"))
                .containsExactly("标书编制的-1", "标书编制的-2");
        // 大小写必须仍然敏感——若哪天有人把 stage 重新下推 SQL，MySQL 上这行会返回 2 条（结果被放大）
        assertThat(requestNames("stage", "drafting", "size", "100")).isEmpty();

        // 空白值视作未传：过去快路径把空白的 stage 原样传进 `p.stage = ?`，`?stage=` 从
        // "返回第 1 页"变成"返回空页"。现在 SQL 不接受任何筛选值，空白与缺省必须等价。
        assertThat(requestNames("stage", "", "size", "2")).containsExactlyElementsOf(requestNames("size", "2"));
        assertThat(requestNames("stage", "   ", "size", "2")).containsExactlyElementsOf(requestNames("size", "2"));
    }

    @Test
    @WithMockUser(username = "paging-specialist", roles = {"MANAGER"})
    void visibleProjectsShouldReturnTheSameOrderOnBothPathsForNonAdmin() throws Exception {
        User specialist = saveUser("paging-specialist", User.Role.MANAGER);
        // 项目挂在非管理员名下 ⇒ 可见集非空，`p.id IN :allowedIds` 那条 SQL 分支才真的被测到。
        // 若把项目挂到管理员名下，可见集为空，两跳都会返回 []，断言就变成了"空集等于空集"。
        for (int i = 1; i <= 4; i++) {
            Project p = saveProject("专员可见-" + i, "INITIATED", specialist.getId());
            pinCreatedAt(p.getId(), BASE_TIME.plusMinutes(i));
        }
        // 别人的项目，用来证明可见性过滤真的在起作用
        saveProject("管理员自己的项目", "INITIATED", admin.getId());

        List<String> slowPath = requestNames();
        // 前置断言：先证明慢路径**确实看得见**这些项目，否则下面的相等断言会退化成"空集等于空集"
        assertThat(slowPath)
                .as("夹具失效：非管理员可见集为空，这条用例将什么都测不到")
                .containsExactly("专员可见-4", "专员可见-3", "专员可见-2", "专员可见-1");

        assertThat(requestNames("size", "100")).containsExactlyElementsOf(slowPath);
    }

    @Test
    @WithMockUser(username = "paging-admin", roles = {"ADMIN"})
    void tiedCreatedAtShouldBreakTheTieTheSameWayOnBothPaths() throws Exception {
        Project first = saveProject("并列-1", "INITIATED", admin.getId());
        Project second = saveProject("并列-2", "INITIATED", admin.getId());
        Project newest = saveProject("并列-3", "INITIATED", admin.getId());
        // 前两行 created_at 完全相同，第三行更晚 ⇒ 期望顺序：并列-3 在前，两个并列行按 id 升序。
        // 兜底键若两条路径不一致（旧路径靠 findAll() 的物理序，新路径是显式 id ASC），
        // 这两行就会落到不同的页里——而按秒级粒度造数据的压测库里，并列是常态。
        pinCreatedAt(first.getId(), BASE_TIME.plusMinutes(1));
        pinCreatedAt(second.getId(), BASE_TIME.plusMinutes(1));
        pinCreatedAt(newest.getId(), BASE_TIME.plusMinutes(2));

        List<String> expected = List.of("并列-3", "并列-1", "并列-2");
        assertThat(requestNames("size", "100")).containsExactlyElementsOf(expected);
        assertThat(requestNames()).containsExactlyElementsOf(expected);

        // 页的边界也必须落在同一个兜底键上：size=1 时三页依次是 并列-3 / 并列-1 / 并列-2
        assertThat(requestNames("page", "0", "size", "1")).containsExactly("并列-3");
        assertThat(requestNames("page", "1", "size", "1")).containsExactly("并列-1");
        assertThat(requestNames("page", "2", "size", "1")).containsExactly("并列-2");
    }

    @Test
    @WithMockUser(username = "paging-admin", roles = {"ADMIN"})
    void nonPushdownableFilterShouldStillFilterAndPage() throws Exception {
        Project p1 = saveProject("国家电网配套项目", "INITIATED", admin.getId());
        Project p2 = saveProject("无关项目", "INITIATED", admin.getId());
        Project p3 = saveProject("国家电网二期", "INITIATED", admin.getId());
        pinCreatedAt(p1.getId(), BASE_TIME.plusMinutes(1));
        pinCreatedAt(p2.getId(), BASE_TIME.plusMinutes(2));
        pinCreatedAt(p3.getId(), BASE_TIME.plusMinutes(3));

        // name 不可下推（它要同时命中项目名、客户名与招标主体，而招标主体不是 Project 的列），
        // 所以这次请求必须回落到全量路径。但分页仍要作用在"筛选之后"的结果上：
        // 若回落路径把筛选与分页的先后搞反，两页就会各自返回同一行、或返回未筛选的行。
        List<String> page0 = requestNames("name", "国家电网", "page", "0", "size", "1");
        List<String> page1 = requestNames("name", "国家电网", "page", "1", "size", "1");

        assertThat(page0).containsExactly("国家电网二期");
        assertThat(page1).containsExactly("国家电网配套项目");
        assertThat(requestNames("name", "国家电网", "page", "2", "size", "1")).isEmpty();
    }
}
