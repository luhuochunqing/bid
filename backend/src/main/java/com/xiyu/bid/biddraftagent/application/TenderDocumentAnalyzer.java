package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;

public interface TenderDocumentAnalyzer {

    TenderRequirementProfile analyze(TenderDocumentAnalysisInput input);
}
