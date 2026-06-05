package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.domain.port.CaseSnapshotPort;
import com.xiyu.bid.casework.dto.CaseDTO;
import com.xiyu.bid.casework.dto.CasePromoteFromProjectRequest;
import com.xiyu.bid.historyproject.dto.HistoricalProjectSnapshotDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CasePromotionAppService {

    private final CaseSnapshotPort caseSnapshotPort;
    private final CaseCrudAppService caseCrudAppService;

    public CaseDTO promoteFromProject(CasePromoteFromProjectRequest request) {
        HistoricalProjectSnapshotDTO snapshot = caseSnapshotPort.getCaseSnapshot(request.getProjectId());
        CaseDTO caseDTO = CaseDTO.builder()
                .title(firstNonBlank(request.getTitle(), snapshot.getProjectName() + "案例"))
                .industry(request.getIndustry() == null ? CaseDTO.Industry.OTHER : request.getIndustry())
                .outcome(request.getOutcome() == null ? CaseDTO.Outcome.WON : request.getOutcome())
                .amount(request.getAmount() == null ? BigDecimal.ZERO : request.getAmount())
                .projectDate(request.getProjectDate() == null ? LocalDate.now() : request.getProjectDate())
                .description(firstNonBlank(request.getDescription(), snapshot.getArchiveSummary()))
                .customerName(firstNonBlank(request.getCustomerName(), snapshot.getCustomerName()))
                .locationName(request.getLocationName())
                .projectPeriod(request.getProjectPeriod())
                .productLine(firstNonBlank(request.getProductLine(), snapshot.getProductLine()))
                .sourceProjectId(snapshot.getProjectId())
                .archiveSummary(firstNonBlank(request.getArchiveSummary(), snapshot.getArchiveSummary()))
                .priceStrategy(request.getPriceStrategy())
                .tags(nonEmptyOrDefault(request.getTags(), snapshot.getRecommendedTags()))
                .highlights(nonEmptyOrDefault(request.getHighlights(), snapshot.getRecommendedTags()))
                .successFactors(nonEmptyOrDefault(request.getSuccessFactors(), snapshot.getRecommendedTags()))
                .lessonsLearned(copyList(request.getLessonsLearned()))
                .documentSnapshotText(firstNonBlank(request.getDocumentSnapshotText(), snapshot.getDocumentSnapshotText()))
                .attachmentNames(copyList(request.getAttachmentNames()))
                .status(firstNonBlank(request.getStatus(), "PUBLISHED"))
                .publishedAt(request.getPublishedAt() == null && "PUBLISHED".equalsIgnoreCase(firstNonBlank(request.getStatus(), "PUBLISHED"))
                        ? LocalDateTime.now()
                        : request.getPublishedAt())
                .visibility(firstNonBlank(request.getVisibility(), "INTERNAL"))
                .technologies(copyList(request.getTechnologies()))
                .build();
        return caseCrudAppService.create(caseDTO);
    }

    private List<String> nonEmptyOrDefault(List<String> requested, List<String> fallback) {
        if (requested != null && !requested.isEmpty()) {
            return copyList(requested);
        }
        return copyList(fallback);
    }

    private List<String> copyList(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback == null ? null : fallback.trim();
    }
}
