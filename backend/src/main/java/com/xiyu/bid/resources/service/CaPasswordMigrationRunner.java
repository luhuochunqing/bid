package com.xiyu.bid.resources.service;

import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.resources.entity.CaCertificateEntity;
import com.xiyu.bid.resources.repository.CaCertificateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;

/**
 * 一次性迁移：将历史明文 CA 密码加密为 AES-256-GCM 密文。
 * <p>
 * 纯核心：不涉及业务规则，仅做数据转换。
 * 副作用：更新 ca_certificates.ca_password 列。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(100)
public class CaPasswordMigrationRunner implements ApplicationRunner {

    private final CaCertificateRepository certificateRepository;
    private final PasswordEncryptionUtil passwordEncryptionUtil;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<CaCertificateEntity> all = certificateRepository.findAll();
        int migrated = 0;
        for (CaCertificateEntity cert : all) {
            String pwd = cert.getCaPassword();
            if (pwd == null || pwd.isEmpty()) continue;
            if (isLikelyEncrypted(pwd)) continue;

            try {
                cert.setCaPassword(passwordEncryptionUtil.encrypt(pwd));
                certificateRepository.save(cert);
                migrated++;
            } catch (RuntimeException e) {
                log.warn("Failed to encrypt CA password for certificate id={}, skipping", cert.getId(), e);
            }
        }
        if (migrated > 0) {
            log.info("CA password migration completed: encrypted {} plain-text passwords", migrated);
        }
    }

    /**
     * 启发式判断密码是否已加密：
     * AES-256-GCM 输出为 Base64（IV + ciphertext + tag），长度通常 ≥ 40 且为合法 Base64。
     */
    private boolean isLikelyEncrypted(String value) {
        if (value.length() < 40) return false;
        try {
            Base64.getDecoder().decode(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
