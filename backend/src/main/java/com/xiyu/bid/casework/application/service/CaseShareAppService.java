package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.dto.CaseShareRecordCreateRequest;
import com.xiyu.bid.casework.dto.CaseShareRecordDTO;
import com.xiyu.bid.casework.entity.CaseShareRecord;
import com.xiyu.bid.casework.repository.CaseShareRecordRepository;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.CaseRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CaseShareAppService {

    private final CaseRepository caseRepository;
    private final CaseShareRecordRepository caseShareRecordRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public java.util.List<CaseShareRecordDTO> getShareRecords(Long caseId) {
        requireCase(caseId);
        return caseShareRecordRepository.findByCaseIdOrderByCreatedAtDesc(caseId).stream().map(this::toDTO).toList();
    }

    public CaseShareRecordDTO createShareRecord(Long caseId, CaseShareRecordCreateRequest request) {
        requireCase(caseId);
        String token = UUID.randomUUID().toString().replace("-", "");
        String baseUrl = request.getBaseUrl().trim().replaceAll("/+$", "");
        CaseShareRecord shareRecord = CaseShareRecord.builder()
                .caseId(caseId)
                .token(token)
                .url(baseUrl + "/knowledge/case/detail?id=" + caseId + "&share=" + token)
                .createdBy(request.getCreatedBy())
                .createdByName(resolveDisplayName(request.getCreatedBy(), request.getCreatedByName()))
                .expiresAt(request.getExpiresAt())
                .build();
        return toDTO(caseShareRecordRepository.save(shareRecord));
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

    private CaseShareRecordDTO toDTO(CaseShareRecord shareRecord) {
        return CaseShareRecordDTO.builder()
                .id(shareRecord.getId())
                .caseId(shareRecord.getCaseId())
                .token(shareRecord.getToken())
                .url(shareRecord.getUrl())
                .createdBy(shareRecord.getCreatedBy())
                .createdByName(shareRecord.getCreatedByName())
                .expiresAt(shareRecord.getExpiresAt())
                .createdAt(shareRecord.getCreatedAt())
                .build();
    }
}
