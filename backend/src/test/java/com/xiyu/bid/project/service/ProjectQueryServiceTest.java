package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluationBasic;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectQueryServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectAccessScopeService projectAccessScopeService;
    @Mock
    private TenderRepository tenderRepository;
    @Mock
    private TenderEvaluationRepository tenderEvaluationRepository;
    @Mock
    private ProjectInitiationDetailsRepository projectInitiationDetailsRepository;
    @Mock
    private ProjectLeadAssignmentRepository projectLeadAssignmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectListStageEnricher stageEnricher;
    @Mock
    private DemoModeService demoModeService;
    @Mock
    private DemoDataProvider demoDataProvider;
    @Mock
    private DemoFusionService demoFusionService;
    @Mock
    private ProjectManagerDepartmentEnricher managerDepartmentEnricher;

    @BeforeEach
    void stubStageEnricherDefaults() {
        // CO-591: stageEnricher 已抽出 4 列 enrichment，这里 stub 成空上下文，
        // 让 ProjectQueryService 的循环不会因为 enricher 返回 null 而 NPE。
        // 用 lenient 是因为 enrichSingle_shouldDoNothing_whenDtoIsNullOrNullId 不会真正进入 enrich 流程。
        lenient().when(stageEnricher.loadContext(any()))
                .thenReturn(ProjectListStageEnricher.Context.empty());
        lenient().when(stageEnricher.collectReviewerIds(any()))
                .thenReturn(Set.of());
    }

    private ProjectQueryService createService() {
        return new ProjectQueryService(
                projectRepository,
                projectAccessScopeService,
                tenderRepository,
                tenderEvaluationRepository,
                projectInitiationDetailsRepository,
                projectLeadAssignmentRepository,
                userRepository,
                stageEnricher,
                demoModeService,
                demoDataProvider,
                demoFusionService,
                managerDepartmentEnricher);
    }

    private Project project(long id, Long managerId) {
        Project p = new Project();
        p.setId(id);
        p.setName("项目" + id);
        p.setManagerId(managerId);
        p.setCreatedAt(LocalDateTime.now());
        p.setStatus(Project.Status.PENDING_INITIATION);
        return p;
    }

    @Test
    @DisplayName("leaderDepartment 为空时应通过 department_code 关联 organization_departments 反查部门名回填")
    void shouldBackfillLeaderDepartmentFromManagerUserWhenEmpty() {
        Project project = project(1L, 99L);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectAccessScopeService.filterAccessibleProjects(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(demoModeService.isEnabled()).thenReturn(false);

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(1L);
        details.setLeaderDepartment(null);
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of());

        User manager = new User();
        manager.setId(99L);
        manager.setDepartmentCode("700498910");
        manager.setDepartmentName("");
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of(manager));
        // mock enricher 返回 99L → "东部二区"
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(99L)), any()))
                .thenReturn(Map.of(99L, "东部二区"));

        ProjectQueryService service = createService();
        List<ProjectDTO> result = service.getAllProjects();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLeaderDepartment()).isEqualTo("东部二区");
    }

    @Test
    @DisplayName("leaderDepartment 已有值时仍被实时部门覆盖（员工调岗后快照需更新）")
    void shouldOverrideLeaderDepartmentWithRealtimeDepartment() {
        Project project = project(1L, 99L);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectAccessScopeService.filterAccessibleProjects(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(demoModeService.isEnabled()).thenReturn(false);

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(1L);
        details.setLeaderDepartment("已有部门");
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of());

        User manager = new User();
        manager.setId(99L);
        manager.setDepartmentCode("700498910");
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of(manager));
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(99L)), any()))
                .thenReturn(Map.of(99L, "东部二区"));

        ProjectQueryService service = createService();
        List<ProjectDTO> result = service.getAllProjects();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLeaderDepartment()).isEqualTo("东部二区");
    }

    @Test
    @DisplayName("leaderDepartment 为空且 manager 用户无 department_code 时保持为空")
    void shouldKeepLeaderDepartmentNullWhenManagerHasNoDepartment() {
        Project project = project(1L, 99L);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectAccessScopeService.filterAccessibleProjects(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(demoModeService.isEnabled()).thenReturn(false);

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(1L);
        details.setLeaderDepartment(null);
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of());

        User manager = new User();
        manager.setId(99L);
        manager.setDepartmentCode(null);
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of(manager));
        // mock enricher 返回空 map（department_code 为空，无法反查）
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(99L)), any()))
                .thenReturn(Map.of());

        ProjectQueryService service = createService();
        List<ProjectDTO> result = service.getAllProjects();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLeaderDepartment()).isNull();
    }

    @Test
    @DisplayName("CO-578: enrichSingle 应为详情接口补充投标负责人和辅助人员姓名")
    void enrichSingle_shouldPopulateBiddingLeaderAndAssistantName() {
        ProjectDTO dto = ProjectDTO.builder().id(1L).managerId(99L).build();

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(1L);
        details.setBiddingLeaderName("李四");
        details.setProjectLeaderName("张三");
        details.setLeaderDepartment("华东区");
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(details));

        com.xiyu.bid.project.entity.ProjectLeadAssignment assignment =
                com.xiyu.bid.project.entity.ProjectLeadAssignment.builder()
                        .projectId(1L)
                        .primaryLeadUserId(10L)
                        .secondaryLeadUserId(20L)
                        .build();
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(assignment));

        User secondaryUser = new User();
        secondaryUser.setId(20L);
        secondaryUser.setFullName("王五");
        when(userRepository.findByIdIn(Set.of(10L, 20L, 99L)))
                .thenReturn(List.of(secondaryUser));
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(99L)), any()))
                .thenReturn(Map.of());

        ProjectQueryService service = createService();
        service.enrichSingle(dto);

        assertThat(dto.getBiddingLeaderName()).isEqualTo("李四");
        assertThat(dto.getSecondaryBiddingLeaderName()).isEqualTo("王五");
        assertThat(dto.getProjectLeaderName()).isEqualTo("张三");
        assertThat(dto.getLeaderDepartment()).isEqualTo("华东区");
    }

    @Test
    @DisplayName("CO-578: enrichSingle 无 assignment 时辅助人员姓名为 null")
    void enrichSingle_shouldReturnNullAssistantName_whenNoAssignment() {
        ProjectDTO dto = ProjectDTO.builder().id(2L).managerId(99L).build();

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(2L);
        details.setBiddingLeaderName("赵六");
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(2L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(2L)))
                .thenReturn(List.of());
        when(userRepository.findByIdIn(Set.of(99L)))
                .thenReturn(List.of());
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(99L)), any()))
                .thenReturn(Map.of());

        ProjectQueryService service = createService();
        service.enrichSingle(dto);

        assertThat(dto.getBiddingLeaderName()).isEqualTo("赵六");
        assertThat(dto.getSecondaryBiddingLeaderName()).isNull();
    }

    @Test
    @DisplayName("CO-578: enrichSingle 传入 null 或无 id 时安全返回")
    void enrichSingle_shouldDoNothing_whenDtoIsNullOrNullId() {
        ProjectQueryService service = createService();
        // 不抛异常即可
        service.enrichSingle(null);
        service.enrichSingle(ProjectDTO.builder().id(null).build());
    }

    // ===== CC2026072071 根因回归：项目负责人工号填充（端到端） =====
    // 生产事故：项目详情页"项目负责人"只显示"王亮"（CRM 推送纯姓名），不带工号。
    // 根因：ProjectListEnrichmentSupport.populateFromTender L77-80 用
    // tender.projectManagerName 兜底填充 projectLeaderName，但未通过
    // projectLeaderId 反查 users.employee_number。
    // 修复方案 B：enrichWithTenderAndDetails 阶段扩展 userMap 预加载范围
    // （含 tender.projectManagerId 和 pid.ownerUserId），调用纯核心方法
    // populateLeaderEmployeeNumber 填充 projectLeaderEmployeeNumber 字段。

    @Test
    @DisplayName("CC2026072071: tender.projectManagerId=75 + users 表有 75 号员工 → DTO.projectLeaderEmployeeNumber='05972'")
    void enrichWithTenderAndDetails_FillsProjectLeaderEmployeeNumber_FromUsersTable() {
        // 场景：tenders.project_manager_name="王亮"（纯姓名），tenders.project_manager_id=75，
        // users 表 id=75 full_name="王亮" employee_number="05972"。
        // 期望：dto.projectLeaderId=75（populateFromTender 填充），
        //       dto.projectLeaderEmployeeNumber="05972"（新逻辑填充）。
        // 通过 enrichSingle 入口触发 enrichWithTenderAndDetails，不需要 projectRepository 等 stub。

        // tender 是 projectLeaderId 的来源
        Tender tender = Tender.builder()
                .id(100L)
                .projectManagerId(75L)
                .projectManagerName("王亮")
                .build();
        when(tenderRepository.findAllById(List.of(100L))).thenReturn(List.of(tender));

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(1L);
        // ownerUserId=null，让 projectLeaderId 来源走 tender.projectManagerId
        details.setOwnerUserId(null);
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of());

        // users 表有 id=75 employeeNumber="05972"
        User leader = new User();
        leader.setId(75L);
        leader.setFullName("王亮");
        leader.setEmployeeNumber("05972");
        // allUserIds = leadUserIds(empty) ∪ managerIds(empty, managerId=null)
        //              ∪ reviewerIds(empty) ∪ tender.projectManagerId(75) ∪ pid.ownerUserId(empty)
        //            = {75}
        when(userRepository.findByIdIn(Set.of(75L))).thenReturn(List.of(leader));
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of()), any()))
                .thenReturn(Map.of());

        ProjectQueryService service = createService();
        // 调用 enrichSingle 触发 enrichWithTenderAndDetails
        ProjectDTO dto = ProjectDTO.builder().id(1L).tenderId(100L).managerId(null).build();
        service.enrichSingle(dto);

        assertThat(dto.getProjectLeaderId()).isEqualTo(75L);
        assertThat(dto.getProjectLeaderName()).isEqualTo("王亮");
        assertThat(dto.getProjectLeaderEmployeeNumber()).isEqualTo("05972");
    }

    @Test
    @DisplayName("CC2026072071: pid.ownerUserId=null 但 tender.projectManagerId=75 → 仍能填充工号")
    void enrichWithTenderAndDetails_FillsEmployeeNumber_FromTenderProjectManagerId_WhenPidOwnerUserIdMissing() {
        // 场景：pid.ownerUserId=null（立项详情未填负责人），但 tender.projectManagerId=75。
        // 期望：projectLeaderId 由 populateFromTender 从 tender.projectManagerId 兜底填充，
        //       projectLeaderEmployeeNumber 由 userMap 反查填充。
        // 通过 enrichSingle 入口触发 enrichWithTenderAndDetails，不需要 projectRepository 等 stub。

        Tender tender = Tender.builder()
                .id(100L)
                .projectManagerId(75L)
                .projectManagerName("王亮")
                .build();
        when(tenderRepository.findAllById(List.of(100L))).thenReturn(List.of(tender));

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(1L);
        details.setOwnerUserId(null); // 关键：pid 无 ownerUserId
        details.setProjectLeaderName(null); // 关键：pid 无 projectLeaderName，由 tender 兜底
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of());

        User leader = new User();
        leader.setId(75L);
        leader.setFullName("王亮");
        leader.setEmployeeNumber("05972");
        when(userRepository.findByIdIn(Set.of(75L))).thenReturn(List.of(leader));
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of()), any()))
                .thenReturn(Map.of());

        ProjectQueryService service = createService();
        ProjectDTO dto = ProjectDTO.builder().id(1L).tenderId(100L).managerId(null).build();
        service.enrichSingle(dto);

        // 验证根因行为：姓名能关联到工号
        assertThat(dto.getProjectLeaderId()).isEqualTo(75L);
        assertThat(dto.getProjectLeaderName()).isEqualTo("王亮");
        assertThat(dto.getProjectLeaderEmployeeNumber()).isEqualTo("05972");
    }

    // ===== revenue 回归 !564：客户营收字段映射错乱（详情页值丢失 + 列表显示 MRO 流水金额） =====

    @Test
    @DisplayName("revenue 回归 d1994a3fa：立项表 annualRevenue 有值时应正确填充 dto.revenue（客户营收）")
    @SuppressWarnings("deprecation")
    void shouldPopulateRevenueFromInitiationAnnualRevenue() {
        Project project = project(1L, 99L);
        project.setTenderId(7L);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectAccessScopeService.filterAccessibleProjects(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(demoModeService.isEnabled()).thenReturn(false);

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(1L);
        details.setAnnualRevenue(new BigDecimal("12.5"));
        details.setAnnualEcommerceAmount(new BigDecimal("888.8"));
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of());
        when(tenderEvaluationRepository.findByTenderIdIn(List.of(7L)))
                .thenReturn(List.of());
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of());
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(99L)), any()))
                .thenReturn(Map.of());

        ProjectQueryService service = createService();
        List<ProjectDTO> result = service.getAllProjects();

        assertThat(result).hasSize(1);
        // 关键断言：revenue 必须来自 det.annualRevenue（客户营收），不能来自 det.annualEcommerceAmount（MRO 流水）
        assertThat(result.get(0).getRevenue()).isEqualByComparingTo(new BigDecimal("12.5"));
    }

    @Test
    @DisplayName("revenue 回归 d1994a3fa：det.annualRevenue 为空时 fallback 到 eval.basic.customerRevenue")
    void shouldFallbackRevenueToEvaluationCustomerRevenueWhenInitiationHasNoAnnualRevenue() {
        Project project = project(2L, 99L);
        project.setTenderId(7L);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectAccessScopeService.filterAccessibleProjects(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(demoModeService.isEnabled()).thenReturn(false);

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(2L);
        // annualRevenue 为空；annualEcommerceAmount 有值（d1994a3fa bug 会错误取这个）
        details.setAnnualEcommerceAmount(new BigDecimal("999.9"));
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(2L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(2L)))
                .thenReturn(List.of());

        TenderEvaluationBasic basic = TenderEvaluationBasic.builder()
                .customerRevenue(new BigDecimal("45.6"))
                .build();
        TenderEvaluation eval = TenderEvaluation.builder()
                .tenderId(7L)
                .basic(basic)
                .build();
        when(tenderEvaluationRepository.findByTenderIdIn(List.of(7L)))
                .thenReturn(List.of(eval));
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of());
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(99L)), any()))
                .thenReturn(Map.of());

        ProjectQueryService service = createService();
        List<ProjectDTO> result = service.getAllProjects();

        assertThat(result).hasSize(1);
        // 关键断言：fallback 应取 eval.basic.customerRevenue，不能取 det.annualEcommerceAmount
        assertThat(result.get(0).getRevenue()).isEqualByComparingTo(new BigDecimal("45.6"));
    }

    @Test
    @DisplayName("revenue 回归 d1994a3fa：det.annualEcommerceAmount（MRO 流水）绝不能污染 dto.revenue（客户营收）")
    @SuppressWarnings("deprecation")
    void shouldNotPolluteRevenueWithAnnualEcommerceAmount() {
        Project project = project(3L, 99L);
        project.setTenderId(7L);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectAccessScopeService.filterAccessibleProjects(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(demoModeService.isEnabled()).thenReturn(false);

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(3L);
        // 只有 MRO 流水金额，annualRevenue 和 eval.basic.customerRevenue 都为空
        details.setAnnualEcommerceAmount(new BigDecimal("777.7"));
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(3L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(3L)))
                .thenReturn(List.of());
        when(tenderEvaluationRepository.findByTenderIdIn(List.of(7L)))
                .thenReturn(List.of());
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of());
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(99L)), any()))
                .thenReturn(Map.of());

        ProjectQueryService service = createService();
        List<ProjectDTO> result = service.getAllProjects();

        assertThat(result).hasSize(1);
        // 关键回归断言：MRO 流水不能进入 revenue 字段（d1994a3fa bug 会让 revenue=777.7）
        assertThat(result.get(0).getRevenue()).isNull();
    }

    @Test
    @DisplayName("revenue 优先级：det.annualRevenue 和 eval.customerRevenue 同时有值时，det.annualRevenue 优先")
    @SuppressWarnings("deprecation")
    void shouldPreferInitiationAnnualRevenueOverEvaluationCustomerRevenueWhenBothPresent() {
        Project project = project(4L, 99L);
        project.setTenderId(7L);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectAccessScopeService.filterAccessibleProjects(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(demoModeService.isEnabled()).thenReturn(false);

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(4L);
        details.setAnnualRevenue(new BigDecimal("12.5")); // det 优先
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(4L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(4L)))
                .thenReturn(List.of());

        TenderEvaluationBasic basic = TenderEvaluationBasic.builder()
                .customerRevenue(new BigDecimal("99.9")) // fallback 不应触发
                .build();
        TenderEvaluation eval = TenderEvaluation.builder()
                .tenderId(7L)
                .basic(basic)
                .build();
        when(tenderEvaluationRepository.findByTenderIdIn(List.of(7L)))
                .thenReturn(List.of(eval));
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of());
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(99L)), any()))
                .thenReturn(Map.of());

        ProjectQueryService service = createService();
        List<ProjectDTO> result = service.getAllProjects();

        assertThat(result).hasSize(1);
        // 优先级断言：det.annualRevenue 胜出，eval.customerRevenue 不覆盖
        assertThat(result.get(0).getRevenue()).isEqualByComparingTo(new BigDecimal("12.5"));
    }

    @Test
    @DisplayName("revenue 已有值保护：dto.revenue 非 null 时，det.annualRevenue 不应覆盖")
    @SuppressWarnings("deprecation")
    void shouldNotOverrideRevenueWhenDtoAlreadyHasValue() {
        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(5L);
        details.setAnnualRevenue(new BigDecimal("12.5")); // 不应被采用
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(5L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(5L)))
                .thenReturn(List.of());

        // 直接构造已带 revenue 的 DTO（模拟其他来源已填充的场景）
        ProjectDTO preFilledDto = ProjectDTO.builder()
                .id(5L)
                .managerId(99L)
                .revenue(new BigDecimal("888.8"))
                .build();

        ProjectQueryService service = createService();
        service.enrichSingle(preFilledDto);

        // 已有值保护断言：revenue 保持原值，不被 det.annualRevenue 覆盖
        assertThat(preFilledDto.getRevenue()).isEqualByComparingTo(new BigDecimal("888.8"));
    }

    // ===== 调岗场景回归：tender.department 历史快照不应覆盖实时部门 =====
    // 生产事故：工号06442（刘向博）在三个项目显示三个不同部门
    //   - project 26: tender.department="能源电力四组"（早期快照）
    //   - project 33/34: tender.department="河南战区"（中期快照）
    //   - project 35/38: tender.department=NULL（无快照，反查生效→"豫皖项目组"）
    // 根因：populateFromTender 用 tender.department 覆盖空字符串的 leaderDepartment 后，
    //       反查逻辑因 isBlank=false 被跳过，导致显示历史快照而非当前部门。
    // 修复：去掉反查的 isBlank 前置条件，只要有实时反查结果就覆盖。

    @Test
    @DisplayName("调岗场景：同一负责人多个项目对应不同 tender.department 快照，全部应显示当前实时部门")
    void shouldAlwaysUseRealtimeDepartmentOverTenderSnapshot_whenEmployeeTransferred() {
        // 场景复现生产 06442 事故：3 个项目，3 个不同的 tender.department 历史快照
        Project p1 = project(26L, 68L); p1.setTenderId(58L);
        Project p2 = project(33L, 68L); p2.setTenderId(69L);
        Project p3 = project(35L, 68L); p3.setTenderId(77L);
        when(projectRepository.findAll()).thenReturn(List.of(p1, p2, p3));
        when(projectAccessScopeService.filterAccessibleProjects(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(demoModeService.isEnabled()).thenReturn(false);

        // 3 个项目的立项详情 leader_department 都是空字符串（生产实际数据）
        ProjectInitiationDetails d1 = new ProjectInitiationDetails(); d1.setProjectId(26L); d1.setLeaderDepartment("");
        ProjectInitiationDetails d2 = new ProjectInitiationDetails(); d2.setProjectId(33L); d2.setLeaderDepartment("");
        ProjectInitiationDetails d3 = new ProjectInitiationDetails(); d3.setProjectId(35L); d3.setLeaderDepartment("");
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(26L, 33L, 35L)))
                .thenReturn(List.of(d1, d2, d3));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(26L, 33L, 35L)))
                .thenReturn(List.of());

        // 3 个 tender 对应 3 个不同的历史快照部门
        Tender t1 = Tender.builder().id(58L).department("能源电力四组").build();
        Tender t2 = Tender.builder().id(69L).department("河南战区").build();
        Tender t3 = Tender.builder().id(77L).department(null).build();
        when(tenderRepository.findAllById(List.of(58L, 69L, 77L)))
                .thenReturn(List.of(t1, t2, t3));

        // 用户 68（刘向博）当前 department_code=700554247（豫皖项目组）
        User manager = new User();
        manager.setId(68L);
        manager.setDepartmentCode("700554247");
        when(userRepository.findByIdIn(Set.of(68L))).thenReturn(List.of(manager));

        // enricher 实时反查返回当前部门"豫皖项目组"
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(68L)), any()))
                .thenReturn(Map.of(68L, "豫皖项目组"));

        ProjectQueryService service = createService();
        List<ProjectDTO> result = service.getAllProjects();

        assertThat(result).hasSize(3);
        // 关键断言：3 个项目的 leaderDepartment 都应该是当前实时部门，不是历史快照
        assertThat(result.get(0).getLeaderDepartment()).isEqualTo("豫皖项目组"); // p1: 不再是"能源电力四组"
        assertThat(result.get(1).getLeaderDepartment()).isEqualTo("豫皖项目组"); // p2: 不再是"河南战区"
        assertThat(result.get(2).getLeaderDepartment()).isEqualTo("豫皖项目组"); // p3: 保持反查结果
    }

    @Test
    @DisplayName("调岗兜底：实时反查 map 为空时保留 tender 快照，避免清空已有值")
    void shouldKeepTenderSnapshotWhenRealtimeLookupMisses() {
        // 场景：员工刚同步到 OSS 但 organization_departments 表尚未建好对应记录，
        // enricher 反查返回空 Map，此时应保留 tender.department 快照作为兜底。
        Project project = project(1L, 99L);
        project.setTenderId(100L);
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectAccessScopeService.filterAccessibleProjects(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(demoModeService.isEnabled()).thenReturn(false);

        ProjectInitiationDetails details = new ProjectInitiationDetails();
        details.setProjectId(1L);
        details.setLeaderDepartment(""); // pid 快照为空
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of(details));
        when(projectLeadAssignmentRepository.findByProjectIdIn(List.of(1L)))
                .thenReturn(List.of());

        Tender tender = Tender.builder().id(100L).department("海外事业部").build();
        when(tenderRepository.findAllById(List.of(100L))).thenReturn(List.of(tender));

        User manager = new User();
        manager.setId(99L);
        manager.setDepartmentCode("700999999"); // 未在 organization_departments 表中
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of(manager));
        // 反查返回空 Map（部门未建记录）
        when(managerDepartmentEnricher.buildManagerDepartmentMap(eq(Set.of(99L)), any()))
                .thenReturn(Map.of());

        ProjectQueryService service = createService();
        List<ProjectDTO> result = service.getAllProjects();

        assertThat(result).hasSize(1);
        // 兜底断言：反查未命中时保留 tender 快照，不清成 null
        assertThat(result.get(0).getLeaderDepartment()).isEqualTo("海外事业部");
    }
}
