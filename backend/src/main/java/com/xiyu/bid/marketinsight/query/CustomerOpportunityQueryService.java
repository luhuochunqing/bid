package com.xiyu.bid.marketinsight.query;

import com.xiyu.bid.marketinsight.dto.CustomerInsightDTO;
import com.xiyu.bid.marketinsight.dto.CustomerOpportunityAssembler;
import com.xiyu.bid.marketinsight.dto.CustomerPredictionDTO;
import com.xiyu.bid.marketinsight.dto.CustomerPurchaseDTO;
import com.xiyu.bid.marketinsight.dto.request.CustomerInsightQuery;
import com.xiyu.bid.marketinsight.entity.CustomerPrediction;
import com.xiyu.bid.marketinsight.repository.CustomerPredictionRepository;
import com.xiyu.bid.marketinsight.support.CustomerOpportunityTenderSupport;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 客户商机查询服务。
 * 只负责查询与过滤，不承载状态变更。
 */
@Service
@RequiredArgsConstructor
public class CustomerOpportunityQueryService {

    private final TenderRepository tenderRepository;
    private final CustomerPredictionRepository customerPredictionRepository;
    private final CustomerOpportunityTenderSupport tenderSupport;

    @Transactional(readOnly = true)
    public List<CustomerInsightDTO> getCustomerInsights(CustomerInsightQuery query) {
        Stream<CustomerPrediction> stream = resolvePredictionStream(query);
        return stream
                .map(CustomerOpportunityAssembler::toInsightDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerPurchaseDTO> getCustomerPurchases(String purchaserHash) {
        return tenderSupport.createSnapshots(tenderRepository.findAll()).stream()
                .filter(snapshot -> purchaserHash.equals(snapshot.purchaserHash()))
                .map(snapshot -> CustomerOpportunityAssembler.toPurchaseDTO(
                        snapshot.tenderId(),
                        snapshot.purchaserHash(),
                        snapshot.createdAt(),
                        snapshot.tenderTitle(),
                        snapshot.industry(),
                        snapshot.budget()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerPredictionDTO> getCustomerPredictions(String purchaserHash) {
        return customerPredictionRepository.findByPurchaserHash(purchaserHash)
                .stream()
                .map(CustomerOpportunityAssembler::toDTO)
                .toList();
    }

    private Stream<CustomerPrediction> resolvePredictionStream(CustomerInsightQuery query) {
        CustomerPrediction.Status status = resolveStatus(query == null ? null : query.getStatus());
        Stream<CustomerPrediction> stream = status == null
                ? customerPredictionRepository.findAllByOrderByOpportunityScoreDesc().stream()
                : customerPredictionRepository.findByStatus(status).stream();

        if (query == null) {
            return stream.sorted(this::compareByScoreDescending);
        }

        return stream
                .filter(item -> matches(item.getSalesRep(), query.getSalesRep()))
                .filter(item -> matches(item.getRegion(), query.getRegion()))
                .filter(item -> matches(item.getIndustry(), query.getIndustry()))
                .filter(item -> contains(item.getPurchaserName(), query.getKeyword()))
                .sorted(this::compareByScoreDescending);
    }

    private int compareByScoreDescending(CustomerPrediction left, CustomerPrediction right) {
        int leftScore = left.getOpportunityScore() == null ? 0 : left.getOpportunityScore();
        int rightScore = right.getOpportunityScore() == null ? 0 : right.getOpportunityScore();
        return Integer.compare(rightScore, leftScore);
    }

    private CustomerPrediction.Status resolveStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return CustomerPrediction.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean matches(String actual, String expected) {
        return expected == null || expected.isBlank() || (actual != null && actual.equalsIgnoreCase(expected));
    }

    private boolean contains(String actual, String keyword) {
        return keyword == null || keyword.isBlank() || (actual != null && actual.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)));
    }
}
