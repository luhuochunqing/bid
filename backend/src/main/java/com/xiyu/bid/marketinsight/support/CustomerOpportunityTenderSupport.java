package com.xiyu.bid.marketinsight.support;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.marketinsight.core.CustomerOpportunityTenderSnapshot;
import com.xiyu.bid.marketinsight.mapper.CustomerOpportunitySnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared support for preparing tender snapshots reused by query and refresh flows.
 */
@Component
@RequiredArgsConstructor
public class CustomerOpportunityTenderSupport {

    private final CustomerOpportunitySnapshotMapper snapshotMapper;

    public List<CustomerOpportunityTenderSnapshot> createSnapshots(final List<Tender> tenders) {
        return tenders.stream()
                .map(snapshotMapper::toTenderSnapshot)
                .toList();
    }

    public Map<String, List<CustomerOpportunityTenderSnapshot>> groupByPurchaserHash(
            final List<CustomerOpportunityTenderSnapshot> snapshots) {
        Map<String, List<CustomerOpportunityTenderSnapshot>> grouped = new LinkedHashMap<>();
        for (CustomerOpportunityTenderSnapshot snapshot : snapshots) {
            if (snapshot.purchaserHash() != null && !snapshot.purchaserHash().isBlank()) {
                grouped.computeIfAbsent(snapshot.purchaserHash(), ignored -> new java.util.ArrayList<>())
                        .add(snapshot);
            }
        }
        return grouped;
    }
}
