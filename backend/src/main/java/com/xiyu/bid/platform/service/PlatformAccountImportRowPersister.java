package com.xiyu.bid.platform.service;

import com.xiyu.bid.platform.domain.PlatformAccountImportPolicy.ParsedAccountRow;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PlatformAccountImportRowPersister {

    private final PlatformAccountRepository accountRepo;
    private final PasswordEncryptionUtil passwordEncryptionUtil;

    /**
     * INSERT-only: 每行创建一个新的 PlatformAccount。
     * 不执行 upsert（账户和 CA 无自然去重键）。
     */
    @Transactional
    public void persist(ParsedAccountRow row) {
        String encryptedPassword = passwordEncryptionUtil.encrypt(row.password());

        PlatformAccount account = PlatformAccount.builder()
                .accountName(row.accountName())
                .url(row.url())
                .username(row.username())
                .password(encryptedPassword)
                .platformType(row.platformType())
                .contactPerson(row.contactPerson())
                .contactPhone(row.contactPhone())
                .contactEmail(row.contactEmail())
                .hasCa(row.hasCa() != null ? row.hasCa() : false)
                .remarks(row.remarks())
                .build();

        accountRepo.save(account);
    }
}
