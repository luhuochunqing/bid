package com.xiyu.bid.personnel.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.common.domain.PagedResult;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog;
import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelOperationLogEntity;
import com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelOperationLogJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonnelOperationLogService {

    private final PersonnelOperationLogJpaRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PersonnelOperationLog save(PersonnelOperationLog log) {
        PersonnelOperationLogEntity entity = toEntity(log);
        PersonnelOperationLogEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Transactional(readOnly = true)
    public Optional<PersonnelOperationLog> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Transactional(readOnly = true)
    public List<PersonnelOperationLog> findByPersonnelId(Long personnelId) {
        return repository.findByPersonnelIdOrderByCreatedAtDesc(personnelId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResult<PersonnelOperationLog> findByPersonnelIdPageable(Long personnelId, int page, int size) {
        Page<PersonnelOperationLogEntity> pageResult = repository
                .findByPersonnelIdOrderByCreatedAtDesc(personnelId, PageRequest.of(page, size));
        List<PersonnelOperationLog> logs = pageResult.getContent()
                .stream()
                .map(this::toDomain)
                .toList();
        return PagedResult.of(logs, pageResult.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public List<PersonnelOperationLog> findByPersonnelIdAndTimeRange(
            Long personnelId, LocalDateTime startTime, LocalDateTime endTime) {
        return repository.findByPersonnelIdAndTimeRange(personnelId, startTime, endTime)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private PersonnelOperationLogEntity toEntity(PersonnelOperationLog log) {
        PersonnelOperationLogEntity entity = new PersonnelOperationLogEntity();
        if (log.id() != null) {
            entity.setId(log.id());
        }
        entity.setPersonnelId(log.personnelId());
        entity.setOperatorId(log.operatorId());
        entity.setOperatorName(log.operatorName());
        entity.setOperationType(log.operationType());
        entity.setChangeDetails(serializeChanges(log.changeDetails()));
        return entity;
    }

    private PersonnelOperationLog toDomain(PersonnelOperationLogEntity entity) {
        return new PersonnelOperationLog(
                entity.getId(),
                entity.getPersonnelId(),
                entity.getOperatorId(),
                entity.getOperatorName(),
                entity.getOperationType(),
                deserializeChanges(entity.getChangeDetails()),
                entity.getCreatedAt()
        );
    }

    private String serializeChanges(List<PersonnelOperationLog.ChangeDetail> changes) {
        if (changes == null || changes.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JsonProcessingException e) {
            log.warn("序列化变更详情失败: {}", e.getMessage());
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private List<PersonnelOperationLog.ChangeDetail> deserializeChanges(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class,
                            PersonnelOperationLog.ChangeDetail.class));
        } catch (JsonProcessingException e) {
            log.warn("反序列化变更详情失败: {}", e.getMessage());
            return List.of();
        }
    }
}
