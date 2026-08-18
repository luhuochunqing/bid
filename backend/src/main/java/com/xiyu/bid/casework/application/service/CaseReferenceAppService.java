package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.dto.CaseReferenceRecordDTO;
import com.xiyu.bid.casework.entity.CaseReferenceRecord;
import com.xiyu.bid.casework.repository.CaseReferenceRecordRepository;
import com.xiyu.bid.repository.CaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CaseReferenceAppService {

    private final CaseReferenceRecordRepository caseReferenceRecordRepository;
    private final CaseRepository caseRepository;

    @Transactional(readOnly = true)
    public java.util.List<CaseReferenceRecordDTO> getReferenceRecords(Long caseId) {
        return caseReferenceRecordRepository.findByCaseIdOrderByReferencedAtDesc(caseId).stream().map(this::toDTO).toList();
    }

    public CaseReferenceRecordDTO createReferenceRecord(Long caseId, String referencedByName,
                                                        String referenceTarget, String referenceContext,
                                                        String sourceProjectName) {
        return createReferenceRecord(caseId, referencedByName, referenceTarget, referenceContext, sourceProjectName, null, null, "APPEND");
    }

    public CaseReferenceRecordDTO createReferenceRecord(Long caseId, String referencedByName,
                                                        String referenceTarget, String referenceContext,
                                                        String sourceProjectName, Long projectId,
                                                        Long scoreItemId, String reuseMode) {
        CaseReferenceRecord record = CaseReferenceRecord.builder()
                .caseId(caseId)
                .referencedByName(referencedByName != null ? referencedByName : "未知用户")
                .referenceTarget(referenceTarget != null ? referenceTarget.trim() : "REUSE")
                .referenceContext(referenceContext)
                .sourceProjectName(sourceProjectName)
                .projectId(projectId)
                .scoreItemId(scoreItemId)
                .reuseMode(reuseMode != null ? reuseMode : "APPEND")
                .build();
        CaseReferenceRecord saved = caseReferenceRecordRepository.save(record);

        // 引用一次，useCount +1
        caseRepository.findById(caseId).ifPresent(c -> {
            c.setUseCount(c.getUseCount() == null ? 1L : c.getUseCount() + 1);
            caseRepository.save(c);
        });

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
                .projectId(referenceRecord.getProjectId())
                .scoreItemId(referenceRecord.getScoreItemId())
                .reuseMode(referenceRecord.getReuseMode())
                .build();
    }
}
