package com.xiyu.bid.resources.service;

import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.resources.domain.CaCertificateImportPolicy.ParsedCaRow;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class CaCertificateImportRowPersister {

    private final CaCertificateRepository caRepo;
    private final UserRepository userRepository;
    private final PasswordEncryptionUtil passwordEncryptionUtil;

    /**
     * INSERT-only: 每行创建一个新的 CaCertificate。
     * CO-566: 关联平台为纯文本，直接写入 related_platforms 列，不再反查平台账号。
     */
    @Transactional
    public void persist(ParsedCaRow row) {
        // Resolve custodian ID by name
        Long custodianId = 0L;
        var users = userRepository.findByFullName(row.custodianName());
        if (!users.isEmpty()) {
            custodianId = users.get(0).getId();
        }

        // Encrypt password if provided (CO-566: 密码非必填)
        String encryptedPassword = null;
        if (row.caPassword() != null && !row.caPassword().isBlank()) {
            encryptedPassword = passwordEncryptionUtil.encrypt(row.caPassword());
        }

        // Determine status based on expiry date
        String status = "ACTIVE";
        if (row.expiryDate() != null) {
            LocalDate now = LocalDate.now();
            if (row.expiryDate().isBefore(now)) {
                status = "EXPIRED";
            } else if (row.expiryDate().isBefore(now.plusMonths(1))) {
                status = "EXPIRING";
            }
        }

        CaCertificateEntity entity = CaCertificateEntity.builder()
                .caType(row.caType())
                .sealType(row.sealType())
                .holderName(row.holderName())
                .custodianId(custodianId)
                .custodianName(row.custodianName())
                .expiryDate(row.expiryDate())
                .issuer(row.issuer())
                .electronicAccount(row.electronicAccount())
                .caPassword(encryptedPassword)
                .caPlatformUrl(row.caPlatformUrl())
                .relatedPlatforms(row.relatedPlatforms())
                .borrowStatus("IN_STOCK")
                .status(status)
                .remarks(row.remarks())
                .build();

        caRepo.save(entity);
    }
}
