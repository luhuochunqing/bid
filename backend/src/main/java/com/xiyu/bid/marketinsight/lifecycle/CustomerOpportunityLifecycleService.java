package com.xiyu.bid.marketinsight.lifecycle;

import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.marketinsight.core.CustomerOpportunityRefreshPolicy;
import com.xiyu.bid.marketinsight.core.CustomerOpportunityTenderSnapshot;
import com.xiyu.bid.marketinsight.core.PredictionTransitionPolicy;
import com.xiyu.bid.marketinsight.dto.CustomerOpportunityAssembler;
import com.xiyu.bid.marketinsight.dto.CustomerPredictionDTO;
import com.xiyu.bid.marketinsight.entity.CustomerPrediction;
import com.xiyu.bid.marketinsight.repository.CustomerPredictionRepository;
import com.xiyu.bid.marketinsight.support.CustomerOpportunityTenderSupport;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 客户商机生命周期服务。
 * 负责刷新、状态流转和转项目回写。
 */
@Service
@RequiredArgsConstructor
public class CustomerOpportunityLifecycleService {

    private final TenderRepository tenderRepository;
    private final CustomerPredictionRepository customerPredictionRepository;
    private final CustomerOpportunityTenderSupport tenderSupport;

    @Transactional
    public void refreshInsights() {
        List<CustomerOpportunityTenderSnapshot> snapshots =
                tenderSupport.createSnapshots(tenderRepository.findAll());
        Map<String, List<CustomerOpportunityTenderSnapshot>> byPurchaser =
                tenderSupport.groupByPurchaserHash(snapshots);

        LocalDateTime now = LocalDateTime.now();
        List<CustomerPrediction> predictions = new ArrayList<>(byPurchaser.size());
        for (var entry : byPurchaser.entrySet()) {
            CustomerOpportunityRefreshPolicy.evaluate(entry.getKey(), entry.getValue(), now)
                    .map(evaluation -> buildPrediction(entry.getKey(), evaluation, now))
                    .ifPresent(predictions::add);
        }
        customerPredictionRepository.saveAll(predictions);

        customerPredictionRepository.findAll().stream()
                .filter(p -> p.getStatus() != CustomerPrediction.Status.CONVERTED)
                .filter(p -> !byPurchaser.containsKey(p.getPurchaserHash()))
                .forEach(customerPredictionRepository::delete);
    }

    @Transactional
    public CustomerPredictionDTO transitionPrediction(Long id, String targetStatus) {
        PredictionTransitionPolicy.PredictionStatus coreTarget = resolveTargetStatus(targetStatus);
        CustomerPrediction prediction = customerPredictionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerPrediction", String.valueOf(id)));

        PredictionTransitionPolicy.PredictionStatus coreCurrent = mapToCoreStatus(prediction.getStatus());

        var result = PredictionTransitionPolicy.validateTransition(coreCurrent, coreTarget);
        if (!result.allowed()) {
            throw new IllegalStateException(result.reason());
        }

        prediction.setStatus(CustomerPrediction.Status.valueOf(coreTarget.name()));
        CustomerPrediction saved = customerPredictionRepository.save(prediction);
        return CustomerOpportunityAssembler.toDTO(saved);
    }

    @Transactional
    public CustomerPredictionDTO convertPrediction(Long id, Long projectId) {
        CustomerPrediction prediction = customerPredictionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerPrediction", String.valueOf(id)));

        PredictionTransitionPolicy.PredictionStatus coreCurrent = mapToCoreStatus(prediction.getStatus());
        var result = PredictionTransitionPolicy.validateConversion(
                coreCurrent,
                prediction.getConvertedProjectId(),
                projectId);
        if (!result.allowed()) {
            throw new IllegalStateException(result.reason());
        }
        if (!result.shouldSave()) {
            return CustomerOpportunityAssembler.toDTO(prediction);
        }

        prediction.setStatus(CustomerPrediction.Status.CONVERTED);
        if (result.resolvedProjectId() != null) {
            prediction.setConvertedProjectId(result.resolvedProjectId());
        }
        CustomerPrediction saved = customerPredictionRepository.save(prediction);
        return CustomerOpportunityAssembler.toDTO(saved);
    }

    private PredictionTransitionPolicy.PredictionStatus mapToCoreStatus(CustomerPrediction.Status entityStatus) {
        return switch (entityStatus) {
            case WATCH -> PredictionTransitionPolicy.PredictionStatus.WATCH;
            case RECOMMEND -> PredictionTransitionPolicy.PredictionStatus.RECOMMEND;
            case CONVERTED -> PredictionTransitionPolicy.PredictionStatus.CONVERTED;
            case CANCELLED -> PredictionTransitionPolicy.PredictionStatus.CANCELLED;
        };
    }

    private CustomerPrediction buildPrediction(
            String hash,
            CustomerOpportunityRefreshPolicy.RefreshEvaluation evaluation,
            LocalDateTime now) {
        CustomerPrediction prediction = customerPredictionRepository.findByPurchaserHash(hash)
                .stream().findFirst()
                .orElseGet(() -> CustomerPrediction.builder()
                        .purchaserHash(hash)
                        .purchaserName(evaluation.purchaserName())
                        .build());

        prediction.setPurchaserName(evaluation.purchaserName());
        prediction.setIndustry(evaluation.industry());
        prediction.setOpportunityScore(evaluation.opportunityScore());
        prediction.setPredictedCategory(evaluation.predictedCategory());
        prediction.setPredictedBudgetMin(evaluation.predictedBudgetMin());
        prediction.setPredictedBudgetMax(evaluation.predictedBudgetMax());
        prediction.setPredictedWindow(evaluation.predictedWindow());
        prediction.setConfidence(evaluation.confidence());
        prediction.setReasoningSummary(evaluation.reasoningSummary());
        prediction.setEvidenceRecordIds(evaluation.evidenceRecordIds());
        prediction.setMainCategories(evaluation.mainCategories());
        prediction.setAvgBudget(evaluation.avgBudget());
        prediction.setCycleType(evaluation.cycleType());
        prediction.setFrequency(evaluation.frequency());
        prediction.setPeriodMonths(evaluation.periodMonths());
        prediction.setLastComputedAt(now);
        return prediction;
    }

    private PredictionTransitionPolicy.PredictionStatus resolveTargetStatus(String targetStatus) {
        if (targetStatus == null || targetStatus.isBlank()) {
            throw new IllegalArgumentException("status 不能为空");
        }

        try {
            return PredictionTransitionPolicy.PredictionStatus.valueOf(
                    targetStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的状态: " + targetStatus, exception);
        }
    }

}
