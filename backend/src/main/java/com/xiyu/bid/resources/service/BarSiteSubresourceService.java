// Input: resources repositories, DTOs, and support services
// Output: Bar Site Subresource business service operations
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.resources.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.resources.dto.BarAssetResponseDTO;
import com.xiyu.bid.resources.dto.BarSiteAccountDTO;
import com.xiyu.bid.resources.dto.BarSiteAccountRequest;
import com.xiyu.bid.resources.dto.BarSiteAttachmentCreateRequest;
import com.xiyu.bid.resources.dto.BarSiteAttachmentDTO;
import com.xiyu.bid.resources.dto.BarSiteSopRequest;
import com.xiyu.bid.resources.dto.BarSiteStatusUpdateRequest;
import com.xiyu.bid.resources.dto.BarSiteVerificationDTO;
import com.xiyu.bid.resources.dto.BarSiteVerificationRequest;
import com.xiyu.bid.resources.dto.ResourceResponseMapper;
import com.xiyu.bid.resources.entity.BarAsset;
import com.xiyu.bid.resources.entity.BarSiteAccount;
import com.xiyu.bid.resources.entity.BarSiteAttachment;
import com.xiyu.bid.resources.entity.BarSiteSop;
import com.xiyu.bid.resources.entity.BarSiteVerification;
import com.xiyu.bid.resources.repository.BarAssetRepository;
import com.xiyu.bid.resources.repository.BarSiteAccountRepository;
import com.xiyu.bid.resources.repository.BarSiteAttachmentRepository;
import com.xiyu.bid.resources.repository.BarSiteSopRepository;
import com.xiyu.bid.resources.repository.BarSiteVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BarSiteSubresourceService {

    private final BarAssetRepository barAssetRepository;
    private final BarSiteAccountRepository barSiteAccountRepository;
    private final BarSiteAttachmentRepository barSiteAttachmentRepository;
    private final BarSiteSopRepository barSiteSopRepository;
    private final BarSiteVerificationRepository barSiteVerificationRepository;
    private final ObjectMapper objectMapper;

    public List<BarSiteAccountDTO> getAccounts(Long assetId) {
        ensureAssetExists(assetId);
        return barSiteAccountRepository.findByBarAssetIdOrderByCreatedAtAsc(assetId).stream()
                .map(ResourceResponseMapper::toDto)
                .toList();
    }

    @Transactional
    public BarSiteAccountDTO createAccount(Long assetId, BarSiteAccountRequest request) {
        ensureAssetExists(assetId);
        return ResourceResponseMapper.toDto(barSiteAccountRepository.save(BarSiteAccount.builder()
                .barAssetId(assetId)
                .username(request.getUsername())
                .role(request.getRole())
                .owner(request.getOwner())
                .phone(request.getPhone())
                .email(request.getEmail())
                .status(normalizeAccountStatus(request.getStatus()))
                .build()));
    }

    @Transactional
    public BarSiteAccountDTO updateAccount(Long assetId, Long accountId, BarSiteAccountRequest request) {
        BarSiteAccount account = getAccount(assetId, accountId);
        account.setUsername(request.getUsername());
        account.setRole(request.getRole());
        account.setOwner(request.getOwner());
        account.setPhone(request.getPhone());
        account.setEmail(request.getEmail());
        account.setStatus(normalizeAccountStatus(request.getStatus()));
        return ResourceResponseMapper.toDto(barSiteAccountRepository.save(account));
    }

    @Transactional
    public void deleteAccount(Long assetId, Long accountId) {
        barSiteAccountRepository.delete(getAccount(assetId, accountId));
    }

    @Transactional
    public BarAssetResponseDTO updateStatus(Long assetId, BarSiteStatusUpdateRequest request) {
        BarAsset asset = getAsset(assetId);
        asset.setStatus(parseAssetStatus(request.getStatus()));
        return ResourceResponseMapper.toDto(barAssetRepository.save(asset));
    }

    @Transactional
    public BarSiteVerificationDTO verify(Long assetId, BarSiteVerificationRequest request) {
        ensureAssetExists(assetId);
        String verifiedBy = isBlank(request.getVerifiedBy()) ? "system" : request.getVerifiedBy().trim();
        String status = isBlank(request.getStatus()) ? "SUCCESS" : request.getStatus().trim().toUpperCase();
        String message = isBlank(request.getMessage()) ? "站点连通性校验通过" : request.getMessage().trim();
        return ResourceResponseMapper.toDto(barSiteVerificationRepository.save(BarSiteVerification.builder()
                .barAssetId(assetId)
                .verifiedBy(verifiedBy)
                .status(status)
                .message(message)
                .verifiedAt(LocalDateTime.now())
                .build()));
    }

    public List<BarSiteVerificationDTO> getVerificationRecords(Long assetId) {
        ensureAssetExists(assetId);
        return barSiteVerificationRepository.findByBarAssetIdOrderByVerifiedAtDesc(assetId).stream()
                .map(ResourceResponseMapper::toDto)
                .toList();
    }

    public BarSiteSopRequest getSop(Long assetId) {
        ensureAssetExists(assetId);
        return barSiteSopRepository.findByBarAssetId(assetId)
                .map(this::toSopRequest)
                .orElseGet(BarSiteSopRequest::new);
    }

    @Transactional
    public BarSiteSopRequest upsertSop(Long assetId, BarSiteSopRequest request) {
        ensureAssetExists(assetId);
        BarSiteSop sop = barSiteSopRepository.findByBarAssetId(assetId)
                .orElseGet(() -> BarSiteSop.builder().barAssetId(assetId).build());
        sop.setResetUrl(request.getResetUrl());
        sop.setUnlockUrl(request.getUnlockUrl());
        sop.setEstimatedTime(request.getEstimatedTime());
        sop.setContactsJson(writeJson(request.getContacts()));
        sop.setRequiredDocsJson(writeJson(request.getRequiredDocs()));
        sop.setFaqsJson(writeJson(request.getFaqs()));
        sop.setHistoryJson(writeJson(request.getHistory()));
        BarSiteSop saved = barSiteSopRepository.save(sop);
        return toSopRequest(saved);
    }

    public List<BarSiteAttachmentDTO> getAttachments(Long assetId) {
        ensureAssetExists(assetId);
        return barSiteAttachmentRepository.findByBarAssetIdOrderByUploadedAtDesc(assetId).stream()
                .map(ResourceResponseMapper::toDto)
                .toList();
    }

    @Transactional
    public BarSiteAttachmentDTO createAttachment(Long assetId, BarSiteAttachmentCreateRequest request) {
        ensureAssetExists(assetId);
        return ResourceResponseMapper.toDto(barSiteAttachmentRepository.save(BarSiteAttachment.builder()
                .barAssetId(assetId)
                .name(request.getName())
                .size(request.getSize())
                .contentType(request.getContentType())
                .url(request.getUrl())
                .uploadedBy(isBlank(request.getUploadedBy()) ? "system" : request.getUploadedBy().trim())
                .uploadedAt(LocalDateTime.now())
                .build()));
    }

    @Transactional
    public void deleteAttachment(Long assetId, Long attachmentId) {
        BarSiteAttachment attachment = barSiteAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("BarSiteAttachment", String.valueOf(attachmentId)));
        if (!attachment.getBarAssetId().equals(assetId)) {
            throw new IllegalArgumentException("Attachment does not belong to the specified asset");
        }
        barSiteAttachmentRepository.delete(attachment);
    }

    private BarAsset getAsset(Long assetId) {
        return barAssetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("BarAsset", String.valueOf(assetId)));
    }

    private void ensureAssetExists(Long assetId) {
        getAsset(assetId);
    }

    private BarSiteAccount getAccount(Long assetId, Long accountId) {
        ensureAssetExists(assetId);
        BarSiteAccount account = barSiteAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("BarSiteAccount", String.valueOf(accountId)));
        if (!account.getBarAssetId().equals(assetId)) {
            throw new IllegalArgumentException("Account does not belong to the specified asset");
        }
        return account;
    }

    private BarAsset.AssetStatus parseAssetStatus(String status) {
        if (isBlank(status)) {
            throw new IllegalArgumentException("状态不能为空");
        }
        String normalized = status.trim().toUpperCase();
        return switch (normalized) {
            case "ACTIVE", "AVAILABLE" -> BarAsset.AssetStatus.AVAILABLE;
            case "INACTIVE", "MAINTENANCE" -> BarAsset.AssetStatus.MAINTENANCE;
            case "IN_USE" -> BarAsset.AssetStatus.IN_USE;
            case "RETIRED" -> BarAsset.AssetStatus.RETIRED;
            case "DISPOSED" -> BarAsset.AssetStatus.DISPOSED;
            default -> throw new IllegalArgumentException("不支持的站点状态: " + status);
        };
    }

    private String normalizeAccountStatus(String status) {
        return isBlank(status) ? "active" : status.trim().toLowerCase();
    }

    private BarSiteSopRequest toSopRequest(BarSiteSop sop) {
        BarSiteSopRequest dto = new BarSiteSopRequest();
        dto.setResetUrl(sop.getResetUrl());
        dto.setUnlockUrl(sop.getUnlockUrl());
        dto.setEstimatedTime(sop.getEstimatedTime());
        dto.setContacts(readJson(sop.getContactsJson(), new TypeReference<List<String>>() {}, new ArrayList<>()));
        dto.setRequiredDocs(readJson(sop.getRequiredDocsJson(), new TypeReference<List<BarSiteSopRequest.RequiredDocItem>>() {}, new ArrayList<>()));
        dto.setFaqs(readJson(sop.getFaqsJson(), new TypeReference<List<BarSiteSopRequest.FaqItem>>() {}, new ArrayList<>()));
        dto.setHistory(readJson(sop.getHistoryJson(), new TypeReference<List<BarSiteSopRequest.HistoryItem>>() {}, new ArrayList<>()));
        return dto;
    }

    private <T> T readJson(String raw, TypeReference<T> typeReference, T defaultValue) {
        if (isBlank(raw)) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(raw, typeReference);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse BAR site JSON payload");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize BAR site JSON payload");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
