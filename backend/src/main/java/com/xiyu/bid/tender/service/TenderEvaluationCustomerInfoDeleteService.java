package com.xiyu.bid.tender.service;

import com.xiyu.bid.tender.repository.TenderEvaluationCustomerInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * CO-266 + CO-310 修复：独立的客户信息删除服务。
 *
 * <p>当评估表的客户信息行需要全量替换时（先删后加），
 * 必须确保 DELETE SQL 在 INSERT 之前执行，以避免唯一约束冲突（CO-266）。
 *
 * <p>两个删除方法：
 * <ul>
 *   <li>{@link #deleteAllByEvaluationIdInTransaction} — 在主事务中执行删除（CO-266 修复）。</li>
 *   <li>{@link #deleteAllByEvaluationId} — 使用 {@code REQUIRES_NEW} 确保立即提交（CO-310 修复）。</li>
 * </ul>
 *
 * <p>使用场景：
 * <ul>
 *   <li>{@link TenderEvaluationBackfillService#backfillFromCrmLink} — 使用 REQUIRES_NEW（CO-310 场景）</li>
 *   <li>{@link TenderEvaluationSubmissionService#applyCustomerInfosWithFlush} — 使用主事务删除（CO-266 场景）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderEvaluationCustomerInfoDeleteService {

    private final TenderEvaluationCustomerInfoRepository infoRepository;

    /**
     * 在主事务中删除（无独立事务）。
     * <p>CO-266 修复：与 {@code clear() + addAll()} 配合，确保 Hibernate orphanRemoval
     * 的 DELETE 与本方法的 DELETE 在同一事务中顺序执行。
     *
     * @param evaluationId 评估表 ID（非 null）
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteAllByEvaluationIdInTransaction(Long evaluationId) {
        log.debug("In-transaction delete: removing all customer infos for evaluation {}", evaluationId);
        infoRepository.deleteAllByEvaluationIdNative(evaluationId);
    }

    /**
     * 删除指定评估表的全部客户信息行（REQUIRES_NEW 独立事务）。
     * <p>CO-310 修复：使用 {@code REQUIRES_NEW} 确保立即提交，不受主事务 rollback 影响。
     *
     * @param evaluationId 评估表 ID（非 null）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteAllByEvaluationId(Long evaluationId) {
        log.debug("REQUIRES_NEW: deleting all customer infos for evaluation {}", evaluationId);
        infoRepository.deleteAllByEvaluationIdNative(evaluationId);
    }
}
