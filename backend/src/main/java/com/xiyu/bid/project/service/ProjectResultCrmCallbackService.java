// Input: projectId + resultType + competitors + userId
// Output: 异步回传 CRM bidInfoSync；失败仅日志，不影响主流程
// Pos: project/service/ - 编排层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.application.CrmChanceService;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoInnerDTO;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoSyncDTO;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.dto.ResultRegistrationRequest;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PRD §3.3.1.4 项目结果登记后的 CRM 回传旁路（与标讯状态回传 webhook 链路相互独立）。
 * <p>独立 Bean 以避开同类内 {@code @Async} 自调用绕过代理的问题——调用方通过注入本 Bean 走 Spring 代理。
 * <p>载荷严格符合 CRM POST /customer-chance/bidInfoSync 契约（{@link BidInfoSyncDTO}）：
 * <ul>
 *   <li>{@code code} ← {@code tender.crmOpportunityId}（商机编号），不可用 externalId 的 sourceId 部分
 *       （那是来源系统 ID，会让 CRM 匹配失败返 code:1，见 crm-integration-lessons.md §4）。</li>
 *   <li>{@code status} ← CRM projectStatus 数字枚举（1-跟进中 2-中标 3-丢标 4-流标 5-投标中 6-弃标，
 *       见 crm-integration-lessons.md §5）。</li>
 *   <li>{@code feedback} ← JSON 字符串（reason/vendor/paymentTerm/remark/operator/operateTime），
 *       competitors 合入 vendor/paymentTerm/remark 自由文本。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectResultCrmCallbackService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter STATUS_EDIT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;
    private final UserRepository userRepository;
    private final CrmChanceService crmChanceService;

    /**
     * 异步通知 CRM 系统结果已确认。失败仅记日志，不影响主流程。
     * <p>必须是 {@code public}（CGLIB 代理要求），且调用方须通过注入的 Bean 引用调用（不可 {@code this.}）。
     */
    @Async
    public void notifyResultConfirmed(Long projectId, BidResultType resultType,
                                      List<ResultRegistrationRequest.CompetitorRow> competitors,
                                      Long userId, Long resultId) {
        try {
            Long tenderId = projectRepository.findById(projectId).map(Project::getTenderId).orElse(null);
            if (tenderId == null) { log.warn("CRM callback skipped: projectId={} has no tenderId", projectId); return; }
            Tender tender = tenderRepository.findById(tenderId).orElse(null);
            if (tender == null) { log.warn("CRM callback skipped: tenderId={} not found", tenderId); return; }
            String crmOpportunityCode = tender.getCrmOpportunityId() != null ? tender.getCrmOpportunityId() : "";
            String crmOpportunityName = tender.getCrmOpportunityName() != null ? tender.getCrmOpportunityName() : "";
            User user = userRepository.findById(userId).orElse(null);
            String operatorName = user != null ? user.getFullName() : "";
            String statusEditTime = LocalDateTime.now().format(STATUS_EDIT_TIME_FORMAT);

            BidInfoInnerDTO inner = new BidInfoInnerDTO(
                    crmOpportunityName,
                    crmOpportunityCode,
                    mapToCrmStatus(resultType),
                    operatorName,
                    statusEditTime,
                    buildFeedback(resultType, competitors, operatorName, statusEditTime));
            BidInfoSyncDTO payload = new BidInfoSyncDTO(List.of(inner));

            boolean ok = crmChanceService.bidInfoSync(payload);
            log.info("CRM callback {}: projectId={} tenderId={} crmOpportunityCode={} resultType={}",
                    ok ? "sent" : "failed-by-crm", projectId, tenderId, crmOpportunityCode, resultType);
        } catch (RuntimeException e) {
            log.error("CRM result callback failed: projectId={} resultType={} error={}", projectId, resultType, e.getMessage(), e);
        }
    }

    /** CRM projectStatus 枚举：1-跟进中 2-中标 3-丢标 4-流标 5-投标中 6-弃标（§5，文档曾误写"1-弃标"）。 */
    private Integer mapToCrmStatus(BidResultType resultType) {
        return switch (resultType) {
            case WON -> 2;
            case LOST -> 3;
            case FAILED -> 4;
            case ABANDONED -> 6;
        };
    }

    /** feedback=JSON(reason+vendor+paymentTerm+remark+operator+operateTime)；competitors 无独立字段，合入自由文本。 */
    private String buildFeedback(BidResultType resultType,
                                 List<ResultRegistrationRequest.CompetitorRow> competitors,
                                 String operator, String operateTime) {
        String notes = joinCompetitorField(competitors, c -> c.notes());
        Map<String, Object> fb = new LinkedHashMap<>();
        // reason 优先取 competitors 的 notes（常含丢标/流标原因），空则退回 resultType 字面量。
        fb.put("reason", notes.isBlank() ? resultType.name() : notes);
        fb.put("vendor", joinCompetitorField(competitors, c -> c.name()));
        fb.put("paymentTerm", joinCompetitorField(competitors, c -> c.paymentTerm()));
        fb.put("remark", notes);
        fb.put("operator", operator);
        fb.put("operateTime", operateTime);
        try {
            return OBJECT_MAPPER.writeValueAsString(fb);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize CRM feedback", ex);
        }
    }

    private static String joinCompetitorField(List<ResultRegistrationRequest.CompetitorRow> competitors,
                                              java.util.function.Function<ResultRegistrationRequest.CompetitorRow, String> field) {
        if (competitors == null || competitors.isEmpty()) return "";
        return competitors.stream()
                .filter(c -> c != null && !isStrBlank(field.apply(c)))
                .map(field)
                .collect(Collectors.joining("; "));
    }

    private static boolean isStrBlank(String s) { return s == null || s.isBlank(); }
}
