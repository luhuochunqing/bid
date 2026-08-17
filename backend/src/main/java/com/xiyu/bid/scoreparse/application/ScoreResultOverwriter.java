// Input: scoreItemIds（待清除旧结果的评分项）+ results（新结果集）
// Output: 事务内原子完成 DELETE + INSERT（FR-021 覆盖语义）
// Pos: scoreparse/application — 打分结果覆盖写入（spec 045 T025 事务修复）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 打分结果原子覆盖（FR-021）。
 * <p>必须以 Spring 代理调用本类方法：@Async 线程无事务上下文，若在
 * AppService 内直接调派生 delete 会抛 TransactionRequiredException
 * （T025 实证：重试路径曾把友好文案覆盖成 No EntityManager 报错）。
 */
@Component
@RequiredArgsConstructor
public class ScoreResultOverwriter {

    private final ScoreResultRepository resultRepository;

    /** 事务内 DELETE + INSERT，两操作原子生效。 */
    @Transactional
    public void replaceAtomically(List<Long> scoreItemIds, List<com.xiyu.bid.scoreparse.entity.ScoreResult> results) {
        resultRepository.deleteByScoreItemIdIn(scoreItemIds);
        resultRepository.saveAll(results);
    }
}
