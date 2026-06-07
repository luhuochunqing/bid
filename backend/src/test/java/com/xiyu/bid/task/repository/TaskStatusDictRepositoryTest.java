package com.xiyu.bid.task.repository;

import com.xiyu.bid.task.entity.TaskStatusCategory;
import com.xiyu.bid.task.entity.TaskStatusDict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository unit test for {@link TaskStatusDictRepository}.
 *
 * <p>Test profile disables Flyway and uses H2 create-drop,
 * so V101 seed data is re-inserted here via TestEntityManager
 * to mirror the production seed set.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("TaskStatusDictRepository 单元测试")
class TaskStatusDictRepositoryTest {

    @Autowired
    private TaskStatusDictRepository repo;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void seedDict() {
        // Mirror V101 seed data (4 rows, sort_order 10/20/30/40)
        // plus one disabled row to verify the enabled filter.
        entityManager.persist(row(
                "TODO", "待办", TaskStatusCategory.OPEN,
                "#909399", 10, true, false, true));
        entityManager.persist(row(
                "IN_PROGRESS", "进行中", TaskStatusCategory.IN_PROGRESS,
                "#409eff", 20, false, false, true));
        entityManager.persist(row(
                "REVIEW", "待审核", TaskStatusCategory.REVIEW,
                "#e6a23c", 30, false, false, true));
        entityManager.persist(row(
                "COMPLETED", "已完成", TaskStatusCategory.CLOSED,
                "#67c23a", 40, false, true, true));
        entityManager.persist(row(
                "ARCHIVED", "归档", TaskStatusCategory.OPEN,
                "#c0c4cc", 50, false, false, false));
        entityManager.flush();
    }

    @Test
    @DisplayName("findByEnabledTrueOrderBySortOrderAsc 返回按 sort_order 升序的全部启用项")
    void findsAllEnabledOrderedBySortOrder() {
        List<TaskStatusDict> list = repo
                .findByEnabledTrueOrderBySortOrderAsc();
        assertThat(list).extracting(TaskStatusDict::getCode)
                .containsExactly(
                        "TODO", "IN_PROGRESS", "REVIEW", "COMPLETED");
        assertThat(list).extracting(TaskStatusDict::getCode)
                .doesNotContain("ARCHIVED");
    }

    @Test
    @DisplayName("findByIsInitialTrue 返回标记为初始状态的字典项")
    void findsInitialStatus() {
        Optional<TaskStatusDict> initial = repo.findByIsInitialTrue();
        assertThat(initial).isPresent();
        assertThat(initial.get().getCode()).isEqualTo("TODO");
    }

    private static TaskStatusDict row(String code, String name,
            TaskStatusCategory category, String color, int sortOrder,
            boolean initial, boolean terminal, boolean enabled) {
        TaskStatusDict d = new TaskStatusDict();
        d.setCode(code);
        d.setName(name);
        d.setCategory(category);
        d.setColor(color);
        d.setSortOrder(sortOrder);
        d.setIsInitial(initial);
        d.setIsTerminal(terminal);
        d.setEnabled(enabled);
        return d;
    }
}
