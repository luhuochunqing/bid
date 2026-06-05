package com.xiyu.bid.task.repository;

import com.xiyu.bid.task.entity.TaskExtendedField;
import com.xiyu.bid.task.entity.TaskExtendedFieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository unit test for {@link TaskExtendedFieldRepository}.
 *
 * <p>Test profile disables Flyway and uses H2 create-drop, so schema is
 * derived from {@code @Entity} annotations. This test seeds three rows
 * (two enabled, one disabled) and verifies
 * {@link TaskExtendedFieldRepository#findByEnabledTrueOrderBySortOrderAsc()}
 * filters by {@code enabled} and orders by {@code sort_order} ascending.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("TaskExtendedFieldRepository 单元测试")
class TaskExtendedFieldRepositoryTest {

    @Autowired
    private TaskExtendedFieldRepository repo;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void seed() {
        entityManager.persist(row(
                "f_text", "文本", TaskExtendedFieldType.text, 10, true));
        entityManager.persist(row(
                "f_date", "日期", TaskExtendedFieldType.date, 20, true));
        entityManager.persist(row(
                "f_archived", "归档字段",
                TaskExtendedFieldType.text, 30, false));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("findByEnabledTrueOrderBySortOrderAsc 仅返回启用项, 按 sort_order 升序")
    void findByEnabledTrueOrderBySortOrderAsc_excludesDisabledAndSorts() {
        List<TaskExtendedField> result =
                repo.findByEnabledTrueOrderBySortOrderAsc();

        assertThat(result)
                .extracting(TaskExtendedField::getFieldKey)
                .containsExactly("f_text", "f_date");
        assertThat(result)
                .extracting(TaskExtendedField::getFieldKey)
                .doesNotContain("f_archived");
    }

    private static TaskExtendedField row(String key, String label,
            TaskExtendedFieldType type, int order, boolean enabled) {
        TaskExtendedField f = new TaskExtendedField();
        f.setFieldKey(key);
        f.setLabel(label);
        f.setFieldType(type);
        f.setSortOrder(order);
        f.setEnabled(enabled);
        f.setRequired(false);
        return f;
    }
}
