package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.BidDraftSnapshot;
import com.xiyu.bid.biddraftagent.domain.GapCheckResult;
import com.xiyu.bid.biddraftagent.domain.ManualConfirmationDecision;
import com.xiyu.bid.biddraftagent.domain.MaterialMatchScore;
import com.xiyu.bid.biddraftagent.domain.RequirementClassification;
import com.xiyu.bid.biddraftagent.domain.WriteCoverageDecision;

public interface BidDraftTextGenerator {

    BidDraftGenerationResult generate(
            BidDraftSnapshot snapshot,
            RequirementClassification classification,
            MaterialMatchScore materialMatchScore,
            GapCheckResult gapCheck,
            ManualConfirmationDecision manualConfirmation,
            WriteCoverageDecision writeCoverage
    );
}
