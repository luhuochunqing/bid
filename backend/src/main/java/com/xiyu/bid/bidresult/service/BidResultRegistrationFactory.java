package com.xiyu.bid.bidresult.service;

import com.xiyu.bid.bidresult.core.AwardRegistration;
import com.xiyu.bid.bidresult.core.AttachmentRequirementResolver;
import com.xiyu.bid.bidresult.core.BidResultAttachmentRef;
import com.xiyu.bid.bidresult.dto.BidResultRegisterRequest;
import com.xiyu.bid.bidresult.dto.BidResultUpdateRequest;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.entity.Project;

final class BidResultRegistrationFactory {

    private final BidResultResultParser resultParser = new BidResultResultParser();

    AwardRegistration fromRegisterRequest(Project project, BidResultRegisterRequest request) {
        AwardRegistration.ResultOutcome outcome = resultParser.parseOutcome(request.getResult());
        return new AwardRegistration(
                project.getId(),
                project.getName(),
                outcome,
                request.getAmount(),
                request.getContractStartDate(),
                request.getContractEndDate(),
                request.getContractDurationMonths(),
                request.getRemark(),
                request.getSkuCount(),
                attachmentReference(request.getAttachmentDocumentId(), outcome)
        );
    }

    AwardRegistration fromUpdateRequest(BidResultFetchResult current, BidResultUpdateRequest request) {
        AwardRegistration.ResultOutcome outcome = request.getResult() == null
                ? toOutcome(current.getResult())
                : resultParser.parseOutcome(request.getResult());
        Long documentId = request.getAttachmentDocumentId() != null
                ? request.getAttachmentDocumentId()
                : existingAttachmentId(current, outcome);
        return new AwardRegistration(
                current.getProjectId(),
                current.getProjectName(),
                outcome,
                request.getAmount() != null ? request.getAmount() : current.getAmount(),
                request.getContractStartDate() != null ? request.getContractStartDate() : current.getContractStartDate(),
                request.getContractEndDate() != null ? request.getContractEndDate() : current.getContractEndDate(),
                request.getContractDurationMonths() != null ? request.getContractDurationMonths() : current.getContractDurationMonths(),
                request.getRemark() != null ? request.getRemark() : current.getRemark(),
                request.getSkuCount() != null ? request.getSkuCount() : current.getSkuCount(),
                attachmentReference(documentId, outcome)
        );
    }

    BidResultFetchResult applyRegistration(
            BidResultFetchResult target,
            AwardRegistration registration
    ) {
        target.setResult(resultParser.toEntityResult(registration.result()));
        target.setAmount(registration.amount());
        target.setContractStartDate(registration.contractStartDate());
        target.setContractEndDate(registration.contractEndDate());
        target.setContractDurationMonths(registration.contractDurationMonths());
        target.setRemark(registration.remark());
        target.setSkuCount(registration.skuCount());
        if (registration.attachmentRef() != null && registration.attachmentRef().isPresent()) {
            if (registration.attachmentRef().attachmentType() == BidResultAttachmentRef.AttachmentType.NOTICE) {
                target.setNoticeDocumentId(registration.attachmentRef().documentId());
            } else {
                target.setAnalysisDocumentId(registration.attachmentRef().documentId());
            }
        }
        return target;
    }

    private AwardRegistration.ResultOutcome toOutcome(BidResultFetchResult.Result result) {
        return result == BidResultFetchResult.Result.WON
                ? AwardRegistration.ResultOutcome.WON
                : AwardRegistration.ResultOutcome.LOST;
    }

    private String attachmentReference(Long documentId, AwardRegistration.ResultOutcome outcome) {
        if (documentId == null || outcome == null) {
            return null;
        }
        return AttachmentRequirementResolver.requiredFor(outcome).name() + ":" + documentId;
    }

    private Long existingAttachmentId(BidResultFetchResult current, AwardRegistration.ResultOutcome outcome) {
        return AttachmentRequirementResolver.requiredFor(outcome) == BidResultAttachmentRef.AttachmentType.NOTICE
                ? current.getNoticeDocumentId()
                : current.getAnalysisDocumentId();
    }
}
