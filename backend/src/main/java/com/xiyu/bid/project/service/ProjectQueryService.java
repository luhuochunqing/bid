package com.xiyu.bid.project.service;

import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.core.ProjectStage;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.entity.TenderEvaluation;
import com.xiyu.bid.tender.repository.TenderEvaluationRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /** User repo for resolving lead user ids to names. */
    private final UserRepository userRepository;

    /** CO-591: stage 相关 4 列 enrichment（标书审核人 / 评标结果 / 服务周期 / bidStatus）。 */
    private final ProjectListStageEnricher stageEnricher;

    /** Demo mode toggles and data for e2e tests. */
    private final DemoModeService demoModeService;
    private final DemoDataProvider demoDataProvider;
    private final DemoFusionService demoFusionService;

    /** 部门名回填：通过 users.department_code（OSS external_dept_id）反查 organization_departments 部门名。 */
    private final ProjectManagerDepartmentEnricher managerDepartmentEnricher;

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
                                Function.identity(),
                                (a, b) -> a)); // CO-027: merge function 防止 Duplicate key 异常

        Map<Long, ProjectInitiationDetails> detailsMap =
                projectInitiationDetailsRepository
                        .findByProjectIdIn(ids)
                        .stream()
                        .collect(Collectors.toMap(
                                ProjectInitiationDetails
                                        ::getProjectId,
                                d -> d,
                                (a, b) -> a)); // CO-027: merge function 防止 Duplicate key 异常

        Map<Long, ProjectLeadAssignment> leadAssignmentMap =
                projectLeadAssignmentRepository
                        .findByProjectIdIn(ids)
                        .stream()
                        .collect(Collectors.toMap(
                                ProjectLeadAssignment::getProjectId,
                                Function.identity(),
                                (a, b) -> a));

        // CO-591: stage 相关 4 列上下文（标书审核 / 评标 / 服务周期 / bidStatus 用的 resultType）一次性批量加载
        ProjectListStageEnricher.Context stageCtx = stageEnricher.loadContext(ids);

        // CO-551: Batch-fetch user names for secondary lead resolution
        Set<Long> leadUserIds = leadAssignmentMap.values().stream()
                .flatMap(a -> Stream.of(
                        a.getPrimaryLeadUserId(),
                        a.getSecondaryLeadUserId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 项目负责人部门为空时，从项目 managerId 反查用户部门兜底回填
        Set<Long> managerIds = projects.stream()
                .map(ProjectDTO::getManagerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // CO-591: 标书审核人姓名解析所需的 reviewer 用户 ID（合并到统一 userMap 减 DB round-trip）
        Set<Long> reviewerIds = stageEnricher.collectReviewerIds(stageCtx);

        // CC2026072071: projectLeaderId 来源有三（pid.ownerUserId /
        // tender.projectManagerId / project.managerId 兜底），需把前两条路径
        // 的 user ID 也加入预加载，让 userMap 覆盖工号反查（managerIds 已含第三条）。
        Set<Long> tenderManagerIds = tenderMap.values().stream()
                .map(Tender::getProjectManagerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> pidOwnerIds = detailsMap.values().stream()
                .map(ProjectInitiationDetails::getOwnerUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 合并 lead/manager/reviewer/tender/pid 五类 user ID 一次性加载，减少 DB round-trip。
        Set<Long> allUserIds = Stream.of(
                        leadUserIds.stream(),
                        managerIds.stream(),
                        reviewerIds.stream(),
                        tenderManagerIds.stream(),
                        pidOwnerIds.stream())
                .flatMap(s -> s)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = allUserIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : userRepository.findByIdIn(allUserIds).stream()
                        .collect(Collectors.toMap(
                                User::getId,
                                Function.identity(),
                                (a, b) -> a));

        // CO-441: 用 HashMap 显式 put，允许 null value，避免 Collectors.toMap 对 null value 抛 NPE。
        Map<Long, String> leadUserNameMap = new HashMap<>();
        leadUserIds.forEach(id -> {
            User user = userMap.get(id);
            if (user != null) {
                leadUserNameMap.put(id, user.getFullName());
            }
        });

        // 部门名回填：生产环境 users.department_name 多为空字符串，
        // 但 users.department_code 存的是 OSS external_dept_id，
        // 通过 organization_departments.external_dept_id 批量反查部门名。
        Map<Long, String> managerDepartmentMap = managerDepartmentEnricher.buildManagerDepartmentMap(managerIds, userMap);

        // Batch-fetch evaluations for list fields (shortlistedCount, customerRevenue)
        Map<Long, TenderEvaluation> evalMap = tenderIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : tenderEvaluationRepository.findByTenderIdIn(tenderIds).stream()
                        .collect(Collectors.toMap(TenderEvaluation::getTenderId, Function.identity(),
                                (a, b) -> a)); // CO-027: merge function 防止 Duplicate key 异常

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
                // CO-551: 副投标负责人姓名（由 secondaryLeadUserId 解析）
                if (leadAssignment.getSecondaryLeadUserId() != null) {
                    dto.setSecondaryBiddingLeaderName(
                            leadUserNameMap.get(
                                    leadAssignment.getSecondaryLeadUserId()));
                }
            }

            ProjectListEnrichmentSupport.populateFromTender(dto, tenderMap);

            // CC2026072071: projectLeaderId 已由 populateFromTender 解析，从 userMap 反查工号填充。
            ProjectListEnrichmentSupport.populateLeaderEmployeeNumber(dto, userMap);

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
            // CO-591: 标书审核人 / 评标结果 / 服务周期 / bidStatus 一并由 stageEnricher 回填
            stageEnricher.populate(dto, stageCtx, stage, submitted, userMap);

            // 项目负责人部门为空时，从项目 managerId 反查用户部门兜底回填
            if (StringUtils.isBlank(dto.getLeaderDepartment()) && dto.getManagerId() != null) {
                String dept = managerDepartmentMap.get(dto.getManagerId());
                if (!StringUtils.isBlank(dept)) {
                    dto.setLeaderDepartment(dept);
                }
            }
        }
    }

    public void enrichSingle(final ProjectDTO dto) {
        if (dto != null && dto.getId() != null) enrichWithTenderAndDetails(List.of(dto));
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
