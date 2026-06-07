package com.xiyu.bid.resources.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class BarSiteAccountDTO {
    Long id;
    Long barAssetId;
    String username;
    String role;
    String owner;
    String phone;
    String email;
    String status;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
