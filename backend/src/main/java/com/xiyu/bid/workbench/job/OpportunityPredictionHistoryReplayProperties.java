// Input: XIYU_OPPORTUNITY_HISTORY_REPLAY_* 环境变量
// Output: 默认关闭的一次性商机预测历史补发配置
// Pos: workbench/job - 历史补发调度安全边界
// 维护声明: runDate/cutoff 必须同时存在且 cutoff <= runDate，否则任何触发均不得运行。
package com.xiyu.bid.workbench.job;

import com.xiyu.bid.workbench.service.OpportunityPredictionHistoryReplayService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 一次性历史补发配置，安全默认值为关闭与 preview。 */
@Component
@ConfigurationProperties(prefix = "xiyu.opportunity-history-replay")
@Getter
@Setter
public class OpportunityPredictionHistoryReplayProperties {

    private boolean enabled;
    private OpportunityPredictionHistoryReplayService.ReplayMode mode =
            OpportunityPredictionHistoryReplayService.ReplayMode.PREVIEW;
    private LocalDate runDate;
    private LocalDate cutoff;

    public boolean hasValidWindow() {
        return runDate != null && cutoff != null && !cutoff.isAfter(runDate);
    }
}
