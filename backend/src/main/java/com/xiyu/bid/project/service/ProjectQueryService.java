package com.xiyu.bid.project.service;

import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.core.ProjectStatusPolicy;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectEvaluationRepository;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectQueryService {

    /** Repository for project persistence operations. */
    private final ProjectRepository projectRepository;

    /** Access control service filtering visible projects. */
    private final ProjectAccessScopeService projectAccessScopeService;

    /** Tender repository for enrich-project-list fields. */
    private final TenderRepository tenderRepository;

    /** Evaluation repo for list fields (shortlistedCount, customerRevenue). */
    private final TenderEvaluationRepository tenderEvaluationRepository;

    /** Details repository for leader/department fields. */
    private final ProjectInitiationDetailsRepository
            projectInitiationDetailsRepository;

    /** Evaluation repo for sub-stage in EVALUATING stage. */
    private final ProjectEvaluationRepository
            projectEvaluationRepository;

    /** Demo mode toggles and data for e2e tests. */
    private final DemoModeService demoModeService;
    private final DemoDataProvider demoDataProvider;
    private final DemoFusionService demoFusionService;

    /**
     * Returns all accessible projects enriched with tender and
     * initiation-detail fields, sorted by creation time descending.
     */
    public List<ProjectDTO> getAllProjects() {
        List<ProjectDTO> projects = projectAccessScopeService
                .filterAccessibleProjects(
                        projectRepository.findAll())
                .stream()
                .map(ProjectMapper::toDTO)
                .collect(Collectors.toList());

        if (!projects.isEmpty()) {
            enrichWithTenderAndDetails(projects);
        }

        projects.sort(Comparator.comparing(
                dto -> dto.getCreatedAt() != null
                        ? dto.getCreatedAt()
                        : java.time.LocalDateTime.MIN,
                Comparator.reverseOrder()));

        return mergeDemoProjectsIfNeeded(projects);
    }

    private void enrichWithTenderAndDetails(
            final List<ProjectDTO> projects) {
        List<Long> ids = projects.stream()
                .map(ProjectDTO::getId)
                .collect(Collectors.toList());

        List<Long> tenderIds = projects.stream()
                .map(ProjectDTO::getTenderId)
                .filter(tid -> tid != null)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Tender> tenderMap = tenderIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : tenderRepository
                        .findAllById(tenderIds)
                        .stream()
                        .collect(Collectors.toMap(
                                Tender::getId,
                                Function.identity()));

        Map<Long, ProjectInitiationDetails> detailsMap =
                projectInitiationDetailsRepository
                        .findByProjectIdIn(ids)
                        .stream()
                        .collect(Collectors.toMap(
                                ProjectInitiationDetails
                                        ::getProjectId,
                                d -> d));

        // Batch-fetch evaluations for list fields (shortlistedCount, customerRevenue)
        Map<Long, TenderEvaluation> evalMap = tenderIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : tenderEvaluationRepository.findByTenderIdIn(tenderIds).stream()
                        .collect(Collectors.toMap(TenderEvaluation::getTenderId, Function.identity()));

        for (ProjectDTO dto : projects) {
            ProjectInitiationDetails det =
                    detailsMap.get(dto.getId());
            if (det != null) {
                dto.setProjectLeaderName(
                        det.getProjectLeaderName());
                dto.setBiddingLeaderName(
                        det.getBiddingLeaderName());
                dto.setLeaderDepartment(
                        det.getLeaderDepartment());
                if (dto.getShortlistedCount() == null
                        && det.getExpectedBidders() != null) {
                    dto.setShortlistedCount(det.getExpectedBidders());
                }
                if (dto.getRevenue() == null
                        && det.getAnnualEcommerceAmount() != null) {
                    dto.setRevenue(det.getAnnualEcommerceAmount());
                }
            }

            populateFromTender(dto, tenderMap);

            // Evaluation-derived fields: shortlistedCount & customerRevenue
            TenderEvaluation eval = evalMap.get(dto.getTenderId());
            if (eval != null && eval.getBasic() != null) {
                if (dto.getShortlistedCount() == null
                        && eval.getBasic().getPlannedShortlistedCount() != null) {
                    dto.setShortlistedCount(eval.getBasic().getPlannedShortlistedCount());
                }
                if (dto.getRevenue() == null
                        && eval.getBasic().getCustomerRevenue() != null) {
                    dto.setRevenue(eval.getBasic().getCustomerRevenue());
                }
            }

            ProjectStage stage = resolveStage(dto.getStage());
            boolean submitted = isInitiationSubmitted(det);
            dto.setBidStatus(ProjectStatusPolicy.compute(
                    stage,
                    dto.getBidResultStatus(),
                    submitted).name());

            if (stage == ProjectStage.EVALUATING) {
                projectEvaluationRepository
                        .findByProjectId(dto.getId())
                        .ifPresent(ev -> dto.setEvaluationSubStage(
                                ev.getSubStage()));
            }
        }
    }

    private void populateFromTender(
            final ProjectDTO dto,
            final Map<Long, Tender> tenderMap) {
        Long tenderId = dto.getTenderId();
        if (tenderId == null) {
            return;
        }
        Tender t = tenderMap.get(tenderId);
        if (t == null) {
            return;
        }
        if (dto.getOwnerUnit() == null
                && t.getPurchaserName() != null) {
            dto.setOwnerUnit(t.getPurchaserName());
        }
        if (dto.getBidOpenTime() == null
                && t.getBidOpeningTime() != null) {
            dto.setBidOpenTime(t.getBidOpeningTime());
        }
        if (dto.getProjectType() == null
                && t.getProjectType() != null) {
            dto.setProjectType(t.getProjectType());
        }
        if (dto.getCustomerType() == null
                && t.getCustomerType() != null) {
            dto.setCustomerType(t.getCustomerType());
        }
        if (dto.getRegion() == null && t.getRegion() != null) {
            dto.setRegion(t.getRegion());
        }
        if (dto.getPriority() == null && t.getPriority() != null) {
            dto.setPriority(t.getPriority());
        }
        if (dto.getBiddingPlatform() == null
                && t.getSourcePlatform() != null) {
            dto.setBiddingPlatform(t.getSourcePlatform());
        }
        if (dto.getBidMonth() == null
                && t.getBidOpeningTime() != null) {
            dto.setBidMonth(t.getBidOpeningTime()
                    .toLocalDate()
                    .toString()
                    .substring(0, 7));
        }
        if (dto.getSourceModule() == null) {
            String sp = t.getSourcePlatform();
            if (sp == null && t.getSourceType() != null) {
                sp = t.getSourceType().name();
            }
            dto.setSourceModule(sp);
        }
        if (dto.getBudget() == null && t.getBudget() != null) {
            dto.setBudget(t.getBudget());
        }
        // Leader fields: fallback to tender when not populated by initiation details
        if (isBlank(dto.getProjectLeaderName())
                && !isBlank(t.getProjectManagerName())) {
            dto.setProjectLeaderName(t.getProjectManagerName());
        }
        if (isBlank(dto.getLeaderDepartment())
                && !isBlank(t.getDepartment())) {
            dto.setLeaderDepartment(t.getDepartment());
        }
        if (isBlank(dto.getBiddingLeaderName())
                && !isBlank(t.getBiddingPersonName())) {
            dto.setBiddingLeaderName(t.getBiddingPersonName());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static ProjectStage resolveStage(final String stageValue) {
        if (stageValue == null || stageValue.isBlank()) {
            return ProjectStage.INITIATED;
        }
        try {
            return ProjectStage.valueOf(stageValue.trim());
        } catch (IllegalArgumentException ex) {
            return ProjectStage.INITIATED;
        }
    }

    private static boolean isInitiationSubmitted(
            final ProjectInitiationDetails details) {
        if (details == null || details.getReviewStatus() == null) {
            return false;
        }
        return switch (details.getReviewStatus()) {
            case "PENDING_REVIEW", "APPROVED" -> true;
            case "DRAFT", "REJECTED" -> false;
            default -> false;
        };
    }

    private List<ProjectDTO> mergeDemoProjectsIfNeeded(
            final List<ProjectDTO> projects) {
        if (!demoModeService.isEnabled()) {
            return projects;
        }
        return demoFusionService.mergeByKey(
                projects,
                demoDataProvider.getDemoProjects(),
                ProjectDTO::getId);
    }
}
