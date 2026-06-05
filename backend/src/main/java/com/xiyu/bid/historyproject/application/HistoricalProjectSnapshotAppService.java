package com.xiyu.bid.historyproject.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.historyproject.entity.HistoricalProjectSnapshotRecord;
import com.xiyu.bid.historyproject.dto.HistoricalProjectSnapshotDTO;
import com.xiyu.bid.historyproject.repository.HistoricalProjectSnapshotRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class HistoricalProjectSnapshotAppService {

    private static final int SNAPSHOT_LIMIT = 2000;

    private final HistoricalProjectSnapshotRecordRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public HistoricalProjectSnapshotDTO capture(HistoricalProjectSnapshotCaptureCommand command) {
        String snapshotText = extractSnapshotText(command.exportContent());
        String archiveSummary = buildArchiveSummary(command.customerName(), command.sourceReasoningSummary(), snapshotText);
        String recommendedTags = inferRecommendedTags(command.projectName(), command.sourceReasoningSummary(), snapshotText).stream()
                .collect(Collectors.joining(","));

        HistoricalProjectSnapshotRecord record = HistoricalProjectSnapshotRecord.builder()
                .projectId(command.projectId())
                .archiveRecordId(command.archiveRecordId())
                .exportId(command.exportId())
                .projectName(command.projectName())
                .customerName(command.customerName())
                .productLine(command.productLine())
                .archiveSummary(archiveSummary)
                .documentSnapshotText(snapshotText)
                .recommendedTags(recommendedTags)
                .build();

        HistoricalProjectSnapshotRecord saved = snapshotRepository.save(record);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public HistoricalProjectSnapshotDTO getLatestSnapshot(Long projectId) {
        HistoricalProjectSnapshotRecord snapshot = snapshotRepository.findTopByProjectIdOrderByCapturedAtDesc(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("HistoricalProjectSnapshot", projectId.toString()));
        return toDto(snapshot);
    }

    private HistoricalProjectSnapshotDTO toDto(HistoricalProjectSnapshotRecord record) {
        return HistoricalProjectSnapshotDTO.builder()
                .projectId(record.getProjectId())
                .archiveRecordId(record.getArchiveRecordId())
                .exportId(record.getExportId())
                .projectName(record.getProjectName())
                .customerName(record.getCustomerName())
                .productLine(record.getProductLine())
                .archiveSummary(record.getArchiveSummary())
                .documentSnapshotText(record.getDocumentSnapshotText())
                .recommendedTags(parseTags(record.getRecommendedTags()))
                .capturedAt(record.getCapturedAt())
                .build();
    }

    private List<String> parseTags(String joinedTags) {
        if (joinedTags == null || joinedTags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joinedTags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
    }

    private String buildArchiveSummary(String customerName, String sourceReasoningSummary, String snapshotText) {
        StringBuilder summary = new StringBuilder();
        summary.append("项目资料已完成归档。");
        if (customerName != null && !customerName.isBlank()) {
            summary.append("客户：").append(customerName).append("。");
        }
        if (sourceReasoningSummary != null && !sourceReasoningSummary.isBlank()) {
            summary.append(sourceReasoningSummary.trim()).append("。");
        }
        if (!snapshotText.isBlank()) {
            summary.append("已提取正文快照，可用于后续检索与案例整理。");
        }
        return summary.toString();
    }

    private String extractSnapshotText(String exportContent) {
        if (exportContent == null || exportContent.isBlank()) {
            return "";
        }

        try {
            JsonNode root = objectMapper.readTree(exportContent);
            JsonNode sections = root.path("sections");
            if (!sections.isArray()) {
                return truncate(exportContent, SNAPSHOT_LIMIT);
            }

            String joined = Arrays.stream(objectMapper.treeToValue(sections, JsonNode[].class))
                    .map(section -> {
                        String title = Optional.ofNullable(section.path("title").asText("")).orElse("");
                        String content = Optional.ofNullable(section.path("content").asText("")).orElse("");
                        return (title + "\n" + content).trim();
                    })
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining("\n\n"));
            return truncate(joined, SNAPSHOT_LIMIT);
        } catch (JsonProcessingException ignored) {
            return truncate(exportContent, SNAPSHOT_LIMIT);
        }
    }

    private List<String> inferRecommendedTags(String projectName, String sourceReasoningSummary, String snapshotText) {
        String haystack = (projectName + " " + Optional.ofNullable(sourceReasoningSummary).orElse("") + " " + snapshotText)
                .toLowerCase(Locale.ROOT);
        return List.of(
                keywordTag(haystack, "智慧", "智慧化"),
                keywordTag(haystack, "园区", "智慧园区"),
                keywordTag(haystack, "城市", "智慧城市"),
                keywordTag(haystack, "政务", "政务"),
                keywordTag(haystack, "能源", "能源"),
                keywordTag(haystack, "交通", "交通")
        ).stream().flatMap(Optional::stream).distinct().toList();
    }

    private Optional<String> keywordTag(String haystack, String keyword, String tag) {
        return haystack.contains(keyword) ? Optional.of(tag) : Optional.empty();
    }

    private String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit);
    }
}
