package com.xiyu.bid.marketinsight.mapper;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.marketinsight.core.CustomerOpportunityTenderSnapshot;
import com.xiyu.bid.marketinsight.core.IndustryClassificationPolicy;
import com.xiyu.bid.marketinsight.core.PurchaserExtractionPolicy;
import org.springframework.stereotype.Component;

/**
 * Maps tenders into reusable customer-opportunity snapshots.
 */
@Component
public class CustomerOpportunitySnapshotMapper {

    public CustomerOpportunityTenderSnapshot toTenderSnapshot(final Tender tender) {
        PurchaserExtractionPolicy.ExtractionResult extraction =
                PurchaserExtractionPolicy.extractPurchaser(tender.getTitle());
        return new CustomerOpportunityTenderSnapshot(
                tender.getId(),
                tender.getTitle(),
                extraction.found() ? extraction.purchaserName() : "",
                extraction.found() ? extraction.purchaserHash() : "",
                IndustryClassificationPolicy.classifyIndustry(tender.getTitle()),
                tender.getBudget(),
                tender.getCreatedAt());
    }
}
