// Input: Project / ProjectResult / Tender 实体
// Output: 派生字段（结果类型、招标主体、项目负责人、客户类型、项目类型）+ 站内信 payload Map
// Pos: Service/业务层 - workbench 商机预测提醒纯派生辅助（无副作用、无依赖注入）
// 维护声明:
//   - 纯函数集中于此，编排逻辑留在 OpportunityPredictionReminderService；
//   - customer/projectLeaderName/customerType/projectType 口径对齐商机预测表格 enrich 链
//     （WorkbenchOpportunityQueryService.toDto 消费 ProjectQueryService.getAllProjects 的 enrich 结果）；
//   - 与 ProjectListEnrichmentSupport 同构（final + 私有构造 + 静态方法）。
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.tenderevent.domain.OpportunityPredictionEventPayload;
import com.xiyu.bid.project.core.InitiationFieldPolicy;
import com.xiyu.bid.project.entity.ProjectResult;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商机预测提醒的字段派生与站内信 payload 组装（纯核心，无副作用、无依赖注入）。
 *
 * <p>从 {@link OpportunityPredictionReminderService} 抽出：编排服务只做查数据/解析接收人/
 * 调通知/落日志/发事件，纯派生规则集中于此，与 {@code ProjectListEnrichmentSupport} 同构。
 */
final class OpportunityPredictionReminderSupport {

    /** 商机中心前端路由（通知深链 targetUrl）。 */
    private static final String OPPORTUNITY_CENTER_PATH = "/bidding/customer-opportunities";

    private OpportunityPredictionReminderSupport() {
    }

    /**
     * 结果类型：结果登记（project_result.resultType）优先，
     * 未登记结果的历史项目回退 Project.status。
     */
    static String resolveResultType(Project project, ProjectResult result) {
        if (result != null && result.getResultType() != null && !result.getResultType().isBlank()) {
            return result.getResultType();
        }
        return project.getStatus() != null ? project.getStatus().name() : null;
    }

    /**
     * 招标主体名称（站内信用）：标讯采购人名称优先，缺失回退项目客户名称。
     */
    static String resolvePurchaserName(Project project, Tender tender) {
        if (tender != null && StringUtils.hasText(tender.getPurchaserName())) {
            return tender.getPurchaserName().trim();
        }
        return StringUtils.hasText(project.getCustomer()) ? project.getCustomer() : null;
    }

    /**
     * 事件推送招标主体：对齐商机预测表格 {@code WorkbenchOpportunityQueryService.toDto}——
     * 项目客户（{@code Project.customer}）优先，为空时回退关联标讯采购人
     * （{@code Tender.purchaserName}，即表格 ownerUnit 列的数据源）。
     * 注意与 {@link #resolvePurchaserName}（站内信用，tender 优先）方向相反。
     */
    static String resolveCustomer(Project project, Tender tender) {
        if (StringUtils.hasText(project.getCustomer())) {
            return project.getCustomer();
        }
        return tender != null && StringUtils.hasText(tender.getPurchaserName())
                ? tender.getPurchaserName() : null;
    }

    /**
     * 项目负责人：立项详情（{@code project_initiation_details.projectLeaderName}）优先，
     * 缺失回退关联标讯项目经理（{@code Tender.projectManagerName}）。对齐商机预测表格
     * enrich 链（ProjectQueryService 立项详情 → ProjectListEnrichmentSupport 标讯兜底）。
     */
    static String resolveProjectLeaderName(String initiationLeaderName, Tender tender) {
        if (StringUtils.hasText(initiationLeaderName)) {
            return initiationLeaderName;
        }
        return tender != null && StringUtils.hasText(tender.getProjectManagerName())
                ? tender.getProjectManagerName() : null;
    }

    /**
     * 客户类型：{@code Project.customerType} 优先，缺失（严格 null）回退关联标讯
     * （{@code Tender.customerType}），并复用 {@link InitiationFieldPolicy#normalizeCustomerType}
     * 将中文/别名归一化为后端枚举名（归一化失败保留原值）。逐条对齐商机预测表格 enrich 链
     * {@code ProjectListEnrichmentSupport.populateFromTender}——保证推送值/预测规则与表格同源同口径。
     */
    static String resolveCustomerType(Project project, Tender tender) {
        if (project.getCustomerType() != null) {
            return project.getCustomerType();
        }
        if (tender == null || tender.getCustomerType() == null) {
            return null;
        }
        String normalized = InitiationFieldPolicy.normalizeCustomerType(tender.getCustomerType());
        return normalized != null ? normalized : tender.getCustomerType();
    }

    /** 项目类型：立项详情优先，缺失时按商机列表口径从关联标讯归一化回退。 */
    static String resolveProjectType(String initiationProjectType, Tender tender) {
        if (StringUtils.hasText(initiationProjectType)) {
            return initiationProjectType;
        }
        if (tender == null || tender.getProjectType() == null) {
            return null;
        }
        String normalized = InitiationFieldPolicy.normalizeProjectType(tender.getProjectType());
        return normalized != null ? normalized : tender.getProjectType();
    }

    /** 站内信 payload：保序组装项目标识 + 招标主体 + 服务期止 + 预测招标日 + 深链。 */
    static Map<String, Object> buildPayload(Project project, String purchaserName,
                                            LocalDate servicePeriodEndDate, LocalDate predicted) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", project.getId());
        payload.put("projectName", project.getName());
        payload.put("purchaserName", purchaserName);
        if (servicePeriodEndDate != null) {
            payload.put("servicePeriodEndDate", servicePeriodEndDate.toString());
        }
        payload.put("predictedBidDate", predicted.toString());
        payload.put("targetUrl", OPPORTUNITY_CENTER_PATH);
        return payload;
    }

    /** 组装事件库 11 字段载荷，序列化与中文映射由 publisher 负责。 */
    static OpportunityPredictionEventPayload buildEventPayload(
            Project project, String resultType, String projectLeaderName, String customer,
            String customerType, String projectType, LocalDate bidOpenTime,
            java.math.BigDecimal servicePeriodYears,
            LocalDate servicePeriodEndDate, LocalDate predicted) {
        return new OpportunityPredictionEventPayload(
                project.getId(), project.getName(), resultType, customer, projectLeaderName, customerType,
                projectType, bidOpenTime != null ? bidOpenTime.toString() : null,
                servicePeriodYears, servicePeriodEndDate, predicted);
    }

}
