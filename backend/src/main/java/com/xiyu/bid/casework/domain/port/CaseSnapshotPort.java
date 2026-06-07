package com.xiyu.bid.casework.domain.port;

import com.xiyu.bid.historyproject.dto.HistoricalProjectSnapshotDTO;

public interface CaseSnapshotPort {

    HistoricalProjectSnapshotDTO getCaseSnapshot(Long projectId);
}
