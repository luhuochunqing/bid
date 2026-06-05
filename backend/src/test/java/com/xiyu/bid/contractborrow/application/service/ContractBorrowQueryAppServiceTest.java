package com.xiyu.bid.contractborrow.application.service;

import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowStatus;
import com.xiyu.bid.contractborrow.infrastructure.persistence.entity.ContractBorrowApplicationEntity;
import com.xiyu.bid.contractborrow.infrastructure.persistence.repository.ContractBorrowApplicationJpaRepository;
import com.xiyu.bid.contractborrow.infrastructure.persistence.repository.ContractBorrowEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractBorrowQueryAppServiceTest {

    private final ContractBorrowApplicationJpaRepository applicationRepository =
            mock(ContractBorrowApplicationJpaRepository.class);
    private final ContractBorrowEventJpaRepository eventRepository = mock(ContractBorrowEventJpaRepository.class);
    private final ContractBorrowQueryAppService service = new ContractBorrowQueryAppService(
            applicationRepository,
            eventRepository,
            new ContractBorrowMapper()
    );

    @Test
    @SuppressWarnings("unchecked")
    void page_ShouldUseRepositorySpecificationAndPageable() {
        var pageable = PageRequest.of(1, 10);
        when(applicationRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sampleEntity()), pageable, 21));

        var result = service.page(new com.xiyu.bid.contractborrow.application.command.ContractBorrowQueryCriteria(
                "智算",
                "APPROVED",
                "小王"
        ), pageable);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(21);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(10);
        verify(applicationRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void overview_ShouldCountOverdueInRepository() {
        when(applicationRepository.count()).thenReturn(7L);
        when(applicationRepository.countByStatus(ContractBorrowStatus.PENDING_APPROVAL)).thenReturn(1L);
        when(applicationRepository.countByStatus(ContractBorrowStatus.APPROVED)).thenReturn(2L);
        when(applicationRepository.countByStatusInAndExpectedReturnDateBefore(any(), any())).thenReturn(3L);

        var overview = service.overview();

        assertThat(overview.total()).isEqualTo(7);
        assertThat(overview.pendingApproval()).isEqualTo(1);
        assertThat(overview.approved()).isEqualTo(2);
        assertThat(overview.overdue()).isEqualTo(3);
        verify(applicationRepository).countByStatusInAndExpectedReturnDateBefore(any(), any(LocalDate.class));
    }

    private ContractBorrowApplicationEntity sampleEntity() {
        return ContractBorrowApplicationEntity.builder()
                .id(9L)
                .contractNo("HT-2026-0421")
                .contractName("西域智算中心年度框架合同")
                .sourceName("法务归档室")
                .borrowerName("小王")
                .borrowerDept("销售一部")
                .customerName("西域智算中心")
                .purpose("投标文件复核")
                .borrowType("原件借阅")
                .expectedReturnDate(LocalDate.of(2026, 4, 30))
                .submittedAt(LocalDateTime.of(2026, 4, 21, 10, 30))
                .status(ContractBorrowStatus.APPROVED)
                .build();
    }
}
