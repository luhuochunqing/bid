package com.xiyu.bid.bidmatch.application;

import com.xiyu.bid.bidmatch.domain.BidMatchModelSnapshotResult;
import com.xiyu.bid.bidmatch.domain.BidMatchModelVersionSnapshot;
import com.xiyu.bid.bidmatch.domain.BidMatchScoringModel;
import com.xiyu.bid.bidmatch.domain.BidMatchScoringPolicy;
import com.xiyu.bid.bidmatch.domain.ValidationResult;
import com.xiyu.bid.bidmatch.dto.BidMatchActivationResponse;
import com.xiyu.bid.bidmatch.dto.BidMatchModelRequest;
import com.xiyu.bid.bidmatch.dto.BidMatchModelResponse;
import com.xiyu.bid.bidmatch.infrastructure.persistence.entity.BidMatchModelVersionEntity;
import com.xiyu.bid.bidmatch.infrastructure.persistence.entity.BidMatchScoringModelEntity;
import com.xiyu.bid.bidmatch.infrastructure.persistence.repository.BidMatchModelVersionJpaRepository;
import com.xiyu.bid.bidmatch.infrastructure.persistence.repository.BidMatchScoringModelJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BidMatchModelAppService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String SYSTEM_OPERATOR = "system";

    private final BidMatchScoringModelJpaRepository modelRepository;
    private final BidMatchModelVersionJpaRepository versionRepository;
    private final BidMatchJsonCodec jsonCodec;
    private final BidMatchModelDefinitionMapper mapper;
    private final BidMatchScoringPolicy policy = new BidMatchScoringPolicy();

    @Transactional
    public List<BidMatchModelResponse> listModels() {
        ensureDefaultDraft();
        return modelRepository.findAllByOrderByIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BidMatchModelResponse createModel(BidMatchModelRequest request) {
        BidMatchScoringModel model = mapper.toDomain(null, request, 1);
        assertValid(model);
        BidMatchScoringModelEntity entity = new BidMatchScoringModelEntity();
        entity.setName(model.name());
        entity.setDescription(model.description());
        entity.setStatus(STATUS_INACTIVE);
        entity.setDraftRevision(model.draftRevision());
        entity.setModelJson(jsonCodec.toJson(model));
        BidMatchScoringModelEntity saved = modelRepository.save(entity);
        BidMatchScoringModel modelWithId = model.withId(saved.getId());
        saved.setModelJson(jsonCodec.toJson(modelWithId));
        return toResponse(modelRepository.save(saved));
    }

    @Transactional
    public BidMatchModelResponse updateModel(BidMatchModelRequest request) {
        if (request.id() == null) {
            throw new IllegalArgumentException("模型ID不能为空");
        }
        BidMatchScoringModelEntity entity = findModel(request.id());
        long nextRevision = entity.getDraftRevision() + 1;
        BidMatchScoringModel model = mapper.toDomain(entity.getId(), request, nextRevision);
        assertValid(model);
        entity.setName(model.name());
        entity.setDescription(model.description());
        entity.setDraftRevision(model.draftRevision());
        entity.setModelJson(jsonCodec.toJson(model));
        return toResponse(modelRepository.save(entity));
    }

    @Transactional
    public BidMatchActivationResponse activateModel(Long modelId) {
        return activateEntity(findModel(modelId), SYSTEM_OPERATOR);
    }

    @Transactional
    public BidMatchActiveModelVersion activeVersion() {
        BidMatchModelVersionEntity version = activeVersionEntity();
        return new BidMatchActiveModelVersion(
                version.getId(),
                jsonCodec.fromJson(version.getSnapshotJson(), BidMatchModelVersionSnapshot.class)
        );
    }

    @Transactional
    public BidMatchModelVersionSnapshot activeSnapshot() {
        return activeVersion().snapshot();
    }

    @Transactional
    public Long activeVersionId() {
        return activeVersion().versionEntityId();
    }

    @Transactional
    public void ensureDefaultDraft() {
        if (modelRepository.count() > 0) {
            return;
        }
        BidMatchScoringModel defaultModel = DefaultBidMatchModelFactory.create();
        BidMatchScoringModelEntity entity = new BidMatchScoringModelEntity();
        entity.setName(defaultModel.name());
        entity.setDescription(defaultModel.description());
        entity.setStatus(STATUS_INACTIVE);
        entity.setDraftRevision(defaultModel.draftRevision());
        entity.setModelJson(jsonCodec.toJson(defaultModel));
        BidMatchScoringModelEntity saved = modelRepository.save(entity);
        BidMatchScoringModel modelWithId = defaultModel.withId(saved.getId());
        saved.setModelJson(jsonCodec.toJson(modelWithId));
        modelRepository.save(saved);
    }

    private BidMatchActivationResponse activateEntity(BidMatchScoringModelEntity entity, String operator) {
        BidMatchScoringModel model = readModel(entity).withId(entity.getId());
        BidMatchModelSnapshotResult snapshotResult = policy.createSnapshot(
                model,
                entity.getId(),
                (int) versionRepository.countByModelId(entity.getId()) + 1
        );
        if (!snapshotResult.validation().valid()) {
            throw new IllegalArgumentException(String.join("; ", snapshotResult.validation().errors()));
        }
        versionRepository.findByActiveTrue().forEach(version -> {
            version.setActive(false);
            versionRepository.save(version);
        });
        modelRepository.findFirstByStatusOrderByIdAsc(STATUS_ACTIVE).ifPresent(activeModel -> {
            activeModel.setStatus(STATUS_INACTIVE);
            modelRepository.save(activeModel);
        });
        LocalDateTime activatedAt = LocalDateTime.now();
        BidMatchModelVersionEntity version = new BidMatchModelVersionEntity();
        version.setModelId(entity.getId());
        version.setVersionNo(snapshotResult.snapshot().orElseThrow().versionNo());
        version.setSnapshotJson(jsonCodec.toJson(snapshotResult.snapshot().orElseThrow()));
        version.setActive(true);
        version.setActivatedAt(activatedAt);
        version.setActivatedBy(operator);
        BidMatchModelVersionEntity savedVersion = versionRepository.save(version);

        entity.setStatus(STATUS_ACTIVE);
        entity.setActiveVersionId(savedVersion.getId());
        entity.setActiveVersionNo(savedVersion.getVersionNo());
        modelRepository.save(entity);
        return new BidMatchActivationResponse(
                entity.getId(),
                savedVersion.getId(),
                savedVersion.getVersionNo(),
                entity.getStatus(),
                activatedAt
        );
    }

    private BidMatchModelVersionEntity activeVersionEntity() {
        return versionRepository.findFirstByActiveTrueOrderByActivatedAtDescIdDesc()
                .orElseThrow(() -> new IllegalStateException("请先在系统设置中配置并激活投标匹配评分模型"));
    }

    private BidMatchModelResponse toResponse(BidMatchScoringModelEntity entity) {
        BidMatchScoringModel model = readModel(entity).withId(entity.getId());
        ValidationResult validation = policy.validate(model);
        return mapper.toResponse(entity, model, validation);
    }

    private BidMatchScoringModel readModel(BidMatchScoringModelEntity entity) {
        return jsonCodec.fromJson(entity.getModelJson(), BidMatchScoringModel.class);
    }

    private BidMatchScoringModelEntity findModel(Long id) {
        return modelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("投标匹配评分模型不存在"));
    }

    private ValidationResult assertValid(BidMatchScoringModel model) {
        ValidationResult validation = policy.validate(model);
        if (!validation.valid()) {
            throw new IllegalArgumentException(String.join("; ", validation.errors()));
        }
        return validation;
    }
}
