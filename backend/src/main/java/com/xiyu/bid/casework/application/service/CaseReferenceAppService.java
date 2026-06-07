package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.dto.CaseReferenceRecordDTO;
import com.xiyu.bid.casework.entity.CaseReferenceRecord;
import com.xiyu.bid.casework.repository.CaseReferenceRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CaseReferenceAppService {

    private final CaseReferenceRecordRepository caseReferenceRecordRepository;

    @Transactional(readOnly = true)
    public java.util.List<CaseReferenceRecordDTO> getReferenceRecords(Long caseId) {
        return caseReferenceRecordRepository.findByCaseIdOrderByReferencedAtDesc(caseId).stream().map(this::toDTO).toList();
    }

    public CaseReferenceRecordDTO createReferenceRecord(Long caseId, String referencedByName,
                                                        String referenceTarget, String referenceContext,
                                                        String sourceProjectName) {
        CaseReferenceRecord record = CaseReferenceRecord.builder()
                .caseId(caseId)
                .referencedByName(referencedByName != null ? referencedByName : "未知用户")
                .referenceTarget(referenceTarget.trim())
                .referenceContext(referenceContext)
                .sourceProjectName(sourceProjectName)
                .build();
        CaseReferenceRecord saved = caseReferenceRecordRepository.save(record);
        return toDTO(saved);
    }

    private CaseReferenceRecordDTO toDTO(CaseReferenceRecord referenceRecord) {
        return CaseReferenceRecordDTO.builder()
                .id(referenceRecord.getId())
                .caseId(referenceRecord.getCaseId())
                .referencedBy(referenceRecord.getReferencedBy())
                .referencedByName(referenceRecord.getReferencedByName())
                .referenceTarget(referenceRecord.getReferenceTarget())
                .referenceContext(referenceRecord.getReferenceContext())
                .referencedAt(referenceRecord.getReferencedAt())
                .sourceProjectName(referenceRecord.getSourceProjectName())
                .build();
    }
}
