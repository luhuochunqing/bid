package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.OrganizationDepartmentSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationSyncItemEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationSyncRunEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationSyncItemRepository;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationSyncRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrganizationManualResyncAppService {
    private static final String DEPARTMENT = "DEPARTMENT";
    private static final String USER = "USER";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String FAILED = "FAILED";

    private final ObjectProvider<OrganizationDirectoryGateway> directoryGatewayProvider;
    private final OrganizationDepartmentSyncWriter departmentWriter;
    private final OrganizationUserSyncWriter userWriter;
    private final OrganizationSyncRunRepository runRepository;
    private final OrganizationSyncItemRepository itemRepository;
    private final OrganizationIntegrationSettingsResolver settingsResolver;

    public OrganizationSyncRunEntity resyncDepartment(String sourceApp, String deptId, String triggeredBy) {
        OrganizationSyncRunEntity run = createRun(sourceApp, "MANUAL_DEPARTMENT_RESYNC", triggeredBy);
        if (!settingsResolver.resolve().enabled()) {
            return finishRejected(run);
        }
        OrganizationDirectoryGateway gateway = directoryGatewayProvider.getIfAvailable();
        if (gateway == null) {
            return finishRejected(run);
        }
        String eventKey = run.getRunKey() + "|DEPARTMENT|" + deptId;
        try {
            OrganizationDirectoryLookupContext context = new OrganizationDirectoryLookupContext(run.getRunKey(), sourceApp);
            Optional<OrganizationDepartmentSnapshot> snapshot = gateway.fetchDepartmentByDeptId(deptId, context);
            if (snapshot.isPresent()) {
                departmentWriter.upsert(sourceApp, eventKey, snapshot.get());
            } else {
                departmentWriter.disableByExternalId(sourceApp, eventKey, deptId);
            }
            itemRepository.save(item(run.getId(), DEPARTMENT, deptId, null, eventKey, SUCCEEDED, null, null));
            return finish(run, 1, 1, 0, SUCCEEDED, null, null);
        } catch (RuntimeException ex) {
            itemRepository.save(item(run.getId(), DEPARTMENT, deptId, null, eventKey, FAILED, "MANUAL_RESYNC_FAILED", message(ex)));
            return finish(run, 1, 0, 1, FAILED, "MANUAL_RESYNC_FAILED", message(ex));
        }
    }

    public OrganizationSyncRunEntity resyncUser(String sourceApp, String userId, String triggeredBy) {
        OrganizationSyncRunEntity run = createRun(sourceApp, "MANUAL_USER_RESYNC", triggeredBy);
        if (!settingsResolver.resolve().enabled()) {
            return finishRejected(run);
        }
        OrganizationDirectoryGateway gateway = directoryGatewayProvider.getIfAvailable();
        if (gateway == null) {
            return finishRejected(run);
        }
        String eventKey = run.getRunKey() + "|USER|" + userId;
        try {
            OrganizationDirectoryLookupContext context = new OrganizationDirectoryLookupContext(run.getRunKey(), sourceApp);
            Optional<OrganizationUserSnapshot> snapshot = gateway.fetchUserByUserId(userId, context);
            if (snapshot.isPresent()) {
                userWriter.upsert(sourceApp, eventKey, snapshot.get());
            } else {
                userWriter.disableByExternalId(sourceApp, eventKey, userId);
            }
            itemRepository.save(item(run.getId(), USER, null, userId, eventKey, SUCCEEDED, null, null));
            return finish(run, 1, 1, 0, SUCCEEDED, null, null);
        } catch (RuntimeException ex) {
            itemRepository.save(item(run.getId(), USER, null, userId, eventKey, FAILED, "MANUAL_RESYNC_FAILED", message(ex)));
            return finish(run, 1, 0, 1, FAILED, "MANUAL_RESYNC_FAILED", message(ex));
        }
    }

    private OrganizationSyncRunEntity createRun(String sourceApp, String runType, String triggeredBy) {
        OrganizationSyncRunEntity run = new OrganizationSyncRunEntity();
        run.setRunKey(sourceApp + "|" + runType + "|" + System.currentTimeMillis());
        run.setRunType(runType);
        run.setSourceApp(sourceApp);
        run.setTriggeredBy(triggeredBy);
        run.setStatus("RUNNING");
        return runRepository.save(run);
    }

    private OrganizationSyncRunEntity finishRejected(OrganizationSyncRunEntity run) {
        return finish(run, 0, 0, 0, "REJECTED", "INTEGRATION_DISABLED", "组织架构集成已关闭");
    }

    private OrganizationSyncRunEntity finish(
            OrganizationSyncRunEntity run,
            int totalCount,
            int successCount,
            int failedCount,
            String status,
            String errorCode,
            String errorMessage
    ) {
        run.setTotalCount(totalCount);
        run.setSuccessCount(successCount);
        run.setFailedCount(failedCount);
        run.setStatus(status);
        run.setLastErrorCode(errorCode);
        run.setLastErrorMessage(errorMessage);
        run.setFinishedAt(LocalDateTime.now());
        return runRepository.save(run);
    }

    private OrganizationSyncItemEntity item(
            Long runId,
            String entityType,
            String deptId,
            String userId,
            String eventKey,
            String status,
            String errorCode,
            String errorMessage
    ) {
        OrganizationSyncItemEntity item = new OrganizationSyncItemEntity();
        item.setRunId(runId);
        item.setEntityType(entityType);
        item.setExternalDeptId(deptId);
        item.setExternalUserId(userId);
        item.setActionType("UPSERT");
        item.setEventKey(eventKey);
        item.setStatus(status);
        item.setErrorCode(errorCode);
        item.setErrorMessage(errorMessage);
        item.setProcessedAt(LocalDateTime.now());
        return item;
    }

    private String message(RuntimeException ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
