package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.core.TenderEvaluationFormPolicy;
import com.xiyu.bid.tender.dto.TenderEvaluationDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;
import com.xiyu.bid.tender.dto.EvaluationBasicDTO;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluation.BidRecommendation;
import com.xiyu.bid.tender.entity.TenderEvaluation.EvaluationStatus;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Failing-tests-first contract for the upcoming TenderEvaluationSubmissionService.
 *
 * <p>This file MUST fail to compile / run today (RED phase) because
 * {@link com.xiyu.bid.tender.service.TenderEvaluationSubmissionService} and
 * {@link com.xiyu.bid.tender.core.TenderEvaluationFormPolicy} do not yet exist.
 * Phase 3 will create them and re-run these tests to drive them to GREEN.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenderEvaluationSubmissionServiceTest {

    private static final Long TENDER_ID = 1L;
    private static final Long EVALUATOR_ID = 9L;
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 5, 11, 10, 30);

    @Mock
    private TenderEvaluationRepository evaluationRepository;

    @Mock
    private TenderRepository tenderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenderProjectAccessGuard accessGuard;

    @Mock
    private TenderAssignmentPermissions permissions;

    @Mock
    private TenderEvaluationNotificationService notificationService;

    private TenderEvaluationSubmissionNotifier notifier;
    private Clock fixedClock;
    private TenderEvaluationSubmissionService service;

    @BeforeEach
    void setUp() {
        notifier = new TenderEvaluationSubmissionNotifier(notificationService);
        fixedClock = Clock.fixed(
                FIXED_NOW.atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        service = new TenderEvaluationSubmissionService(
                evaluationRepository,
                tenderRepository,
                userRepository,
                accessGuard,
                permissions,
                notifier,
                fixedClock
        );
        // Default permissive — individual tests override for forbidden cases.
        // lenient() because not every test path consults both flags.
        org.mockito.Mockito.lenient()
                .when(permissions.canFill(any(), any())).thenReturn(true);
        org.mockito.Mockito.lenient()
                .when(permissions.canDecide(any(), any())).thenReturn(true);
    }

    // ---------- helpers ----------

    private Tender tender() {
        return Tender.builder().id(TENDER_ID).title("测试标讯").status(Tender.Status.TRACKING).build();
    }

    private User evaluator() {
        return User.builder().id(EVALUATOR_ID).username("alice").build();
    }

    private TenderEvaluationSubmitRequest fullValidRequest() {
        return new TenderEvaluationSubmitRequest(
                BidRecommendation.RECOMMEND,
                null, null, null
        );
    }

    private TenderEvaluationSubmitRequest partialDraftRequest() {
        return new TenderEvaluationSubmitRequest(
                null, null, null, null);
    }

    private void stubTenderAndEvaluator() {
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender()));
        when(userRepository.findById(EVALUATOR_ID)).thenReturn(Optional.of(evaluator()));
    }

    // ---------- 1. loadOrInitDraft ----------

    @Test
    @DisplayName("loadOrInitDraft: 已存在记录时返回当前 DTO")
    void loadOrInitDraft_existing_returnsCurrentRecord() {
        TenderEvaluation existing = TenderEvaluation.builder()
                .id(50L).tenderId(TENDER_ID).evaluatorId(EVALUATOR_ID)
                .evaluationStatus(EvaluationStatus.DRAFT)
                
                .build();
        stubTenderAndEvaluator();
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.of(existing));

        TenderEvaluationDTO dto = service.loadOrInitDraft(TENDER_ID, EVALUATOR_ID);

        assertThat(dto).isNotNull();
        assertThat(dto.tenderId()).isEqualTo(TENDER_ID);
        assertThat(dto.evaluationStatus()).isEqualTo(EvaluationStatus.DRAFT);
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    @DisplayName("loadOrInitDraft: 不存在时返回空白 DRAFT 且不持久化")
    void loadOrInitDraft_missing_returnsNewDraft() {
        stubTenderAndEvaluator();
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.empty());

        TenderEvaluationDTO dto = service.loadOrInitDraft(TENDER_ID, EVALUATOR_ID);

        assertThat(dto.tenderId()).isEqualTo(TENDER_ID);
        assertThat(dto.evaluatorId()).isEqualTo(EVALUATOR_ID);
        assertThat(dto.evaluationStatus()).isEqualTo(EvaluationStatus.DRAFT);
        assertThat(dto.submittedAt()).isNull();
        verify(evaluationRepository, never()).save(any());
    }

    // ---------- 2. saveDraft ----------

    @Test
    @DisplayName("saveDraft: 已有 DRAFT 时覆盖字段并保持 DRAFT")
    void saveDraft_existingDraft_overwritesFields() {
        TenderEvaluation existing = TenderEvaluation.builder()
                .id(50L).tenderId(TENDER_ID).evaluatorId(EVALUATOR_ID)
                .evaluationStatus(EvaluationStatus.DRAFT)
                
                .build();
        stubTenderAndEvaluator();
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.of(existing));
        when(evaluationRepository.save(any(TenderEvaluation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TenderEvaluationDTO dto = service.saveDraft(TENDER_ID, fullValidRequest(), EVALUATOR_ID);

        assertThat(dto.evaluationStatus()).isEqualTo(EvaluationStatus.DRAFT);
        verify(evaluationRepository).save(any(TenderEvaluation.class));
    }

    @Test
    @DisplayName("saveDraft: 不存在时新建 DRAFT 并持久化请求字段")
    void saveDraft_newRecord_persistsAsDraft() {
        stubTenderAndEvaluator();
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.empty());
        when(evaluationRepository.save(any(TenderEvaluation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TenderEvaluationDTO dto = service.saveDraft(TENDER_ID, fullValidRequest(), EVALUATOR_ID);

        assertThat(dto.evaluationStatus()).isEqualTo(EvaluationStatus.DRAFT);
        verify(evaluationRepository).save(any(TenderEvaluation.class));
    }

    @Test
    @DisplayName("saveDraft: 已提交记录不允许再次草稿保存，抛 IllegalStateException")
    void saveDraft_alreadySubmitted_throwsOrRefuses() {
        TenderEvaluation submitted = TenderEvaluation.builder()
                .id(50L).tenderId(TENDER_ID).evaluatorId(EVALUATOR_ID)
                .evaluationStatus(EvaluationStatus.SUBMITTED)
                .submittedAt(LocalDateTime.of(2026, 5, 1, 9, 0))
                .build();
        stubTenderAndEvaluator();
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.of(submitted));

        assertThatThrownBy(() -> service.saveDraft(TENDER_ID, fullValidRequest(), EVALUATOR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("submitted");

        verify(evaluationRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveDraft: 部分字段也可持久化（草稿无校验）")
    void saveDraft_partialFields_persistsWhatGiven() {
        stubTenderAndEvaluator();
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.empty());
        when(evaluationRepository.save(any(TenderEvaluation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TenderEvaluationDTO dto = service.saveDraft(TENDER_ID, partialDraftRequest(), EVALUATOR_ID);

        assertThat(dto.evaluationStatus()).isEqualTo(EvaluationStatus.DRAFT);
        verify(evaluationRepository).save(any(TenderEvaluation.class));
    }

    // ---------- 3. submit ----------

    @Test
    @DisplayName("submit: 合法输入将状态置 SUBMITTED 并打 submittedAt 时间戳")
    void submit_validInput_changesStatusAndStampsTime() {
        // Phase 3 alignment: project's Mockito MockMaker is configured as
        // mock-maker-subclass (not inline), so we cannot mockStatic the policy.
        // Instead, pass a fully-valid request that the REAL policy accepts —
        // this also tests the integration between service and policy.
        TenderEvaluation existingDraft = TenderEvaluation.builder()
                .id(50L).tenderId(TENDER_ID).evaluatorId(EVALUATOR_ID)
                .evaluationStatus(EvaluationStatus.DRAFT)
                .build();
        stubTenderAndEvaluator();
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.of(existingDraft));
        when(evaluationRepository.save(any(TenderEvaluation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TenderEvaluationDTO dto = service.submit(TENDER_ID, fullValidRequest(), EVALUATOR_ID);

        assertThat(dto.evaluationStatus()).isEqualTo(EvaluationStatus.SUBMITTED);
        assertThat(dto.submittedAt()).isEqualTo(FIXED_NOW);
        assertThat(dto.evaluatorId()).isEqualTo(EVALUATOR_ID);
        verify(evaluationRepository).save(any(TenderEvaluation.class));
    }

    @Test
    @DisplayName("submit: 非法输入（policy 返回错误）抛异常且不持久化")
    void submit_invalidInput_throwsAndDoesNotPersist() {
        TenderEvaluation existingDraft = TenderEvaluation.builder()
                .id(50L).tenderId(TENDER_ID).evaluatorId(EVALUATOR_ID)
                .evaluationStatus(EvaluationStatus.DRAFT)
                .build();
        stubTenderAndEvaluator();
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.of(existingDraft));

        TenderEvaluationSubmitRequest invalid = new TenderEvaluationSubmitRequest(
                null,
                new EvaluationBasicDTO(0, BigDecimal.ZERO, "", "", "", "", "", "", BigDecimal.ZERO),
                null, null);

        assertThatThrownBy(() ->
                service.submit(TENDER_ID, invalid, EVALUATOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("计划入围供应商数量");

        verify(evaluationRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit: 二次提交已 SUBMITTED 的记录抛 IllegalStateException（选择 throw 语义）")
    void submit_secondTimeOnSubmitted_isIdempotentOrThrows() {
        // Phase 3 alignment: drop mockStatic; fullValidRequest already passes real policy.
        TenderEvaluation alreadySubmitted = TenderEvaluation.builder()
                .id(50L).tenderId(TENDER_ID).evaluatorId(EVALUATOR_ID)
                .evaluationStatus(EvaluationStatus.SUBMITTED)
                .submittedAt(LocalDateTime.of(2026, 5, 1, 9, 0))
                .build();
        stubTenderAndEvaluator();
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.of(alreadySubmitted));

        assertThatThrownBy(() -> service.submit(TENDER_ID, fullValidRequest(), EVALUATOR_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already submitted");

        verify(evaluationRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit: 标讯不存在抛 ResourceNotFoundException")
    void submit_tenderNotFound_throwsResourceNotFound() {
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(TENDER_ID, fullValidRequest(), EVALUATOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(evaluationRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit: 评估人不存在抛 ResourceNotFoundException")
    void submit_evaluatorNotFound_throwsResourceNotFound() {
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender()));
        when(userRepository.findById(EVALUATOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(TENDER_ID, fullValidRequest(), EVALUATOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(evaluationRepository, never()).save(any());
    }

    // ---------- 4. IDOR / access-guard enforcement (C1) ----------

    @Test
    @DisplayName("submit: 无权访问该标讯时直接抛 AccessDeniedException（不持久化）")
    void submit_unauthorizedUser_throwsForbidden() {
        stubTenderAndEvaluator();
        org.mockito.Mockito.doThrow(new AccessDeniedException("forbidden"))
                .when(accessGuard).assertCanAccessTender(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> service.submit(TENDER_ID, fullValidRequest(), EVALUATOR_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(evaluationRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveDraft: 无权访问该标讯时直接抛 AccessDeniedException（不持久化）")
    void saveDraft_unauthorizedUser_throwsForbidden() {
        stubTenderAndEvaluator();
        org.mockito.Mockito.doThrow(new AccessDeniedException("forbidden"))
                .when(accessGuard).assertCanAccessTender(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> service.saveDraft(TENDER_ID, fullValidRequest(), EVALUATOR_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(evaluationRepository, never()).save(any());
    }

    @Test
    @DisplayName("loadOrInitDraft: 无权访问该标讯时直接抛 AccessDeniedException")
    void loadOrInitDraft_unauthorizedUser_throwsForbidden() {
        stubTenderAndEvaluator();
        org.mockito.Mockito.doThrow(new AccessDeniedException("forbidden"))
                .when(accessGuard).assertCanAccessTender(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> service.loadOrInitDraft(TENDER_ID, EVALUATOR_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------- 5. H4: submit without prior DRAFT — accepted ----------

    @Test
    @DisplayName("submit: 无前置 DRAFT 时一步到位创建并标记为 SUBMITTED（UX-friendly）")
    void submit_withoutPriorDraft_createsAndSubmitsInOneStep() {
        stubTenderAndEvaluator();
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.empty());
        when(evaluationRepository.save(any(TenderEvaluation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TenderEvaluationDTO dto = service.submit(TENDER_ID, fullValidRequest(), EVALUATOR_ID);

        assertThat(dto.evaluationStatus()).isEqualTo(EvaluationStatus.SUBMITTED);
        assertThat(dto.submittedAt()).isEqualTo(FIXED_NOW);
        verify(evaluationRepository).save(any(TenderEvaluation.class));
    }

    // ---------- 6. H5: tender.status 在 submit 后 transition 到 EVALUATED ----------

    @Test
    @DisplayName("submit: 提交后将 tender.status 推进到 EVALUATED")
    void submit_movesTenderStatusToEvaluated() {
        Tender pending = Tender.builder()
                .id(TENDER_ID).title("测试标讯").status(Tender.Status.TRACKING).build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(pending));
        when(userRepository.findById(EVALUATOR_ID)).thenReturn(Optional.of(evaluator()));
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.empty());
        when(evaluationRepository.save(any(TenderEvaluation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        service.submit(TENDER_ID, fullValidRequest(), EVALUATOR_ID);

        assertThat(pending.getStatus()).isEqualTo(Tender.Status.EVALUATED);
    }

    @Test
    @DisplayName("submit: 标讯已是 EVALUATED 时不再二次切换 / 不持久化 tender")
    void submit_skipsTenderStatusUpdate_whenAlreadyEvaluated() {
        Tender already = Tender.builder()
                .id(TENDER_ID).title("测试标讯").status(Tender.Status.EVALUATED).build();
        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(already));
        when(userRepository.findById(EVALUATOR_ID)).thenReturn(Optional.of(evaluator()));
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.empty());
        when(evaluationRepository.save(any(TenderEvaluation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.submit(TENDER_ID, fullValidRequest(), EVALUATOR_ID);

        assertThat(already.getStatus()).isEqualTo(Tender.Status.EVALUATED);
        verify(tenderRepository, never()).save(any(Tender.class));
    }

    // ---------- 7. instance-level permissions (canFill / canDecide) ----------

    @Test
    @DisplayName("loadOrInitDraft: DTO 中 canFillEvaluation/canDecideBid 由 permissions 决定")
    void loadOrInitDraft_populatesPermissionFlags() {
        stubTenderAndEvaluator();
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.empty());
        when(permissions.canFill(TENDER_ID, EVALUATOR_ID)).thenReturn(true);
        when(permissions.canDecide(TENDER_ID, EVALUATOR_ID)).thenReturn(false);

        TenderEvaluationDTO dto = service.loadOrInitDraft(TENDER_ID, EVALUATOR_ID);

        assertThat(dto.canFillEvaluation()).isTrue();
        assertThat(dto.canDecideBid()).isFalse();
    }

    @Test
    @DisplayName("loadOrInitDraft: 已存在记录也会回填 permissions 标志")
    void loadOrInitDraft_existingAlsoCarriesFlags() {
        TenderEvaluation existing = TenderEvaluation.builder()
                .id(50L).tenderId(TENDER_ID).evaluatorId(EVALUATOR_ID)
                .evaluationStatus(EvaluationStatus.SUBMITTED)
                .build();
        stubTenderAndEvaluator();
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.of(existing));
        when(permissions.canFill(TENDER_ID, EVALUATOR_ID)).thenReturn(false);
        when(permissions.canDecide(TENDER_ID, EVALUATOR_ID)).thenReturn(true);

        TenderEvaluationDTO dto = service.loadOrInitDraft(TENDER_ID, EVALUATOR_ID);

        assertThat(dto.canFillEvaluation()).isFalse();
        assertThat(dto.canDecideBid()).isTrue();
    }

    @Test
    @DisplayName("saveDraft: 非 assignee (canFill=false) → AccessDeniedException，不持久化")
    void saveDraft_nonAssignee_throwsForbidden() {
        stubTenderAndEvaluator();
        when(permissions.canFill(TENDER_ID, EVALUATOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.saveDraft(TENDER_ID, fullValidRequest(), EVALUATOR_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(evaluationRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit: 非 assignee (canFill=false) → AccessDeniedException，不持久化")
    void submit_nonAssignee_throwsForbidden() {
        stubTenderAndEvaluator();
        when(permissions.canFill(TENDER_ID, EVALUATOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.submit(TENDER_ID, fullValidRequest(), EVALUATOR_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(evaluationRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit: 成功返回的 DTO 也带 permissions 标志")
    void submit_returnsDtoWithFlags() {
        stubTenderAndEvaluator();
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.empty());
        when(evaluationRepository.save(any(TenderEvaluation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(permissions.canFill(TENDER_ID, EVALUATOR_ID)).thenReturn(true);
        when(permissions.canDecide(TENDER_ID, EVALUATOR_ID)).thenReturn(true);

        TenderEvaluationDTO dto = service.submit(TENDER_ID, fullValidRequest(), EVALUATOR_ID);

        assertThat(dto.canFillEvaluation()).isTrue();
        assertThat(dto.canDecideBid()).isTrue();
    }

    @Test
    @DisplayName("loadOrInitDraft: 未分配标讯（permissions 全 false）DTO 标志也为 false")
    void loadOrInitDraft_unassignedTender_bothFlagsFalse() {
        stubTenderAndEvaluator();
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.empty());
        when(permissions.canFill(TENDER_ID, EVALUATOR_ID)).thenReturn(false);
        when(permissions.canDecide(TENDER_ID, EVALUATOR_ID)).thenReturn(false);

        TenderEvaluationDTO dto = service.loadOrInitDraft(TENDER_ID, EVALUATOR_ID);

        assertThat(dto.canFillEvaluation()).isFalse();
        assertThat(dto.canDecideBid()).isFalse();
    }

    @Test
    @DisplayName("submit: 必须经由 TenderEvaluationFormPolicy.validate 校验请求")
    void submit_usesPolicyForValidation() {
        TenderEvaluation existingDraft = TenderEvaluation.builder()
                .id(50L).tenderId(TENDER_ID).evaluatorId(EVALUATOR_ID)
                .evaluationStatus(EvaluationStatus.DRAFT)
                .build();
        stubTenderAndEvaluator();
        when(evaluationRepository.findByTenderId(TENDER_ID)).thenReturn(Optional.of(existingDraft));

        TenderEvaluationSubmitRequest invalid = new TenderEvaluationSubmitRequest(
                null,
                new EvaluationBasicDTO(0, new BigDecimal("-1"), "", "", "", "", "", "", new BigDecimal("-1")),
                null, null);

        assertThatThrownBy(() -> service.submit(TENDER_ID, invalid, EVALUATOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Validation failed");

        verify(evaluationRepository, never()).save(any());
    }
}
