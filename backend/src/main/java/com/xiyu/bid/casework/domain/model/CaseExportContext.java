package com.xiyu.bid.casework.domain.model;

import java.time.LocalDateTime;
import java.util.List;

public record CaseExportContext(
        List<? extends KnowledgeCaseReadModel> cases,
        List<CaseExportZipEntry> zipEntries,
        String zipFileName,
        String operatorName,
        LocalDateTime exportTime
) {
}
