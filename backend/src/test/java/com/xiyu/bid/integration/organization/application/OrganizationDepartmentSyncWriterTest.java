package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.OrganizationDepartmentSnapshot;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationDepartmentSyncWriter - department persistence")
class OrganizationDepartmentSyncWriterTest {
    @Mock
    private OrganizationDepartmentRepository repository;

    @Test
    @DisplayName("upsert looks up by business key (source_app, department_code)")
    void upsert_looksUpByBusinessKey() {
        when(repository.findBySourceAppAndDepartmentCode("customer-org", "sales")).thenReturn(Optional.empty());
        when(repository.save(any(OrganizationDepartmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrganizationDepartmentSyncWriter writer = new OrganizationDepartmentSyncWriter(repository);

        writer.upsert("customer-org", "event-key", new OrganizationDepartmentSnapshot("D001", "sales", "销售部", "", "", true));

        ArgumentCaptor<OrganizationDepartmentEntity> saved = ArgumentCaptor.forClass(OrganizationDepartmentEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSourceApp()).isEqualTo("customer-org");
        assertThat(saved.getValue().getDepartmentCode()).isEqualTo("sales");
        assertThat(saved.getValue().getExternalDeptId()).isEqualTo("D001");
        assertThat(saved.getValue().getLastEventKey()).isEqualTo("event-key");
        verify(repository, never()).findBySourceAppAndExternalDeptId(any(), any());
    }

    @Test
    @DisplayName("upsert resolves parent department code within the same source_app")
    void upsert_resolvesParentDepartmentCodeWithinSameSourceApp() {
        OrganizationDepartmentEntity parent = new OrganizationDepartmentEntity();
        parent.setSourceApp("customer-org");
        parent.setDepartmentCode("headquarters");
        parent.setExternalDeptId("P001");
        when(repository.findBySourceAppAndDepartmentCode("customer-org", "sales")).thenReturn(Optional.empty());
        when(repository.findBySourceAppAndExternalDeptId("customer-org", "P001")).thenReturn(Optional.of(parent));
        when(repository.save(any(OrganizationDepartmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrganizationDepartmentSyncWriter writer = new OrganizationDepartmentSyncWriter(repository);

        writer.upsert("customer-org", "event-key", new OrganizationDepartmentSnapshot("D001", "sales", "销售部", "P001", "", true));

        ArgumentCaptor<OrganizationDepartmentEntity> saved = ArgumentCaptor.forClass(OrganizationDepartmentEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getParentDepartmentCode()).isEqualTo("headquarters");
        assertThat(saved.getValue().getParentExternalDeptId()).isEqualTo("P001");
    }

    @Test
    @DisplayName("upsert does not overwrite a department from another source_app with the same department code")
    void upsert_doesNotOverwriteDepartmentFromAnotherSourceApp() {
        OrganizationDepartmentEntity existingOss = new OrganizationDepartmentEntity();
        existingOss.setSourceApp("oss");
        existingOss.setDepartmentCode("sales");
        existingOss.setDepartmentName("OSS 销售部");
        existingOss.setExternalDeptId("OSS-D001");
        when(repository.findBySourceAppAndDepartmentCode("ehsy", "sales")).thenReturn(Optional.empty());
        when(repository.save(any(OrganizationDepartmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrganizationDepartmentSyncWriter writer = new OrganizationDepartmentSyncWriter(repository);

        writer.upsert("ehsy", "event-key", new OrganizationDepartmentSnapshot("EHSY-D001", "sales", "EHSY 销售部", "", "", true));

        ArgumentCaptor<OrganizationDepartmentEntity> saved = ArgumentCaptor.forClass(OrganizationDepartmentEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSourceApp()).isEqualTo("ehsy");
        assertThat(saved.getValue().getDepartmentCode()).isEqualTo("sales");
        assertThat(saved.getValue().getExternalDeptId()).isEqualTo("EHSY-D001");
        assertThat(saved.getValue().getDepartmentName()).isEqualTo("EHSY 销售部");
        verify(repository, never()).findBySourceAppAndDepartmentCode("oss", "sales");
    }

    @Test
    @DisplayName("disable marks existing department inactive by immutable external department id")
    void disable_marksExistingDepartmentInactiveByExternalId() {
        OrganizationDepartmentEntity existing = new OrganizationDepartmentEntity();
        existing.setEnabled(true);
        existing.setExternalDeptId("3730158");
        existing.setSourceApp("oss");
        when(repository.findBySourceAppAndExternalDeptId("oss", "3730158")).thenReturn(Optional.of(existing));
        when(repository.save(any(OrganizationDepartmentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrganizationDepartmentSyncWriter writer = new OrganizationDepartmentSyncWriter(repository);

        writer.disableByExternalId("oss", "event-key", "3730158");

        ArgumentCaptor<OrganizationDepartmentEntity> saved = ArgumentCaptor.forClass(OrganizationDepartmentEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEnabled()).isFalse();
        assertThat(saved.getValue().getLastEventKey()).isEqualTo("event-key");
        assertThat(saved.getValue().getLastSyncedAt()).isNotNull();
    }
}
