package com.xiyu.bid.analytics.integration;

import com.xiyu.bid.audit.service.AuditLogService;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.documentexport.entity.DocumentExport;
import com.xiyu.bid.documentexport.repository.DocumentExportRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.AuditLogRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractAuditOperationalAnalyticsIntegrationTest {
    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected IAuditLogService auditLogService;
    @Autowired
    protected AuditLogRepository auditLogRepository;
    @Autowired
    protected TenderRepository tenderRepository;
    @Autowired
    protected ProjectRepository projectRepository;
    @Autowired
    protected TaskRepository taskRepository;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected com.xiyu.bid.repository.RoleProfileRepository roleProfileRepository;
    @Autowired
    protected ProjectDocumentRepository projectDocumentRepository;
    @Autowired
    protected DocumentExportRepository documentExportRepository;
    @PersistenceUnit
    protected EntityManagerFactory entityManagerFactory;
    protected User adminUser;
    protected Project project;
    protected String currentMonthKey;

    @BeforeEach
    void setUpAuditOperationalAnalyticsFixture() {
        documentExportRepository.deleteAll();
        projectDocumentRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();
        tenderRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        roleProfileRepository.deleteAll();

        com.xiyu.bid.entity.RoleProfile defaultProfile = roleProfileRepository.save(
                com.xiyu.bid.entity.RoleProfile.builder()
                        .code("admin")
                        .name("审计测试权限")
                        .dataScope("self")
                        .build()
        );

        adminUser = userRepository.save(User.builder()
                .username("audit-admin")
                .password("XiyuDemo!2026")
                .email("audit-admin@example.com")
                .fullName("审计管理员")
                .role(User.Role.ADMIN)
                .roleProfile(defaultProfile)
                .enabled(true)
                .build());

        Tender biddingTender = tenderRepository.save(Tender.builder()
                .title("智慧办公平台采购")
                .source("中国政府采购网")
                .budget(new BigDecimal("500000"))
                .status(Tender.Status.BIDDING)
                .aiScore(88)
                .riskLevel(Tender.RiskLevel.LOW)
                .build());

        tenderRepository.save(Tender.builder()
                .title("云服务平台扩容")
                .source("中国政府采购网")
                .budget(new BigDecimal("800000"))
                .status(Tender.Status.TRACKING)
                .aiScore(75)
                .riskLevel(Tender.RiskLevel.MEDIUM)
                .build());

        tenderRepository.save(Tender.builder()
                .title("Server升级采购")
                .source("中国政府采购网")
                .budget(new BigDecimal("600000"))
                .status(Tender.Status.WON)
                .aiScore(92)
                .riskLevel(Tender.RiskLevel.LOW)
                .build());

        currentMonthKey = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        project = projectRepository.save(Project.builder()
                .name("智慧办公实施项目")
                .tenderId(biddingTender.getId())
                .status(Project.Status.BIDDING)
                .managerId(adminUser.getId())
                .teamMembers(List.of(adminUser.getId()))
                .startDate(LocalDateTime.now().minusDays(2))
                .endDate(LocalDateTime.now().plusDays(10))
                .build());

        taskRepository.save(Task.builder()
                .projectId(project.getId())
                .title("准备商务应答")
                .description("汇总商务偏离表")
                .assigneeId(adminUser.getId())
                .status(Task.Status.IN_PROGRESS)
                .priority(Task.Priority.HIGH)
                .dueDate(LocalDateTime.now().plusDays(2))
                .build());

        projectDocumentRepository.save(ProjectDocument.builder()
                .projectId(project.getId())
                .name("技术应答.docx")
                .size("2MB")
                .fileType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .uploaderId(adminUser.getId())
                .uploaderName("审计管理员")
                .build());

        documentExportRepository.save(DocumentExport.builder()
                .projectId(project.getId())
                .structureId(1L)
                .projectName(project.getName())
                .format("json")
                .fileName("智慧办公实施项目_export.json")
                .contentType("application/json")
                .fileSize(5120L)
                .exportedBy(adminUser.getId())
                .exportedByName("审计管理员")
                .build());

        auditLogService.logSync(AuditLogService.AuditLogEntry.builder()
                .userId(String.valueOf(adminUser.getId()))
                .username(adminUser.getUsername())
                .action("UPDATE")
                .entityType("PROJECT")
                .entityId(String.valueOf(project.getId()))
                .description("Updated project status")
                .success(true)
                .build());
        auditLogService.logSync(AuditLogService.AuditLogEntry.builder()
                .userId(String.valueOf(adminUser.getId()))
                .username(adminUser.getUsername())
                .action("ARCHIVE")
                .entityType("PROJECT")
                .entityId(String.valueOf(project.getId()))
                .description("Archived final package")
                .success(true)
                .build());
        auditLogService.logSync(AuditLogService.AuditLogEntry.builder()
                .userId("unknown")
                .username("unknown")
                .action("LOGIN")
                .entityType("SYSTEM")
                .entityId("login")
                .description("Failed login attempt")
                .success(false)
                .errorMessage("Bad credentials")
                .build());
        resetStatistics();
    }

    protected long measureStatements(ThrowingAction action) throws Exception {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            action.run();
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.clear();
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
    }

    protected void resetStatistics() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    protected void assertQueryCountAtMost(long expectedMax) {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        assertThat(statistics.getPrepareStatementCount())
                .as("SQL statement count should stay within the optimized threshold")
                .isLessThanOrEqualTo(expectedMax);
    }

    protected void runNaiveProductLineBaseline() {
        tenderRepository.findAll().forEach(tender -> {
            tenderRepository.findById(tender.getId());
            projectRepository.findByTenderId(tender.getId());
        });
    }

    protected void runNaiveDrillDownBaseline(String type, String key) {
        List<Tender> tenders = tenderRepository.findAll();
        List<Project> projects = projectRepository.findAll();
        List<Task> tasks = taskRepository.findAll();
        projectDocumentRepository.findAll();
        documentExportRepository.findAll();

        for (Tender tender : tenders) {
            tenderRepository.findById(tender.getId());
        }
        for (Project project : projects) {
            projectRepository.findById(project.getId());
            if (project.getTenderId() != null) {
                tenderRepository.findById(project.getTenderId());
            }
            projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
            documentExportRepository.findByProjectIdOrderByExportedAtDesc(project.getId());
        }
        for (Task task : tasks) {
            taskRepository.findById(task.getId());
        }
        if ("trend".equals(type) && key != null) {
            tenderRepository.findAll().stream()
                    .filter(tender -> tender.getCreatedAt() != null)
                    .filter(tender -> tender.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM")).equals(key))
                    .toList();
        }
    }

    protected void runNaiveWinRateBaseline() {
        List<Tender> tenders = tenderRepository.findAll();
        List<Project> projects = projectRepository.findAll();
        userRepository.findAll();

        for (Tender tender : tenders) {
            tenderRepository.findById(tender.getId());
            projectRepository.findByTenderId(tender.getId());
        }
        for (Project project : projects) {
            if (project.getManagerId() != null) {
                userRepository.findById(project.getManagerId());
            }
            if (project.getTenderId() != null) {
                tenderRepository.findById(project.getTenderId());
            }
        }
    }

    protected void runNaiveTeamBaseline() {
        List<Project> projects = projectRepository.findAll();
        List<Tender> tenders = tenderRepository.findAll();
        List<User> users = userRepository.findAll();
        List<Task> tasks = taskRepository.findAll();

        for (Project project : projects) {
            projectRepository.findById(project.getId());
            if (project.getTenderId() != null) {
                tenderRepository.findById(project.getTenderId());
            }
            if (project.getManagerId() != null) {
                userRepository.findById(project.getManagerId());
            }
            taskRepository.findByProjectId(project.getId());
            projectDocumentRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
            documentExportRepository.findByProjectIdOrderByExportedAtDesc(project.getId());
        }
        tenders.forEach(tender -> tenderRepository.findById(tender.getId()));
        users.forEach(user -> userRepository.findById(user.getId()));
        tasks.forEach(task -> taskRepository.findById(task.getId()));
    }
    @FunctionalInterface
    protected interface ThrowingAction {
        void run() throws Exception;
    }
}
