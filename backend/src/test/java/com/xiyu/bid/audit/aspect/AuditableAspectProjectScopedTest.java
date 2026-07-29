package com.xiyu.bid.audit.aspect;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.aspect.AuditableAspect;
import com.xiyu.bid.audit.core.AuditActionPolicy;
import com.xiyu.bid.audit.service.AuditLogService;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.dto.ApiResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-XXX 回归测试：验证 @Auditable(projectScoped) 注解驱动 + getProjectId() 反射提取。
 *
 * <p>背景：原 AuditableAspect 有两条 bug 路径：
 * <ul>
 *   <li>Long args-first：把任意 Long 入参当 projectId，导致 performanceId/feeId 错写为 project_id</li>
 *   <li>getId() fallback：fallback 到 getId()，对 FeeDTO.getId() 返回 feeId 也被错当 projectId</li>
 * </ul>
 * 修复：移除两条 bug 路径，只通过显式 getProjectId() 方法提取。
 *
 * <p>关键场景：
 * <ul>
 *   <li>非项目操作（Performance）+ Long 入参 → projectId=null（修复污染）</li>
 *   <li>项目操作（Project）+ Long 入参 + 返回值 ProjectDTO.getProjectId() → 正确提取</li>
 *   <li>项目操作（Fee）+ 对象入参 FeeCreateRequest.getProjectId() → 正确提取</li>
 *   <li>项目操作（Fee）+ Long 入参 + 返回值 FeeDTO.getProjectId() → 正确提取（updateFee/markAsPaid）</li>
 *   <li>项目操作（Fee）+ Long 入参 + void 返回值 → 改为返回 FeeDTO 后正确提取（deleteFee）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuditableAspectProjectScopedTest {

    @Mock
    private IAuditLogService auditLogService;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;

    private AuditableAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new AuditableAspect(auditLogService, new AuditActionPolicy());
        when(joinPoint.getSignature()).thenReturn(signature);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 修复核心：非项目操作（projectScoped=false）+ 第一参 Long（performanceId=31）
     * → audit_logs.project_id 必须为 null，不能错写为 performanceId。
     *
     * <p>复现用户报告的 bug：哈睿（11651）更新业绩（performance.id=31）
     * 被错写到 /project/31 的项目动态中。
     */
    @Test
    void nonProjectScopedLongArgDoesNotPolluteProjectId() throws Throwable {
        when(signature.getMethod()).thenReturn(method("updatePerformance"));
        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getArgs()).thenReturn(new Object[]{31L});

        aspect.auditMethod(joinPoint);

        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getProjectId())
                .as("非项目操作（projectScoped=false）的 Long 入参不得被错写为 project_id")
                .isNull();
    }

    /**
     * 项目操作（projectScoped=true）+ 第一参 Long projectId + 返回值 ProjectDTO.getProjectId()
     * → projectId 正确写入。
     *
     * <p>场景：ProjectService.updateProjectStatus(Long id, Project.Status status) 返回 ProjectDTO
     */
    @Test
    void projectScopedUpdateProjectStatusExtractsFromReturnValue() throws Throwable {
        when(signature.getMethod()).thenReturn(method("updateProjectStatus"));
        when(joinPoint.proceed()).thenReturn(new ProjectLikeRecord(42L));
        when(joinPoint.getArgs()).thenReturn(new Object[]{42L});

        aspect.auditMethod(joinPoint);

        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getProjectId())
                .as("projectScoped=true 时从返回值 ProjectDTO.getProjectId() 提取")
                .isEqualTo(42L);
    }

    /**
     * 项目操作（projectScoped=true）+ 对象入参 FeeCreateRequest.getProjectId()
     * → projectId 正确写入。
     *
     * <p>场景：FeeService.createFee(FeeCreateRequest request)
     */
    @Test
    void projectScopedCreateFeeExtractsFromArgObject() throws Throwable {
        when(signature.getMethod()).thenReturn(method("createFee"));
        when(joinPoint.proceed()).thenReturn(new FeeLikeRecord(99L, 55L));
        when(joinPoint.getArgs()).thenReturn(new Object[]{new FeeCreateRequestLike(55L)});

        aspect.auditMethod(joinPoint);

        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getProjectId())
                .as("projectScoped=true 时从入参 FeeCreateRequest.getProjectId() 提取")
                .isEqualTo(55L);
    }

    /**
     * 项目操作（projectScoped=true）+ Long 入参（feeId）+ 返回值 FeeDTO.getProjectId()
     * → projectId 从返回值正确提取，不会错写为 feeId。
     *
     * <p>场景：FeeService.updateFee(Long id, FeeUpdateRequest request) 返回 FeeDTO
     */
    @Test
    void projectScopedUpdateFeeExtractsFromReturnValueNotLongArg() throws Throwable {
        when(signature.getMethod()).thenReturn(method("updateFee"));
        when(joinPoint.proceed()).thenReturn(new FeeLikeRecord(101L, 77L));
        when(joinPoint.getArgs()).thenReturn(new Object[]{101L, new FeeUpdateRequestLike()});

        aspect.auditMethod(joinPoint);

        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getProjectId())
                .as("projectScoped=true 时从返回值 FeeDTO.getProjectId() 提取，不是 Long 入参 feeId")
                .isEqualTo(77L);
        assertThat(entryCaptor.getValue().getEntityId())
                .as("entityId 仍然正确记录为 feeId")
                .isEqualTo("101");
    }

    /**
     * 项目操作（projectScoped=true）+ Long 入参（feeId）+ 返回 FeeDTO
     * → deleteFee 改为返回 FeeDTO 后，projectId 从返回值正确提取。
     *
     * <p>场景：FeeService.deleteFee(Long id) 返回 FeeDTO（修复 void 返回值无法提取的问题）
     */
    @Test
    void projectScopedDeleteFeeExtractsFromReturnedFeeDTO() throws Throwable {
        when(signature.getMethod()).thenReturn(method("deleteFee"));
        when(joinPoint.proceed()).thenReturn(new FeeLikeRecord(88L, 66L));
        when(joinPoint.getArgs()).thenReturn(new Object[]{88L});

        aspect.auditMethod(joinPoint);

        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getProjectId())
                .as("deleteFee 返回 FeeDTO 后从 getProjectId() 正确提取")
                .isEqualTo(66L);
    }

    /**
     * 非项目操作（projectScoped=false）+ 对象入参无 getProjectId()
     * → projectId=null，不会 fallback 到 getId()。
     *
     * <p>场景：PerformanceController.update(Long id, PerformanceRequest) —— PerformanceRequest 无 getProjectId()
     */
    @Test
    void nonProjectScopedObjectWithoutGetProjectIdReturnsNull() throws Throwable {
        when(signature.getMethod()).thenReturn(method("updatePerformance"));
        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getArgs()).thenReturn(new Object[]{31L, new PerformanceRequestLike()});

        aspect.auditMethod(joinPoint);

        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getProjectId())
                .as("非项目操作 + 无 getProjectId() → projectId=null，不 fallback 到 getId()")
                .isNull();
    }

    /**
     * 项目操作（projectScoped=true）+ 对象入参 CalendarEventCreateRequest.getProjectId()
     * → projectId 正确写入。
     *
     * <p>场景：CalendarService.createEvent(CalendarEventCreateRequest) —— 项目关联事件
     */
    @Test
    void projectScopedCreateCalendarEventExtractsFromArgObject() throws Throwable {
        when(signature.getMethod()).thenReturn(method("createCalendarEvent"));
        when(joinPoint.proceed()).thenReturn(new CalendarEventLikeRecord(201L, 88L));
        when(joinPoint.getArgs()).thenReturn(new Object[]{new CalendarEventCreateRequestLike(88L)});

        aspect.auditMethod(joinPoint);

        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getProjectId())
                .as("projectScoped=true 时从入参 CalendarEventCreateRequest.getProjectId() 提取")
                .isEqualTo(88L);
    }

    /**
     * 项目操作（projectScoped=true）+ Long 入参（eventId）+ 返回值 CalendarEventDTO.getProjectId()
     * → projectId 从返回值正确提取，不会错写为 eventId。
     *
     * <p>场景：CalendarService.updateEvent(Long id, CalendarEventUpdateRequest) 返回 CalendarEventDTO
     */
    @Test
    void projectScopedUpdateCalendarEventExtractsFromReturnValueNotLongArg() throws Throwable {
        when(signature.getMethod()).thenReturn(method("updateCalendarEvent"));
        when(joinPoint.proceed()).thenReturn(new CalendarEventLikeRecord(202L, 99L));
        when(joinPoint.getArgs()).thenReturn(new Object[]{202L, new CalendarEventUpdateRequestLike()});

        aspect.auditMethod(joinPoint);

        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getProjectId())
                .as("projectScoped=true 时从返回值 CalendarEventDTO.getProjectId() 提取，不是 Long 入参 eventId")
                .isEqualTo(99L);
        assertThat(entryCaptor.getValue().getEntityId())
                .as("entityId 仍然正确记录为 eventId")
                .isEqualTo("202");
    }

    /**
     * 项目操作（projectScoped=true）+ Long 入参（eventId）+ 返回 CalendarEventDTO
     * → deleteEvent 改为返回 CalendarEventDTO 后，projectId 从返回值正确提取。
     *
     * <p>场景：CalendarService.deleteEvent(Long id) 返回 CalendarEventDTO（修复 void 返回值无法提取的问题）
     */
    @Test
    void projectScopedDeleteCalendarEventExtractsFromReturnedDTO() throws Throwable {
        when(signature.getMethod()).thenReturn(method("deleteCalendarEvent"));
        when(joinPoint.proceed()).thenReturn(new CalendarEventLikeRecord(203L, 77L));
        when(joinPoint.getArgs()).thenReturn(new Object[]{203L});

        aspect.auditMethod(joinPoint);

        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getProjectId())
                .as("deleteEvent 返回 CalendarEventDTO 后从 getProjectId() 正确提取")
                .isEqualTo(77L);
    }

    /**
     * PR #2212 回归：BidReviewAppService.submitForReview 改为返回 BidDocumentReviewViewDto
     * → projectId 从返回值正确提取，不再为 null。
     *
     * <p>场景：BidReviewAppService.submitForReview(Long projectId, List<Long> reviewerIds, Long submittedBy)
     * 原为 void 方法，导致 project_id=NULL，项目动态丢失"提交标书审核"记录。
     */
    @Test
    void projectScopedSubmitBidReviewExtractsFromReturnedDTO() throws Throwable {
        when(signature.getMethod()).thenReturn(method("submitBidReview"));
        when(joinPoint.proceed()).thenReturn(new BidReviewLikeRecord(301L, 501L));
        when(joinPoint.getArgs()).thenReturn(new Object[]{501L, java.util.List.of(1L), 10L});

        aspect.auditMethod(joinPoint);

        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getProjectId())
                .as("submitForReview 返回 BidDocumentReviewViewDto 后从 getProjectId() 正确提取")
                .isEqualTo(501L);
        assertThat(entryCaptor.getValue().getEntityId())
                .as("entityId 仍然正确记录为审核记录 ID（review.id）")
                .isEqualTo("301");
    }

    /**
     * PR #2212 回归：BidReviewAppService.approveBid 改为返回 BidDocumentReviewViewDto
     * → projectId 从返回值正确提取，不再为 null。
     *
     * <p>场景：BidReviewAppService.approveBid(Long projectId, Long currentUserId, String comment)
     * 原为 void 方法，导致 project_id=NULL，项目动态丢失"标书审核通过"记录。
     */
    @Test
    void projectScopedApproveBidExtractsFromReturnedDTO() throws Throwable {
        when(signature.getMethod()).thenReturn(method("approveBid"));
        when(joinPoint.proceed()).thenReturn(new BidReviewLikeRecord(302L, 502L));
        when(joinPoint.getArgs()).thenReturn(new Object[]{502L, 10L, "同意"});

        aspect.auditMethod(joinPoint);

        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getProjectId())
                .as("approveBid 返回 BidDocumentReviewViewDto 后从 getProjectId() 正确提取")
                .isEqualTo(502L);
        assertThat(entryCaptor.getValue().getEntityId())
                .as("entityId 仍然正确记录为审核记录 ID（review.id）")
                .isEqualTo("302");
    }

    /**
     * PR #2212 回归：BidReviewAppService.rejectBid 改为返回 BidDocumentReviewViewDto
     * → projectId 从返回值正确提取，不再为 null。
     *
     * <p>场景：BidReviewAppService.rejectBid(Long projectId, Long currentUserId, String reason)
     * 原为 void 方法，导致 project_id=NULL，项目动态丢失"标书审核驳回"记录。
     */
    @Test
    void projectScopedRejectBidExtractsFromReturnedDTO() throws Throwable {
        when(signature.getMethod()).thenReturn(method("rejectBid"));
        when(joinPoint.proceed()).thenReturn(new BidReviewLikeRecord(303L, 503L));
        when(joinPoint.getArgs()).thenReturn(new Object[]{503L, 10L, "不通过"});

        aspect.auditMethod(joinPoint);

        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getProjectId())
                .as("rejectBid 返回 BidDocumentReviewViewDto 后从 getProjectId() 正确提取")
                .isEqualTo(503L);
        assertThat(entryCaptor.getValue().getEntityId())
                .as("entityId 仍然正确记录为审核记录 ID（review.id）")
                .isEqualTo("303");
    }

    /**
     * PR #2212 回归：ProjectInitiationApprovalService.approve 改为返回 InitiationViewDto
     * → projectId 从返回值正确提取，不再为 null。
     *
     * <p>场景：ProjectInitiationApprovalService.approve(Long projectId, InitiationApprovalRequest req, Long currentUserId)
     * 原为 void 方法，导致 project_id=NULL，项目动态丢失"审核通过项目立项"记录。
     */
    @Test
    void projectScopedApproveInitiationExtractsFromReturnedDTO() throws Throwable {
        when(signature.getMethod()).thenReturn(method("approveInitiation"));
        when(joinPoint.proceed()).thenReturn(new InitiationViewLikeRecord(401L, 601L));
        when(joinPoint.getArgs()).thenReturn(new Object[]{601L, new InitiationApprovalRequestLike(), 5L});

        aspect.auditMethod(joinPoint);

        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getProjectId())
                .as("approve 返回 InitiationViewDto 后从 getProjectId() 正确提取")
                .isEqualTo(601L);
        assertThat(entryCaptor.getValue().getEntityId())
                .as("entityId 仍然正确记录为立项详情 ID（initiation.id）")
                .isEqualTo("401");
    }

    /**
     * PR #2212 回归：ProjectInitiationApprovalService.reject 改为返回 InitiationViewDto
     * → projectId 从返回值正确提取，不再为 null。
     *
     * <p>场景：ProjectInitiationApprovalService.reject(Long projectId, InitiationRejectionRequest req, Long currentUserId)
     * 原为 void 方法，导致 project_id=NULL，项目动态丢失"驳回项目立项"记录。
     */
    @Test
    void projectScopedRejectInitiationExtractsFromReturnedDTO() throws Throwable {
        when(signature.getMethod()).thenReturn(method("rejectInitiation"));
        when(joinPoint.proceed()).thenReturn(new InitiationViewLikeRecord(402L, 602L));
        when(joinPoint.getArgs()).thenReturn(new Object[]{602L, new InitiationRejectionRequestLike(), 5L});

        aspect.auditMethod(joinPoint);

        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getProjectId())
                .as("reject 返回 InitiationViewDto 后从 getProjectId() 正确提取")
                .isEqualTo(602L);
        assertThat(entryCaptor.getValue().getEntityId())
                .as("entityId 仍然正确记录为立项详情 ID（initiation.id）")
                .isEqualTo("402");
    }

    private static Method method(String name) {
        try {
            return TargetActions.class.getMethod(name);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    static final class TargetActions {
        /** 模拟 PerformanceController.update(Long id, ...) —— projectScoped 默认 false */
        @Auditable(action = "UPDATE", entityType = "Performance", description = "更新业绩")
        public String updatePerformance() {
            return "ok";
        }

        /** 模拟 ProjectService.updateProjectStatus(Long id, ...) —— projectScoped=true */
        @Auditable(action = "UPDATE_STATUS", entityType = "Project", description = "更新项目状态", projectScoped = true)
        public ProjectLikeRecord updateProjectStatus() {
            return new ProjectLikeRecord(42L);
        }

        /** 模拟 FeeService.createFee(FeeCreateRequest) —— projectScoped=true */
        @Auditable(action = "CREATE", entityType = "Fee", description = "Create new fee", projectScoped = true)
        public FeeLikeRecord createFee() {
            return new FeeLikeRecord(99L, 55L);
        }

        /** 模拟 FeeService.updateFee(Long id, FeeUpdateRequest) —— projectScoped=true */
        @Auditable(action = "UPDATE", entityType = "Fee", description = "Update fee", projectScoped = true)
        public FeeLikeRecord updateFee() {
            return new FeeLikeRecord(101L, 77L);
        }

        /** 模拟 FeeService.deleteFee(Long id) —— projectScoped=true，返回 FeeDTO */
        @Auditable(action = "DELETE", entityType = "Fee", description = "Delete fee", projectScoped = true)
        public FeeLikeRecord deleteFee() {
            return new FeeLikeRecord(88L, 66L);
        }

        /** 模拟 CalendarService.createEvent(CalendarEventCreateRequest) —— projectScoped=true */
        @Auditable(action = "CREATE", entityType = "CalendarEvent", description = "Created calendar event", projectScoped = true)
        public CalendarEventLikeRecord createCalendarEvent() {
            return new CalendarEventLikeRecord(201L, 88L);
        }

        /** 模拟 CalendarService.updateEvent(Long id, CalendarEventUpdateRequest) —— projectScoped=true */
        @Auditable(action = "UPDATE", entityType = "CalendarEvent", description = "Updated calendar event", projectScoped = true)
        public CalendarEventLikeRecord updateCalendarEvent() {
            return new CalendarEventLikeRecord(202L, 99L);
        }

        /** 模拟 CalendarService.deleteEvent(Long id) —— projectScoped=true，返回 CalendarEventDTO */
        @Auditable(action = "DELETE", entityType = "CalendarEvent", description = "Deleted calendar event", projectScoped = true)
        public CalendarEventLikeRecord deleteCalendarEvent() {
            return new CalendarEventLikeRecord(203L, 77L);
        }

        /** 模拟 BidReviewAppService.submitForReview(Long, List, Long) —— projectScoped=true，返回 BidDocumentReviewViewDto */
        @Auditable(action = "SUBMIT_BID_REVIEW", entityType = "BidDocumentReview",
                description = "提交标书审核", projectScoped = true)
        public BidReviewLikeRecord submitBidReview() {
            return new BidReviewLikeRecord(301L, 501L);
        }

        /** 模拟 BidReviewAppService.approveBid(Long, Long, String) —— projectScoped=true，返回 BidDocumentReviewViewDto */
        @Auditable(action = "APPROVE_BID", entityType = "BidDocumentReview",
                description = "标书审核通过", projectScoped = true)
        public BidReviewLikeRecord approveBid() {
            return new BidReviewLikeRecord(302L, 502L);
        }

        /** 模拟 BidReviewAppService.rejectBid(Long, Long, String) —— projectScoped=true，返回 BidDocumentReviewViewDto */
        @Auditable(action = "REJECT_BID", entityType = "BidDocumentReview",
                description = "标书审核驳回", projectScoped = true)
        public BidReviewLikeRecord rejectBid() {
            return new BidReviewLikeRecord(303L, 503L);
        }

        /** 模拟 ProjectInitiationApprovalService.approve(Long, req, Long) —— projectScoped=true，返回 InitiationViewDto */
        @Auditable(action = "APPROVE_INITIATION", entityType = "ProjectInitiationDetails",
                description = "审核通过项目立项", projectScoped = true)
        public InitiationViewLikeRecord approveInitiation() {
            return new InitiationViewLikeRecord(401L, 601L);
        }

        /** 模拟 ProjectInitiationApprovalService.reject(Long, req, Long) —— projectScoped=true，返回 InitiationViewDto */
        @Auditable(action = "REJECT_INITIATION", entityType = "ProjectInitiationDetails",
                description = "驳回项目立项", projectScoped = true)
        public InitiationViewLikeRecord rejectInitiation() {
            return new InitiationViewLikeRecord(402L, 602L);
        }
    }

    /** 模拟 ProjectDTO：id 即 projectId，通过 getProjectId() 显式返回。 */
    record ProjectLikeRecord(Long id) {
        public Long getProjectId() {
            return id;
        }

        public Long getId() {
            return id;
        }
    }

    /** 模拟 FeeDTO：含 id（feeId）和 projectId。 */
    record FeeLikeRecord(Long id, Long projectId) {
        public Long getId() {
            return id;
        }

        public Long getProjectId() {
            return projectId;
        }
    }

    /** 模拟 FeeCreateRequest：含 projectId。 */
    static final class FeeCreateRequestLike {
        private final Long projectId;

        FeeCreateRequestLike(Long projectId) {
            this.projectId = projectId;
        }

        public Long getProjectId() {
            return projectId;
        }
    }

    /** 模拟 FeeUpdateRequest：无 projectId（与真实 DTO 一致）。 */
    static final class FeeUpdateRequestLike {
    }

    /** 模拟 PerformanceRequest：无 projectId。 */
    static final class PerformanceRequestLike {
    }

    /** 模拟 CalendarEventDTO：含 id（eventId）和 projectId。 */
    record CalendarEventLikeRecord(Long id, Long projectId) {
        public Long getId() {
            return id;
        }

        public Long getProjectId() {
            return projectId;
        }
    }

    /** 模拟 CalendarEventCreateRequest：含 projectId。 */
    static final class CalendarEventCreateRequestLike {
        private final Long projectId;

        CalendarEventCreateRequestLike(Long projectId) {
            this.projectId = projectId;
        }

        public Long getProjectId() {
            return projectId;
        }
    }

    /** 模拟 CalendarEventUpdateRequest：无 projectId（与真实 DTO 一致）。 */
    static final class CalendarEventUpdateRequestLike {
    }

    /** 模拟 BidDocumentReviewViewDto：含 id（审核记录 ID）和 projectId。 */
    record BidReviewLikeRecord(Long id, Long projectId) {
        public Long getId() {
            return id;
        }

        public Long getProjectId() {
            return projectId;
        }
    }

    /** 模拟 InitiationViewDto：含 id（立项详情 ID）和 projectId。 */
    record InitiationViewLikeRecord(Long id, Long projectId) {
        public Long getId() {
            return id;
        }

        public Long getProjectId() {
            return projectId;
        }
    }

    /** 模拟 InitiationApprovalRequest：无 projectId（与真实 DTO 一致）。 */
    static final class InitiationApprovalRequestLike {
    }

    /** 模拟 InitiationRejectionRequest：无 projectId（与真实 DTO 一致）。 */
    static final class InitiationRejectionRequestLike {
    }
}
