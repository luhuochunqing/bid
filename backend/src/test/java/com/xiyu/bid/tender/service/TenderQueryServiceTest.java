package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.tender.repository.TenderAttachmentRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderQueryServiceTest {

    @Mock
    private TenderRepository tenderRepository;
    @Mock
    private TenderMapper tenderMapper;
    @Mock
    private TenderProjectAccessGuard accessGuard;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenderAttachmentRepository tenderAttachmentRepository;
    @Mock
    private TenderAssignmentRecordRepository tenderAssignmentRecordRepository;

    private TenderQueryService createService() {
        return new TenderQueryService(tenderRepository, tenderMapper, tenderAttachmentRepository, accessGuard,
                projectRepository, userRepository, tenderAssignmentRecordRepository);
    }

    private Tender tender(long id, String title) {
        Tender t = new Tender();
        t.setId(id);
        t.setTitle(title);
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    @Test
    @DisplayName("searchTendersPaged 应过滤不可见的标讯")
    void shouldFilterInvisibleTendersInPagedSearch() {
        Tender visible = tender(1L, "可见标讯");
        Tender invisible = tender(2L, "不可见标讯");
        List<Tender> allFromDb = List.of(visible, invisible);

        PageRequest pageable = PageRequest.of(0, 20);
        when(tenderRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(allFromDb, pageable, 2));

        when(accessGuard.filterVisibleTenders(allFromDb))
                .thenReturn(List.of(visible));

        TenderDTO dto1 = new TenderDTO();
        dto1.setId(1L);
        dto1.setTitle("可见标讯");
        when(tenderMapper.toDTO(visible)).thenReturn(dto1);

        TenderQueryService service = createService();

        Page<TenderDTO> result = service.searchTendersPaged(TenderSearchCriteria.empty(), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("可见标讯");
        verify(accessGuard).filterVisibleTenders(allFromDb);
    }

    @Test
    @DisplayName("searchTendersPaged 当所有标讯都不可见时应返回空列表")
    void shouldReturnEmptyWhenAllTendersFilteredOut() {
        Tender tender = tender(1L, "被过滤的标讯");
        List<Tender> allFromDb = List.of(tender);

        PageRequest pageable = PageRequest.of(0, 20);
        when(tenderRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(allFromDb, pageable, 1));

        when(accessGuard.filterVisibleTenders(allFromDb))
                .thenReturn(List.of());

        TenderQueryService service = createService();

        Page<TenderDTO> result = service.searchTendersPaged(TenderSearchCriteria.empty(), pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
        verify(accessGuard).filterVisibleTenders(allFromDb);
    }

    @Test
    @DisplayName("searchTendersPaged 当所有标讯都可见时应保持原样")
    void shouldKeepAllWhenAllTendersVisible() {
        Tender t1 = tender(1L, "标讯1");
        Tender t2 = tender(2L, "标讯2");
        List<Tender> allFromDb = List.of(t1, t2);

        PageRequest pageable = PageRequest.of(0, 20);
        when(tenderRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(allFromDb, pageable, 2));

        when(accessGuard.filterVisibleTenders(allFromDb))
                .thenReturn(allFromDb);

        TenderDTO dto1 = new TenderDTO();
        dto1.setId(1L);
        dto1.setTitle("标讯1");
        TenderDTO dto2 = new TenderDTO();
        dto2.setId(2L);
        dto2.setTitle("标讯2");
        when(tenderMapper.toDTO(t1)).thenReturn(dto1);
        when(tenderMapper.toDTO(t2)).thenReturn(dto2);

        TenderQueryService service = createService();

        Page<TenderDTO> result = service.searchTendersPaged(TenderSearchCriteria.empty(), pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(accessGuard).filterVisibleTenders(allFromDb);
    }

    @Test
    @DisplayName("searchTendersPaged 空数据应返回空页")
    void shouldReturnEmptyPageForNoData() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(tenderRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        when(accessGuard.filterVisibleTenders(List.of()))
                .thenReturn(List.of());

        TenderQueryService service = createService();

        Page<TenderDTO> result = service.searchTendersPaged(TenderSearchCriteria.empty(), pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(accessGuard).filterVisibleTenders(List.of());
    }

    @Test
    @DisplayName("CO-333: 标讯已有项目负责人姓名时，关联项目后不被覆盖（立即投标后前端显示不变化）")
    void shouldNotOverrideProjectManagerNameWhenTenderAlreadyHasOne() {
        Tender tender = tender(1L, "已投标标讯");
        tender.setProjectManagerName("韩超");

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        TenderDTO dto = new TenderDTO();
        dto.setId(1L);
        dto.setProjectManagerName("韩超");
        when(tenderMapper.toDTO(tender)).thenReturn(dto);

        // 关联项目存在（管理员点击「立即投标」后 tender.projectId 已设置）
        Project project = new Project();
        project.setManagerId(99L);
        when(projectRepository.findByTenderId(1L)).thenReturn(List.of(project));
        when(tenderAttachmentRepository.findByTenderId(1L)).thenReturn(List.of());

        TenderQueryService service = createService();
        TenderDTO result = service.getTenderById(1L);

        assertThat(result.getProjectManagerName()).isEqualTo("韩超");
        // 不应触发用 project.managerId 反查用户（标讯自身已有姓名，提前返回）
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("CO-333: 标讯无项目负责人姓名时，仍用项目 managerId 反查补充（保留原兜底逻辑）")
    void shouldFallbackToProjectManagerIdWhenTenderHasNoName() {
        Tender tender = tender(1L, "已投标标讯");

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        TenderDTO dto = new TenderDTO();
        dto.setId(1L);
        when(tenderMapper.toDTO(tender)).thenReturn(dto);

        Project project = new Project();
        project.setManagerId(99L);
        when(projectRepository.findByTenderId(1L)).thenReturn(List.of(project));
        User manager = new User();
        manager.setId(99L);
        manager.setFullName("李四");
        when(userRepository.findById(99L)).thenReturn(Optional.of(manager));
        when(tenderAttachmentRepository.findByTenderId(1L)).thenReturn(List.of());

        TenderQueryService service = createService();
        TenderDTO result = service.getTenderById(1L);

        assertThat(result.getProjectManagerName()).isEqualTo("李四");
    }

    @Test
    @DisplayName("CO-333: 批量查询时标讯已有项目负责人姓名不被项目 managerId 覆盖")
    void shouldNotOverrideProjectManagerNameInBatchWhenTenderAlreadyHasOne() {
        TenderDTO dto = new TenderDTO();
        dto.setId(1L);
        dto.setProjectManagerName("韩超");

        Project project = new Project();
        project.setTenderId(1L);
        project.setManagerId(99L);
        when(projectRepository.findByTenderIdIn(Set.of(1L))).thenReturn(List.of(project));
        User manager = new User();
        manager.setId(99L);
        manager.setFullName("李四");
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of(manager));
        when(tenderAssignmentRecordRepository.findLatestByTenderIds(Set.of(1L))).thenReturn(List.of());

        TenderQueryService service = createService();
        service.enrichAssignmentInfoBatch(List.of(dto));

        assertThat(dto.getProjectManagerName()).isEqualTo("韩超");
    }

    @Test
    @DisplayName("CO-441: 项目 managerId 指向已删除用户时不应抛 NPE（孤儿外键兜底）")
    void shouldNotThrowNpeWhenManagerUserDeleted() {
        TenderDTO dto = new TenderDTO();
        dto.setId(1L);

        Project project = new Project();
        project.setTenderId(1L);
        project.setManagerId(99L);
        when(projectRepository.findByTenderIdIn(Set.of(1L))).thenReturn(List.of(project));
        // 模拟 user 99 已删除（孤儿外键）—— 修复前会抛 NPE
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of());
        when(tenderAssignmentRecordRepository.findLatestByTenderIds(Set.of(1L))).thenReturn(List.of());

        TenderQueryService service = createService();
        service.enrichAssignmentInfoBatch(List.of(dto));

        // 不抛 NPE，且 managerName 保持 null（前端容错显示）
        assertThat(dto.getProjectManagerName()).isNull();
    }

    @Test
    @DisplayName("CO-441: 标讯 assignee 指向已删除用户时不应抛 NPE（防御性兜底）")
    void shouldNotThrowNpeWhenAssigneeUserDeleted() {
        TenderDTO dto = new TenderDTO();
        dto.setId(1L);

        when(projectRepository.findByTenderIdIn(Set.of(1L))).thenReturn(List.of());
        TenderAssignmentRecord record = new TenderAssignmentRecord();
        record.setTenderId(1L);
        record.setAssigneeId(88L);
        when(tenderAssignmentRecordRepository.findLatestByTenderIds(Set.of(1L))).thenReturn(List.of(record));
        // 模拟 user 88 已删除——修复前会抛 NPE
        when(userRepository.findByIdIn(Set.of(88L))).thenReturn(List.of());

        TenderQueryService service = createService();
        service.enrichAssignmentInfoBatch(List.of(dto));

        assertThat(dto.getAssigneeName()).isNull();
    }
}
