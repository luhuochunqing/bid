package com.xiyu.bid.changetracking.event;

import java.util.Map;

public record EntityChangedEvent(
    String entityType,
    Long entityId,
    Long actorUserId,
    Map<String, Object> before,
    Map<String, Object> after,
    String entityTitle,
    Map<String, Object> metadata
) {

    public EntityChangedEvent(
        String entityType,
        Long entityId,
        Long actorUserId,
        Map<String, Object> before,
        Map<String, Object> after,
        String entityTitle
    ) {
        this(entityType, entityId, actorUserId, before, after, entityTitle, Map.of());
    }

    public EntityChangedEvent {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
