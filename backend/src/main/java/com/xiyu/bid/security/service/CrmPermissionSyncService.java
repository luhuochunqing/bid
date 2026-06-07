package com.xiyu.bid.security.service;

import com.xiyu.bid.dto.webhook.CrmPermissionWebhookPayload;
import com.xiyu.bid.matrixcollaboration.entity.CrmCustomerPermission;
import com.xiyu.bid.matrixcollaboration.repository.CrmCustomerPermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrmPermissionSyncService {

    private final CrmCustomerPermissionRepository repository;

    @Transactional
    public void syncCustomerPermissions(CrmPermissionWebhookPayload payload) {
        log.info("Syncing permissions for customer: {}", payload.getCustomerId());
        
        // Idempotent sync: delete existing for this customer and re-insert
        repository.deleteByCustomerId(payload.getCustomerId());
        
        if (payload.getPermissions() != null) {
            List<CrmCustomerPermission> entities = payload.getPermissions().stream()
                    .map(p -> CrmCustomerPermission.builder()
                            .customerId(payload.getCustomerId())
                            .userId(p.getUserId())
                            .permissionType(p.getPermissionType())
                            .build())
                    .collect(Collectors.toList());
            
            repository.saveAll(entities);
        }
    }
}
