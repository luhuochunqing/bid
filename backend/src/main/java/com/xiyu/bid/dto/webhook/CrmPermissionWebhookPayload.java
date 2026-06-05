package com.xiyu.bid.dto.webhook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmPermissionWebhookPayload {
    private String customerId;
    private List<UserPermission> permissions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserPermission {
        private Long userId;
        private String permissionType;
    }
}
