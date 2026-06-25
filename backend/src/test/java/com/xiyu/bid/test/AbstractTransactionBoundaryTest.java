package com.xiyu.bid.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事务边界测试基类（CO-325 防护层）。
 *
 * <p>用法：继承此类，在测试方法上标注 {@code @Transactional(propagation = Propagation.NEVER)}，
 * 让测试方法本身不开事务。如果被测 Service 方法有事务泄漏（未正确提交或回滚），
 * 测试会立即失败。
 *
 * <p>典型场景：
 * <pre>{@code
 * @SpringBootTest
 * class TenderCommandServiceTransactionTest extends AbstractTransactionBoundaryTest {
 *
 *     @Autowired private TenderCommandService service;
 *     @Autowired private TenderRepository tenderRepository;
 *
 *     @Test
 *     @Transactional(propagation = Propagation.NEVER)
 *     void linkCrmOpportunity_当backfill校验失败_主事务应正常提交() {
 *         Long tenderId = prepareTender();
 *         var badPayload = buildPayloadWithInvalidShortlistCount(0);
 *
 *         service.linkCrmOpportunity(tenderId, "CC-1", "商机", badPayload, userId);
 *
 *         // 走到这行 = 主事务提交成功（未抛 UnexpectedRollbackException）
 *         assertThat(tenderRepository.findById(tenderId))
 *             .hasValueSatisfying(t -> assertThat(t.getCrmOpportunityId()).isEqualTo("CC-1"));
 *     }
 * }
 * }</pre>
 *
 * <p>关键点：
 * <ul>
 *   <li>测试方法用 {@link Propagation#NEVER}，如果被测方法有事务泄漏，测试会立即失败</li>
 *   <li>如果被测方法的子调用抛 RuntimeException 但被 catch，事务仍会被标记 rollback-only，
 *       提交时抛 {@link org.springframework.transaction.UnexpectedRollbackException}</li>
 *   <li>修复方式：子调用使用 {@link Propagation#REQUIRES_NEW} 在独立事务中执行</li>
 * </ul>
 *
 * <p>参考修复：{@link com.xiyu.bid.tender.service.TenderEvaluationBackfillService#backfillFromCrmLink}
 * 已改为 {@code @Transactional(propagation = Propagation.REQUIRES_NEW)}。
 *
 * @see com.xiyu.bid.ArchitectureTest#class_level_transactional_should_not_have_auditable_methods RULE 17
 */
@SpringBootTest
public abstract class AbstractTransactionBoundaryTest {
    // 子类继承此基类，使用 @Transactional(propagation = Propagation.NEVER) 标注测试方法
    // 此基类仅提供 @SpringBootTest 上下文，不强制事务策略
}
