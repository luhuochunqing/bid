// Input: TenderDocumentStoredEvent（招标文档保存完成）
// Output: 无（触发评分标准解析任务）
// Pos: scoreparse/application — 自动触发监听器（spec 041 US1 / contracts §8）
// 维护声明: 维护者按项目SOP；@Async 隔离主链路，异常不阻塞标讯/文档导入
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.TenderDocumentStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 招标文档存储事件监听器（spec 041）。
 * <p>消费 {@link TenderDocumentStoredEvent} → 自动触发评分标准解析。
 * 互斥由 {@link ScoreParseAppService#triggerParse} 内部处理：
 * 项目已有进行中解析任务时返回现有任务（本监听器仅 log.info 跳过）。
 * <p>监听器不抛异常：自动触发是增强行为，失败不影响文档导入主链路。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenderDocumentStoredListener {

    private final ScoreParseAppService scoreParseAppService;

    @Async("scoreParseExecutor")
    @EventListener
    public void onTenderDocumentStored(TenderDocumentStoredEvent event) {
        try {
            if (!scoreParseAppService.allowAutoParse(event.projectId())) {
                log.info("跳过自动评分解析（已有历史或熔断）: projectId={}, documentId={}",
                        event.projectId(), event.documentId());
                return;
            }
            log.info("收到招标文档存储事件，触发评分标准解析: projectId={}, documentId={}",
                    event.projectId(), event.documentId());
            scoreParseAppService.triggerParseFromEvent(event.projectId());
        } catch (RuntimeException exception) {
            log.warn("自动触发评分标准解析失败（不影响文档导入）: projectId={}, error={}",
                    event.projectId(), exception.getMessage());
        }
    }
}
