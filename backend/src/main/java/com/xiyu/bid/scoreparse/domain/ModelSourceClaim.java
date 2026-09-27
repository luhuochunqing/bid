package com.xiyu.bid.scoreparse.domain;

import java.util.List;

/** MODEL_CLAIM only: model-supplied location and scope, never verified source provenance. */
public record ModelSourceClaim(String sectionTitle, Integer pageNumber, String tableId,
                               List<String> sourceCellIds, String schemeId, String scopeStatus) {
    public static final ModelSourceClaim EMPTY = new ModelSourceClaim("", null, "", List.of(), "", "");
    public ModelSourceClaim {
        sectionTitle = sectionTitle == null ? "" : sectionTitle;
        tableId = tableId == null ? "" : tableId;
        sourceCellIds = sourceCellIds == null ? List.of() : List.copyOf(sourceCellIds);
        schemeId = schemeId == null ? "" : schemeId;
        scopeStatus = scopeStatus == null ? "" : scopeStatus;
    }
    public static ModelSourceClaim from(MultimodalScoreFact fact) {
        return new ModelSourceClaim(fact.sectionTitle(), fact.pageNumber(), fact.tableId(), fact.sourceCellIds(),
                fact.schemeId(), fact.scopeStatus());
    }
}
