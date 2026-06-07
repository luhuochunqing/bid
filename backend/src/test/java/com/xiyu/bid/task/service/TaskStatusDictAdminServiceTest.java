package com.xiyu.bid.task.service;

import com.xiyu.bid.task.dto.TaskStatusDictReorderRequest;
import com.xiyu.bid.task.dto.TaskStatusDictUpsertRequest;
import com.xiyu.bid.task.entity.TaskStatusCategory;
import com.xiyu.bid.task.entity.TaskStatusDict;
import com.xiyu.bid.task.repository.TaskStatusDictRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import(TaskStatusDictAdminService.class)
class TaskStatusDictAdminServiceTest {

    @Autowired
    private TaskStatusDictAdminService service;

    @Autowired
    private TaskStatusDictRepository repo;

    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void seed() {
        seedDict("TODO", "待办", TaskStatusCategory.OPEN, "#909399", 10, true, false, true);
        seedDict("IN_PROGRESS", "进行中", TaskStatusCategory.IN_PROGRESS, "#409eff", 20, false, false, true);
        seedDict("REVIEW", "待审核", TaskStatusCategory.REVIEW, "#e6a23c", 30, false, false, true);
        seedDict("COMPLETED", "已完成", TaskStatusCategory.CLOSED, "#67c23a", 40, false, true, true);
        em.flush();
        em.clear();
    }

    private void seedDict(String code, String name, TaskStatusCategory cat, String color,
                          int sort, boolean initial, boolean terminal, boolean enabled) {
        TaskStatusDict s = new TaskStatusDict();
        s.setCode(code);
        s.setName(name);
        s.setCategory(cat);
        s.setColor(color);
        s.setSortOrder(sort);
        s.setIsInitial(initial);
        s.setIsTerminal(terminal);
        s.setEnabled(enabled);
        em.persist(s);
    }

    private TaskStatusDictUpsertRequest req(String code, String name, TaskStatusCategory cat) {
        TaskStatusDictUpsertRequest r = new TaskStatusDictUpsertRequest();
        r.setCode(code);
        r.setName(name);
        r.setCategory(cat);
        r.setColor("#cccccc");
        return r;
    }

    @Test
    void create_setsAutoSortOrder() {
        var r = req("ARCHIVED", "已归档", TaskStatusCategory.CLOSED);
        var dto = service.create(r);
        assertThat(dto.sortOrder()).isEqualTo(50);
    }

    @Test
    void create_rejectsLowercaseCode() {
        var r = req("todo_x", "x", TaskStatusCategory.OPEN);
        assertThatThrownBy(() -> service.create(r))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Code");
    }

    @Test
    void create_rejectsDuplicateCode() {
        var r = req("TODO", "待办2", TaskStatusCategory.OPEN);
        assertThatThrownBy(() -> service.create(r))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void create_settingInitialClearsOtherInitial() {
        var r = req("BACKLOG", "待规划", TaskStatusCategory.OPEN);
        r.setIsInitial(true);
        service.create(r);
        em.flush();
        em.clear();
        assertThat(repo.findById("TODO").get().getIsInitial()).isFalse();
        assertThat(repo.findById("BACKLOG").get().getIsInitial()).isTrue();
    }

    @Test
    void update_changesNameAndColor() {
        var r = new TaskStatusDictUpsertRequest();
        r.setName("待办（更新）");
        r.setColor("#aabbcc");
        var dto = service.update("TODO", r);
        assertThat(dto.name()).isEqualTo("待办（更新）");
        assertThat(dto.color()).isEqualTo("#aabbcc");
    }

    @Test
    void update_settingInitialClearsOtherInitial() {
        var r = new TaskStatusDictUpsertRequest();
        r.setIsInitial(true);
        service.update("IN_PROGRESS", r);
        em.flush();
        em.clear();
        assertThat(repo.findById("TODO").get().getIsInitial()).isFalse();
        assertThat(repo.findById("IN_PROGRESS").get().getIsInitial()).isTrue();
    }

    @Test
    void disable_rejectsLastInitial() {
        assertThatThrownBy(() -> service.disable("TODO"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("初始");
    }

    @Test
    void disable_rejectsLastTerminal() {
        assertThatThrownBy(() -> service.disable("COMPLETED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("终态");
    }

    @Test
    void disable_succeedsWhenAnotherTerminalExists() {
        var r = req("ARCHIVED", "已归档", TaskStatusCategory.CLOSED);
        r.setIsTerminal(true);
        service.create(r);
        em.flush();
        var dto = service.disable("COMPLETED");
        assertThat(dto.enabled()).isFalse();
    }

    @Test
    void enable_succeeds() {
        seedDict("REJECTED", "已拒绝", TaskStatusCategory.OPEN, "#f56c6c", 5, false, false, false);
        em.flush();
        em.clear();
        var dto = service.enable("REJECTED");
        assertThat(dto.enabled()).isTrue();
    }

    @Test
    void reorder_batchUpdatesSortOrder() {
        var req = new TaskStatusDictReorderRequest();
        req.setItems(List.of(
                new TaskStatusDictReorderRequest.Item("TODO", 100),
                new TaskStatusDictReorderRequest.Item("COMPLETED", 200)
        ));
        service.reorder(req);
        em.flush();
        em.clear();
        assertThat(repo.findById("TODO").get().getSortOrder()).isEqualTo(100);
        assertThat(repo.findById("COMPLETED").get().getSortOrder()).isEqualTo(200);
    }

    @Test
    void reorder_rejectsUnknownCode() {
        var req = new TaskStatusDictReorderRequest();
        req.setItems(List.of(new TaskStatusDictReorderRequest.Item("UNKNOWN_X", 1)));
        assertThatThrownBy(() -> service.reorder(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN_X");
    }
}
