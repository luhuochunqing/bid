package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.mapper.PerformanceMapper;
import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;

/**
 * 业绩列表服务单元测试（CO-583）
 * 验证 groupTotalExpiryDate 聚合值注入逻辑：
 *   - 聚合范围不受筛选条件影响（基于全量数据）
 *   - 同集团各行显示相同聚合值
 *   - 空集团/无截止日期的边界处理
 */
class ListPerformanceAppServiceTest {

    private PerformanceRepository repository;
    private PerformanceMapper mapper;
    private PerformanceAlertConfigAppService configService;
    private ListPerformanceAppService service;

    @BeforeEach
    void setUp() {
        repository = mock(PerformanceRepository.class);
        mapper = mock(PerformanceMapper.class);
        configService = mock(PerformanceAlertConfigAppService.class);
        service = new ListPerformanceAppService(repository, mapper, configService);
    }

    @Test
    void list_injectsGroupTotalExpiryDate_fromAllDataNotFiltered() {
        // 场景：集团A 有 3 份合同，expiryDate 分别为 2023、2024、2025
        // 当前筛选只返回 1 份（2023），但聚合值应为 2025（基于全量）
        var criteria = PerformanceSearchCriteria.empty();
        var record = buildRecord(1L, "集团A", LocalDate.of(2022, 7, 1), LocalDate.of(2023, 6, 30));
        Map<String, LocalDate> groupTotalMap = Map.of("集团A", LocalDate.of(2025, 9, 24));
        when(configService.getConfig()).thenReturn(null);
        when(repository.findAll(eq(criteria), any())).thenReturn(List.of(record));
        when(repository.findGroupTotalExpiryDates()).thenReturn(groupTotalMap);
        stubMapperInject(record, groupTotalMap);

        var result = service.list(criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).groupTotalExpiryDate()).isEqualTo(LocalDate.of(2025, 9, 24));
        verify(repository, times(1)).findGroupTotalExpiryDates();
    }

    @Test
    void list_sameGroupSharesSameAggregateValue() {
        // 同集团的多个合同共享同一个聚合值
        var criteria = PerformanceSearchCriteria.empty();
        var r1 = buildRecord(1L, "集团A", LocalDate.of(2022, 7, 1), LocalDate.of(2023, 6, 30));
        var r2 = buildRecord(2L, "集团A", LocalDate.of(2023, 8, 16), LocalDate.of(2024, 8, 15));
        var r3 = buildRecord(3L, "集团A", LocalDate.of(2024, 9, 25), LocalDate.of(2025, 9, 24));
        Map<String, LocalDate> groupTotalMap = Map.of("集团A", LocalDate.of(2025, 9, 24));
        when(configService.getConfig()).thenReturn(null);
        when(repository.findAll(eq(criteria), any())).thenReturn(List.of(r1, r2, r3));
        when(repository.findGroupTotalExpiryDates()).thenReturn(groupTotalMap);
        stubMapperInject(r1, groupTotalMap);
        stubMapperInject(r2, groupTotalMap);
        stubMapperInject(r3, groupTotalMap);

        var result = service.list(criteria);

        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(dto ->
                assertThat(dto.groupTotalExpiryDate()).isEqualTo(LocalDate.of(2025, 9, 24)));
    }

    @Test
    void list_groupWithoutExpiryDate_returnsNullAggregate() {
        // 集团内所有合同均无 expiryDate 时，聚合值为 null
        var criteria = PerformanceSearchCriteria.empty();
        var record = buildRecord(1L, "集团B", LocalDate.of(2024, 1, 1), null);
        Map<String, LocalDate> groupTotalMap = Map.of();
        when(configService.getConfig()).thenReturn(null);
        when(repository.findAll(eq(criteria), any())).thenReturn(List.of(record));
        when(repository.findGroupTotalExpiryDates()).thenReturn(groupTotalMap);
        stubMapperInject(record, groupTotalMap);

        var result = service.list(criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).groupTotalExpiryDate()).isNull();
    }

    @Test
    void list_recordWithoutGroupCompany_returnsNullAggregate() {
        // 无 groupCompany 的记录，聚合值为 null
        var criteria = PerformanceSearchCriteria.empty();
        var record = buildRecord(1L, null, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
        Map<String, LocalDate> groupTotalMap = Map.of();
        when(configService.getConfig()).thenReturn(null);
        when(repository.findAll(eq(criteria), any())).thenReturn(List.of(record));
        when(repository.findGroupTotalExpiryDates()).thenReturn(groupTotalMap);
        stubMapperInject(record, groupTotalMap);

        var result = service.list(criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).groupTotalExpiryDate()).isNull();
    }

    // ========== get(id) 测试（CO-583 Google Code Review 阻断2 补测）==========
    // 旧代码 get(id) 用 Map.of(groupCompany, null) 构造 Map 传给 mapper，
    // Map.of 不允许 null value 会抛 NPE。以下测试覆盖两个 NPE 场景 + happy path。

    @Test
    void get_recordWithNullGroupCompany_returnsDtoWithoutNPE() {
        // 场景 A：groupCompany 为 null → service 不调用 findGroupTotalExpiryDate
        // 旧代码会构造 Map.of(null, null) 抛 NPE；新代码直接传 null 给 mapper
        var record = buildRecord(1L, null, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        stubMapperInjectSingle(record, null);

        var result = service.get(1L);

        assertThat(result).isNotNull();
        assertThat(result.groupTotalExpiryDate()).isNull();
        verify(repository, never()).findGroupTotalExpiryDate(any());
    }

    @Test
    void get_groupWithoutExpiryDate_returnsDtoWithoutNPE() {
        // 场景 B：集团存在但无 expiryDate → findGroupTotalExpiryDate 返回 Optional.empty()
        // 旧代码会构造 Map.of("集团B", null) 抛 NPE；新代码 .orElse(null) 得到 null 直传
        var record = buildRecord(1L, "集团B", LocalDate.of(2024, 1, 1), null);
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(repository.findGroupTotalExpiryDate("集团B")).thenReturn(Optional.empty());
        stubMapperInjectSingle(record, null);

        var result = service.get(1L);

        assertThat(result).isNotNull();
        assertThat(result.groupTotalExpiryDate()).isNull();
    }

    @Test
    void get_recordWithValidGroup_returnsAggregateValue() {
        // Happy path：集团有 expiryDate，get(id) 返回聚合值
        var record = buildRecord(1L, "集团A", LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(repository.findGroupTotalExpiryDate("集团A"))
                .thenReturn(Optional.of(LocalDate.of(2025, 9, 24)));
        stubMapperInjectSingle(record, LocalDate.of(2025, 9, 24));

        var result = service.get(1L);

        assertThat(result).isNotNull();
        assertThat(result.groupTotalExpiryDate()).isEqualTo(LocalDate.of(2025, 9, 24));
    }

    /**
     * 模拟真实 PerformanceMapper.toDTO(record, groupTotalMap) 行为：
     * 根据 record.groupCompany() 从 groupTotalMap 取值，注入到返回的 DTO。
     */
    @SuppressWarnings("unchecked")
    private void stubMapperInject(PerformanceRecord record, Map<String, LocalDate> groupTotalMap) {
        when(mapper.toDTO(eq(record), any(Map.class))).thenAnswer(invocation -> {
            PerformanceRecord r = invocation.getArgument(0);
            Map<String, LocalDate> map = invocation.getArgument(1);
            LocalDate groupTotal = (map != null && r.groupCompany() != null)
                    ? map.get(r.groupCompany()) : null;
            return baseDto(r, groupTotal);
        });
    }

    /**
     * 模拟真实 PerformanceMapper.toDTO(record, LocalDate) 单值重载行为：
     * 直接将传入的 groupTotal 注入到返回的 DTO。
     * 使用 nullable 匹配 null 值（groupCompany 为空或集团无 expiryDate 时 groupTotal 为 null）。
     */
    private void stubMapperInjectSingle(PerformanceRecord record, LocalDate groupTotal) {
        when(mapper.toDTO(eq(record), nullable(LocalDate.class))).thenAnswer(invocation -> {
            PerformanceRecord r = invocation.getArgument(0);
            LocalDate total = invocation.getArgument(1);
            return baseDto(r, total);
        });
    }

    private static PerformanceRecord buildRecord(Long id, String groupCompany,
                                                  LocalDate signingDate, LocalDate expiryDate) {
        return new PerformanceRecord(
                id, "合同" + id, "签约单位" + id, groupCompany,
                null, "行业" + id, null, null, null,
                signingDate, expiryDate, null,
                "联系人" + id, "13800000000", "属地" + id, "地址" + id, "负责人" + id,
                "http://mall.com", false, "备注" + id,
                List.of(), null, null
        );
    }

    /** 构造基础 DTO（groupTotalExpiryDate 由 service 注入，初始传 null）。 */
    private static PerformanceDTO baseDto(PerformanceRecord r, LocalDate groupTotal) {
        return new PerformanceDTO(
                r.id(), r.contractName(), r.signingEntity(), r.groupCompany(),
                null, r.industry(), null, null, null,
                r.signingDate(), r.expiryDate(), r.totalExpiryDate(),
                groupTotal,
                0L, "", null,
                r.contactPerson(), r.contactInfo(), r.territory(),
                r.customerAddress(), r.xiyuProjectManager(),
                r.mallWebsiteUrl(), r.hasBidNotice(), r.remarks(),
                List.of(), r.createdAt(), r.updatedAt()
        );
    }
}
