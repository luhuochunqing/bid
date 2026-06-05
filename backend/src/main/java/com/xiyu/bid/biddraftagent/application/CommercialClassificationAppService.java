package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.biddraftagent.domain.commercial.CommercialRequirementItem;
import com.xiyu.bid.biddraftagent.domain.commercial.CommercialSubTypePolicy;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommercialClassificationAppService {

    private final BidTenderDocumentSnapshotRepository snapshotRepository;
    private final BidDraftAgentJsonCodec jsonCodec;
    private final CommercialSubTypePolicy classificationPolicy = new CommercialSubTypePolicy();

    public CommercialClassificationResult classifyForProject(Long projectId) {
        var snapshot = snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId).orElse(null);
        if (snapshot == null || snapshot.getProfileJson() == null) return new CommercialClassificationResult(List.of());
        TenderRequirementProfile profile = jsonCodec.fromJson(snapshot.getProfileJson(), TenderRequirementProfile.class);
        if (profile == null || profile.commercialRequirements() == null) return new CommercialClassificationResult(List.of());
        return new CommercialClassificationResult(classificationPolicy.classifyAll(profile.commercialRequirements()));
    }

    public record CommercialClassificationResult(List<CommercialRequirementItem> items) {}
}
