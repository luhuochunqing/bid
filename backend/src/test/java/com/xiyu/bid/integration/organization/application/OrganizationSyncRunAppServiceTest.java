package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.OrganizationDepartmentSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationSyncItemEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationSyncRunEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationSyncItemRepository;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationSyncRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationSyncRunAppService - batch orchestration")
class OrganizationSyncRunAppServiceTest {
    @Mock
    private OrganizationSyncRunRepository runRepository;
    @Mock
    private OrganizationSyncItemRepository itemRepository;

    @Test
    @DisplayName("sync window writes departments users and run items")
    void syncWindow_writesRunAndItems() {
        FakeGateway gateway = new FakeGateway();
        ObjectProvider<OrganizationDirectoryGateway> gatewayProvider = mock(ObjectProvider.class);
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        FakeDepartmentWriter departmentWriter = new FakeDepartmentWriter();
        FakeUserWriter userWriter = new FakeUserWriter();
        when(runRepository.save(any(OrganizationSyncRunEntity.class))).thenAnswer(invocation -> {
            OrganizationSyncRunEntity run = invocation.getArgument(0);
            run.setId(10L);
            return run;
        });
        OrganizationSyncRunAppService service = new OrganizationSyncRunAppService(
                gatewayProvider,
                departmentWriter,
                userWriter,
                runRepository,
                itemRepository,
                OrganizationDirectorySyncAppServiceTest.fixedSettings(true)
        );

        OrganizationSyncRunEntity run = service.syncWindow(
                "customer-org",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now(),
                "COMPENSATION"
        );

        assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(run.getTotalCount()).isEqualTo(2);
        assertThat(departmentWriter.writes).isEqualTo(1);
        assertThat(userWriter.writes).isEqualTo(1);
        verify(itemRepository, times(2)).save(any(OrganizationSyncItemEntity.class));
    }

    @Test
    @DisplayName("sync window records failed items and keeps run inspectable")
    void syncWindow_itemFailure_marksPartialFailed() {
        FakeGateway gateway = new FakeGateway();
        ObjectProvider<OrganizationDirectoryGateway> gatewayProvider = mock(ObjectProvider.class);
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        FakeDepartmentWriter departmentWriter = new FakeDepartmentWriter();
        FakeUserWriter userWriter = new FakeUserWriter();
        userWriter.throwOnWrite = true;
        when(runRepository.save(any(OrganizationSyncRunEntity.class))).thenAnswer(invocation -> {
            OrganizationSyncRunEntity run = invocation.getArgument(0);
            run.setId(10L);
            return run;
        });
        when(itemRepository.save(any(OrganizationSyncItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrganizationSyncRunAppService service = new OrganizationSyncRunAppService(
                gatewayProvider,
                departmentWriter,
                userWriter,
                runRepository,
                itemRepository,
                OrganizationDirectorySyncAppServiceTest.fixedSettings(true)
        );

        OrganizationSyncRunEntity run = service.syncWindow(
                "customer-org",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now(),
                "COMPENSATION"
        );

        assertThat(run.getStatus()).isEqualTo("PARTIAL_FAILED");
        assertThat(run.getTotalCount()).isEqualTo(2);
        assertThat(run.getSuccessCount()).isEqualTo(1);
        assertThat(run.getFailedCount()).isEqualTo(1);
        verify(itemRepository, times(2)).save(any(OrganizationSyncItemEntity.class));
    }

    private static class FakeGateway implements OrganizationDirectoryGateway {
        public Optional<OrganizationDepartmentSnapshot> fetchDepartmentByDeptId(String deptId) {
            return Optional.empty();
        }

        public Optional<OrganizationUserSnapshot> fetchUserByUserId(String userId) {
            return Optional.empty();
        }

        public List<OrganizationDepartmentSnapshot> listDepartmentsByWindow(LocalDateTime startAt, LocalDateTime endAt) {
            return List.of(new OrganizationDepartmentSnapshot("D001", "sales", "销售部", "", "", true));
        }

        public List<OrganizationUserSnapshot> listUsersByWindow(LocalDateTime startAt, LocalDateTime endAt) {
            return List.of(new OrganizationUserSnapshot("10001", "zhangsan", "张三", "zhangsan@example.com", "", "sales", "销售部", "", true));
        }
    }

    private static class FakeDepartmentWriter extends OrganizationDepartmentSyncWriter {
        int writes;

        FakeDepartmentWriter() {
            super(null);
        }

        public com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity upsert(
                String sourceApp,
                String eventKey,
                OrganizationDepartmentSnapshot snapshot
        ) {
            writes++;
            return new com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity();
        }
    }

    private static class FakeUserWriter extends OrganizationUserSyncWriter {
        int writes;
        boolean throwOnWrite;

        FakeUserWriter() {
            super(null, null, new OrganizationIntegrationProperties(), null);
        }

        public com.xiyu.bid.entity.User upsert(String sourceApp, String eventKey, OrganizationUserSnapshot snapshot) {
            if (throwOnWrite) {
                throw new IllegalStateException("write failed");
            }
            writes++;
            return new com.xiyu.bid.entity.User();
        }
    }
}
