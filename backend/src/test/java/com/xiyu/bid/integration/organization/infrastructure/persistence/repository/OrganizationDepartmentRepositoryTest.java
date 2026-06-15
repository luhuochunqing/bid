package com.xiyu.bid.integration.organization.infrastructure.persistence.repository;

import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("OrganizationDepartmentRepository")
class OrganizationDepartmentRepositoryTest {
    @Autowired
    private OrganizationDepartmentRepository repository;

    @Test
    @DisplayName("allows two source_apps to share the same department_code")
    void allowsTwoSourceAppsToShareDepartmentCode() {
        repository.saveAndFlush(department("oss", "sales", "OSS-SALES", "OSS 销售部"));
        repository.saveAndFlush(department("ehsy", "sales", "EHSY-SALES", "EHSY 销售部"));

        assertThat(repository.findBySourceAppAndDepartmentCode("oss", "sales"))
                .isPresent()
                .hasValueSatisfying(d -> assertThat(d.getDepartmentName()).isEqualTo("OSS 销售部"));
        assertThat(repository.findBySourceAppAndDepartmentCode("ehsy", "sales"))
                .isPresent()
                .hasValueSatisfying(d -> assertThat(d.getDepartmentName()).isEqualTo("EHSY 销售部"));
    }

    @Test
    @DisplayName("updates an existing row by business key without creating a duplicate")
    void updatesExistingRowByBusinessKey() {
        repository.saveAndFlush(department("oss", "sales", "OSS-SALES", "Original Name"));

        OrganizationDepartmentEntity updated = department("oss", "sales", "OSS-SALES", "Updated Name");
        repository.saveAndFlush(updated);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findBySourceAppAndDepartmentCode("oss", "sales"))
                .isPresent()
                .hasValueSatisfying(d -> assertThat(d.getDepartmentName()).isEqualTo("Updated Name"));
    }

    private OrganizationDepartmentEntity department(String sourceApp, String departmentCode, String externalDeptId, String name) {
        OrganizationDepartmentEntity d = new OrganizationDepartmentEntity();
        d.setSourceApp(sourceApp);
        d.setDepartmentCode(departmentCode);
        d.setExternalDeptId(externalDeptId);
        d.setDepartmentName(name);
        d.setEnabled(true);
        return d;
    }
}
