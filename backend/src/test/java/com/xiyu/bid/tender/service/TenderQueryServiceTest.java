package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationDepartmentEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationDepartmentRepository;
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
import static org.assertj.core.api.Assertions.assertThatCode;
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
    @Mock
    private OrganizationDepartmentRepository organizationDepartmentRepository;

    private TenderQueryService createService() {
        return new TenderQueryService(tenderRepository, tenderMapper, tenderAttachmentRepository, accessGuard,
                projectRepository, userRepository, tenderAssignmentRecordRepository, organizationDepartmentRepository);
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

    @Test
    @DisplayName("CO-027: enrichment 阶段抛异常时降级返回基础数据，不中断主列表")
    void shouldDegradeGracefullyWhenEnrichmentThrowsException() {
        TenderDTO dto = new TenderDTO();
        dto.setId(1L);
        dto.setTitle("测试标讯");

        // 模拟 fetchManagerNames 内部调用 projectRepository.findByTenderIdIn 抛 RuntimeException
        //（如 DB 查询超时、连接异常等）
        when(projectRepository.findByTenderIdIn(Set.of(1L)))
                .thenThrow(new RuntimeException("模拟 DB 查询超时"));

        TenderQueryService service = createService();

        // 修复后：enrichment 降级，不抛异常
        // enrichAssignmentInfoBatch 返回 void，dto 保持原样（基础数据完整，装饰性字段为空）
        assertThatCode(() -> service.enrichAssignmentInfoBatch(List.of(dto)))
                .doesNotThrowAnyException();

        // dto 基础数据未被破坏
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getTitle()).isEqualTo("测试标讯");
        // 装饰性字段为空（enrichment 降级）
        assertThat(dto.getProjectManagerName()).isNull();
        assertThat(dto.getAssigneeName()).isNull();
    }

    @Test
    @DisplayName("department 为空时应通过 Project.managerId 关联 users.department_code 反查部门名回填")
    void shouldBackfillDepartmentFromProjectManagerUserWhenEmpty() {
        TenderDTO dto = new TenderDTO();
        dto.setId(1L);
        // 标讯自身 projectManagerId 为 null（生产 78% 场景），但对应 Project.managerId 有值
        dto.setProjectManagerId(null);
        dto.setDepartment(null);

        Project project = new Project();
        project.setTenderId(1L);
        project.setManagerId(99L);
        when(projectRepository.findByTenderIdIn(Set.of(1L))).thenReturn(List.of(project));
        when(tenderAssignmentRecordRepository.findLatestByTenderIds(Set.of(1L))).thenReturn(List.of());
        User manager = new User();
        manager.setId(99L);
        // 生产环境 users.department_name 多为空，但 department_code 存的是 OSS external_dept_id
        manager.setDepartmentCode("700498910");
        manager.setDepartmentName("");
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of(manager));
        OrganizationDepartmentEntity dept = new OrganizationDepartmentEntity();
        dept.setSourceApp("oss");
        dept.setDepartmentCode("2501");
        dept.setExternalDeptId("700498910");
        dept.setDepartmentName("东部二区");
        when(organizationDepartmentRepository.findBySourceAppAndExternalDeptIdIn("oss", Set.of("700498910")))
                .thenReturn(List.of(dept));

        TenderQueryService service = createService();
        service.enrichAssignmentInfoBatch(List.of(dto));

        assertThat(dto.getDepartment()).isEqualTo("东部二区");
    }

    @Test
    @DisplayName("department 已有值时不被用户部门覆盖")
    void shouldNotOverrideDepartmentWhenAlreadyPresent() {
        TenderDTO dto = new TenderDTO();
        dto.setId(1L);
        dto.setProjectManagerId(null);
        dto.setDepartment("已有部门");

        Project project = new Project();
        project.setTenderId(1L);
        project.setManagerId(99L);
        when(projectRepository.findByTenderIdIn(Set.of(1L))).thenReturn(List.of(project));
        when(tenderAssignmentRecordRepository.findLatestByTenderIds(Set.of(1L))).thenReturn(List.of());
        User manager = new User();
        manager.setId(99L);
        manager.setDepartmentCode("700498910");
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of(manager));

        TenderQueryService service = createService();
        service.enrichAssignmentInfoBatch(List.of(dto));

        assertThat(dto.getDepartment()).isEqualTo("已有部门");
    }

    @Test
    @DisplayName("department 为空且 Project.managerId 用户无 department_code 时保持为空")
    void shouldKeepDepartmentNullWhenProjectManagerHasNoDepartment() {
        TenderDTO dto = new TenderDTO();
        dto.setId(1L);
        dto.setProjectManagerId(null);
        dto.setDepartment(null);

        Project project = new Project();
        project.setTenderId(1L);
        project.setManagerId(99L);
        when(projectRepository.findByTenderIdIn(Set.of(1L))).thenReturn(List.of(project));
        when(tenderAssignmentRecordRepository.findLatestByTenderIds(Set.of(1L))).thenReturn(List.of());
        User manager = new User();
        manager.setId(99L);
        // department_code 为空，无法反查
        manager.setDepartmentCode(null);
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of(manager));

        TenderQueryService service = createService();
        service.enrichAssignmentInfoBatch(List.of(dto));

        assertThat(dto.getDepartment()).isNull();
    }

    @Test
    @DisplayName("department 为空且标讯无关联项目时不抛 NPE")
    void shouldNotThrowNpeWhenProjectManagerIdIsNull() {
        TenderDTO dto = new TenderDTO();
        dto.setId(1L);
        dto.setProjectManagerId(null);
        dto.setDepartment(null);

        // 无关联项目（生产 1066 条标讯无对应 project）
        when(projectRepository.findByTenderIdIn(Set.of(1L))).thenReturn(List.of());
        when(tenderAssignmentRecordRepository.findLatestByTenderIds(Set.of(1L))).thenReturn(List.of());

        TenderQueryService service = createService();

        assertThatCode(() -> service.enrichAssignmentInfoBatch(List.of(dto)))
                .doesNotThrowAnyException();
        assertThat(dto.getDepartment()).isNull();
    }

    @Test
    @DisplayName("CO-441: Project.managerId 指向已删除用户时不抛 NPE（孤儿外键兜底，name 和 department 均为 null）")
    void shouldNotThrowNpeWhenProjectManagerUserDeleted() {
        TenderDTO dto = new TenderDTO();
        dto.setId(1L);
        dto.setProjectManagerId(null);
        dto.setDepartment(null);

        // Project.managerId=99L 有值，但 user 表已删除该用户（孤儿外键）
        Project project = new Project();
        project.setTenderId(1L);
        project.setManagerId(99L);
        when(projectRepository.findByTenderIdIn(Set.of(1L))).thenReturn(List.of(project));
        when(tenderAssignmentRecordRepository.findLatestByTenderIds(Set.of(1L))).thenReturn(List.of());
        // 模拟 user 99 已删除（findByIdIn 返回空 List）
        when(userRepository.findByIdIn(Set.of(99L))).thenReturn(List.of());

        TenderQueryService service = createService();

        // 不抛 NPE，且 managerName / department 均保持 null（前端容错显示）
        assertThatCode(() -> service.enrichAssignmentInfoBatch(List.of(dto)))
                .doesNotThrowAnyException();
        assertThat(dto.getProjectManagerName()).isNull();
        assertThat(dto.getDepartment()).isNull();
    }
}
