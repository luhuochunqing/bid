package com.xiyu.bid.casework.infrastructure.client;

import com.xiyu.bid.casework.domain.port.CaseSnapshotPort;
import com.xiyu.bid.historyproject.application.HistoricalProjectSnapshotAppService;
import com.xiyu.bid.historyproject.dto.HistoricalProjectSnapshotDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HistoricalProjectCaseSnapshotClient implements CaseSnapshotPort {

    private final HistoricalProjectSnapshotAppService historicalProjectSnapshotAppService;

    @Override
    public HistoricalProjectSnapshotDTO getCaseSnapshot(Long projectId) {
        return historicalProjectSnapshotAppService.getLatestSnapshot(projectId);
    }
}
