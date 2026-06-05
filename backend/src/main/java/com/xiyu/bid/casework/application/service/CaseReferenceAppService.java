package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.dto.CaseReferenceRecordCreateRequest;
import com.xiyu.bid.casework.dto.CaseReferenceRecordDTO;
import com.xiyu.bid.casework.entity.CaseReferenceRecord;
import com.xiyu.bid.casework.repository.CaseReferenceRecordRepository;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.CaseRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CaseReferenceAppService {

    private final CaseRepository caseRepository;
    private final CaseReferenceRecordRepository caseReferenceRecordRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public java.util.List<CaseReferenceRecordDTO> getReferenceRecords(Long caseId) {
        requireCase(caseId);
        return caseReferenceRecordRepository.findByCaseIdOrderByReferencedAtDesc(caseId).stream().map(this::toDTO).toList();
    }

    public CaseReferenceRecordDTO createReferenceRecord(Long caseId, CaseReferenceRecordCreateRequest request) {
        Case caseStudy = requireCase(caseId);
        CaseReferenceRecord referenceRecord = CaseReferenceRecord.builder()
                .caseId(caseId)
                .referencedBy(request.getReferencedBy())
                .referencedByName(resolveDisplayName(request.getReferencedBy(), request.getReferencedByName()))
                .referenceTarget(request.getReferenceTarget().trim())
                .referenceContext(trimToNull(request.getReferenceContext()))
                .build();
        CaseReferenceRecord saved = caseReferenceRecordRepository.save(referenceRecord);
        caseStudy.setUseCount((caseStudy.getUseCount() == null ? 0L : caseStudy.getUseCount()) + 1);
        caseRepository.save(caseStudy);
        return toDTO(saved);
    }

    private Case requireCase(Long caseId) {
        return caseRepository.findById(caseId).orElseThrow(() -> new ResourceNotFoundException("Case", caseId.toString()));
    }

    private String resolveDisplayName(Long userId, String fallback) {
        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
                return user.getFullName();
            }
        }
        return fallback != null && !fallback.isBlank() ? fallback.trim() : "未命名用户";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
