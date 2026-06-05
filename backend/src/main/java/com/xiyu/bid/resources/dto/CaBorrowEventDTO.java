package com.xiyu.bid.resources.dto;

import com.xiyu.bid.resources.entity.CaBorrowEventEntity;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class CaBorrowEventDTO {
    private Long id;
    private Long applicationId;
    private String eventType;
    private Long actorId;
    private String actorName;
    private String comment;
    private String statusBefore;
    private String statusAfter;
    private LocalDateTime createdAt;

    public static CaBorrowEventDTO from(CaBorrowEventEntity entity) {
        return CaBorrowEventDTO.builder()
                .id(entity.getId())
                .applicationId(entity.getApplicationId())
                .eventType(entity.getEventType())
                .actorId(entity.getActorId())
                .actorName(entity.getActorName())
                .comment(entity.getComment())
                .statusBefore(entity.getStatusBefore())
                .statusAfter(entity.getStatusAfter())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
