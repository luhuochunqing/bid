package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.BidDraftSnapshot;
import com.xiyu.bid.biddraftagent.domain.GapCheckPolicy;
import com.xiyu.bid.biddraftagent.domain.GapCheckResult;
import com.xiyu.bid.biddraftagent.domain.ManualConfirmationDecision;
import com.xiyu.bid.biddraftagent.domain.ManualConfirmationPolicy;
import com.xiyu.bid.biddraftagent.domain.MaterialMatchScore;
import com.xiyu.bid.biddraftagent.domain.MaterialMatchScoringPolicy;
import com.xiyu.bid.biddraftagent.domain.RequirementClassification;
import com.xiyu.bid.biddraftagent.domain.RequirementClassificationPolicy;
import com.xiyu.bid.biddraftagent.domain.WriteCoverageDecision;
import com.xiyu.bid.biddraftagent.domain.WriteCoveragePolicy;
import org.springframework.stereotype.Service;

@Service
public class BidDraftAgentEvaluator {

    private final RequirementClassificationPolicy requirementClassificationPolicy = new RequirementClassificationPolicy();
    private final MaterialMatchScoringPolicy materialMatchScoringPolicy = new MaterialMatchScoringPolicy();
    private final GapCheckPolicy gapCheckPolicy = new GapCheckPolicy();
    private final ManualConfirmationPolicy manualConfirmationPolicy = new ManualConfirmationPolicy();
    private final WriteCoveragePolicy writeCoveragePolicy = new WriteCoveragePolicy();

    public BidDraftAgentEvaluation evaluate(BidDraftSnapshot snapshot) {
        RequirementClassification requirementClassification = requirementClassificationPolicy.classify(snapshot);
        MaterialMatchScore materialMatchScore = materialMatchScoringPolicy.score(snapshot, requirementClassification);
        GapCheckResult gapCheck = gapCheckPolicy.check(snapshot, requirementClassification, materialMatchScore);
        ManualConfirmationDecision manualConfirmation = manualConfirmationPolicy.evaluate(requirementClassification, gapCheck);
        WriteCoverageDecision writeCoverage = writeCoveragePolicy.evaluate(
                snapshot,
                requirementClassification,
                materialMatchScore,
                gapCheck,
                manualConfirmation
        );
        return new BidDraftAgentEvaluation(
                requirementClassification,
                materialMatchScore,
                gapCheck,
                manualConfirmation,
                writeCoverage
        );
    }
}
