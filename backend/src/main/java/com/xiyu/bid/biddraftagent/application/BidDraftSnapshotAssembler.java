package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.BidDraftSnapshot;
import com.xiyu.bid.biddraftagent.entity.BidRequirementItem;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.CaseRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.QualificationRepository;
import com.xiyu.bid.repository.TemplateRepository;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BidDraftSnapshotAssembler {

    private final ProjectRepository projectRepository;
    private final TenderRepository tenderRepository;
    private final QualificationRepository qualificationRepository;
    private final TemplateRepository templateRepository;
    private final CaseRepository caseRepository;
    private final BidRequirementSnapshotReader requirementSnapshotReader;

    public BidDraftSnapshot assemble(Long projectId) {
        return assemble(projectId, null);
    }

    public BidDraftSnapshot assemble(Long projectId, Long snapshotId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", String.valueOf(projectId)));
        Tender tender = tenderRepository.findById(project.getTenderId())
                .orElseThrow(() -> new ResourceNotFoundException("Tender", String.valueOf(project.getTenderId())));
        List<BidRequirementItem> requirementItems = collectRequirementItems(projectId, snapshotId);

        return new BidDraftSnapshot(
                project.getId(),
                tender.getId(),
                project.getName(),
                project.getDescription(),
                project.getSourceReasoningSummary(),
                project.getCustomer(),
                project.getCustomerType(),
                firstText(project.getRegion(), tender.getRegion()),
                firstText(project.getIndustry(), tender.getIndustry()),
                firstBudget(project.getBudget(), tender.getBudget()),
                firstDeadline(project.getDeadline(), tender),
                tender.getTitle(),
                tender.getDescription(),
                tender.getPurchaserName(),
                tender.getSource(),
                tender.getTags() == null ? List.of() : splitValues(tender.getTags()),
                collectRequirementSignals(requirementItems),
                collectRequirementSignals(requirementItems, "material"),
                collectRequirementSignals(requirementItems, "scoring"),
                collectQualificationSignals(),
                collectTemplateSignals(),
                collectCaseSignals()
        );
    }

    private List<String> collectQualificationSignals() {
        return qualificationRepository.findAll(PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "id"))).stream()
                .map(qualification -> qualification.getName() + " / " + qualification.getType() + " / " + qualification.getLevel())
                .toList();
    }

    private List<String> collectTemplateSignals() {
        return templateRepository.findAll(PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "id"))).stream()
                .map(template -> template.getName() + " / " + template.getCategory() + (template.getDescription() == null ? "" : " / " + template.getDescription()))
                .toList();
    }

    private List<String> collectCaseSignals() {
        return caseRepository.searchCases(
                        null,
                        null,
                        "PUBLISHED",
                        null,
                        PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "publishedAt").and(Sort.by(Sort.Direction.DESC, "id")))
                ).stream()
                .map(caseEntity -> {
                    String highlights = caseEntity.getHighlights() == null || caseEntity.getHighlights().isEmpty()
                            ? ""
                            : " / " + String.join("、", caseEntity.getHighlights());
                    String summary = caseEntity.getArchiveSummary() == null ? "" : " / " + caseEntity.getArchiveSummary();
                    return caseEntity.getTitle() + summary + highlights;
                })
                .toList();
    }

    private List<BidRequirementItem> collectRequirementItems(Long projectId, Long snapshotId) {
        if (snapshotId != null) {
            return requirementSnapshotReader.requirementsForSnapshot(projectId, snapshotId);
        }
        return requirementSnapshotReader.latestRequirementsForProject(projectId);
    }

    private List<String> collectRequirementSignals(List<BidRequirementItem> requirementItems) {
        return requirementItems.stream()
                .limit(60)
                .map(this::formatRequirementSignal)
                .toList();
    }

    private List<String> collectRequirementSignals(List<BidRequirementItem> requirementItems, String category) {
        return requirementItems.stream()
                .filter(item -> item.getCategory() != null && item.getCategory().equalsIgnoreCase(category))
                .limit(20)
                .map(this::formatRequirementSignal)
                .toList();
    }

    private String formatRequirementSignal(BidRequirementItem item) {
        String mandatory = item.isMandatory() ? "必须响应" : "参考";
        return String.join(" / ",
                item.getCategory() == null ? "other" : item.getCategory(),
                mandatory,
                item.getTitle() == null ? "未命名要求" : item.getTitle(),
                item.getContent() == null ? "" : item.getContent()
        );
    }

    private List<String> splitValues(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(","));
    }

    private String firstText(String projectValue, String tenderValue) {
        if (projectValue != null && !projectValue.isBlank()) {
            return projectValue;
        }
        return tenderValue == null || tenderValue.isBlank() ? null : tenderValue;
    }

    private BigDecimal firstBudget(BigDecimal projectBudget, BigDecimal tenderBudget) {
        return projectBudget == null ? tenderBudget : projectBudget;
    }

    private LocalDate firstDeadline(LocalDate projectDeadline, Tender tender) {
        if (projectDeadline != null) {
            return projectDeadline;
        }
        return tender.getDeadline() == null ? null : tender.getDeadline().toLocalDate();
    }
}
