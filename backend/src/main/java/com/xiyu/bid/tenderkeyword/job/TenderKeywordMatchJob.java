// Input: daily cron trigger
// Output: executes keyword matching for all active subscriptions and sends notifications
// Pos: Job/标讯关键词匹配定时任务
package com.xiyu.bid.tenderkeyword.job;

import com.xiyu.bid.tenderkeyword.service.TenderKeywordMatchService;
import com.xiyu.bid.tenderkeyword.service.TenderKeywordMatchService.MatchJobResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TenderKeywordMatchJob {

    private final TenderKeywordMatchService matchService;

    @Scheduled(cron = "0 0 8 * * *")
    public void processKeywordMatching() {
        log.info("开始执行标讯关键词匹配定时任务");
        try {
            MatchJobResult result = matchService.executeMatching();
            log.info("标讯关键词匹配定时任务完成: subscriptions={}, matched={}, notifications={}",
                    result.subscriptionsProcessed(), result.totalMatched(), result.notificationsSent());
        } catch (Exception e) {
            log.error("标讯关键词匹配定时任务执行失败", e);
        }
    }
}
