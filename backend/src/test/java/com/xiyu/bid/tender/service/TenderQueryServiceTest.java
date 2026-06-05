package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private TenderAssignmentRecordRepository tenderAssignmentRecordRepository;

    private TenderQueryService createService() {
        return new TenderQueryService(tenderRepository, tenderMapper, accessGuard,
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
}
