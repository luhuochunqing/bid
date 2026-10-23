package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.infrastructure.CasePrecipitationTask;
import com.xiyu.bid.casework.infrastructure.CasePrecipitationTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CasePrecipitationTaskStateService {
    static final Duration STALE_AFTER = Duration.ofHours(2);
    private final CasePrecipitationTaskRepository repository;

    @Transactional
    public ClaimResult claim(Long projectId, Long triggerUserId) {
        repository.lockProject(projectId);
        LocalDateTime now = LocalDateTime.now();
        var latest = repository.findLatestForUpdate(projectId).orElse(null);
        if (latest != null && isActive(latest.getStatus())) {
            if (latest.getUpdatedAt() != null && latest.getUpdatedAt().isBefore(now.minus(STALE_AFTER))) {
                failEntity(latest, "任务执行超时，请重新触发", now);
            } else {
                return new ClaimResult(toView(latest), true);
            }
        }
        var task = CasePrecipitationTask.builder()
                .taskId(UUID.randomUUID().toString()).projectId(projectId)
                .status("PENDING").stage("等待执行").triggerUserId(triggerUserId).build();
        return new ClaimResult(toView(repository.saveAndFlush(task)), false);
    }

    @Transactional(readOnly = true)
    public TaskView latest(Long projectId) {
        return repository.findFirstByProjectIdOrderByIdDesc(projectId).map(this::toView).orElse(null);
    }

    @Transactional
    public boolean start(String taskId) {
        var task = repository.findByTaskIdForUpdate(taskId).orElse(null);
        if (task == null || !"PENDING".equals(task.getStatus())) return false;
        LocalDateTime now = LocalDateTime.now();
        task.setStatus("PROCESSING"); task.setStage("解析标书"); task.setStartedAt(now); task.setHeartbeatAt(now);
        return true;
    }

    @Transactional
    public boolean complete(String taskId, int caseCount) {
        var task = repository.findByTaskIdForUpdate(taskId).orElse(null);
        if (task == null || !isActive(task.getStatus())) return false;
        LocalDateTime now = LocalDateTime.now();
        task.setStatus("COMPLETED"); task.setStage("沉淀完成"); task.setCaseCount(caseCount); task.setCompletedAt(now);
        return true;
    }

    @Transactional
    public void fail(String taskId, String message) {
        repository.findByTaskIdForUpdate(taskId).filter(t -> isActive(t.getStatus()))
                .ifPresent(t -> failEntity(t, message, LocalDateTime.now()));
    }

    private void failEntity(CasePrecipitationTask task, String message, LocalDateTime now) {
        task.setStatus("FAILED"); task.setStage("沉淀失败"); task.setUserErrorMessage(message); task.setCompletedAt(now);
    }
    private boolean isActive(String status) { return "PENDING".equals(status) || "PROCESSING".equals(status); }
    private TaskView toView(CasePrecipitationTask t) {
        return new TaskView(t.getTaskId(), t.getProjectId(), t.getStatus(), t.getStage(), t.getCaseCount(),
                t.getUserErrorMessage(), t.getCreatedAt(), t.getUpdatedAt(), t.getCompletedAt());
    }
    public record ClaimResult(TaskView task, boolean reused) {}
    public record TaskView(String taskId, Long projectId, String status, String stage, Integer caseCount,
                           String message, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime completedAt) {}
}
