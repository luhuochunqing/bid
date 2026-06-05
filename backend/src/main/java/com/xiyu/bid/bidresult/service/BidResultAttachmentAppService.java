package com.xiyu.bid.bidresult.service;

import com.xiyu.bid.bidresult.core.BidResultAttachmentRef;
import com.xiyu.bid.bidresult.dto.BidResultAttachmentBindRequest;
import com.xiyu.bid.bidresult.dto.BidResultFetchResultAssembler;
import com.xiyu.bid.bidresult.dto.BidResultFetchResultDTO;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BidResultAttachmentAppService {

    private final BidResultFetchResultRepository fetchResultRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final BidResultReminderAppService reminderAppService;
    private final BidResultProjectAccessGuard accessGuard;

    @Transactional
    public BidResultFetchResultDTO bindAttachment(Long resultId, BidResultAttachmentBindRequest request, Long operatorId, String operatorName) {
        if (request == null || request.getDocumentId() == null || request.getAttachmentType() == null) {
            throw new BusinessException("附件绑定参数不完整");
        }
        BidResultFetchResult result = fetchResultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Bid result fetch record not found: " + resultId));
        accessGuard.assertCanAccess(result.getProjectId());
        ProjectDocument document = projectDocumentRepository.findById(request.getDocumentId())
                .orElseThrow(() -> new ResourceNotFoundException("Project document not found: " + request.getDocumentId()));
        accessGuard.assertCanAccess(document.getProjectId());
        if (!result.getProjectId().equals(document.getProjectId())) {
            throw new BusinessException("附件所属项目与投标结果不一致");
        }

        if (request.getAttachmentType() == BidResultAttachmentRef.AttachmentType.NOTICE) {
            result.setNoticeDocumentId(request.getDocumentId());
        } else {
            result.setAnalysisDocumentId(request.getDocumentId());
        }
        BidResultFetchResult saved = fetchResultRepository.save(result);
        reminderAppService.markUploadedForResult(saved, request.getDocumentId(), operatorId, operatorName);
        return BidResultFetchResultAssembler.toDto(saved);
    }

    @Transactional
    public void bindAttachmentFromDocument(ProjectDocument document) {
        if (document == null || document.getLinkedEntityId() == null) {
            return;
        }
        BidResultAttachmentRef.AttachmentType attachmentType = resolveAttachmentType(document.getDocumentCategory());
        if (attachmentType == null) {
            return;
        }
        BidResultFetchResult result = fetchResultRepository.findById(document.getLinkedEntityId())
                .orElseThrow(() -> new ResourceNotFoundException("Bid result fetch record not found: " + document.getLinkedEntityId()));
        accessGuard.assertCanAccess(result.getProjectId());
        accessGuard.assertCanAccess(document.getProjectId());
        if (!result.getProjectId().equals(document.getProjectId())) {
            throw new BusinessException("附件所属项目与投标结果不一致");
        }
        if (attachmentType == BidResultAttachmentRef.AttachmentType.NOTICE) {
            result.setNoticeDocumentId(document.getId());
        } else {
            result.setAnalysisDocumentId(document.getId());
        }
        BidResultFetchResult saved = fetchResultRepository.save(result);
        reminderAppService.markUploadedForResult(saved, document.getId(), document.getUploaderId(), document.getUploaderName());
    }

    @Transactional
    public void unbindAttachmentByDocument(Long documentId) {
        if (documentId == null) {
            return;
        }
        fetchResultRepository.findAll().stream()
                .filter(result -> documentId.equals(result.getNoticeDocumentId()) || documentId.equals(result.getAnalysisDocumentId()))
                .forEach(result -> {
                    accessGuard.assertCanAccess(result.getProjectId());
                    BidResultAttachmentRef.AttachmentType attachmentType;
                    if (documentId.equals(result.getNoticeDocumentId())) {
                        result.setNoticeDocumentId(null);
                        attachmentType = BidResultAttachmentRef.AttachmentType.NOTICE;
                    } else {
                        result.setAnalysisDocumentId(null);
                        attachmentType = BidResultAttachmentRef.AttachmentType.REPORT;
                    }
                    fetchResultRepository.save(result);
                    reminderAppService.revertAttachmentRemoved(result, attachmentType);
                });
    }

    private BidResultAttachmentRef.AttachmentType resolveAttachmentType(String documentCategory) {
        if ("BID_RESULT_NOTICE".equalsIgnoreCase(documentCategory)) {
            return BidResultAttachmentRef.AttachmentType.NOTICE;
        }
        if ("BID_RESULT_ANALYSIS".equalsIgnoreCase(documentCategory)) {
            return BidResultAttachmentRef.AttachmentType.REPORT;
        }
        return null;
    }
}
