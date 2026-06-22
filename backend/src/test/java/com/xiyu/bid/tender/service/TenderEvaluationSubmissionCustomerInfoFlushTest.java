package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.EvaluationCustomerInfoDTO;
import com.xiyu.bid.tender.dto.EvaluationRecommendationDTO;
import com.xiyu.bid.tender.dto.TenderEvaluationSubmitRequest;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import com.xiyu.bid.tender.repository.TenderEvaluationCustomerInfoRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CO-266: 验证前端保存路径（{@link TenderEvaluationSubmissionService#saveDraft} /
 * {@link TenderEvaluationSubmissionService#submit}）的 customerInfos 二次更新
 * 不会因 Hibernate INSERT-before-DELETE flush 顺序撞 uk_eval_role_info 唯一约束。
 *
 * <p>背景：原 {@link TenderEvaluationSubmissionMapper#applyRequest} 中的
 * {@code clear() + addAll()} 在同一事务内会触发 500 错误，导致数据丢失，
 * 投标系统评估表中不显示客户信息。
 *
 * <p>修复方案：Service 层先 {@code clear() + saveAndFlush()} 确保 DELETE SQL 落库，
 * 再 {@code addAll(newRows)} 执行 INSERT。
 */
@DataJpaTest
@ActiveProfiles("test")
class TenderEvaluationSubmissionCustomerInfoFlushTest {

    @Autowired private TenderRepository tenderRepository;
    @Autowired private TenderEvaluationRepository evaluationRepository;
    @Autowired private TenderEvaluationCustomerInfoRepository customerInfoRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectDocumentRepository projectDocumentRepository;

    private static final Clock FIXED_CLOCK = Clock.fixed(
            java.time.LocalDateTime.of(2026, 6, 18, 10, 0)
                    .atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault());

    private TenderEvaluationSubmissionService service;
    private Long tenderId;
    private Long evaluatorId;

    @BeforeEach
    void setUp() {
        // 创建测试标讯（TRACKING 状态允许 submit）
        Tender tender = Tender.builder()
                .title("CO-266 测试标讯")
                .status(Tender.Status.TRACKING)
                .build();
        tenderId = tenderRepository.save(tender).getId();

        // 创建评估人（User 实体多个字段 nullable=false；不预设 id，由 JPA 生成）
        User evaluator = User.builder()
                .username("co266-evaluator")
                .password("dummy")
                .email("co266-evaluator@test.local")
                .fullName("CO-266 测试评估人")
                .role(User.Role.STAFF)
                .emailVerified(false)
                .build();
        evaluatorId = userRepository.save(evaluator).getId();

        // Mock 依赖
        TenderProjectAccessGuard accessGuard = mock(TenderProjectAccessGuard.class);
        TenderEvaluationDocumentService documentService = mock(TenderEvaluationDocumentService.class);
        when(documentService.getDocuments(tenderId)).thenReturn(java.util.List.of());
        TenderAssignmentPermissions permissions = mock(TenderAssignmentPermissions.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        when(permissions.canFill(tenderId, evaluatorId)).thenReturn(true);
        when(permissions.canDecide(tenderId, evaluatorId)).thenReturn(true);

        service = new TenderEvaluationSubmissionService(
                evaluationRepository,
                tenderRepository,
                userRepository,
                accessGuard,
                permissions,
                eventPublisher,
                projectDocumentRepository,
                documentService,
                FIXED_CLOCK);
    }

    private TenderEvaluationSubmitRequest buildRequest(String roleKey, String infoKey, String value) {
        return new TenderEvaluationSubmitRequest(
                TenderEvaluation.BidRecommendation.RECOMMEND,
                null,
                List.of(new EvaluationCustomerInfoDTO(roleKey, infoKey, value, "TEXT")),
                new EvaluationRecommendationDTO(true, "测试理由"));
    }

    private List<TenderEvaluationCustomerInfo> getCustomerInfos() {
        TenderEvaluation eval = evaluationRepository.findByTenderId(tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", String.valueOf(tenderId)));
        return customerInfoRepository.findByEvaluationId(eval.getId());
    }

    @Test
    @DisplayName("saveDraft 二次更新相同 roleKey+infoKey 不应抛唯一约束异常")
    void saveDraft_secondUpdateWithSameRoleAndInfoKey_shouldNotThrow() {
        // 首次保存
        service.saveDraft(tenderId,
                buildRequest("DECISION_MAKER", "attitude", "支持"),
                evaluatorId);
        assertThat(getCustomerInfos()).hasSize(1);

        // 二次更新（相同 roleKey+infoKey，不同值）
        // 修复前：Hibernate INSERT-before-DELETE 顺序会撞 uk_eval_role_info → 500
        // 修复后：clear() + saveAndFlush 确保 DELETE 先执行
        assertThatCode(() -> service.saveDraft(tenderId,
                buildRequest("DECISION_MAKER", "attitude", "中立"),
                evaluatorId))
                .doesNotThrowAnyException();

        List<TenderEvaluationCustomerInfo> rows = getCustomerInfos();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCellValue()).isEqualTo("中立");
    }

    @Test
    @DisplayName("saveDraft 二次更新传入空数组应清空旧数据")
    void saveDraft_secondUpdateWithEmptyArray_shouldClearOldRows() {
        // 首次保存有数据
        service.saveDraft(tenderId,
                buildRequest("DECISION_MAKER", "attitude", "支持"),
                evaluatorId);
        assertThat(getCustomerInfos()).hasSize(1);

        // 二次更新传空数组
        TenderEvaluationSubmitRequest emptyRequest = new TenderEvaluationSubmitRequest(
                TenderEvaluation.BidRecommendation.RECOMMEND,
                null,
                List.of(),
                new EvaluationRecommendationDTO(true, "测试理由"));
        assertThatCode(() -> service.saveDraft(tenderId, emptyRequest, evaluatorId))
                .doesNotThrowAnyException();

        assertThat(getCustomerInfos()).isEmpty();
    }

    @Test
    @DisplayName("saveDraft 多角色多维度二次更新应成功")
    void saveDraft_multiRoleMultiDimensionSecondUpdate_shouldSucceed() {
        // 首次保存：2 角色 × 2 维度 = 4 行
        TenderEvaluationSubmitRequest firstReq = new TenderEvaluationSubmitRequest(
                TenderEvaluation.BidRecommendation.RECOMMEND,
                null,
                List.of(
                        new EvaluationCustomerInfoDTO("DECISION_MAKER", "attitude", "支持", "TEXT"),
                        new EvaluationCustomerInfoDTO("DECISION_MAKER", "position", "总经理", "TEXT"),
                        new EvaluationCustomerInfoDTO("INFLUENCER", "attitude", "中立", "TEXT"),
                        new EvaluationCustomerInfoDTO("INFLUENCER", "position", "总监", "TEXT")),
                new EvaluationRecommendationDTO(true, "测试理由"));
        service.saveDraft(tenderId, firstReq, evaluatorId);
        assertThat(getCustomerInfos()).hasSize(4);

        // 二次更新：相同 roleKey+infoKey，不同值
        TenderEvaluationSubmitRequest secondReq = new TenderEvaluationSubmitRequest(
                TenderEvaluation.BidRecommendation.RECOMMEND,
                null,
                List.of(
                        new EvaluationCustomerInfoDTO("DECISION_MAKER", "attitude", "强烈支持", "TEXT"),
                        new EvaluationCustomerInfoDTO("DECISION_MAKER", "position", "CEO", "TEXT"),
                        new EvaluationCustomerInfoDTO("INFLUENCER", "attitude", "反对", "TEXT"),
                        new EvaluationCustomerInfoDTO("INFLUENCER", "position", "CTO", "TEXT")),
                new EvaluationRecommendationDTO(true, "测试理由"));

        assertThatCode(() -> service.saveDraft(tenderId, secondReq, evaluatorId))
                .doesNotThrowAnyException();

        List<TenderEvaluationCustomerInfo> rows = getCustomerInfos();
        assertThat(rows).hasSize(4);
        assertThat(rows).anyMatch(r -> r.getRoleKey().equals("DECISION_MAKER")
                && r.getInfoKey().equals("attitude") && r.getCellValue().equals("强烈支持"));
        assertThat(rows).anyMatch(r -> r.getRoleKey().equals("INFLUENCER")
                && r.getInfoKey().equals("position") && r.getCellValue().equals("CTO"));
    }

    @Test
    @DisplayName("submit 二次更新相同 roleKey+infoKey 不应抛唯一约束异常")
    void submit_secondUpdateWithSameRoleAndInfoKey_shouldNotThrow() {
        // 先保存草稿（使用 14 个固定角色 + 14 个固定信息维度的合法组合）
        service.saveDraft(tenderId,
                buildRequest("PROJECT_HIGHEST_DECISION_MAKER", "NAME", "张三"),
                evaluatorId);

        // 重置标讯状态为 TRACKING 以允许 submit
        Tender tender = tenderRepository.findById(tenderId).orElseThrow();
        tender.setStatus(Tender.Status.TRACKING);
        tenderRepository.save(tender);

        // 重置评估表为 DRAFT 以允许 submit
        TenderEvaluation eval = evaluationRepository.findByTenderId(tenderId).orElseThrow();
        eval.setEvaluationStatus(TenderEvaluation.EvaluationStatus.DRAFT);
        evaluationRepository.save(eval);

        // 提交（相同 roleKey+infoKey，不同值）
        assertThatCode(() -> service.submit(tenderId,
                buildRequest("PROJECT_HIGHEST_DECISION_MAKER", "NAME", "李四"),
                evaluatorId))
                .doesNotThrowAnyException();

        List<TenderEvaluationCustomerInfo> rows = getCustomerInfos();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCellValue()).isEqualTo("李四");
    }
}
