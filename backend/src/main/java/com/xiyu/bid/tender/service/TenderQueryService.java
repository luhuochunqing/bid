package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final TenderProjectAccessGuard accessGuard;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TenderAssignmentRecordRepository tenderAssignmentRecordRepository;

    public List<TenderDTO> searchTenders(TenderSearchCriteria criteria) {
        log.debug("Searching tenders with criteria: {}", criteria);
        return accessGuard.filterVisibleTenders(tenderRepository.findAll(TenderSpecification.byCriteria(criteria))).stream()
                .map(tenderMapper::toDTO)
                .toList();
    }

    public TenderDTO getTenderById(Long id) {
        log.debug("Fetching tender by id: {}", id);
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", id.toString()));
        accessGuard.assertCanAccessTender(tender);
        TenderDTO dto = tenderMapper.toDTO(tender);
        enrichAssignmentInfo(dto, id);
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
        return new org.springframework.data.domain.PageImpl<>(dtos, pageable, visible.size());
    }

    /**
     * 批量补充分配信息（用于列表查询场景）
     * 将 N+1 查询优化为 4 次固定查询
     */
    public void enrichAssignmentInfoBatch(List<TenderDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) return;

        Set<Long> tenderIds = dtos.stream().map(TenderDTO::getId).collect(Collectors.toSet());

        Map<Long, String> managerNames = fetchManagerNames(tenderIds);
        Map<Long, String> assigneeNames = fetchAssigneeNames(tenderIds);

        for (TenderDTO dto : dtos) {
            dto.setProjectManagerName(managerNames.get(dto.getId()));
            dto.setAssigneeName(assigneeNames.get(dto.getId()));
        }
    }

    private Map<Long, String> fetchManagerNames(Set<Long> tenderIds) {
        Map<Long, Long> tenderToManager = projectRepository.findByTenderIdIn(tenderIds).stream()
                .filter(p -> p.getManagerId() != null)
                .collect(Collectors.toMap(Project::getTenderId, Project::getManagerId));

        if (tenderToManager.isEmpty()) return Map.of();

        Set<Long> managerIds = Set.copyOf(tenderToManager.values());
        Map<Long, String> idToName = userRepository.findByIdIn(managerIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return tenderToManager.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> idToName.getOrDefault(e.getValue(), null)
                ));
    }

    private Map<Long, String> fetchAssigneeNames(Set<Long> tenderIds) {
        Map<Long, Long> tenderToAssignee = tenderAssignmentRecordRepository
                .findLatestByTenderIds(tenderIds).stream()
                .collect(Collectors.toMap(
                        TenderAssignmentRecord::getTenderId,
                        TenderAssignmentRecord::getAssigneeId
                ));

        if (tenderToAssignee.isEmpty()) return Map.of();

        Set<Long> assigneeIds = Set.copyOf(tenderToAssignee.values());
        Map<Long, String> idToName = userRepository.findByIdIn(assigneeIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return tenderToAssignee.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> idToName.getOrDefault(e.getValue(), null)
                ));
    }
}
