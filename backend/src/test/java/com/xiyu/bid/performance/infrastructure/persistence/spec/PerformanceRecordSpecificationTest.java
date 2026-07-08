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
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 业绩列表 Specification 回归测试。
 *
 * <p>Sentry XIYU-Y：前端旧版本/浏览器缓存仍可能传 {@code projectTypes=CENTRALIZED}，
 * 后端 {@link ProjectType} 枚举已统一为 {@code COLLECTIVE}，Specification 必须做旧值
 * 别名兼容，避免 {@code InvalidDataAccessApiUsageException} 500 错误。
 */
@DataJpaTest
@ActiveProfiles("test")
class PerformanceRecordSpecificationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PerformanceRecordJpaRepository repository;

    @Test
    void build_projectTypeCentralizedAlias_mapsToCollective() {
        // given: 一条集采记录 + 一条办公记录
        persistRecord("集采合同", ProjectType.COLLECTIVE);
        persistRecord("办公合同", ProjectType.OFFICE);
        entityManager.flush();
        entityManager.clear();

        // when: 前端传旧值 CENTRALIZED（等同于 COLLECTIVE）
        PerformanceSearchCriteria criteria = new PerformanceSearchCriteria(
                null, null, List.of("CENTRALIZED"), null, null,
                null, null, null, null, null, null, null);

        List<PerformanceRecordEntity> result = repository.findAll(
                PerformanceRecordSpecification.build(criteria, alertConfig()));

        // then: 只返回集采记录
        assertThat(result)
                .extracting(PerformanceRecordEntity::getContractName)
                .containsExactlyInAnyOrder("集采合同");
    }

    @Test
    void build_projectTypeCollective_stillWorks() {
        persistRecord("集采合同", ProjectType.COLLECTIVE);
        persistRecord("办公合同", ProjectType.OFFICE);
        entityManager.flush();
        entityManager.clear();

        PerformanceSearchCriteria criteria = new PerformanceSearchCriteria(
                null, null, List.of("COLLECTIVE"), null, null,
                null, null, null, null, null, null, null);

        List<PerformanceRecordEntity> result = repository.findAll(
                PerformanceRecordSpecification.build(criteria, alertConfig()));

        assertThat(result)
                .extracting(PerformanceRecordEntity::getContractName)
                .containsExactlyInAnyOrder("集采合同");
    }

    @Test
    void build_projectTypeUnknown_stillThrows() {
        PerformanceSearchCriteria criteria = new PerformanceSearchCriteria(
                null, null, List.of("UNKNOWN_TYPE"), null, null,
                null, null, null, null, null, null, null);

        org.springframework.dao.InvalidDataAccessApiUsageException ex =
                catchThrowableOfType(
                        () -> repository.findAll(PerformanceRecordSpecification.build(criteria, alertConfig())),
                        org.springframework.dao.InvalidDataAccessApiUsageException.class);

        assertThat(ex).hasMessageContaining("无效的项目类型");
    }

    private void persistRecord(String contractName, ProjectType projectType) {
        PerformanceRecordEntity record = PerformanceRecordEntity.builder()
                .contractName(contractName)
                .projectType(projectType)
                .hasBidNotice(false)
                .build();
        entityManager.persist(record);
    }

    private PerformanceAlertConfig alertConfig() {
        return new PerformanceAlertConfig(1L, 180, 90, true);
    }
}
