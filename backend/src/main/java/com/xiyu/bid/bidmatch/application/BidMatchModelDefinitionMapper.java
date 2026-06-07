package com.xiyu.bid.bidmatch.application;

import com.xiyu.bid.bidmatch.domain.BidMatchDimension;
import com.xiyu.bid.bidmatch.domain.BidMatchRule;
import com.xiyu.bid.bidmatch.domain.BidMatchRuleType;
import com.xiyu.bid.bidmatch.domain.BidMatchScoringModel;
import com.xiyu.bid.bidmatch.domain.ValidationResult;
import com.xiyu.bid.bidmatch.dto.BidMatchModelRequest;
import com.xiyu.bid.bidmatch.dto.BidMatchModelResponse;
import com.xiyu.bid.bidmatch.infrastructure.persistence.entity.BidMatchScoringModelEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class BidMatchModelDefinitionMapper {

    public BidMatchScoringModel toDomain(Long id, BidMatchModelRequest request, long draftRevision) {
        List<BidMatchDimension> dimensions = request.dimensions() == null
                ? List.of()
                : request.dimensions().stream().map(this::toDimension).toList();
        return new BidMatchScoringModel(
                id,
                blankToDefault(request.name(), "未命名投标匹配模型"),
                request.description(),
                dimensions,
                draftRevision
        );
    }

    public BidMatchModelResponse toResponse(
            BidMatchScoringModelEntity entity,
            BidMatchScoringModel model,
            ValidationResult validation
    ) {
        return new BidMatchModelResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getDraftRevision(),
                entity.getActiveVersionId(),
                entity.getActiveVersionNo(),
                model.dimensions().stream().map(this::toDimensionResponse).toList(),
                validation.errors()
        );
    }

    private BidMatchDimension toDimension(BidMatchModelRequest.DimensionRequest request) {
        List<BidMatchRule> rules = request.rules() == null
                ? List.of()
                : request.rules().stream().map(this::toRule).toList();
        return new BidMatchDimension(
                request.code(),
                request.name(),
                request.weight() == null ? 0 : request.weight(),
                request.enabled() == null || request.enabled(),
                rules
        );
    }

    private BidMatchRule toRule(BidMatchModelRequest.RuleRequest request) {
        return new BidMatchRule(
                request.code(),
                request.name(),
                parseType(request.type()),
                request.evidenceKey(),
                request.keywords() == null ? List.of() : request.keywords(),
                request.minValue(),
                request.maxValue(),
                request.weight() == null ? 0 : request.weight(),
                request.enabled() == null || request.enabled()
        );
    }

    private BidMatchModelResponse.DimensionResponse toDimensionResponse(BidMatchDimension dimension) {
        return new BidMatchModelResponse.DimensionResponse(
                dimension.code(),
                dimension.name(),
                dimension.weight(),
                dimension.enabled(),
                dimension.rules().stream().map(this::toRuleResponse).toList()
        );
    }

    private BidMatchModelResponse.RuleResponse toRuleResponse(BidMatchRule rule) {
        return new BidMatchModelResponse.RuleResponse(
                rule.code(),
                rule.name(),
                rule.type().name(),
                rule.evidenceKey(),
                rule.keywords(),
                rule.minValue(),
                rule.maxValue(),
                rule.weight(),
                rule.enabled()
        );
    }

    private BidMatchRuleType parseType(String type) {
        if (type == null || type.isBlank()) {
            return BidMatchRuleType.KEYWORD;
        }
        return BidMatchRuleType.valueOf(type.trim().toUpperCase(Locale.ROOT));
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
