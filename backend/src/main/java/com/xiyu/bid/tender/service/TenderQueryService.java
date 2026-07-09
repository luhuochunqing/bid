package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.tender.entity.TenderAttachment;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.integration.external.TenderIntegrationMapper;
import com.xiyu.bid.project.service.ProjectManagerDepartmentEnricher;
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

import java.util.List;
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
    private final TenderManagerInfoFetcher managerInfoFetcher;
    private final ProjectManagerDepartmentEnricher departmentEnricher;

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
        enrichDepartment(dto);
    }

    /**
     * department 为空时从 Tender.projectManagerId 反查 user 部门回填。
     *
     * <p>数据源与批量路径 {@link #enrichAssignmentInfoBatch} 一致：
     * Tender.projectManagerId（标讯自己的字段，方向正确，不从 Project 反查）。</p>
     */
    private void enrichDepartment(TenderDTO dto) {
        if (StringUtils.isNotBlank(dto.getDepartment()) || dto.getProjectManagerId() == null) {
            return;
        }
        String deptName = departmentEnricher.resolveDepartmentNameByUserId(dto.getProjectManagerId());
        if (StringUtils.isNotBlank(deptName)) {
            dto.setDepartment(deptName);
        }
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

            // managerName 用 Project.managerId（CO-333），deptName 用 Tender.projectManagerId（标讯自己的字段）
            Map<Long, TenderManagerInfoFetcher.ManagerInfo> managerInfo = managerInfoFetcher.fetch(dtos);
            Map<Long, String> assigneeNames = fetchAssigneeNames(tenderIds);

            for (TenderDTO dto : dtos) {
                // 同一 dto 只查一次 ManagerInfo，name 和 department 共用（避免重复 HashMap lookup）
                TenderManagerInfoFetcher.ManagerInfo info = managerInfo.get(dto.getId());
                // CO-333: 标讯自身已存项目负责人姓名时不被项目 managerId 反查覆盖，
                // 避免管理员点击「立即投标」生成项目后，前端项目负责人显示值发生变化。
                if ((dto.getProjectManagerName() == null || dto.getProjectManagerName().isBlank())
                        && info != null && info.managerName() != null) {
                    dto.setProjectManagerName(info.managerName());
                }
                dto.setAssigneeName(assigneeNames.get(dto.getId()));

                // department 为空时从项目负责人用户反查部门回填
                // deptName 数据源：Tender.projectManagerId（标讯自己的字段，方向正确）
                if (StringUtils.isBlank(dto.getDepartment())
                        && info != null && !StringUtils.isBlank(info.managerDeptName())) {
                    dto.setDepartment(info.managerDeptName());
                }
            }
        } catch (RuntimeException e) {
            // CO-027: enrichment 降级——dtos 已是基础数据，enrichment 失败就保持原样
            // 捕获 RuntimeException 覆盖 DB 超时、NPE、IllegalStateException 等运行时异常
            log.warn("enrichment 降级 - tender enrichment failed, returning base data", e);
        }
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

        // CO-441: 同 fetchManagerInfo，防御性兜底 assignee 孤儿外键。
        Map<Long, String> result = new java.util.HashMap<>(tenderToAssignee.size());
        tenderToAssignee.forEach((tenderId, assigneeId) ->
                result.put(tenderId, idToName.get(assigneeId))
        );
        return result;
    }

}
