package com.xiyu.bid.repository;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.project.core.ProjectStage;
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
 * Repository unit test for {@link TaskRepository}.
 *
 * <p>BE-1（工作台角色化改造）：验证 findByAssigneeIdAndProjectStage 的
 * JOIN Project + stage 过滤语义。Test profile 使用 H2 create-drop，
 * 通过 TestEntityManager 持久化 Project/Task 夹具数据。</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("TaskRepository 单元测试")
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Project draftingProject;
    private Project initiatedProject;

    @BeforeEach
    void seedProjects() {
        draftingProject = entityManager.persist(Project.builder()
                .name("标书制作阶段项目").tenderId(1L).managerId(1L)
                .stage(ProjectStage.DRAFTING.name()).build());
        initiatedProject = entityManager.persist(Project.builder()
                .name("已立项阶段项目").tenderId(2L).managerId(1L)
                .stage(ProjectStage.INITIATED.name()).build());
        entityManager.flush();
    }

    private Task persistTask(Long projectId, Long assigneeId, String title) {
        return entityManager.persist(Task.builder()
                .projectId(projectId).assigneeId(assigneeId).title(title).build());
    }

    @Test
    @DisplayName("findByAssigneeIdAndProjectStage 仅返回执行人匹配且项目阶段匹配的任务")
    void filtersByAssigneeAndProjectStage() {
        Task expected = persistTask(draftingProject.getId(), 1L, "我的标书任务");
        persistTask(draftingProject.getId(), 2L, "他人的标书任务");
        persistTask(initiatedProject.getId(), 1L, "我的立项阶段任务");
        entityManager.flush();

        List<Task> result = taskRepository.findByAssigneeIdAndProjectStage(1L, ProjectStage.DRAFTING);

        assertThat(result).extracting(Task::getId).containsExactly(expected.getId());
    }

    @Test
    @DisplayName("findByAssigneeIdAndProjectStageName 字符串重载与枚举 default 方法结果一致")
    void stringOverloadMatchesEnumOverload() {
        Task expected = persistTask(draftingProject.getId(), 1L, "我的标书任务");
        entityManager.flush();

        List<Task> byEnum = taskRepository.findByAssigneeIdAndProjectStage(1L, ProjectStage.DRAFTING);
        List<Task> byName = taskRepository.findByAssigneeIdAndProjectStageName(1L, ProjectStage.DRAFTING.name());

        assertThat(byEnum).extracting(Task::getId).containsExactly(expected.getId());
        assertThat(byName).extracting(Task::getId).containsExactly(expected.getId());
    }

    @Test
    @DisplayName("项目阶段无匹配时返回空列表")
    void returnsEmptyWhenNoStageMatch() {
        persistTask(initiatedProject.getId(), 1L, "我的立项阶段任务");
        entityManager.flush();

        List<Task> result = taskRepository.findByAssigneeIdAndProjectStage(1L, ProjectStage.DRAFTING);

        assertThat(result).isEmpty();
    }
}
