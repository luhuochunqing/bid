package com.xiyu.bid.integration.organization.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * {@link OrganizationDepartmentEntity} 的复合主键。
 *
 * <p>同一 {@code department_code} 可能同时存在于多个 source_app（如 oss/ehsy），
 * 因此主键必须包含 {@code source_app}，避免不同来源的部门记录互相覆盖。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class OrganizationDepartmentId implements Serializable {
    private String sourceApp;
    private String departmentCode;
}
