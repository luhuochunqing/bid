// Input: OrganizationDepartmentRepository 返回的混合数据（含空 code / 空 name 脏行）
// Output: loadAll() 只产出可用 DeptNode 且携带 sourceApp 分区键的断言
// Pos: Test/部门树读取网关单测（脏行前置跳过的降级行为）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AnalyticsDepartmentTreeGateway：脏行降级为忽略，不得升级为接口 500")
class AnalyticsDepartmentTreeGatewayTest {

    @Test
    @DisplayName("跳过 code/name 为空的脏行，并保留 sourceApp 分区键")
    void skipsDirtyRowsAndCarriesSourceApp() {
        OrganizationDepartmentRepository repository = mock(OrganizationDepartmentRepository.class);
        when(repository.findAllByOrderByDepartmentCode()).thenReturn(List.of(
                department("oss", "D01", null, "销售部"),
                department("oss", " ", "D01", "无码部门"),      // DeptNode 构造器对此抛 IAE
                department("ehsy", "D02", null, "  ")));       // name 空：进桶会得到不可展示的标签
        var gateway = new AnalyticsDepartmentTreeGateway(repository);

        var nodes = gateway.loadAll();

        assertThat(nodes).extracting(AnalyticsDepartmentHierarchySupport.DeptNode::name)
                .containsExactly("销售部");
        assertThat(nodes).extracting(AnalyticsDepartmentHierarchySupport.DeptNode::sourceApp)
                .containsExactly("oss");
    }

    private static OrganizationDepartmentEntity department(
            String sourceApp, String code, String parentCode, String name) {
        OrganizationDepartmentEntity d = new OrganizationDepartmentEntity();
        d.setSourceApp(sourceApp);
        d.setDepartmentCode(code);
        d.setParentDepartmentCode(parentCode);
        d.setDepartmentName(name);
        d.setEnabled(true);
        return d;
    }
}
