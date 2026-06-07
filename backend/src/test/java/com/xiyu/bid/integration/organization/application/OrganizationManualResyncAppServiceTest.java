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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationManualResyncAppService")
class OrganizationManualResyncAppServiceTest {
    @Mock
    private OrganizationSyncRunRepository runRepository;
    @Mock
    private OrganizationSyncItemRepository itemRepository;

    @Test
    @DisplayName("resyncs one department by immutable deptId")
    void resyncDepartment_fetchesAndWritesOneDepartment() {
        FakeGateway gateway = new FakeGateway();
        ObjectProvider<OrganizationDirectoryGateway> gatewayProvider = mock(ObjectProvider.class);
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        FakeDepartmentWriter departmentWriter = new FakeDepartmentWriter();
        gateway.department = Optional.of(new OrganizationDepartmentSnapshot("D001", "sales", "销售部", "", "", true));
        when(runRepository.save(any(OrganizationSyncRunEntity.class))).thenAnswer(invocation -> {
            OrganizationSyncRunEntity run = invocation.getArgument(0);
            run.setId(11L);
            return run;
        });

        OrganizationSyncRunEntity run = new OrganizationManualResyncAppService(
                gatewayProvider,
                departmentWriter,
                new FakeUserWriter(),
                runRepository,
                itemRepository,
                OrganizationDirectorySyncAppServiceTest.fixedSettings(true)
        ).resyncDepartment("oss", "D001", "admin");

        assertThat(run.getRunType()).isEqualTo("MANUAL_DEPARTMENT_RESYNC");
        assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(gateway.fetchedDeptId).isEqualTo("D001");
        assertThat(departmentWriter.writes).isEqualTo(1);
        verify(itemRepository).save(any(OrganizationSyncItemEntity.class));
    }

    @Test
    @DisplayName("resyncs one user by immutable userId")
    void resyncUser_fetchesAndWritesOneUser() {
        FakeGateway gateway = new FakeGateway();
        ObjectProvider<OrganizationDirectoryGateway> gatewayProvider = mock(ObjectProvider.class);
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        FakeUserWriter userWriter = new FakeUserWriter();
        gateway.user = Optional.of(new OrganizationUserSnapshot(
                "10001", "zhangsan", "张三", "zhangsan@example.com", "13900000000", "sales", "销售部", "", true
        ));
        when(runRepository.save(any(OrganizationSyncRunEntity.class))).thenAnswer(invocation -> {
            OrganizationSyncRunEntity run = invocation.getArgument(0);
            run.setId(12L);
            return run;
        });

        OrganizationSyncRunEntity run = new OrganizationManualResyncAppService(
                gatewayProvider,
                new FakeDepartmentWriter(),
                userWriter,
                runRepository,
                itemRepository,
                OrganizationDirectorySyncAppServiceTest.fixedSettings(true)
        ).resyncUser("oss", "10001", "admin");

        assertThat(run.getRunType()).isEqualTo("MANUAL_USER_RESYNC");
        assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(gateway.fetchedUserId).isEqualTo("10001");
        assertThat(userWriter.writes).isEqualTo(1);
        verify(itemRepository).save(any(OrganizationSyncItemEntity.class));
    }

    private static class FakeGateway implements OrganizationDirectoryGateway {
        Optional<OrganizationDepartmentSnapshot> department = Optional.empty();
        Optional<OrganizationUserSnapshot> user = Optional.empty();
        String fetchedDeptId;
        String fetchedUserId;

        public Optional<OrganizationDepartmentSnapshot> fetchDepartmentByDeptId(String deptId) {
            fetchedDeptId = deptId;
            return department;
        }

        public Optional<OrganizationUserSnapshot> fetchUserByUserId(String userId) {
            fetchedUserId = userId;
            return user;
        }

        public List<OrganizationDepartmentSnapshot> listDepartmentsByWindow(LocalDateTime startAt, LocalDateTime endAt) {
            return List.of();
        }

        public List<OrganizationUserSnapshot> listUsersByWindow(LocalDateTime startAt, LocalDateTime endAt) {
            return List.of();
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

        FakeUserWriter() {
            super(null, null, new OrganizationIntegrationProperties(), null);
        }

        public com.xiyu.bid.entity.User upsert(String sourceApp, String eventKey, OrganizationUserSnapshot snapshot) {
            writes++;
            return new com.xiyu.bid.entity.User();
        }
    }
}
