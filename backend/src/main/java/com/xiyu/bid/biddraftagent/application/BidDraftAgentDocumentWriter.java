package com.xiyu.bid.biddraftagent.application;

public interface BidDraftAgentDocumentWriter {

    BidDraftAgentDocumentWriteResult write(Long projectId, BidDraftAgentDocumentWritePlan plan);
}
