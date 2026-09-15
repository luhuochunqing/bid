// Input: ProjectQueryService（已按权限过滤并 enrich 的项目列表）
// Output: 未来 1 年内预测招标的商机行列表（按预测时间升序）
// Pos: workbench/service/ - 工作台商机预测查询服务
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.service.ProjectQueryService;
import com.xiyu.bid.workbench.domain.OpportunityPredictionPolicy;
import com.xiyu.bid.workbench.dto.WorkbenchOpportunityDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 工作台 AI 商机预测查询服务。
 *
 * <p>所有状态的项目均参与预测（已中标/流标/未中标/投标中等），按
 * {@link OpportunityPredictionPolicy} 提醒规则预测下一轮招标时间，
 * 只返回未来 1 年（12 个月）内的商机，按预测招标时间升序。
 *
 * <p>数据可见性由 {@link ProjectQueryService#getAllProjects()} 内置的
 * 权限过滤保证（管理员看全部，其他角色看参与项目）。
 *
 * <p>纯核心: OpportunityPredictionPolicy；副作用: 数据库读取（经 ProjectQueryService）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkbenchOpportunityQueryService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ProjectQueryService projectQueryService;

    /**
     * 查询工作台商机预测列表。
     *
     * @param today 当前日期（预测窗口基准）
     * @return 未来 1 年内的预测商机，按预测招标时间升序
     */
    @Transactional(readOnly = true)
    public List<WorkbenchOpportunityDTO> getOpportunityPredictions(final LocalDate today) {
        List<WorkbenchOpportunityDTO> opportunities = projectQueryService.getAllProjects().stream()
                .map(project -> toDto(project, today))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(WorkbenchOpportunityDTO::predictedBidDate))
                .toList();
        log.debug("Workbench opportunity predictions: {} rows (window={} months)",
                opportunities.size(), OpportunityPredictionPolicy.VISIBLE_WINDOW_MONTHS);
        return opportunities;
    }

    /**
     * 解析项目结果类型：列表 enrich 的 bidStatus 优先（来自结果登记），
     * 未登记结果的历史项目回退到 Project.status。
     */
    private static String resolveResultType(final ProjectDTO project) {
        if (project.getBidStatus() != null && !project.getBidStatus().isBlank()) {
            return project.getBidStatus();
        }
        return project.getStatus() != null ? project.getStatus().name() : null;
    }

    /** 计算预测时间并映射为 DTO；预测时间落在展示窗口外时返回 null（由调用方过滤）。 */
    private static WorkbenchOpportunityDTO toDto(final ProjectDTO project, final LocalDate today) {
        LocalDate bidOpenTime = project.getBidOpenTime() != null
                ? project.getBidOpenTime().toLocalDate()
                : null;
        var input = new OpportunityPredictionPolicy.Input(
                resolveResultType(project),
                project.getCustomerType(),
                bidOpenTime,
                project.getServicePeriodYears(),
                project.getServicePeriodEndDate());
        LocalDate predicted = OpportunityPredictionPolicy.predict(input).orElse(null);
        if (!OpportunityPredictionPolicy.isVisible(predicted, today)) {
            return null;
        }
        String customer = project.getCustomer() != null && !project.getCustomer().isBlank()
                ? project.getCustomer()
                : project.getOwnerUnit();
        return new WorkbenchOpportunityDTO(
                project.getId(),
                project.getName(),
                resolveResultType(project),
                customer,
                project.getProjectLeaderName(),
                project.getCustomerType(),
                project.getProjectType(),
                formatDate(bidOpenTime),
                project.getServicePeriodYears(),
                formatDate(project.getServicePeriodEndDate()),
                formatDate(predicted),
                OpportunityPredictionPolicy.isNear(predicted, today));
    }

    private static String formatDate(final LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : null;
    }
}
