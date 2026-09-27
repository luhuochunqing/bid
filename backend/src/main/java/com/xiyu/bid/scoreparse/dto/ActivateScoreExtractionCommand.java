package com.xiyu.bid.scoreparse.dto;
import java.util.List;
/** Complete replacement selection. Omitted selection means all computable units; [] is invalid. */
public record ActivateScoreExtractionCommand(String schemeId,String expectedActiveFingerprint,String reason,List<String> selectedItemIds) {}
