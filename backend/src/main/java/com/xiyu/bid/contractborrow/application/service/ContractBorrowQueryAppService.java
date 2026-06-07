package com.xiyu.bid.contractborrow.application.service;

import com.xiyu.bid.contractborrow.application.command.ContractBorrowQueryCriteria;
import com.xiyu.bid.contractborrow.application.view.ContractBorrowEventView;
import com.xiyu.bid.contractborrow.application.view.ContractBorrowOverviewView;
import com.xiyu.bid.contractborrow.application.view.ContractBorrowPageView;
import com.xiyu.bid.contractborrow.application.view.ContractBorrowView;
import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowStatus;
import com.xiyu.bid.contractborrow.infrastructure.persistence.entity.ContractBorrowApplicationEntity;
import com.xiyu.bid.contractborrow.infrastructure.persistence.repository.ContractBorrowApplicationJpaRepository;
import com.xiyu.bid.contractborrow.infrastructure.persistence.repository.ContractBorrowEventJpaRepository;
import com.xiyu.bid.contractborrow.infrastructure.persistence.repository.ContractBorrowSpecification;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractBorrowQueryAppService {

    private final ContractBorrowApplicationJpaRepository applicationRepository;
    private final ContractBorrowEventJpaRepository eventRepository;
    private final ContractBorrowMapper mapper;

    @Transactional(readOnly = true)
    public ContractBorrowOverviewView overview() {
        LocalDate today = LocalDate.now();
        long overdue = applicationRepository.countByStatusInAndExpectedReturnDateBefore(
            ContractBorrowSpecification.activeBorrowingStatuses(),
            today
        );
        return new ContractBorrowOverviewView(
            applicationRepository.count(),
            applicationRepository.countByStatus(ContractBorrowStatus.PENDING_APPROVAL),
            applicationRepository.countByStatus(ContractBorrowStatus.APPROVED),
            applicationRepository.countByStatus(ContractBorrowStatus.BORROWED),
            applicationRepository.countByStatus(ContractBorrowStatus.RETURNED),
            applicationRepository.countByStatus(ContractBorrowStatus.REJECTED),
            applicationRepository.countByStatus(ContractBorrowStatus.CANCELLED),
            overdue
        );
    }

    @Transactional(readOnly = true)
    public List<ContractBorrowView> list(ContractBorrowQueryCriteria criteria) {
        return page(criteria, Pageable.unpaged()).items();
    }

    @Transactional(readOnly = true)
    public ContractBorrowPageView page(ContractBorrowQueryCriteria criteria, Pageable pageable) {
        LocalDate today = LocalDate.now();
        Page<ContractBorrowApplicationEntity> page = applicationRepository.findAll(
            ContractBorrowSpecification.byCriteria(criteria, today),
            pageable
        );
        List<ContractBorrowView> items = page.getContent().stream()
            .map(application -> mapper.toView(application, today))
            .toList();
        return new ContractBorrowPageView(
            items,
            page.getTotalElements(),
            pageable.isPaged() ? pageable.getPageNumber() + 1 : 1,
            pageable.isPaged() ? pageable.getPageSize() : items.size(),
            page.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ContractBorrowView detail(Long id) {
        ContractBorrowApplicationEntity entity = applicationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ContractBorrowApplication", String.valueOf(id)));
        return mapper.toView(entity, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<ContractBorrowEventView> events(Long applicationId) {
        if (!applicationRepository.existsById(applicationId)) {
            throw new ResourceNotFoundException("ContractBorrowApplication", String.valueOf(applicationId));
        }
        return eventRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream()
            .map(mapper::toEventView)
            .toList();
    }
}
