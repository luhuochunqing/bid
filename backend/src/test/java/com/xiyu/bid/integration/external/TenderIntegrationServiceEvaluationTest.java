package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.entity.TenderEvaluationCustomerInfo;
import com.xiyu.bid.tender.repository.TenderEvaluationCustomerInfoRepository;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.tender.service.TenderEvaluationSubmissionMapper;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * {@link TenderIntegrationService#saveEvaluation} 的 JPA 集成测试。
 *
 * <p>验证修复 PR784/PR785 未彻底解决的 500 bug：
 * <ul>
 *   <li>二次更新相同 (roleKey, infoKey) 的 customerInfos 时，
 *       Hibernate 默认 INSERT-before-DELETE 顺序会撞 uk_eval_role_info 唯一约束</li>
 *   <li>修复方案：deleteAll 后立即 flush，确保 DELETE SQL 先于 INSERT 执行</li>
 * </ul>
 *
 * <p>用反射调用 private saveEvaluation，聚焦测试 JPA 层行为，
 * 避免 @SpringBootTest 拉起完整上下文的成本。
 */
@DataJpaTest
@ActiveProfiles("test")
class TenderIntegrationServiceEvaluationTest {

    @Autowired private TenderRepository tenderRepository;
    @Autowired private TenderEvaluationRepository tenderEvaluationRepository;
    @Autowired private TenderEvaluationCustomerInfoRepository customerInfoRepository;

    private TenderIntegrationService service;

    @BeforeEach
    void setUp() {
        service = new TenderIntegrationService(
                tenderRepository,
                mock(TenderMapper.class),
                mock(TenderAttachmentRepository.class),
                tenderEvaluationRepository,
                customerInfoRepository,
                mock(TenderEvaluationSubmissionMapper.class),
                mock(CrmTenderLinkService.class));
    }

    /** 用反射调用 private saveEvaluation。 */
    private void invokeSaveEvaluation(Long tenderId, TenderUpdateRequest.EvaluationUpdate eval) throws Exception {
        Method method = TenderIntegrationService.class.getDeclaredMethod(
                "saveEvaluation", Long.class, TenderUpdateRequest.EvaluationUpdate.class);
        method.setAccessible(true);
        method.invoke(service, tenderId, eval);
    }

    private Long createTender() {
        Tender t = new Tender();
        t.setTitle("测试标讯");
        t.setExternalId("TEST:001");
        return tenderRepository.save(t).getId();
    }

    private TenderUpdateRequest.EvaluationUpdate buildEval(String roleKey, String infoKey, String value) {
        Map<String, Object> roleData = new LinkedHashMap<>();
        roleData.put("roleKey", roleKey);
        roleData.put(infoKey, value);
        return TenderUpdateRequest.EvaluationUpdate.builder()
                .evaluationCustomerInfos(List.of(roleData))
                .build();
    }

    @Test
    @DisplayName("首次保存 customerInfos 应成功")
    void saveEvaluation_firstTime_shouldSucceed() throws Exception {
        Long tenderId = createTender();

        invokeSaveEvaluation(tenderId, buildEval("DECISION_MAKER", "attitude", "支持"));

        TenderEvaluation eval = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(eval.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRoleKey()).isEqualTo("DECISION_MAKER");
        assertThat(rows.get(0).getInfoKey()).isEqualTo("attitude");
        assertThat(rows.get(0).getCellValue()).isEqualTo("支持");
    }

    @Test
    @DisplayName("二次更新相同 roleKey+infoKey 的 customerInfos 不应抛唯一约束异常（PR784/PR785 未修复的 bug）")
    void saveEvaluation_secondUpdateWithSameRoleAndInfoKey_shouldNotThrow() throws Exception {
        Long tenderId = createTender();

        // 首次保存
        invokeSaveEvaluation(tenderId, buildEval("DECISION_MAKER", "attitude", "支持"));

        // 二次更新（相同 roleKey+infoKey，会触发 deleteAll + insert）
        // 修复前：Hibernate INSERT-before-DELETE 顺序会撞 uk_eval_role_info 唯一约束 → 500
        // 修复后：deleteAll + flush 确保 DELETE 先执行，INSERT 不冲突
        assertThatCode(() -> invokeSaveEvaluation(tenderId, buildEval("DECISION_MAKER", "attitude", "中立")))
                .doesNotThrowAnyException();

        TenderEvaluation eval = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(eval.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCellValue()).isEqualTo("中立");
    }

    @Test
    @DisplayName("二次更新不同 roleKey+infoKey 的 customerInfos 应成功")
    void saveEvaluation_secondUpdateWithDifferentRoleAndInfoKey_shouldSucceed() throws Exception {
        Long tenderId = createTender();

        invokeSaveEvaluation(tenderId, buildEval("DECISION_MAKER", "attitude", "支持"));
        invokeSaveEvaluation(tenderId, buildEval("INFLUENCER", "position", "总监"));

        TenderEvaluation eval = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(eval.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRoleKey()).isEqualTo("INFLUENCER");
        assertThat(rows.get(0).getInfoKey()).isEqualTo("position");
    }

    @Test
    @DisplayName("二次更新传入空数组 customerInfos 应清空旧数据")
    void saveEvaluation_secondUpdateWithEmptyArray_shouldClearOldRows() throws Exception {
        Long tenderId = createTender();

        // 首次保存有数据
        invokeSaveEvaluation(tenderId, buildEval("DECISION_MAKER", "attitude", "支持"));

        // 二次更新传空数组
        TenderUpdateRequest.EvaluationUpdate emptyEval = TenderUpdateRequest.EvaluationUpdate.builder()
                .evaluationCustomerInfos(List.of())
                .build();
        assertThatCode(() -> invokeSaveEvaluation(tenderId, emptyEval))
                .doesNotThrowAnyException();

        TenderEvaluation eval = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(eval.getId());
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("多角色多维度二次更新应成功（覆盖 14 角色 × 17 维度场景）")
    void saveEvaluation_multiRoleMultiDimensionSecondUpdate_shouldSucceed() throws Exception {
        Long tenderId = createTender();

        // 首次保存：2 角色 × 2 维度 = 4 行
        Map<String, Object> role1 = new LinkedHashMap<>();
        role1.put("roleKey", "DECISION_MAKER");
        role1.put("attitude", "支持");
        role1.put("position", "总经理");
        Map<String, Object> role2 = new LinkedHashMap<>();
        role2.put("roleKey", "INFLUENCER");
        role2.put("attitude", "中立");
        role2.put("position", "总监");
        TenderUpdateRequest.EvaluationUpdate firstEval = TenderUpdateRequest.EvaluationUpdate.builder()
                .evaluationCustomerInfos(List.of(role1, role2))
                .build();
        invokeSaveEvaluation(tenderId, firstEval);

        // 二次更新：相同 roleKey+infoKey，不同值
        Map<String, Object> role1Update = new LinkedHashMap<>();
        role1Update.put("roleKey", "DECISION_MAKER");
        role1Update.put("attitude", "强烈支持");
        role1Update.put("position", "CEO");
        Map<String, Object> role2Update = new LinkedHashMap<>();
        role2Update.put("roleKey", "INFLUENCER");
        role2Update.put("attitude", "反对");
        role2Update.put("position", "CTO");
        TenderUpdateRequest.EvaluationUpdate secondEval = TenderUpdateRequest.EvaluationUpdate.builder()
                .evaluationCustomerInfos(List.of(role1Update, role2Update))
                .build();

        assertThatCode(() -> invokeSaveEvaluation(tenderId, secondEval))
                .doesNotThrowAnyException();

        TenderEvaluation eval = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(eval.getId());
        assertThat(rows).hasSize(4);
        // 验证值已更新
        assertThat(rows).anyMatch(r -> r.getRoleKey().equals("DECISION_MAKER")
                && r.getInfoKey().equals("attitude") && r.getCellValue().equals("强烈支持"));
        assertThat(rows).anyMatch(r -> r.getRoleKey().equals("INFLUENCER")
                && r.getInfoKey().equals("position") && r.getCellValue().equals("CTO"));
    }

    @Test
    @DisplayName("saveEvaluation 应将 CRM 旧字段名 CONTACT/EVALUATION_BASIS 标准化为 CONTACT_INFO/INFO_TENDENCY_BASIS")
    void saveEvaluation_crmLegacyInfoKeys_shouldBeNormalizedBeforeSave() throws Exception {
        Long tenderId = createTender();

        Map<String, Object> roleData = new LinkedHashMap<>();
        roleData.put("roleKey", "PROJECT_HIGHEST_DECISION_MAKER");
        roleData.put("NAME", "张三");
        roleData.put("CONTACT", "13800138000");
        roleData.put("EVALUATION_BASIS", "长期合作");
        TenderUpdateRequest.EvaluationUpdate eval = TenderUpdateRequest.EvaluationUpdate.builder()
                .evaluationCustomerInfos(List.of(roleData))
                .build();

        invokeSaveEvaluation(tenderId, eval);

        TenderEvaluation evaluation = tenderEvaluationRepository.findByTenderId(tenderId).orElseThrow();
        List<TenderEvaluationCustomerInfo> rows = customerInfoRepository.findByEvaluationId(evaluation.getId());
        assertThat(rows).hasSize(3);
        assertThat(rows).anyMatch(r -> "PROJECT_HIGHEST_DECISION_MAKER".equals(r.getRoleKey())
                && "NAME".equals(r.getInfoKey()) && "张三".equals(r.getCellValue()));
        assertThat(rows).anyMatch(r -> "PROJECT_HIGHEST_DECISION_MAKER".equals(r.getRoleKey())
                && "CONTACT_INFO".equals(r.getInfoKey()) && "13800138000".equals(r.getCellValue()));
        assertThat(rows).anyMatch(r -> "PROJECT_HIGHEST_DECISION_MAKER".equals(r.getRoleKey())
                && "INFO_TENDENCY_BASIS".equals(r.getInfoKey()) && "长期合作".equals(r.getCellValue()));
    }
}
