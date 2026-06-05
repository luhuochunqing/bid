package com.xiyu.bid.resources.dto;

import com.xiyu.bid.resources.entity.Account;
import com.xiyu.bid.resources.entity.BarAsset;
import com.xiyu.bid.resources.entity.BarCertificate;
import com.xiyu.bid.resources.entity.BarCertificateBorrowRecord;
import com.xiyu.bid.resources.entity.BarSiteAccount;
import com.xiyu.bid.resources.entity.BarSiteAttachment;
import com.xiyu.bid.resources.entity.BarSiteVerification;
import com.xiyu.bid.resources.entity.Expense;
import com.xiyu.bid.resources.entity.ExpenseApprovalRecord;
import com.xiyu.bid.resources.entity.ExpensePaymentRecord;

public final class ResourceResponseMapper {

    private ResourceResponseMapper() {
    }

    public static AccountResponseDTO toDto(Account account) {
        return AccountResponseDTO.builder()
            .id(account.getId())
            .name(account.getName())
            .type(AccountResponseDTO.AccountType.valueOf(account.getType().name()))
            .contactInfo(account.getContactInfo())
            .industry(account.getIndustry())
            .region(account.getRegion())
            .creditLevel(AccountResponseDTO.CreditLevel.valueOf(account.getCreditLevel().name()))
            .createdAt(account.getCreatedAt())
            .updatedAt(account.getUpdatedAt())
            .build();
    }

    public static BarAssetResponseDTO toDto(BarAsset asset) {
        return BarAssetResponseDTO.builder()
            .id(asset.getId())
            .name(asset.getName())
            .type(BarAssetResponseDTO.AssetType.valueOf(asset.getType().name()))
            .value(asset.getValue())
            .status(BarAssetResponseDTO.AssetStatus.valueOf(asset.getStatus().name()))
            .acquireDate(asset.getAcquireDate())
            .remark(asset.getRemark())
            .createdAt(asset.getCreatedAt())
            .updatedAt(asset.getUpdatedAt())
            .build();
    }

    public static BarCertificateResponseDTO toDto(BarCertificate certificate) {
        return BarCertificateResponseDTO.builder()
            .id(certificate.getId())
            .barAssetId(certificate.getBarAssetId())
            .type(certificate.getType())
            .provider(certificate.getProvider())
            .serialNo(certificate.getSerialNo())
            .holder(certificate.getHolder())
            .location(certificate.getLocation())
            .expiryDate(certificate.getExpiryDate())
            .status(BarCertificateResponseDTO.CertificateStatus.valueOf(certificate.getStatus().name()))
            .currentBorrower(certificate.getCurrentBorrower())
            .currentProjectId(certificate.getCurrentProjectId())
            .borrowPurpose(certificate.getBorrowPurpose())
            .expectedReturnDate(certificate.getExpectedReturnDate())
            .remark(certificate.getRemark())
            .createdAt(certificate.getCreatedAt())
            .updatedAt(certificate.getUpdatedAt())
            .build();
    }

    public static BarCertificateBorrowRecordDTO toDto(BarCertificateBorrowRecord record) {
        return BarCertificateBorrowRecordDTO.builder()
            .id(record.getId())
            .certificateId(record.getCertificateId())
            .borrower(record.getBorrower())
            .projectId(record.getProjectId())
            .purpose(record.getPurpose())
            .remark(record.getRemark())
            .borrowedAt(record.getBorrowedAt())
            .expectedReturnDate(record.getExpectedReturnDate())
            .returnedAt(record.getReturnedAt())
            .status(BarCertificateBorrowRecordDTO.BorrowStatus.valueOf(record.getStatus().name()))
            .build();
    }

    public static BarSiteAccountDTO toDto(BarSiteAccount account) {
        return BarSiteAccountDTO.builder()
            .id(account.getId())
            .barAssetId(account.getBarAssetId())
            .username(account.getUsername())
            .role(account.getRole())
            .owner(account.getOwner())
            .phone(account.getPhone())
            .email(account.getEmail())
            .status(account.getStatus())
            .createdAt(account.getCreatedAt())
            .updatedAt(account.getUpdatedAt())
            .build();
    }

    public static BarSiteAttachmentDTO toDto(BarSiteAttachment attachment) {
        return BarSiteAttachmentDTO.builder()
            .id(attachment.getId())
            .barAssetId(attachment.getBarAssetId())
            .name(attachment.getName())
            .size(attachment.getSize())
            .contentType(attachment.getContentType())
            .url(attachment.getUrl())
            .uploadedBy(attachment.getUploadedBy())
            .uploadedAt(attachment.getUploadedAt())
            .build();
    }

    public static BarSiteVerificationDTO toDto(BarSiteVerification verification) {
        return BarSiteVerificationDTO.builder()
            .id(verification.getId())
            .barAssetId(verification.getBarAssetId())
            .verifiedBy(verification.getVerifiedBy())
            .verifiedAt(verification.getVerifiedAt())
            .status(verification.getStatus())
            .message(verification.getMessage())
            .build();
    }

    public static ExpenseDTO toDto(Expense expense) {
        return toDto(expense, null);
    }

    public static ExpenseDTO toDto(Expense expense, ExpensePaymentRecord paymentRecord) {
        return ExpenseDTO.builder()
            .id(expense.getId())
            .projectId(expense.getProjectId())
            .category(ExpenseDTO.ExpenseCategory.valueOf(expense.getCategory().name()))
            .expenseType(expense.getExpenseType())
            .amount(expense.getAmount())
            .date(expense.getDate())
            .description(expense.getDescription())
            .createdBy(expense.getCreatedBy())
            .status(ExpenseDTO.ExpenseStatus.valueOf(expense.getStatus().name()))
            .approvalComment(expense.getApprovalComment())
            .approvedBy(expense.getApprovedBy())
            .approvedAt(expense.getApprovedAt())
            .returnRequestedAt(expense.getReturnRequestedAt())
            .returnConfirmedAt(expense.getReturnConfirmedAt())
            .expectedReturnDate(expense.getExpectedReturnDate())
            .lastReturnReminderAt(expense.getLastReturnReminderAt())
            .returnComment(expense.getReturnComment())
            .paidAt(paymentRecord != null ? paymentRecord.getPaidAt() : null)
            .paidBy(paymentRecord != null ? paymentRecord.getPaidBy() : null)
            .paymentReference(paymentRecord != null ? paymentRecord.getPaymentReference() : null)
            .paymentMethod(paymentRecord != null ? paymentRecord.getPaymentMethod() : null)
            .createdAt(expense.getCreatedAt())
            .updatedAt(expense.getUpdatedAt())
            .build();
    }

    public static ExpenseApprovalRecordDTO toDto(ExpenseApprovalRecord record) {
        return ExpenseApprovalRecordDTO.builder()
            .id(record.getId())
            .expenseId(record.getExpenseId())
            .result(ExpenseApprovalRecordDTO.ApprovalResult.valueOf(record.getResult().name()))
            .comment(record.getComment())
            .approver(record.getApprover())
            .actedAt(record.getActedAt())
            .build();
    }

    public static ExpensePaymentRecordDTO toDto(ExpensePaymentRecord record) {
        return ExpensePaymentRecordDTO.builder()
            .id(record.getId())
            .expenseId(record.getExpenseId())
            .amount(record.getAmount())
            .paidAt(record.getPaidAt())
            .paidBy(record.getPaidBy())
            .paymentReference(record.getPaymentReference())
            .paymentMethod(record.getPaymentMethod())
            .remark(record.getRemark())
            .createdAt(record.getCreatedAt())
            .build();
    }
}
