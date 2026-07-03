// Input: Mockito 桩 TaskRepository
// Output: 任务摘要构建 + GateInputs 组装行为断言
// Pos: backend test source
package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.project.core.AllTasksCompletedPolicy;
import com.xiyu.bid.project.core.ProjectClosureGatePolicy;
import com.xiyu.bid.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectClosureTaskAssemblerTest {

    private TaskRepository taskRepo;
    private ProjectClosureTaskAssembler assembler;

    private static final Long PID = 1L;

    @BeforeEach
    void setup() {
        taskRepo = mock(TaskRepository.class);
        assembler = new ProjectClosureTaskAssembler(taskRepo);
    }

    private Task taskWithStatus(Task.Status status) {
        Task t = new Task();
        t.setStatus(status);
        return t;
    }

    @Test
    void noTasks_zeroCount() {
        when(taskRepo.findByProjectId(PID)).thenReturn(List.of());
        var summary = assembler.loadTaskSummary(PID);
        assertEquals(0, summary.totalTaskCount());
        assertEquals(0, summary.completedTaskCount());
        assertEquals(0, summary.incompleteTaskCount());
        assertEquals(0, summary.taskStates().size());
    }

    @Test
    void allCompleted_incompleteIsZero() {
        when(taskRepo.findByProjectId(PID)).thenReturn(List.of(
                taskWithStatus(Task.Status.COMPLETED),
                taskWithStatus(Task.Status.COMPLETED),
                taskWithStatus(Task.Status.COMPLETED)));
        var summary = assembler.loadTaskSummary(PID);
        assertEquals(3, summary.totalTaskCount());
        assertEquals(3, summary.completedTaskCount());
        assertEquals(0, summary.incompleteTaskCount());
    }

    @Test
    void mixedStatuses_correctCounts() {
        when(taskRepo.findByProjectId(PID)).thenReturn(List.of(
                taskWithStatus(Task.Status.TODO),
                taskWithStatus(Task.Status.REVIEW),
                taskWithStatus(Task.Status.COMPLETED)));
        var summary = assembler.loadTaskSummary(PID);
        assertEquals(3, summary.totalTaskCount());
        assertEquals(1, summary.completedTaskCount());
        assertEquals(2, summary.incompleteTaskCount());
    }

    @Test
    void toPolicyState_mapsCorrectly() {
        when(taskRepo.findByProjectId(PID)).thenReturn(List.of(
                taskWithStatus(Task.Status.TODO),
                taskWithStatus(Task.Status.REVIEW),
                taskWithStatus(Task.Status.COMPLETED)));
        var states = assembler.loadTaskSummary(PID).taskStates();
        assertEquals(AllTasksCompletedPolicy.TaskState.TODO, states.get(0));
        assertEquals(AllTasksCompletedPolicy.TaskState.REVIEW, states.get(1));
        assertEquals(AllTasksCompletedPolicy.TaskState.COMPLETED, states.get(2));
    }

    @Test
    void buildGateInputs_containsTaskStates() {
        when(taskRepo.findByProjectId(PID)).thenReturn(List.of(
                taskWithStatus(Task.Status.COMPLETED)));
        var depositSnap = new ProjectClosureGatePolicy.DepositSnapshot(
                false, ProjectClosureGatePolicy.DepositReturnStatus.NA,
                null, null, null, null);
        var closureInput = new ProjectClosureGatePolicy.ClosureInput("/archive", "notes");
        var gateInputs = assembler.buildGateInputs(depositSnap, closureInput, PID);
        assertNotNull(gateInputs);
        assertEquals(1, gateInputs.taskStates().size());
        assertEquals(AllTasksCompletedPolicy.TaskState.COMPLETED, gateInputs.taskStates().get(0));
    }
}
