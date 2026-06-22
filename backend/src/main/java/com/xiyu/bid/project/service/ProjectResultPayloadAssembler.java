// Input: ProjectResultConfirmedEvent + 查询 Project/Tender/User/ProjectDocument
// Output: 组装好的 §4.2 ProjectResultCallbackPayload（不含 HTTP/重试逻辑）
// Pos: project/service/ - 编排层（只做查询+组装，不做 HTTP）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.infrastructure.dto.CompetitorInfo;
import com.xiyu.bid.crm.infrastructure.dto.EvidenceFile;
import com.xiyu.bid.crm.infrastructure.dto.ProjectResultCallbackPayload;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.external.CallerContext;
import com.xiyu.bid.integration.external.ExternalIdParser;
import com.xiyu.bid.integration.external.TenderAttachmentUrlResolver;
import com.xiyu.bid.project.core.BidResultType;
import com.xiyu.bid.project.domain.ProjectResultConfirmedEvent;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * §4.2 项目结果确认回调载荷组装器。
 * <p>单一职责：查询关联数据 + 组装符合 §4.2 契约的 {@link ProjectResultCallbackPayload}。
 * <p>不负责 HTTP 发送和重试——由 {@code WebhookDeliveryTask} 队列统一处理。
 * <p>sourceId 解析复用 {@link ExternalIdParser#extractSourceId}，避免逻辑重复。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectResultPayloadAssembler {

    private static final String SYSTEM_NAME = "西域数智化投标管理平台";
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter OPERATED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter STATUS_EDIT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TenderRepository tenderRepository;
    private final UserRepository userRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ObjectMapper objectMapper;

    /**
     * 组装 §4.2 回调载荷。
     * <p>查不到关联数据时静默填空值（不抛异常，避免阻塞回调入队）。
     *
     * @param event 结果确认事件
     * @return 回调载荷；若 tender 查不到则返回 null（调用方跳过入队）
     */
    public ProjectResultCallbackPayload assemble(ProjectResultConfirmedEvent event) {
        Tender tender = tenderRepository.findById(event.tenderId()).orElse(null);
        if (tender == null) {
            log.warn("Cannot assemble §4.2 payload: tenderId={} not found", event.tenderId());
            return null;
        }
        User user = userRepository.findById(event.operatorUserId()).orElse(null);
        String operatorName = user != null ? safe(user.getFullName()) : "";
        String operatorEmployeeId = user != null ? safe(user.getEmployeeNumber()) : "";
        String operatedAt = event.occurredAt().atZone(ZONE_SHANGHAI).format(OPERATED_AT_FORMAT);

        return new ProjectResultCallbackPayload(
                event.tenderId(),
                event.projectId(),
                ExternalIdParser.extractSourceId(tender.getExternalId()),
                event.resultType().name(),
                buildEvidenceFiles(event.evidenceFileIds()),
                buildCompetitors(event.resultType(), event.competitors()),
                SYSTEM_NAME,
                operatorName,
                operatorEmployeeId,
                operatedAt);
    }

    /**
     * 组装 CRM feedback JSON 字符串（供 §4.2 bidInfoSync 接口使用）。
     * <p>包含：reason / vendor / paymentTerm / remark / operator / operateTime
     * / evidenceFiles / competitors / systemName。
     * <p>CO-300: evidenceFiles 使用 TenderAttachmentUrlResolver.resolve() 标准化
     * doc-insight:// / 内部端点 URL → CRM 集成下载端点，确保外部系统可通过 API Key 访问。
     */
    public String buildFeedbackString(ProjectResultConfirmedEvent event, String operator) {
        Map<String, Object> fb = new LinkedHashMap<>();
        fb.put("reason", event.resultType().name());
        String vendor = event.competitors() != null && !event.competitors().isEmpty()
                ? event.competitors().stream()
                    .map(c -> c.name() != null ? c.name() : "")
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(", "))
                : "";
        fb.put("vendor", vendor);
        fb.put("paymentTerm", "");
        fb.put("remark", "");
        fb.put("operator", operator);
        fb.put("operateTime", event.occurredAt().format(STATUS_EDIT_TIME_FORMAT));
        fb.put("evidenceFiles", buildEvidenceFileMaps(event.evidenceFileIds()));
        fb.put("competitors", buildCompetitorMaps(event.competitors()));
        fb.put("systemName", SYSTEM_NAME);
        try {
            return objectMapper.writeValueAsString(fb);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize feedback", ex);
        }
    }

    /**
     * evidenceFiles 数组（feedback 使用）：fileName/fileUrl/fileSize，
     * fileUrl 使用 {@link TenderAttachmentUrlResolver#resolve} 转换为 CRM 集成下载端点。
     */
    private List<Map<String, Object>> buildEvidenceFileMaps(List<Long> evidenceFileIds) {
        if (evidenceFileIds == null || evidenceFileIds.isEmpty()) return List.of();
        List<ProjectDocument> docs = projectDocumentRepository.findAllById(evidenceFileIds);
        List<Map<String, Object>> files = new ArrayList<>();
        for (ProjectDocument doc : docs) {
            if (doc == null) continue;
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("fileName", safe(doc.getName()));
            file.put("fileUrl", TenderAttachmentUrlResolver.resolve(
                    safe(doc.getFileUrl()), CallerContext.externalSystem(null)));
            file.put("fileSize", parseSize(doc.getSize()));
            files.add(file);
        }
        return files;
    }

    /**
     * competitors 对象数组（feedback 使用）：name/discount/paymentTerm/notes，过滤空行。
     */
    private List<Map<String, Object>> buildCompetitorMaps(List<ProjectResultConfirmedEvent.CompetitorSnapshot> competitors) {
        if (competitors == null || competitors.isEmpty()) return List.of();
        return competitors.stream()
                .filter(c -> c != null && !isBlankRow(c))
                .map(c -> {
                    Map<String, Object> competitor = new LinkedHashMap<>();
                    competitor.put("name", safe(c.name()));
                    competitor.put("discount", safe(c.discount()));
                    competitor.put("paymentTerm", safe(c.paymentTerm()));
                    competitor.put("notes", safe(c.notes()));
                    return competitor;
                })
                .collect(Collectors.toList());
    }

    /**
     * 构造凭证文件列表：按 evidenceFileIds 查 ProjectDocument，取 fileName/fileUrl/fileSize。
     * 查不到的 id 静默跳过（不阻塞回调）。
     * <p>CO-280 修复：将原始 fileUrl（doc-insight:// / 内部端点 URL）标准化为 CRM 集成下载端点
     * {@code /api/integration/tenders/attachments/download}，确保外部系统可通过 API Key 访问。
     */
    private List<EvidenceFile> buildEvidenceFiles(List<Long> evidenceFileIds) {
        if (evidenceFileIds == null || evidenceFileIds.isEmpty()) return List.of();
        List<ProjectDocument> docs = projectDocumentRepository.findAllById(evidenceFileIds);
        List<EvidenceFile> files = new ArrayList<>();
        for (ProjectDocument doc : docs) {
            if (doc == null) continue;
            files.add(new EvidenceFile(
                    safe(doc.getName()),
                    TenderAttachmentUrlResolver.resolve(safe(doc.getFileUrl()), CallerContext.externalSystem(null)),
                    parseSize(doc.getSize())));
        }
        return files;
    }

    /**
     * 构造竞争对手列表：WON/LOST 时填 competitors（过滤空行），FAILED/ABANDONED 时为空数组。
     */
    private List<CompetitorInfo> buildCompetitors(BidResultType resultType,
                                                  List<ProjectResultConfirmedEvent.CompetitorSnapshot> competitors) {
        if (resultType != BidResultType.WON && resultType != BidResultType.LOST) {
            return Collections.emptyList();
        }
        if (competitors == null || competitors.isEmpty()) return Collections.emptyList();
        return competitors.stream()
                .filter(c -> c != null && !isBlankRow(c))
                .map(c -> new CompetitorInfo(
                        safe(c.name()), safe(c.discount()),
                        safe(c.paymentTerm()), safe(c.notes())))
                .collect(Collectors.toList());
    }

    private static boolean isBlankRow(ProjectResultConfirmedEvent.CompetitorSnapshot row) {
        return isStrBlank(row.name()) && isStrBlank(row.discount())
                && isStrBlank(row.paymentTerm()) && isStrBlank(row.notes());
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static boolean isStrBlank(String s) { return s == null || s.isBlank(); }

    /**
     * ProjectDocument.size 是 String 类型（如 "2048000"），解析为 Long；解析失败返回 null。
     */
    private static Long parseSize(String size) {
        if (size == null || size.isBlank()) return null;
        try {
            return Long.parseLong(size.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
