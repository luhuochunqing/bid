package com.xiyu.bid.scoreparse.dto;

import java.util.List;
import java.math.BigDecimal;
import java.util.HashSet;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Refresh receipt describes this calculation; items remain compatible with the existing read API. */
public record ScoreEstimateRefreshDTO(List<ScoreItemDTO> items, ScoreParseItemsDTO.Summary summary,
        ScoreParseItemsDTO.Meta meta, Refresh refresh) {
    public record Refresh(int processedCount, int scoredCount, int pendingCount, List<PendingItem> pendingItems,
            @JsonIgnore List<SavedItem> savedItems) {
        public Refresh {
            pendingItems = List.copyOf(pendingItems);
            savedItems = List.copyOf(savedItems);
        }
        public boolean matchesSavedResults(List<ScoreItemDTO> read) {
            return read != null && read.size() == savedItems.size()
                    && new HashSet<>(savedItems).equals(new HashSet<>(read.stream().map(i -> new SavedItem(
                            i.id(), i.code(), i.dim(), i.detail(), i.weight(), i.scoreType(), i.status(),
                            i.estScore(), i.estBasis(), i.kbHit())).toList()));
        }
    }
    /** Immutable values captured from the successfully committed calculation, not a later read. */
    public record SavedItem(Long id, String code, String dim, String detail, BigDecimal weight,
            String scoreType, String status, BigDecimal score, String basis, Boolean kbHit) {
        public SavedItem {
            weight = weight == null ? null : weight.stripTrailingZeros();
            score = score == null ? null : score.stripTrailingZeros();
        }
    }
    public record PendingItem(Long id, String dim, String reason) {}
}
