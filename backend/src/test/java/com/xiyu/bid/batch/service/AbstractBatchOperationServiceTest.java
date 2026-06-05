package com.xiyu.bid.batch.service;

import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.batch.core.BatchAssignmentPolicy;
import com.xiyu.bid.batch.core.BatchValidationPolicy;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.fees.entity.Fee;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
abstract class AbstractBatchOperationServiceTest {

    @Mock
    protected TenderRepository tenderRepository;

    @Mock
    protected TaskRepository taskRepository;

    @Mock
    protected ProjectRepository projectRepository;

    @Mock
    protected UserRepository userRepository;

    @Mock
    protected IAuditLogService auditLogService;

    protected BatchOperationService batchOperationService;

    @Mock
    protected com.xiyu.bid.fees.repository.FeeRepository feeRepository;

    @Mock
    protected ProjectAccessScopeService projectAccessScopeService;

    protected Tender testTender1;
    protected Tender testTender2;
    protected Task testTask1;
    protected Task testTask2;
    protected Project testProject1;
    protected Project testProject2;
    protected Fee testFee1;
    protected Fee testFee2;

    @BeforeEach
    void setUpBatchOperationFixtures() {
        BatchValidationPolicy validationPolicy = new BatchValidationPolicy();
        BatchOperationLogService batchOperationLogService = new BatchOperationLogService(auditLogService);
        BatchAssignmentPolicy batchAssignmentPolicy = new BatchAssignmentPolicy(projectAccessScopeService);
        BatchProjectAccessGuard projectAccessGuard = new BatchProjectAccessGuard(projectAccessScopeService, projectRepository);
        BatchTaskAssignmentResolver taskAssignmentResolver =
                new BatchTaskAssignmentResolver(userRepository, batchAssignmentPolicy);
        BatchTenderCommandService tenderCommandService =
                new BatchTenderCommandService(tenderRepository, projectRepository, validationPolicy, batchOperationLogService, projectAccessScopeService);
        BatchTaskCommandService taskCommandService = new BatchTaskCommandService(
                taskRepository,
                validationPolicy,
                taskAssignmentResolver,
                batchOperationLogService,
                projectAccessGuard
        );
        BatchProjectCommandService projectCommandService =
                new BatchProjectCommandService(projectRepository, validationPolicy, batchOperationLogService, projectAccessScopeService);
        BatchFeeCommandService feeCommandService =
                new BatchFeeCommandService(feeRepository, validationPolicy, batchOperationLogService, projectAccessScopeService);
        batchOperationService = new BatchOperationService(
                tenderCommandService,
                taskCommandService,
                projectCommandService,
                feeCommandService,
                validationPolicy
        );

        testTender1 = Tender.builder()
                .id(1L)
                .title("Test Tender 1")
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .build();

        testTender2 = Tender.builder()
                .id(2L)
                .title("Test Tender 2")
                .status(Tender.Status.PENDING_ASSIGNMENT)
                .build();

        testTask1 = Task.builder()
                .id(1L)
                .projectId(100L)
                .title("Test Task 1")
                .assigneeId(null)
                .status(Task.Status.TODO)
                .build();

        testTask2 = Task.builder()
                .id(2L)
                .projectId(100L)
                .title("Test Task 2")
                .assigneeId(null)
                .status(Task.Status.TODO)
                .build();

        testProject1 = Project.builder()
                .id(1L)
                .name("Test Project 1")
                .tenderId(10L)
                .status(Project.Status.INITIATED)
                .managerId(1L)
                .build();

        testProject2 = Project.builder()
                .id(2L)
                .name("Test Project 2")
                .tenderId(20L)
                .status(Project.Status.INITIATED)
                .managerId(1L)
                .build();

        testFee1 = Fee.builder()
                .id(1L)
                .projectId(100L)
                .feeType(Fee.FeeType.BID_BOND)
                .amount(java.math.BigDecimal.valueOf(1000.00))
                .feeDate(java.time.LocalDateTime.now())
                .status(Fee.Status.PENDING)
                .build();

        testFee2 = Fee.builder()
                .id(2L)
                .projectId(100L)
                .feeType(Fee.FeeType.SERVICE_FEE)
                .amount(java.math.BigDecimal.valueOf(500.00))
                .feeDate(java.time.LocalDateTime.now())
                .status(Fee.Status.PENDING)
                .build();
    }
}
