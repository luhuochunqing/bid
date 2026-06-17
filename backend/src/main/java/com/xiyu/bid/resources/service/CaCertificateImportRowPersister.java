package com.xiyu.bid.resources.service;

import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.resources.domain.CaCertificateImportPolicy.ParsedCaRow;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.entity.CaCertificatePlatformEntity;
import com.xiyu.bid.resources.repository.CaCertificatePlatformRepository;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class CaCertificateImportRowPersister {

    private final CaCertificateRepository caRepo;
    private final CaCertificatePlatformRepository platformLinkRepo;
    private final PlatformAccountRepository platformAccountRepo;
    private final UserRepository userRepository;
    private final PasswordEncryptionUtil passwordEncryptionUtil;

    /**
     * INSERT-only: 每行创建一个新的 CaCertificate。
     * 关联平台通过名称查找。
     */
    @Transactional
    public void persist(ParsedCaRow row) {
        // Resolve custodian ID by name
        Long custodianId = 0L;
        var users = userRepository.findByFullName(row.custodianName());
        if (!users.isEmpty()) {
            custodianId = users.get(0).getId();
        }

        // Encrypt password if provided
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
                .borrowStatus("IN_STOCK")
                .status(status)
                .remarks(row.remarks())
                .build();

        final CaCertificateEntity saved = caRepo.save(entity);

        // Link to platform accounts
        if (!row.platformNames().isEmpty()) {
            for (String platformName : row.platformNames()) {
                platformAccountRepo.findByAccountName(platformName).ifPresent(pa -> {
                    CaCertificatePlatformEntity link = CaCertificatePlatformEntity.builder()
                            .caCertificateId(saved.getId())
                            .platformAccountId(pa.getId())
                            .build();
                    platformLinkRepo.save(link);
                });
            }
        }
    }
}
