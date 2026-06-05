package com.xiyu.bid.contractborrow.application.service;

import com.xiyu.bid.contractborrow.application.command.ContractBorrowActionCommand;
import com.xiyu.bid.contractborrow.application.command.CreateContractBorrowCommand;
import com.xiyu.bid.contractborrow.application.view.ContractBorrowView;
import com.xiyu.bid.contractborrow.domain.model.ContractBorrowApplication;
import com.xiyu.bid.contractborrow.domain.service.ContractBorrowDecision;
import com.xiyu.bid.contractborrow.domain.service.ContractBorrowLifecyclePolicy;
import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowEventType;
import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowStatus;
import com.xiyu.bid.contractborrow.infrastructure.persistence.entity.ContractBorrowApplicationEntity;
import com.xiyu.bid.contractborrow.infrastructure.persistence.entity.ContractBorrowEventEntity;
import com.xiyu.bid.contractborrow.infrastructure.persistence.repository.ContractBorrowApplicationJpaRepository;
import com.xiyu.bid.contractborrow.infrastructure.persistence.repository.ContractBorrowEventJpaRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ContractBorrowCommandAppService {

    private final ContractBorrowApplicationJpaRepository applicationRepository;
    private final ContractBorrowEventJpaRepository eventRepository;
    private final ContractBorrowMapper mapper;

    @Transactional
    public ContractBorrowView create(CreateContractBorrowCommand command) {
        LocalDateTime now = LocalDateTime.now();
        ContractBorrowApplicationEntity entity = ContractBorrowApplicationEntity.builder()
            .contractNo(command.contractNo())
            .contractName(command.contractName())
            .sourceName(command.sourceName())
            .borrowerName(command.borrowerName())
            .borrowerDept(command.borrowerDept())
            .customerName(command.customerName())
            .purpose(command.purpose())
            .borrowType(command.borrowType())
            .expectedReturnDate(command.expectedReturnDate())
            .submittedAt(now)
            .status(ContractBorrowStatus.PENDING_APPROVAL)
            .lastComment(command.purpose())
            .build();
        ContractBorrowApplicationEntity saved = applicationRepository.save(entity);
        recordEvent(saved.getId(), ContractBorrowEventType.SUBMITTED, saved.getStatus(), command.borrowerName(), command.purpose(), now);
        return mapper.toView(saved, LocalDate.now());
    }

    @Transactional
    public ContractBorrowView approve(Long id, ContractBorrowActionCommand command) {
        return transition(id, command, ContractBorrowEventType.APPROVED,
            application -> ContractBorrowLifecyclePolicy.approve(application, LocalDateTime.now(), command.actorName()));
    }

    @Transactional
    public ContractBorrowView reject(Long id, ContractBorrowActionCommand command) {
        return transition(id, command, ContractBorrowEventType.REJECTED,
            application -> ContractBorrowLifecyclePolicy.reject(application, LocalDateTime.now(), command.effectiveComment()));
    }

    @Transactional
    public ContractBorrowView returnBack(Long id, ContractBorrowActionCommand command) {
        return transition(id, command, ContractBorrowEventType.RETURNED,
            application -> ContractBorrowLifecyclePolicy.returnBack(application, LocalDateTime.now(), command.effectiveComment()));
    }

    @Transactional
    public ContractBorrowView cancel(Long id, ContractBorrowActionCommand command) {
        return transition(id, command, ContractBorrowEventType.CANCELLED,
            application -> ContractBorrowLifecyclePolicy.cancel(application, LocalDateTime.now(), command.effectiveComment()));
    }

    private ContractBorrowView transition(Long id, ContractBorrowActionCommand command, ContractBorrowEventType eventType, Transition transition) {
        ContractBorrowApplicationEntity entity = getEntity(id);
        ContractBorrowDecision decision = transition.apply(mapper.toDomain(entity));
        if (!decision.allowed()) {
            throw new IllegalStateException(decision.reason());
        }
        mapper.copyDomainToEntity(decision.application(), entity);
        ContractBorrowApplicationEntity saved = applicationRepository.save(entity);
        recordEvent(saved.getId(), eventType, saved.getStatus(), command.actorName(), command.effectiveComment(), LocalDateTime.now());
        return mapper.toView(saved, LocalDate.now());
    }

    private ContractBorrowApplicationEntity getEntity(Long id) {
        return applicationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ContractBorrowApplication", String.valueOf(id)));
    }

    private void recordEvent(
        Long applicationId,
        ContractBorrowEventType eventType,
        ContractBorrowStatus statusAfter,
        String actorName,
        String comment,
        LocalDateTime createdAt
    ) {
        eventRepository.save(ContractBorrowEventEntity.builder()
            .applicationId(applicationId)
            .eventType(eventType)
            .statusAfter(statusAfter)
            .actorName(actorName)
            .comment(comment)
            .createdAt(createdAt)
            .build());
    }

    private interface Transition {
        ContractBorrowDecision apply(ContractBorrowApplication application);
    }
}
