// Input: 运维重推请求（项目结果记录 ID）
// Output: 从 DB 重建 ProjectResultConfirmedEvent 并重新发布（现有监听器复用入队）
// Pos: webhook/application/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.webhook.application;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.domain.ProjectResultConfirmedEvent;
import com.xiyu.bid.project.entity.ProjectResult;
import com.xiyu.bid.project.entity.ProjectResultCompetitor;
import com.xiyu.bid.project.repository.ProjectResultCompetitorRepository;
import com.xiyu.bid.project.repository.ProjectResultRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * §4.2 项目结果确认回调运维重推服务。
 * <p><b>背景：</b>!2331 之前推送给 CRM 的 evidenceFiles.fileUrl 是 OBS 预签名 URL（1 小时过期），
 * CRM 侧持久化的旧 URL 已失效。本服务用于上线后对历史项目重推回调，用当前代码重新组装 payload
 * （fileUrl 为永久有效的集成下载端点），覆盖 CRM 侧旧值。
 * <p><b>实现：</b>从源头 DB 数据（project_result / project_result_competitors / projects / users）
 * 重建 {@link ProjectResultConfirmedEvent} 后重新发布。现有
 * {@link ProjectResultConfirmedWebhookListener} 用当前代码组装 payload 并入队，
 * 复用 §4.1 的 1min/5min/15min 重试机制——不维护第二套 payload 组装逻辑。
 * <p>源头数据（project_documents.file_url 存 {@code obs-direct:{uploadId}} 伪协议）永不过期，无需修 DB。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectResultRedeliveryService {

    private final ProjectResultRepository resultRepository;
    private final ProjectResultCompetitorRepository competitorRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 重推指定结果记录对应的 §4.2 回调。
     * <p>无事务上下文发布事件：监听器 fallbackExecution 同步执行（其内部 REQUIRES_NEW 独立事务入队），
     * 接口返回时任务已入队。
     *
     * @throws ResponseStatusException 404 结果记录或项目不存在
     */
    public RedeliveryOutcome redeliver(Long resultId) {
        ProjectResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "项目结果记录不存在: " + resultId));
        Project project = projectRepository.findById(result.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "项目不存在: " + result.getProjectId()));
        User operator = result.getCreatedBy() != null
                ? userRepository.findById(result.getCreatedBy()).orElse(null)
                : null;
        List<ProjectResultCompetitor> competitors = competitorRepository.findByResultIdOrderBySortOrderAsc(resultId);
        ProjectResultConfirmedEvent event = rebuildEvent(result, project, competitors, operator);
        eventPublisher.publishEvent(event);
        log.info("Redelivered project.result_confirmed webhook: resultId={}, projectId={}, tenderId={}, evidenceFiles={}, competitors={}",
                resultId, event.projectId(), event.tenderId(),
                event.evidenceFileIds().size(), event.competitors().size());
        return new RedeliveryOutcome(resultId, event.projectId(), event.tenderId(),
                event.resultType().name(), event.evidenceFileIds().size(), event.competitors().size());
    }

    /**
     * 纯函数：从 DB 实体重建领域事件（与 ProjectResultRegistrationService 发布时的字段映射一致）。
     * <p>occurredAt 取原登记时间（registeredAt），保证 feedback.operateTime 与 businessKey 和历史一致。
     */
    static ProjectResultConfirmedEvent rebuildEvent(ProjectResult result, Project project,
                                                    List<ProjectResultCompetitor> competitors, User operator) {
        List<ProjectResultConfirmedEvent.CompetitorSnapshot> snapshots = competitors == null ? List.of()
                : competitors.stream()
                    .map(c -> new ProjectResultConfirmedEvent.CompetitorSnapshot(
                            c.getName(), c.getDiscount(), c.getPaymentTerm(), c.getNotes()))
                    .toList();
        LocalDateTime occurredAt = result.getRegisteredAt() != null
                ? result.getRegisteredAt() : result.getCreatedAt();
        return new ProjectResultConfirmedEvent(
                result.getProjectId(), project.getTenderId(),
                BidResultType.valueOf(result.getResultType()),
                result.getSummary(), parseCsv(result.getEvidenceDocIds()), snapshots,
                result.getCreatedBy(), formatOperatorName(operator),
                result.getId(), occurredAt);
    }

    /** 与 ProjectResultRegistrationService.fromCsv 同语义：脏数据不打挂重推路径，返回空列表。 */
    static List<Long> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        try {
            return Arrays.stream(csv.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::parseLong).toList();
        } catch (NumberFormatException ex) {
            log.warn("evidenceDocIds CSV 解析失败，返回空列表 csv='{}' error={}", csv, ex.getMessage());
            return List.of();
        }
    }

    /** 与 ProjectResultRegistrationService.formatOperatorName 同格式：姓名（工号）。 */
    static String formatOperatorName(User user) {
        if (user == null) return "unknown";
        String fullName = user.getFullName();
        String employeeId = user.getDisplayEmployeeNumber();
        boolean blankName = fullName == null || fullName.isBlank();
        boolean blankId = employeeId == null || employeeId.isBlank();
        if (blankName) return blankId ? "unknown" : employeeId;
        return blankId ? fullName : "%s（%s）".formatted(fullName, employeeId);
    }

    /** 重推结果回执（运维接口响应体）。 */
    public record RedeliveryOutcome(
            Long resultId,
            Long projectId,
            Long tenderId,
            String resultType,
            int evidenceFileCount,
            int competitorCount
    ) {}
}
