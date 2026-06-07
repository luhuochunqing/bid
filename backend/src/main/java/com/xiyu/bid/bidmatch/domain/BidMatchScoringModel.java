package com.xiyu.bid.bidmatch.domain;

import java.util.List;

public record BidMatchScoringModel(
        Long id,
        String name,
        String description,
        List<BidMatchDimension> dimensions,
        long draftRevision
) {

    public BidMatchScoringModel {
        dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
    }

    public BidMatchScoringModel withId(Long nextId) {
        return new BidMatchScoringModel(nextId, name, description, dimensions, draftRevision);
    }

    public BidMatchScoringModel nextRevision(List<BidMatchDimension> nextDimensions) {
        return new BidMatchScoringModel(id, name, description, nextDimensions, draftRevision + 1);
    }
}
