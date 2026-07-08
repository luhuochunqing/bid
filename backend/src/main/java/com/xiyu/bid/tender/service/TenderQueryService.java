package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.tender.entity.TenderAttachment;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.integration.external.TenderIntegrationMapper;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.TenderAttachmentDTO;
import com.xiyu.bid.tender.dto.TenderDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TenderQueryService {

    private final TenderRepository tenderRepository;
    private final TenderMapper tenderMapper;
    private final TenderAttachmentRepository attachmentRepository;
    private final TenderProjectAccessGuard accessGuard;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TenderAssignmentRecordRepository tenderAssignmentRecordRepository;
    private final OrganizationDepartmentRepository organizationDepartmentRepository;

    /**
     * OSS 同步用户的 department_code 实际存的是 OSS external_dept_id
     * （见 OrganizationDirectoryJsonMapper.user() 读取 "deptCode"/"deptId"）。
     * 因此列表回填部门名时，需要用 users.department_code 关联 organization_departments.external_dept_id。
     */
    private static final String OSS_SOURCE_APP = "oss";

    public List<TenderDTO> searchTenders(TenderSearchCriteria criteria) {
        log.debug("Searching tenders with criteria: {}", criteria);
        List<TenderDTO> dtos = accessGuard.filterVisibleTenders(tenderRepository.findAll(TenderSpecification.byCriteria(criteria))).stream()
                .map(tenderMapper::toDTO)
                .toList();
        enrichAssignmentInfoBatch(dtos);
        return dtos;
    }

    public TenderDTO getTenderById(Long id) {
        log.debug("Fetching tender by id: {}", id);
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));
        accessGuard.assertCanAccessTender(tender);
        TenderDTO dto = tenderMapper.toDTO(tender);
        enrichAssignmentInfo(dto, id);
        // 加载附件
        List<TenderAttachment> attachments = attachmentRepository.findByTenderId(id);
        dto.setAttachments(attachments.stream()
                .map(a -> TenderAttachmentDTO.builder()
                        .fileName(a.getFileName())
                        .fileType(a.getFileType())
                        .fileUrl(TenderIntegrationMapper.toDownloadUrl(a.getFileUrl()))
                        .build())
                .collect(Collectors.toList()));
        return dto;
    }

    public List<TenderDTO> getTendersByStatus(Tender.Status status) {
        log.debug("Fetching tenders by status: {}", status);
        return accessGuard.filterVisibleTenders(tenderRepository.findByStatus(status)).stream()
                .map(tenderMapper::toDTO)
                .toList();
    }

    public List<TenderDTO> getTendersBySource(String source) {
        log.debug("Fetching tenders by source: {}", source);
        return accessGuard.filterVisibleTenders(tenderRepository.findBySource(source)).stream()
                .map(tenderMapper::toDTO)
                .toList();
    }

    public Map<Tender.Status, Long> getTenderStatistics() {
        log.debug("Fetching tender statistics");
        List<Tender> visibleTenders = accessGuard.filterVisibleTenders(tenderRepository.findAll());
        return Map.of(
                Tender.Status.PENDING_ASSIGNMENT, countStatus(visibleTenders, Tender.Status.PENDING_ASSIGNMENT),
                Tender.Status.TRACKING, countStatus(visibleTenders, Tender.Status.TRACKING),
                Tender.Status.EVALUATED, countStatus(visibleTenders, Tender.Status.EVALUATED),
                Tender.Status.BIDDING, countStatus(visibleTenders, Tender.Status.BIDDING),
                Tender.Status.WON, countStatus(visibleTenders, Tender.Status.WON),
                Tender.Status.LOST, countStatus(visibleTenders, Tender.Status.LOST),
                Tender.Status.ABANDONED, countStatus(visibleTenders, Tender.Status.ABANDONED)
        );
    }

    private long countStatus(List<Tender> tenders, Tender.Status status) {
        return tenders.stream().filter(tender -> tender.getStatus() == status).count();
    }

    private void enrichAssignmentInfo(TenderDTO dto, Long tenderId) {
        projectRepository.findByTenderId(tenderId).stream()
                .findFirst()
                .ifPresent(project -> enrichProjectManager(dto, project));

        enrichAssignee(dto, tenderId);
    }

    private void enrichProjectManager(TenderDTO dto, Project project) {
        if (project.getManagerId() == null) return;
        // CO-333: 标讯自身已存项目负责人姓名时不被项目 managerId 反查覆盖，
        // 避免管理员点击「立即投标」生成项目后，前端项目负责人显示值发生变化。
        if (dto.getProjectManagerName() != null && !dto.getProjectManagerName().isBlank()) return;
        userRepository.findById(project.getManagerId())
                .ifPresent(manager -> dto.setProjectManagerName(manager.getFullName()));
    }

    private void enrichAssignee(TenderDTO dto, Long tenderId) {
        tenderAssignmentRecordRepository.findByTenderIdOrderByAssignedAtDesc(tenderId).stream()
                .findFirst()
                .flatMap(record -> userRepository.findById(record.getAssigneeId()))
                .map(User::getFullName)
                .ifPresent(dto::setAssigneeName);
    }


    /**
     * 分页搜索标讯（支持 JPA Specification + Pageable）。
     * 用于外部 API 增量同步场景，避免全量加载。
     */
    public Page<TenderDTO> searchTendersPaged(TenderSearchCriteria criteria, Pageable pageable) {
        log.debug("Searching tenders paged with criteria: {}", criteria);
        Page<Tender> page = tenderRepository.findAll(
                TenderSpecification.byCriteria(criteria), pageable);
        List<Tender> visible = accessGuard.filterVisibleTenders(page.getContent());
        List<TenderDTO> dtos = visible.stream()
                .map(tenderMapper::toDTO)
                .toList();
        enrichAssignmentInfoBatch(dtos);
        return new org.springframework.data.domain.PageImpl<>(dtos, pageable, visible.size());
    }

    /**
     * 批量补充分配信息（用于列表查询场景）
     * 将 N+1 查询优化为 4 次固定查询
     */
    public void enrichAssignmentInfoBatch(List<TenderDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) return;

        try {
            Set<Long> tenderIds = dtos.stream().map(TenderDTO::getId).collect(Collectors.toSet());

            Map<Long, String> managerNames = fetchManagerNames(tenderIds);
            Map<Long, String> assigneeNames = fetchAssigneeNames(tenderIds);
            Map<Long, String> managerDepartments = fetchManagerDepartments(dtos);

            for (TenderDTO dto : dtos) {
                // CO-333: 标讯自身已存项目负责人姓名时不被项目 managerId 反查覆盖，
                // 避免管理员点击「立即投标」生成项目后，前端项目负责人显示值发生变化。
                if (dto.getProjectManagerName() == null || dto.getProjectManagerName().isBlank()) {
                    String managerName = managerNames.get(dto.getId());
                    if (managerName != null) {
                        dto.setProjectManagerName(managerName);
                    }
                }
                dto.setAssigneeName(assigneeNames.get(dto.getId()));

                // department 为空时从项目负责人用户反查部门回填
                if (StringUtils.isBlank(dto.getDepartment()) && dto.getProjectManagerId() != null) {
                    String dept = managerDepartments.get(dto.getProjectManagerId());
                    if (!StringUtils.isBlank(dept)) {
                        dto.setDepartment(dept);
                    }
                }
            }
        } catch (RuntimeException e) {
            // CO-027: enrichment 降级——dtos 已是基础数据，enrichment 失败就保持原样
            // 捕获 RuntimeException 覆盖 DB 超时、NPE、IllegalStateException 等运行时异常
            log.warn("enrichment 降级 - tender enrichment failed, returning base data", e);
        }
    }

    private Map<Long, String> fetchManagerNames(Set<Long> tenderIds) {
        Map<Long, Long> tenderToManager = projectRepository.findByTenderIdIn(tenderIds).stream()
                .filter(p -> p.getManagerId() != null)
                .collect(Collectors.toMap(Project::getTenderId, Project::getManagerId, (a, b) -> a)); // CO-027: merge function 防止 Duplicate key 异常

        if (tenderToManager.isEmpty()) return Map.of();

        Set<Long> managerIds = Set.copyOf(tenderToManager.values());
        Map<Long, String> idToName = userRepository.findByIdIn(managerIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a)); // CO-027: merge function 防止 Duplicate key 异常

        // CO-441: Collectors.toMap 不允许 null value，孤儿 manager_id（指向已删除用户）会触发 NPE。
        // 改用 HashMap 显式 put 允许 null value，保持 tenderId → null 映射，前端容错显示。
        Map<Long, String> result = new HashMap<>(tenderToManager.size());
        tenderToManager.forEach((tenderId, managerId) ->
                result.put(tenderId, idToName.get(managerId))
        );
        return result;
    }

    private Map<Long, String> fetchAssigneeNames(Set<Long> tenderIds) {
        Map<Long, Long> tenderToAssignee = tenderAssignmentRecordRepository
                .findLatestByTenderIds(tenderIds).stream()
                .collect(Collectors.toMap(
                        TenderAssignmentRecord::getTenderId,
                        TenderAssignmentRecord::getAssigneeId,
                        (a, b) -> a // CO-027: merge function 防止 Duplicate key 异常
                ));

        if (tenderToAssignee.isEmpty()) return Map.of();

        Set<Long> assigneeIds = Set.copyOf(tenderToAssignee.values());
        Map<Long, String> idToName = userRepository.findByIdIn(assigneeIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a)); // CO-027: merge function 防止 Duplicate key 异常

        // CO-441: 同 fetchManagerNames，防御性兜底 assignee 孤儿外键。
        Map<Long, String> result = new HashMap<>(tenderToAssignee.size());
        tenderToAssignee.forEach((tenderId, assigneeId) ->
                result.put(tenderId, idToName.get(assigneeId))
        );
        return result;
    }

    /**
     * 批量获取项目负责人的部门名称，用于 department 字段为空时兜底回填。
     * 生产环境 users.department_name 多为空字符串，但 users.department_code 存的是 OSS external_dept_id，
     * 因此通过 organization_departments.external_dept_id 反查部门名。
     */
    private Map<Long, String> fetchManagerDepartments(List<TenderDTO> dtos) {
        Set<Long> projectManagerIds = dtos.stream()
                .map(TenderDTO::getProjectManagerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (projectManagerIds.isEmpty()) {
            // CO-441: 用 HashMap 显式 put，允许 null value/null key，避免 Collectors.toMap/Map.of 对 null 抛异常。
            return new HashMap<>();
        }
        // userId → user.departmentCode（实际是 OSS external_dept_id）
        Map<Long, String> userIdToDeptCode = new HashMap<>(projectManagerIds.size());
        for (User user : userRepository.findByIdIn(projectManagerIds)) {
            userIdToDeptCode.put(user.getId(), user.getDepartmentCode());
        }
        // 收集所有非空 department_code，批量查 organization_departments
        Set<String> externalDeptIds = userIdToDeptCode.values().stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        Map<String, String> externalDeptIdToName = externalDeptIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : organizationDepartmentRepository
                        .findBySourceAppAndExternalDeptIdIn(OSS_SOURCE_APP, externalDeptIds).stream()
                        .collect(Collectors.toMap(
                                OrganizationDepartmentEntity::getExternalDeptId,
                                OrganizationDepartmentEntity::getDepartmentName,
                                (a, b) -> a));
        // 构建 userId → departmentName 映射
        Map<Long, String> result = new HashMap<>(projectManagerIds.size());
        userIdToDeptCode.forEach((userId, deptCode) ->
                result.put(userId, externalDeptIdToName.get(deptCode)));
        return result;
    }

}
