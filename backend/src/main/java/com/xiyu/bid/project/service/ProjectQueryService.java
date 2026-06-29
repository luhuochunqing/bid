package com.xiyu.bid.project.service;

import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.entity.ProjectResult;
import com.xiyu.bid.project.repository.ProjectEvaluationRepository;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.project.repository.ProjectResultRepository;
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

    /** Lead assignment repo for exact leader user id fields. */
    private final ProjectLeadAssignmentRepository projectLeadAssignmentRepository;

    /** Evaluation repo for sub-stage in EVALUATING stage. */
    private final ProjectEvaluationRepository
            projectEvaluationRepository;

    /** Project result repo for bidStatus computation. */
    private final ProjectResultRepository projectResultRepository;

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

        Map<Long, ProjectLeadAssignment> leadAssignmentMap =
                projectLeadAssignmentRepository
                        .findByProjectIdIn(ids)
                        .stream()
                        .collect(Collectors.toMap(
                                ProjectLeadAssignment::getProjectId,
                                Function.identity(),
                                (a, b) -> a));

        // Batch-fetch evaluations for list fields (shortlistedCount, customerRevenue)
        Map<Long, TenderEvaluation> evalMap = tenderIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : tenderEvaluationRepository.findByTenderIdIn(tenderIds).stream()
                        .collect(Collectors.toMap(TenderEvaluation::getTenderId, Function.identity()));

        // Batch-fetch project results for bidStatus computation
        Map<Long, String> projectResultMap = projectResultRepository
                .findByProjectIdIn(projects.stream().map(ProjectDTO::getId).collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(ProjectResult::getProjectId, ProjectResult::getResultType));

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
                if (dto.getProjectLeaderId() == null
                        && det.getOwnerUserId() != null) {
                    dto.setProjectLeaderId(det.getOwnerUserId());
                }
            }

            ProjectLeadAssignment leadAssignment =
                    leadAssignmentMap.get(dto.getId());
            if (leadAssignment != null) {
                dto.setBiddingLeaderId(
                        leadAssignment.getPrimaryLeadUserId());
                dto.setSecondaryBiddingLeaderId(
                        leadAssignment.getSecondaryLeadUserId());
                // CO-387 fix: 同步填充 primaryLeadUserId / secondaryLeadUserId，
                // 供前端 currentProject 权限判断（详情接口可能从列表缓存返回）
                dto.setPrimaryLeadUserId(
                        leadAssignment.getPrimaryLeadUserId());
                dto.setSecondaryLeadUserId(
                        leadAssignment.getSecondaryLeadUserId());
            }

            ProjectListEnrichmentSupport.populateFromTender(dto, tenderMap);

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

            ProjectStage stage = ProjectListEnrichmentSupport.resolveStage(dto.getStage());
            boolean submitted = ProjectListEnrichmentSupport.isInitiationSubmitted(det);
            // Populate bidResultStatus from the actual project result (project_result table),
            // not from ProjectInitiationDetails.bid_result_status (which may be NULL).
            // This ensures bidStatus reflects the real result type after result registration.
            String bidResult = projectResultMap.getOrDefault(dto.getId(), dto.getBidResultStatus());
            dto.setBidStatus(ProjectListEnrichmentSupport.computeBidStatus(
                    stage,
                    bidResult,
                    submitted));

            if (stage == ProjectStage.EVALUATING) {
                projectEvaluationRepository
                        .findByProjectId(dto.getId())
                        .ifPresent(ev -> dto.setEvaluationSubStage(
                                ev.getSubStage()));
            }
        }
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
