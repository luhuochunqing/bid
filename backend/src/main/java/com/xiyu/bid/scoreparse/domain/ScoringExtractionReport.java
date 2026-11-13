package com.xiyu.bid.scoreparse.domain;

import java.util.List;
import java.util.Map;

/** Coverage is about executed source units, independent of an individual scoring conclusion. */
public record ScoringExtractionReport(int schemaVersion, String engineVersion, String coverageStatus,
        long sourceUnitCount, long processedUnitCount, long uniqueOcrUnitCount, long unreadUnitCount,
        long failedUnitCount, long emptyUnitCount, List<Map<String,Object>> unreadUnits,
        List<Map<String,Object>> failedUnits, List<Map<String,Object>> emptyUnits, long elapsedMillis,
        Boolean enumerationComplete) {
    public ScoringExtractionReport(int schemaVersion,String engineVersion,String coverageStatus,
            long sourceUnitCount,long processedUnitCount,long uniqueOcrUnitCount,long unreadUnitCount,
            long failedUnitCount,long emptyUnitCount,List<Map<String,Object>> unreadUnits,
            List<Map<String,Object>> failedUnits,List<Map<String,Object>> emptyUnits,long elapsedMillis) {
        this(schemaVersion,engineVersion,coverageStatus,sourceUnitCount,processedUnitCount,uniqueOcrUnitCount,
                unreadUnitCount,failedUnitCount,emptyUnitCount,unreadUnits,failedUnits,emptyUnits,elapsedMillis,true);
    }
    public ScoringExtractionReport {
        unreadUnits = unreadUnits == null ? List.of() : List.copyOf(unreadUnits);
        failedUnits = failedUnits == null ? List.of() : List.copyOf(failedUnits);
        emptyUnits = emptyUnits == null ? List.of() : List.copyOf(emptyUnits);
        if (schemaVersion != 1 || engineVersion == null || engineVersion.isBlank() || sourceUnitCount < 0 || processedUnitCount < 0 || processedUnitCount > sourceUnitCount
                || uniqueOcrUnitCount < 0 || uniqueOcrUnitCount > processedUnitCount || unreadUnitCount < 0
                || failedUnitCount < 0 || emptyUnitCount < 0 || emptyUnitCount > processedUnitCount
                || (Boolean.TRUE.equals(enumerationComplete) && unreadUnitCount + processedUnitCount != sourceUnitCount) || failedUnitCount > processedUnitCount
                || !List.of("COMPLETE", "PARTIAL", "UNKNOWN").contains(coverageStatus)
                || (!"UNKNOWN".equals(coverageStatus) && !Boolean.TRUE.equals(enumerationComplete))
                || ("COMPLETE".equals(coverageStatus) && (unreadUnitCount != 0 || failedUnitCount != 0))) {
            throw new IllegalArgumentException("INVALID_SCORING_COVERAGE");
        }
    }
    public static ScoringExtractionReport unknown() {
        return new ScoringExtractionReport(1, "unknown", "UNKNOWN", 0,0,0,0,0,0,List.of(),List.of(),List.of(),0,false);
    }
}
