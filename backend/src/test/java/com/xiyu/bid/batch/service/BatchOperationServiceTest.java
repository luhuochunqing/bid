package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.core.BatchAssignmentPolicy;
import com.xiyu.bid.batch.core.BatchValidationPolicy;
import com.xiyu.bid.batch.dto.BatchApproveFeesRequest;
import com.xiyu.bid.batch.dto.BatchProjectUpdateRequest;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.fees.entity.Fee;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.service.ProjectAccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchOperationServiceTest {

    @Mock
    private TenderRepository tenderRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IAuditLogService auditLogService;

    private BatchOperationService batchOperationService;

    @Mock
    private com.xiyu.bid.fees.repository.FeeRepository feeRepository;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    private Tender testTender1;
    private Tender testTender2;
    private Task testTask1;
    private Task testTask2;
    private Project testProject1;
    private Project testProject2;
    private Fee testFee1;
    private Fee testFee2;

    @BeforeEach
    void setUp() {
        BatchValidationPolicy validationPolicy = new BatchValidationPolicy();
        BatchAssignmentPolicy assignmentPolicy = new BatchAssignmentPolicy(projectAccessScopeService);
        BatchOperationLogService logService = new BatchOperationLogService(auditLogService);
        BatchProjectAccessGuard projectAccessGuard = new BatchProjectAccessGuard(projectAccessScopeService, projectRepository);
        BatchTaskAssignmentResolver taskAssignmentResolver = new BatchTaskAssignmentResolver(userRepository, assignmentPolicy);
        BatchTenderCommandService tenderCommandService = new BatchTenderCommandService(
                tenderRepository, projectRepository, validationPolicy, logService, projectAccessScopeService
        );
        BatchTaskCommandService taskCommandService = new BatchTaskCommandService(
                taskRepository, validationPolicy, taskAssignmentResolver, logService, projectAccessGuard
        );
        BatchProjectCommandService projectCommandService = new BatchProjectCommandService(
                projectRepository, validationPolicy, logService, projectAccessScopeService
        );
        BatchFeeCommandService feeCommandService = new BatchFeeCommandService(
                feeRepository, validationPolicy, logService, projectAccessScopeService
        );
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

    @Nested
    class BatchClaimTendersTests {

        @Test
        void batchClaimTenders_AllSuccess() {
            List<Long> tenderIds = Arrays.asList(1L, 2L);
            Long userId = 100L;

            when(tenderRepository.findById(1L)).thenReturn(Optional.of(testTender1));
            when(tenderRepository.findById(2L)).thenReturn(Optional.of(testTender2));
            when(tenderRepository.saveAll(anyList())).thenReturn(Arrays.asList(testTender1, testTender2));

            var response = batchOperationService.batchClaimTenders(tenderIds, userId);

            assertTrue(response.getSuccess());
            assertEquals(2, response.getSuccessCount());
            assertEquals(0, response.getFailureCount());
            assertTrue(response.isAllSuccess());

            verify(tenderRepository, times(1)).saveAll(anyList());
            verify(auditLogService, times(1)).log(any());
        }

        @Test
        void batchClaimTenders_PartialFailure() {
            List<Long> tenderIds = Arrays.asList(1L, 2L, 999L);
            Long userId = 100L;

            when(tenderRepository.findById(1L)).thenReturn(Optional.of(testTender1));
            when(tenderRepository.findById(2L)).thenReturn(Optional.of(testTender2));
            when(tenderRepository.findById(999L)).thenReturn(Optional.empty());
            when(tenderRepository.saveAll(anyList())).thenReturn(Arrays.asList(testTender1, testTender2));

            var response = batchOperationService.batchClaimTenders(tenderIds, userId);

            assertFalse(response.getSuccess());
            assertEquals(2, response.getSuccessCount());
            assertEquals(1, response.getFailureCount());
        }

        @Test
        void batchClaimTenders_EmptyList() {
            List<Long> tenderIds = Collections.emptyList();
            Long userId = 100L;

            assertThrows(IllegalArgumentException.class, () -> {
                batchOperationService.batchClaimTenders(tenderIds, userId);
            });
        }
    }

    @Nested
    class BatchAssignTasksTests {

        @Test
        void batchAssignTasks_AllSuccess() {
            List<Long> taskIds = Arrays.asList(1L, 2L);
            Long assigneeId = 200L;

            when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask1));
            when(taskRepository.findById(2L)).thenReturn(Optional.of(testTask2));
            when(taskRepository.saveAll(anyList())).thenReturn(Arrays.asList(testTask1, testTask2));

            var response = batchOperationService.batchAssignTasks(taskIds, assigneeId);

            assertTrue(response.getSuccess());
            assertEquals(2, response.getSuccessCount());
            assertEquals(0, response.getFailureCount());
            assertTrue(response.isAllSuccess());
        }

        @Test
        void batchAssignTasks_PartialFailure() {
            List<Long> taskIds = Arrays.asList(1L, 999L);
            Long assigneeId = 200L;

            when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask1));
            when(taskRepository.findById(999L)).thenReturn(Optional.empty());
            when(taskRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testTask1));

            var response = batchOperationService.batchAssignTasks(taskIds, assigneeId);

            assertFalse(response.getSuccess());
            assertEquals(1, response.getSuccessCount());
            assertEquals(1, response.getFailureCount());
        }

        @Test
        void batchAssignTasks_EmptyList() {
            List<Long> taskIds = Collections.emptyList();
            Long assigneeId = 200L;

            assertThrows(IllegalArgumentException.class, () -> {
                batchOperationService.batchAssignTasks(taskIds, assigneeId);
            });
        }
    }

    @Nested
    class BatchDeleteProjectsTests {

        @Test
        void batchDeleteProjects_AllSuccess() {
            List<Long> projectIds = Arrays.asList(1L, 2L);
            Long userId = 1L;

            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
            when(projectRepository.findById(2L)).thenReturn(Optional.of(testProject2));
            doNothing().when(projectRepository).deleteAll(anyList());

            var response = batchOperationService.batchDeleteProjects(projectIds, userId, User.Role.MANAGER);

            assertTrue(response.getSuccess());
            assertEquals(2, response.getSuccessCount());
            assertEquals(0, response.getFailureCount());
            assertTrue(response.isAllSuccess());
        }

        @Test
        void batchDeleteProjects_PermissionCheck() {
            testProject1.setManagerId(999L);
            List<Long> projectIds = Collections.singletonList(1L);
            Long userId = 100L;

            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));

            var response = batchOperationService.batchDeleteProjects(projectIds, userId, User.Role.MANAGER);

            assertFalse(response.getSuccess());
            assertEquals(0, response.getSuccessCount());
            assertEquals(1, response.getFailureCount());
            assertEquals("PERMISSION_DENIED", response.getErrors().get(0).getErrorCode());

            verify(projectRepository, never()).deleteAll(anyList());
        }

        @Test
        void batchDeleteProjects_EmptyList() {
            List<Long> projectIds = Collections.emptyList();
            Long userId = 100L;

            assertThrows(IllegalArgumentException.class, () -> {
                batchOperationService.batchDeleteProjects(projectIds, userId, User.Role.MANAGER);
            });
        }
    }

    @Nested
    class BatchDeleteItemsTests {

        @Test
        void batchDeleteItems_TenderType() {
            String itemType = "tender";
            List<Long> ids = Collections.singletonList(1L);

            when(tenderRepository.findById(1L)).thenReturn(Optional.of(testTender1));
            doNothing().when(tenderRepository).deleteAll(anyList());

            var response = batchOperationService.batchDeleteItems(itemType, ids, 1L, User.Role.ADMIN);

            assertTrue(response.getSuccess());
            assertEquals(1, response.getSuccessCount());
            assertEquals(0, response.getFailureCount());
        }

        @Test
        void batchDeleteItems_TenderType_NonAdmin_ThrowsException() {
            String itemType = "tender";
            List<Long> ids = Collections.singletonList(1L);

            assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
                batchOperationService.batchDeleteItems(itemType, ids, 1L, User.Role.MANAGER);
            });
        }

        @Test
        void batchDeleteItems_TaskType() {
            String itemType = "task";
            List<Long> ids = Collections.singletonList(1L);

            when(taskRepository.findById(1L)).thenReturn(Optional.of(testTask1));
            doNothing().when(taskRepository).deleteAll(anyList());

            var response = batchOperationService.batchDeleteItems(itemType, ids, 1L, User.Role.MANAGER);

            assertTrue(response.getSuccess());
            assertEquals(1, response.getSuccessCount());
        }

        @Test
        void batchDeleteItems_ProjectType() {
            String itemType = "project";
            List<Long> ids = Collections.singletonList(1L);

            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
            doNothing().when(projectRepository).deleteAll(anyList());

            var response = batchOperationService.batchDeleteItems(itemType, ids, 1L, User.Role.MANAGER);

            assertTrue(response.getSuccess());
            assertEquals(1, response.getSuccessCount());
        }

        @Test
        void batchDeleteItems_UnsupportedType() {
            String itemType = "unsupported_type";
            List<Long> ids = Collections.singletonList(1L);

            assertThrows(IllegalArgumentException.class, () -> {
                batchOperationService.batchDeleteItems(itemType, ids, 1L, User.Role.MANAGER);
            });
        }

        @Test
        void batchDeleteItems_CaseInsensitive() {
            String itemType = "TENDER";
            List<Long> ids = Collections.singletonList(1L);

            when(tenderRepository.findById(1L)).thenReturn(Optional.of(testTender1));
            doNothing().when(tenderRepository).deleteAll(anyList());

            var response = batchOperationService.batchDeleteItems(itemType, ids, 1L, User.Role.ADMIN);

            assertTrue(response.getSuccess());
            assertEquals(1, response.getSuccessCount());
        }

        @Test
        void batchDeleteItems_ProjectTypeHonorsProjectOwnershipForManagers() {
            String itemType = "project";
            List<Long> ids = Collections.singletonList(1L);
            testProject1.setManagerId(999L);

            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));

            var response = batchOperationService.batchDeleteItems(itemType, ids, 1L, User.Role.MANAGER);

            assertFalse(response.getSuccess());
            assertEquals(0, response.getSuccessCount());
            assertEquals(1, response.getFailureCount());
            assertEquals("PERMISSION_DENIED", response.getErrors().get(0).getErrorCode());
            verify(projectRepository, never()).deleteAll(anyList());
        }

        @Test
        void batchDeleteItems_ProjectTypeAllowsAdminsAcrossProjects() {
            String itemType = "project";
            List<Long> ids = Collections.singletonList(1L);
            testProject1.setManagerId(999L);

            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
            doNothing().when(projectRepository).deleteAll(anyList());

            var response = batchOperationService.batchDeleteItems(itemType, ids, 1L, User.Role.ADMIN);

            assertTrue(response.getSuccess());
            assertEquals(1, response.getSuccessCount());
            verify(projectRepository).deleteAll(anyList());
        }
    }

    @Nested
    class BatchOperationBoundaryTests {

        @Test
        void batchOperation_ExceedsMaxBatchSize() {
            List<Long> tooManyIds = java.util.Collections.nCopies(101, 1L);
            Long userId = 100L;

            assertThrows(IllegalArgumentException.class, () -> {
                batchOperationService.batchClaimTenders(tooManyIds, userId);
            });
        }

        @Test
        void batchOperation_NullIds() {
            assertThrows(IllegalArgumentException.class, () -> {
                batchOperationService.batchClaimTenders(null, 100L);
            });
        }

        @Test
        void batchOperation_NullUserId() {
            assertThrows(IllegalArgumentException.class, () -> {
                batchOperationService.batchClaimTenders(Collections.singletonList(1L), null);
            });
        }

        @Test
        void batchOperation_NegativeUserId() {
            assertThrows(IllegalArgumentException.class, () -> {
                batchOperationService.batchClaimTenders(Collections.singletonList(1L), -1L);
            });
        }
    }

    @Nested
    class BatchUpdateProjectsTests {

        @Test
        void batchUpdateProjects_UpdateStatus_AllSuccess() {
            // Arrange
            List<Long> projectIds = Arrays.asList(1L, 2L);
            Long userId = 1L;
            BatchProjectUpdateRequest request = BatchProjectUpdateRequest.builder()
                    .projectIds(projectIds)
                    .status(Project.Status.EVALUATING)
                    .build();

            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
            when(projectRepository.findById(2L)).thenReturn(Optional.of(testProject2));
            when(projectRepository.saveAll(anyList())).thenReturn(Arrays.asList(testProject1, testProject2));

            // Act
            var response = batchOperationService.batchUpdateProjects(request, userId, User.Role.MANAGER);

            // Assert
            assertTrue(response.getSuccess());
            assertEquals(2, response.getSuccessCount());
            assertEquals(0, response.getFailureCount());
            assertEquals(Project.Status.EVALUATING, testProject1.getStatus());
            assertEquals(Project.Status.EVALUATING, testProject2.getStatus());
            verify(projectRepository, times(1)).saveAll(anyList());
        }

        @Test
        void batchUpdateProjects_UpdateManager_AllSuccess() {
            // Arrange
            List<Long> projectIds = Arrays.asList(1L, 2L);
            Long userId = 1L;
            Long newManagerId = 999L;
            BatchProjectUpdateRequest request = BatchProjectUpdateRequest.builder()
                    .projectIds(projectIds)
                    .managerId(newManagerId)
                    .build();

            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
            when(projectRepository.findById(2L)).thenReturn(Optional.of(testProject2));
            when(projectRepository.saveAll(anyList())).thenReturn(Arrays.asList(testProject1, testProject2));

            // Act
            var response = batchOperationService.batchUpdateProjects(request, userId, User.Role.ADMIN);

            // Assert
            assertTrue(response.getSuccess());
            assertEquals(2, response.getSuccessCount());
            assertEquals(newManagerId, testProject1.getManagerId());
            assertEquals(newManagerId, testProject2.getManagerId());
            verify(projectRepository, times(1)).saveAll(anyList());
        }

        @Test
        void batchUpdateProjects_UpdateStatusAndManager_AllSuccess() {
            // Arrange
            List<Long> projectIds = Collections.singletonList(1L);
            Long userId = 1L;
            Long newManagerId = 999L;
            BatchProjectUpdateRequest request = BatchProjectUpdateRequest.builder()
                    .projectIds(projectIds)
                    .status(Project.Status.EVALUATING)
                    .managerId(newManagerId)
                    .build();

            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
            when(projectRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testProject1));

            // Act
            var response = batchOperationService.batchUpdateProjects(request, userId, User.Role.ADMIN);

            // Assert
            assertTrue(response.getSuccess());
            assertEquals(Project.Status.EVALUATING, testProject1.getStatus());
            assertEquals(newManagerId, testProject1.getManagerId());
        }

        @Test
        void batchUpdateProjects_NoFieldsToUpdate_ThrowsException() {
            // Arrange
            BatchProjectUpdateRequest request = BatchProjectUpdateRequest.builder()
                    .projectIds(Collections.singletonList(1L))
                    .build();

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                batchOperationService.batchUpdateProjects(request, 1L, User.Role.ADMIN);
            });
        }

        @Test
        void batchUpdateProjects_NullRequest_ThrowsException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                batchOperationService.batchUpdateProjects(null, 1L, User.Role.ADMIN);
            });
        }

        @Test
        void batchUpdateProjects_PermissionDenied_ForNonManager() {
            // Arrange
            testProject1.setManagerId(999L); // Different from userId
            List<Long> projectIds = Collections.singletonList(1L);
            Long userId = 1L;
            BatchProjectUpdateRequest request = BatchProjectUpdateRequest.builder()
                    .projectIds(projectIds)
                    .status(Project.Status.EVALUATING)
                    .build();

            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));

            // Act
            var response = batchOperationService.batchUpdateProjects(request, userId, User.Role.MANAGER);

            // Assert
            assertFalse(response.getSuccess());
            assertEquals(0, response.getSuccessCount());
            assertEquals(1, response.getFailureCount());
            assertEquals("PERMISSION_DENIED", response.getErrors().get(0).getErrorCode());
            verify(projectRepository, never()).saveAll(anyList());
        }

        @Test
        void batchUpdateProjects_AdminCanUpdateAnyProject() {
            // Arrange
            testProject1.setManagerId(999L); // Different from userId
            List<Long> projectIds = Collections.singletonList(1L);
            Long userId = 1L;
            BatchProjectUpdateRequest request = BatchProjectUpdateRequest.builder()
                    .projectIds(projectIds)
                    .status(Project.Status.EVALUATING)
                    .build();

            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
            when(projectRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testProject1));

            // Act
            var response = batchOperationService.batchUpdateProjects(request, userId, User.Role.ADMIN);

            // Assert
            assertTrue(response.getSuccess());
            assertEquals(1, response.getSuccessCount());
            verify(projectRepository, times(1)).saveAll(anyList());
        }

        @Test
        void batchUpdateProjects_ProjectNotFound_PartialFailure() {
            // Arrange
            List<Long> projectIds = Arrays.asList(1L, 999L);
            Long userId = 1L;
            BatchProjectUpdateRequest request = BatchProjectUpdateRequest.builder()
                    .projectIds(projectIds)
                    .status(Project.Status.EVALUATING)
                    .build();

            when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject1));
            when(projectRepository.findById(999L)).thenReturn(Optional.empty());
            when(projectRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testProject1));

            // Act
            var response = batchOperationService.batchUpdateProjects(request, userId, User.Role.ADMIN);

            // Assert
            assertFalse(response.getSuccess());
            assertEquals(1, response.getSuccessCount());
            assertEquals(1, response.getFailureCount());
            assertEquals("NOT_FOUND", response.getErrors().get(0).getErrorCode());
        }
    }

    @Nested
    class BatchApproveFeesTests {

        @Test
        void batchApproveFees_AllSuccess() {
            // Arrange
            List<Long> feeIds = Arrays.asList(1L, 2L);
            Long userId = 1L;
            BatchApproveFeesRequest request = BatchApproveFeesRequest.builder()
                    .feeIds(feeIds)
                    .paidBy("Finance Department")
                    .build();

            when(feeRepository.findById(1L)).thenReturn(Optional.of(testFee1));
            when(feeRepository.findById(2L)).thenReturn(Optional.of(testFee2));
            when(feeRepository.saveAll(anyList())).thenReturn(Arrays.asList(testFee1, testFee2));

            // Act
            var response = batchOperationService.batchApproveFees(request, userId);

            // Assert
            assertTrue(response.getSuccess());
            assertEquals(2, response.getSuccessCount());
            assertEquals(0, response.getFailureCount());
            assertEquals(Fee.Status.PAID, testFee1.getStatus());
            assertEquals(Fee.Status.PAID, testFee2.getStatus());
            assertNotNull(testFee1.getPaymentDate());
            assertNotNull(testFee2.getPaymentDate());
            assertEquals("Finance Department", testFee1.getPaidBy());
            verify(feeRepository, times(1)).saveAll(anyList());
        }

        @Test
        void batchApproveFees_OnlyPendingFeesCanBeApproved() {
            // Arrange
            testFee1.setStatus(Fee.Status.PAID); // Already paid
            List<Long> feeIds = Arrays.asList(1L, 2L);
            Long userId = 1L;
            BatchApproveFeesRequest request = BatchApproveFeesRequest.builder()
                    .feeIds(feeIds)
                    .paidBy("Finance Department")
                    .build();

            when(feeRepository.findById(1L)).thenReturn(Optional.of(testFee1));
            when(feeRepository.findById(2L)).thenReturn(Optional.of(testFee2));
            when(feeRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testFee2));

            // Act
            var response = batchOperationService.batchApproveFees(request, userId);

            // Assert
            assertFalse(response.getSuccess());
            assertEquals(1, response.getSuccessCount());
            assertEquals(1, response.getFailureCount());
            assertEquals("INVALID_STATUS", response.getErrors().get(0).getErrorCode());
        }

        @Test
        void batchApproveFees_FeeNotFound_PartialFailure() {
            // Arrange
            List<Long> feeIds = Arrays.asList(1L, 999L);
            Long userId = 1L;
            BatchApproveFeesRequest request = BatchApproveFeesRequest.builder()
                    .feeIds(feeIds)
                    .build();

            when(feeRepository.findById(1L)).thenReturn(Optional.of(testFee1));
            when(feeRepository.findById(999L)).thenReturn(Optional.empty());
            when(feeRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testFee1));

            // Act
            var response = batchOperationService.batchApproveFees(request, userId);

            // Assert
            assertFalse(response.getSuccess());
            assertEquals(1, response.getSuccessCount());
            assertEquals(1, response.getFailureCount());
            assertEquals("NOT_FOUND", response.getErrors().get(0).getErrorCode());
        }

        @Test
        void batchApproveFees_NullRequest_ThrowsException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                batchOperationService.batchApproveFees(null, 1L);
            });
        }

        @Test
        void batchApproveFees_EmptyList_ThrowsException() {
            // Arrange
            BatchApproveFeesRequest request = BatchApproveFeesRequest.builder()
                    .feeIds(Collections.emptyList())
                    .build();

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                batchOperationService.batchApproveFees(request, 1L);
            });
        }

        @Test
        void batchApproveFees_UsesDefaultPaidByWhenNotProvided() {
            // Arrange
            List<Long> feeIds = Collections.singletonList(1L);
            Long userId = 1L;
            BatchApproveFeesRequest request = BatchApproveFeesRequest.builder()
                    .feeIds(feeIds)
                    .paidBy(null) // No paidBy specified
                    .payerId(userId)
                    .build();

            when(feeRepository.findById(1L)).thenReturn(Optional.of(testFee1));
            when(feeRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testFee1));

            // Act
            var response = batchOperationService.batchApproveFees(request, userId);

            // Assert
            assertTrue(response.getSuccess());
            assertNotNull(testFee1.getPaidBy());
            assertTrue(testFee1.getPaidBy().contains("System"));
        }

        @Test
        void batchApproveFees_SanitizesPaidByField() {
            // Arrange
            List<Long> feeIds = Collections.singletonList(1L);
            Long userId = 1L;
            String maliciousInput = "<script>alert('xss')</script>Finance Department";
            // InputSanitizer.sanitizeString doesn't remove HTML, but stripHtml does
            // The service applies both: sanitizeString then stripHtml
            String expectedSanitized = "Finance Department"; // stripHtml removes all tags

            BatchApproveFeesRequest request = BatchApproveFeesRequest.builder()
                    .feeIds(feeIds)
                    .paidBy(maliciousInput)
                    .build();

            when(feeRepository.findById(1L)).thenReturn(Optional.of(testFee1));
            when(feeRepository.saveAll(anyList())).thenReturn(Collections.singletonList(testFee1));

            // Act
            var response = batchOperationService.batchApproveFees(request, userId);

            // Assert
            assertTrue(response.getSuccess());
            // The paidBy field should contain the sanitized value without HTML tags
            assertEquals(expectedSanitized, testFee1.getPaidBy());
            assertFalse(testFee1.getPaidBy().contains("<script>"));
        }
    }

    private void assertNotNull(Object obj) {
        if (obj == null) {
            throw new AssertionError("Expected object to not be null");
        }
    }

    private void assertNotEquals(String unexpected, String actual) {
        if (unexpected.equals(actual)) {
            throw new AssertionError("Expected values to be different but both are: " + unexpected);
        }
    }

    private void assertFalse(boolean condition) {
        if (condition) {
            throw new AssertionError("Expected condition to be false");
        }
    }
}
