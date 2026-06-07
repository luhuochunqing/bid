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
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganizationSyncRunAppService {
    private final ObjectProvider<OrganizationDirectoryGateway> directoryGatewayProvider;
    private final OrganizationDepartmentSyncWriter departmentWriter;
    private final OrganizationUserSyncWriter userWriter;
    private final OrganizationSyncRunRepository runRepository;
    private final OrganizationSyncItemRepository itemRepository;
    private final OrganizationIntegrationSettingsResolver settingsResolver;

    public OrganizationSyncRunEntity syncWindow(String sourceApp, LocalDateTime startAt, LocalDateTime endAt, String runType) {
        OrganizationSyncRunEntity run = createRun(sourceApp, runType);
        if (!settingsResolver.resolve().enabled()) {
            return finishRejected(run);
        }
        try {
            RunCounters counters = syncEntities(run, sourceApp, startAt, endAt);
            run.setTotalCount(counters.totalCount());
            run.setSuccessCount(counters.successCount());
            run.setFailedCount(counters.failedCount());
            run.setStatus(counters.failedCount() == 0 ? "SUCCEEDED" : "PARTIAL_FAILED");
        } catch (RuntimeException ex) {
            run.setStatus("FAILED");
            run.setLastErrorCode("LIST_FAILED");
            run.setLastErrorMessage(message(ex));
        }
        run.setFinishedAt(LocalDateTime.now());
        return runRepository.save(run);
    }

    private OrganizationSyncRunEntity finishRejected(OrganizationSyncRunEntity run) {
        run.setStatus("REJECTED");
        run.setTotalCount(0);
        run.setSuccessCount(0);
        run.setFailedCount(0);
        run.setLastErrorCode("INTEGRATION_DISABLED");
        run.setLastErrorMessage("组织架构集成已关闭");
        run.setFinishedAt(LocalDateTime.now());
        return runRepository.save(run);
    }

    private RunCounters syncEntities(OrganizationSyncRunEntity run, String sourceApp, LocalDateTime startAt, LocalDateTime endAt) {
        OrganizationDirectoryGateway gateway = directoryGatewayProvider.getIfAvailable();
        if (gateway == null) {
            return new RunCounters(0, 0, 0);
        }
        RunCounters counters = new RunCounters(0, 0, 0);
        OrganizationDirectoryLookupContext context = new OrganizationDirectoryLookupContext(run.getRunKey(), sourceApp);
        List<OrganizationDepartmentSnapshot> departments = gateway.listDepartmentsByWindow(startAt, endAt, context);
        for (OrganizationDepartmentSnapshot department : departments) {
            counters = counters.add(processDepartment(run, sourceApp, department));
        }
        List<OrganizationUserSnapshot> users = gateway.listUsersByWindow(startAt, endAt, context);
        for (OrganizationUserSnapshot user : users) {
            counters = counters.add(processUser(run, sourceApp, user));
        }
        return counters;
    }

    private OrganizationSyncRunEntity createRun(String sourceApp, String runType) {
        OrganizationSyncRunEntity run = new OrganizationSyncRunEntity();
        run.setRunKey(sourceApp + "|" + runType + "|" + System.currentTimeMillis());
        run.setRunType(runType);
        run.setSourceApp(sourceApp);
        run.setStatus("RUNNING");
        run.setTriggeredBy("system");
        return runRepository.save(run);
    }

    private RunCounters processDepartment(OrganizationSyncRunEntity run, String sourceApp, OrganizationDepartmentSnapshot snapshot) {
        String eventKey = run.getRunKey() + "|DEPARTMENT|" + snapshot.externalDeptId();
        try {
            departmentWriter.upsert(sourceApp, eventKey, snapshot);
            itemRepository.save(successItem(run.getId(), "DEPARTMENT", snapshot.externalDeptId(), null, eventKey));
            return RunCounters.success();
        } catch (RuntimeException ex) {
            itemRepository.save(failedItem(run.getId(), "DEPARTMENT", snapshot.externalDeptId(), null, eventKey, ex));
            return RunCounters.failed();
        }
    }

    private RunCounters processUser(OrganizationSyncRunEntity run, String sourceApp, OrganizationUserSnapshot snapshot) {
        String eventKey = run.getRunKey() + "|USER|" + snapshot.externalUserId();
        try {
            userWriter.upsert(sourceApp, eventKey, snapshot);
            itemRepository.save(successItem(run.getId(), "USER", null, snapshot.externalUserId(), eventKey));
            return RunCounters.success();
        } catch (RuntimeException ex) {
            itemRepository.save(failedItem(run.getId(), "USER", null, snapshot.externalUserId(), eventKey, ex));
            return RunCounters.failed();
        }
    }

    private OrganizationSyncItemEntity successItem(Long runId, String entityType, String deptId, String userId, String eventKey) {
        OrganizationSyncItemEntity item = item(runId, entityType, deptId, userId, eventKey);
        item.setStatus("SUCCEEDED");
        return item;
    }

    private OrganizationSyncItemEntity failedItem(
            Long runId,
            String entityType,
            String deptId,
            String userId,
            String eventKey,
            RuntimeException ex
    ) {
        OrganizationSyncItemEntity item = item(runId, entityType, deptId, userId, eventKey);
        item.setStatus("FAILED");
        item.setErrorCode("ITEM_SYNC_FAILED");
        item.setErrorMessage(message(ex));
        return item;
    }

    private OrganizationSyncItemEntity item(Long runId, String entityType, String deptId, String userId, String eventKey) {
        OrganizationSyncItemEntity item = new OrganizationSyncItemEntity();
        item.setRunId(runId);
        item.setEntityType(entityType);
        item.setExternalDeptId(deptId);
        item.setExternalUserId(userId);
        item.setActionType("UPSERT");
        item.setEventKey(eventKey);
        item.setProcessedAt(LocalDateTime.now());
        return item;
    }

    private String message(RuntimeException ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private record RunCounters(int totalCount, int successCount, int failedCount) {
        static RunCounters success() {
            return new RunCounters(1, 1, 0);
        }

        static RunCounters failed() {
            return new RunCounters(1, 0, 1);
        }

        RunCounters add(RunCounters other) {
            return new RunCounters(
                    totalCount + other.totalCount,
                    successCount + other.successCount,
                    failedCount + other.failedCount
            );
        }
    }
}
