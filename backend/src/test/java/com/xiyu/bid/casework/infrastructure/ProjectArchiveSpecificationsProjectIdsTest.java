package com.xiyu.bid.casework.infrastructure;

import com.xiyu.bid.casework.dto.ProjectArchiveQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目档案导出按勾选 projectIds 过滤回归测试。
 *
 * <p>修复前：前端未传递 projectIds，后端 Specification 也不支持该字段，
 * 导致勾选单个项目导出时返回列表中所有项目。
 *
 * <p>修复后：query.projectIds 非空时，Specification 追加 projectId IN 条件，
 * 仅导出被勾选的项目。
 */
@DataJpaTest
@ActiveProfiles("test")
class ProjectArchiveSpecificationsProjectIdsTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectArchiveRepository archiveRepository;

    @Test
    void withFilters_projectIds_filtersToSelectedProjects() {
        // given: 3 个项目档案
        persistArchive(1L, "项目 A");
        persistArchive(2L, "项目 B");
        persistArchive(3L, "项目 C");
        entityManager.flush();
        entityManager.clear();

        // when: 仅勾选项目 A 和 C
        ProjectArchiveQuery query = new ProjectArchiveQuery();
        query.setProjectIds(List.of(1L, 3L));

        List<ProjectArchive> result = archiveRepository.findAll(
                ProjectArchiveSpecifications.withFilters(query, List.of(), true));

        // then: 只返回勾选的项目
        assertThat(result)
                .extracting(ProjectArchive::getProjectId)
                .containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void withFilters_projectIds_intersectsWithAllowedProjectIdsForNonAdmin() {
        // given: 3 个项目档案
        persistArchive(1L, "项目 A");
        persistArchive(2L, "项目 B");
        persistArchive(3L, "项目 C");
        entityManager.flush();
        entityManager.clear();

        // when: 勾选全部，但当前用户只能访问 1 和 2
        ProjectArchiveQuery query = new ProjectArchiveQuery();
        query.setProjectIds(List.of(1L, 2L, 3L));

        List<ProjectArchive> result = archiveRepository.findAll(
                ProjectArchiveSpecifications.withFilters(query, List.of(1L, 2L), false));

        // then: 权限与勾选取交集
        assertThat(result)
                .extracting(ProjectArchive::getProjectId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void withFilters_emptyProjectIds_ignoresFilterAndReturnsAllAccessible() {
        // given: 2 个项目档案
        persistArchive(1L, "项目 A");
        persistArchive(2L, "项目 B");
        entityManager.flush();
        entityManager.clear();

        // when: projectIds 为空（未勾选）
        ProjectArchiveQuery query = new ProjectArchiveQuery();
        query.setProjectIds(List.of());

        List<ProjectArchive> result = archiveRepository.findAll(
                ProjectArchiveSpecifications.withFilters(query, List.of(), true));

        // then: 返回全部
        assertThat(result).hasSize(2);
    }

    private void persistArchive(Long projectId, String projectName) {
        ProjectArchive archive = new ProjectArchive();
        archive.setProjectId(projectId);
        archive.setProjectName(projectName);
        archive.setArchiveStatus("ACTIVE");
        entityManager.persist(archive);
    }
}
