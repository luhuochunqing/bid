package com.xiyu.bid.performance.infrastructure.persistence.spec;

import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.valueobject.ProjectType;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceRecordEntity;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceRecordJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PerformanceRecordSpecification 集成测试。
 *
 * 重点覆盖：项目类型筛选时前端 option value 与后端枚举必须一致。
 * 业绩模块后端枚举使用 CENTRALIZED（集采），历史前端误传 COLLECTIVE 会导致 IllegalArgumentException。
 */
@DataJpaTest
@ActiveProfiles("test")
class PerformanceRecordSpecificationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PerformanceRecordJpaRepository repository;

    private final PerformanceAlertConfig config = new PerformanceAlertConfig(null, 180, 90, true);

    @Test
    void filterByProjectTypeCentralized_returnsOnlyCentralizedRecords() {
        // given
        persistRecord("合同-集采", ProjectType.CENTRALIZED);
        persistRecord("合同-办公", ProjectType.OFFICE);
        entityManager.flush();
        entityManager.clear();

        // when: 前端按后端枚举名 CENTRALIZED 筛选
        var criteria = PerformanceSearchCriteria.of(
                null, null, List.of("CENTRALIZED"), null, null,
                null, null, null, null, null, null, null);
        var result = repository.findAll(PerformanceRecordSpecification.build(criteria, config));

        // then
        assertThat(result)
                .hasSize(1)
                .extracting(PerformanceRecordEntity::getContractName)
                .containsExactly("合同-集采");
    }

    @Test
    void filterByProjectTypeCollective_throwsIllegalArgumentException() {
        // when: 前端误传项目模块的 COLLECTIVE（业绩模块枚举为 CENTRALIZED）
        var criteria = PerformanceSearchCriteria.of(
                null, null, List.of("COLLECTIVE"), null, null,
                null, null, null, null, null, null, null);

        // then
        assertThatThrownBy(() -> PerformanceRecordSpecification.build(criteria, config)
                .toPredicate(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无效的项目类型: COLLECTIVE");
    }

    private void persistRecord(String contractName, ProjectType projectType) {
        PerformanceRecordEntity entity = PerformanceRecordEntity.builder()
                .contractName(contractName)
                .projectType(projectType)
                .hasBidNotice(false)
                .build();
        entityManager.persist(entity);
    }
}
