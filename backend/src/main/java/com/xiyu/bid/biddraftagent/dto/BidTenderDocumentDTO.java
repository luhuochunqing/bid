package com.xiyu.bid.biddraftagent.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BidTenderDocumentDTO {

    private Long id;
    private Long projectId;
    private Long tenderId;
    private String name;
    private String fileType;
    private String size;
    private String fileUrl;
    private Long snapshotId;
    private Integer extractedTextLength;
}
