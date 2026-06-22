// Input: ProjectResultConfirmedEvent + 查询 Project/Tender/User/ProjectDocument
// Output: 组装好的 §4.2 ProjectResultCallbackPayload（不含 HTTP/重试逻辑）
// Pos: project/service/ - 编排层（只做查询+组装，不做 HTTP）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

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
import java.util.List;
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

    private final TenderRepository tenderRepository;
    private final UserRepository userRepository;
    private final ProjectDocumentRepository projectDocumentRepository;

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
