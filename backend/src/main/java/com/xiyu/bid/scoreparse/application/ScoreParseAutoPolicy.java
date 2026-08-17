package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.AutoFailCircuit;
import com.xiyu.bid.scoreparse.domain.AutoParseGate;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;

import java.time.LocalDateTime;
import java.util.List;

final class ScoreParseAutoPolicy {

    private final ScoreParseTaskRepository taskRepository;
    private final ScoreItemRepository itemRepository;

    ScoreParseAutoPolicy(ScoreParseTaskRepository taskRepository, ScoreItemRepository itemRepository) {
        this.taskRepository = taskRepository;
        this.itemRepository = itemRepository;
    }

    boolean allowAuto(Long projectId, ScoreParseTask latestParse) {
        boolean hasItems = !itemRepository.findByProjectIdOrderByItemIndexAsc(projectId).isEmpty();
        return AutoParseGate.allowAutoCreate(latestParse != null, hasItems) && !circuitOpen(projectId);
    }

    boolean circuitOpen(Long projectId) {
        LocalDateTime now = LocalDateTime.now();
        int fails = 0;
        LocalDateTime latestAutoFail = null;
        LocalDateTime latestManualOk = null;
        for (String type : List.of("PARSE", "SCORING")) {
            for (ScoreParseTask task : taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                    projectId, type, List.of("FAILED"))) {
                if ("AUTO".equals(task.getTriggerSource()) && AutoFailCircuit.inWindow(task.getCompletedAt(), now)) {
                    fails++;
                    latestAutoFail = later(latestAutoFail, task.getCompletedAt());
                }
            }
            for (ScoreParseTask task : taskRepository.findByProjectIdAndTaskTypeAndStatusIn(
                    projectId, type, List.of("COMPLETED"))) {
                if (!"AUTO".equals(task.getTriggerSource())) {
                    latestManualOk = later(latestManualOk, task.getCompletedAt());
                }
            }
        }
        return AutoFailCircuit.isOpen(fails, latestAutoFail, latestManualOk);
    }

    private static LocalDateTime later(LocalDateTime current, LocalDateTime candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }
}
