package com.xiyu.bid.platform.service;

import com.xiyu.bid.platform.domain.PlatformAccountImportPolicy.ParsedAccountRow;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.repository.PlatformAccountRepository;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PlatformAccountImportRowPersister {

    private final PlatformAccountRepository accountRepo;
    private final PasswordEncryptionUtil passwordEncryptionUtil;

    /**
     * INSERT-only: 每行创建一个新的 PlatformAccount。
     * 不执行 upsert（账户和 CA 无自然去重键）。
     *
     * <p><b>CO-560 事务传播改为 REQUIRES_NEW</b>：每行独立事务，一行失败只回滚该行，
     * 不影响其他行。根因：原 REQUIRED 传播下，单行 DB 异常（如字段超长）会把外层事务
     * 标记为 rollback-only，Hibernate Session 中毒（null id 实体残留），后续行全部抛
     * AssertionFailure + UnexpectedRollbackException。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(ParsedAccountRow row, Long contactPersonId) {
        // CO-567: 平台密码非必填，空则存 NULL（表示无密码）。
        String rawPassword = row.password();
        String encryptedPassword = (rawPassword == null || rawPassword.isBlank())
                ? null
                : passwordEncryptionUtil.encrypt(rawPassword);

        PlatformAccount account = PlatformAccount.builder()
                .accountName(row.accountName())
                .url(row.url())
                .username(row.username())
                .password(encryptedPassword)
                .contactPerson(contactPersonId)
                .registrant(row.registrant())
                .registerPhone(row.registerPhone())
                .registerEmail(row.registerEmail())
                .hasCa(row.hasCa() != null ? row.hasCa() : false)
                .remarks(row.remarks())
                .build();

        accountRepo.save(account);
    }
}
