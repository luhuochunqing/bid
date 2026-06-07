// Input: projectId + 目标阶段 + GateInputs；调用 ProjectStageTransitionPolicy
// Output: 写入 Project.stage + 审计 PROJECT_STAGE_TRANSITIONED；非法跳转 → 409
// Pos: project/service/ - 编排层，纯规则委托 core 包
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.casework.application.ProjectClosedEvent;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.core.ProjectStatusPolicy;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.ProjectStageTransitionPolicy;
import com.xiyu.bid.project.core.ProjectStageTransitionPolicy.GateInputs;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PRD §5.4 全局阶段编排 (WS-G)。
 *
 * <p>职责：
 * <ul>
 *   <li>读取/写入 Project.stage 列。</li>
 *   <li>调用 {@link ProjectStageTransitionPolicy} 决策合法性（线性、CLOSED 终态、跨阶段拒绝）。</li>
 *   <li>记录 audit (PROJECT_STAGE_TRANSITIONED)。</li>
 * </ul>
 * 不在此类内拼装具体 gate 业务规则——由调用方装配 GateInputs。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectStageService {

    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public ProjectStage currentStage(Long projectId) {
        Project p = mustGet(projectId);
        return parse(p.getStage());
    }

    @Transactional(readOnly = true)
    public List<ProjectStage> allowedNext(Long projectId) {
        ProjectStage cur = currentStage(projectId);
        return switch (cur) {
            case INITIATED -> List.of(ProjectStage.DRAFTING);
            case DRAFTING -> List.of(ProjectStage.EVALUATING);
            case EVALUATING -> List.of(ProjectStage.RESULT_PENDING);
            case RESULT_PENDING -> List.of(ProjectStage.RETROSPECTIVE, ProjectStage.CLOSED);
            case RETROSPECTIVE -> List.of(ProjectStage.CLOSED);
            case CLOSED -> List.of();
        };
    }

    /**
     * 推进阶段：校验 + 持久化 + 审计。非法跳转 / CLOSED 终态 / 同态 → 409。
     */
    @Auditable(action = "PROJECT_STAGE_TRANSITIONED", entityType = "Project",
            description = "推进项目阶段")
    public ProjectStage requestTransition(Long projectId, ProjectStage target, GateInputs gateInputs) {
        Project p = mustGet(projectId);
        ProjectStage current = parse(p.getStage());
        var decision = ProjectStageTransitionPolicy.decide(current, target,
                gateInputs == null ? GateInputs.EMPTY : gateInputs);
        if (!decision.allowed()) {
            var deny = (ProjectStageTransitionPolicy.Decision.Deny) decision;
            throw new ResponseStatusException(HttpStatus.CONFLICT, deny.reason());
        }
        p.setStage(target.name());
        syncProjectStatus(p, target);
        // 档案 4.1.1.1.1：阶段时间戳自动记录（首次写入原则）
        if (target == ProjectStage.EVALUATING && p.getEvaluatingAt() == null) {
            p.setEvaluatingAt(LocalDateTime.now());
        }
        if (target == ProjectStage.CLOSED && p.getClosedAt() == null) {
            p.setClosedAt(LocalDateTime.now());
        }
        projectRepository.save(p);
        log.info("Project stage transitioned project={} {}→{}", projectId, current, target);

        // 蓝图 4.1.1.2.1：项目阶段机进入 CLOSED 之后触发 AI 案例沉淀。
        // 这里把"已结项"信号发到 ProjectClosedEvent，由 ProjectClosedEventListener 异步处理。
        if (target == ProjectStage.CLOSED && current != ProjectStage.CLOSED) {
            eventPublisher.publishEvent(new ProjectClosedEvent(this, projectId, p.getName()));
        }
        return target;
    }

    private Project mustGet(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
    }

    private void syncProjectStatus(Project project, ProjectStage targetStage) {
        Project.Status nextStatus = ProjectStatusPolicy.compute(targetStage, null, true);
        project.setStatus(nextStatus);
    }

    private ProjectStage parse(String code) {
        if (code == null || code.isBlank()) return ProjectStage.INITIATED;
        try {
            return ProjectStage.valueOf(code);
        } catch (IllegalArgumentException ex) {
            // H3: 非空但不在枚举里的值是数据腐败 — 静默回落会掩盖坏数据。
            log.warn("Project.stage 列含未知枚举值 '{}'，疑似数据腐败/未迁移历史数据", code);
            throw new IllegalStateException("Project.stage 含未知枚举值: " + code);
        }
    }
}
