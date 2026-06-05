package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.biddraftagent.domain.risk.RiskItem;
import com.xiyu.bid.biddraftagent.domain.risk.RedLineRiskPolicy;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RiskClassificationAppService {

    private final BidTenderDocumentSnapshotRepository snapshotRepository;
    private final BidDraftAgentJsonCodec jsonCodec;
    private final RedLineRiskPolicy policy = new RedLineRiskPolicy();

    public RiskClassificationResult classifyForProject(Long projectId) {
        var snapshot = snapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId).orElse(null);
        if (snapshot == null || snapshot.getProfileJson() == null) return new RiskClassificationResult(List.of());
        TenderRequirementProfile profile = jsonCodec.fromJson(snapshot.getProfileJson(), TenderRequirementProfile.class);
        if (profile == null || profile.riskPoints() == null) return new RiskClassificationResult(List.of());
        return new RiskClassificationResult(policy.classifyAll(profile.riskPoints()));
    }

    public record RiskClassificationResult(List<RiskItem> items) {}
}
