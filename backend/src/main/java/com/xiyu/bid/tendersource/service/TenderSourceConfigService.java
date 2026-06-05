package com.xiyu.bid.tendersource.service;

import com.xiyu.bid.entity.AuditLog;
import com.xiyu.bid.platform.util.PasswordEncryptionUtil;
import com.xiyu.bid.repository.AuditLogRepository;
import com.xiyu.bid.tendersource.entity.TenderSourceConfig;
import com.xiyu.bid.tendersource.repository.TenderSourceConfigRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 标讯源配置服务。
 * 提供配置的获取与保存能力，API 密钥使用 AES 加密存储。
 * 单例模式：id 始终为 1。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderSourceConfigService {

    private static final Long SINGLETON_ID = 1L;

    private final TenderSourceConfigRepository repository;
    private final PasswordEncryptionUtil encryptionUtil;
    private final AuditLogRepository auditLogRepository;
    private final EntityManager entityManager;

    /**
     * 获取当前配置（含解密后的 API 密钥）。
     */
    @Transactional(readOnly = true)
    public TenderSourceConfig getConfig() {
        return repository.findById(SINGLETON_ID)
                .orElse(null);
    }

    /**
     * 保存配置。
     * API 密钥字段在入库前自动加密。
     *
     * @param config     配置实体（可能包含明文 apiKey）
     * @param operatorId 操作人用户 ID
     * @param ipAddress  操作人 IP
     * @return 保存后的配置（密钥字段保持加密）
     */
    @Transactional
    public TenderSourceConfig saveConfig(TenderSourceConfig config, String operatorId, String ipAddress) {
        TenderSourceConfig existing = repository.findById(SINGLETON_ID).orElse(null);

        // 记录变更审计日志
        if (existing != null) {
            logConfigChanges(existing, config, operatorId, ipAddress);
        }

        config.setId(SINGLETON_ID);
        config.setUpdatedBy(operatorId);

        // 加密 API 密钥（如果提供了新的明文密钥，由 controller 写入 config.apiKeyEncrypted 传入）
        if (config.getApiKeyEncrypted() != null && !config.getApiKeyEncrypted().isBlank()) {
            String rawKey = config.getApiKeyEncrypted();
            boolean isAlreadyEncrypted = rawKey.startsWith("ENC(");
            config.setApiKeyEncrypted(isAlreadyEncrypted ? rawKey : encryptionUtil.encrypt(rawKey));
        } else if (existing != null) {
            // 未提供新密钥，保留现有加密值
            config.setApiKeyEncrypted(existing.getApiKeyEncrypted());
        }

        TenderSourceConfig saved = repository.save(config);
        log.info("Tender source config saved by operator: {}", operatorId);
        return saved;
    }

    /**
     * 解密 API 密钥（用于测试连接等场景）。
     */
    public String decryptApiKey(String encryptedApiKey) {
        if (encryptedApiKey == null || encryptedApiKey.isBlank()) {
            return null;
        }
        return encryptionUtil.decrypt(encryptedApiKey);
    }

    /**
     * 从配置中提取明文 API 密钥。
     */
    private String extractRawApiKey(TenderSourceConfig config) {
        // 如果实体有一个 transient 的 apiKey 字段，在此提取
        // 当前通过字段直接传递，调用方传入明文
        return null; // 由 controller 层处理明文密钥的传递
    }

    private void logConfigChanges(TenderSourceConfig oldConfig, TenderSourceConfig newConfig,
                                   String operatorId, String ipAddress) {
        try {
            String oldVal = summarizeConfig(oldConfig);
            String newVal = summarizeConfig(newConfig);
            AuditLog logEntry = new AuditLog();
            logEntry.setAction("UPDATE");
            logEntry.setEntityType("TENDER_SOURCE_CONFIG");
            logEntry.setEntityId("1");
            logEntry.setDescription("标讯源配置更新");
            logEntry.setOldValue(oldVal);
            logEntry.setNewValue(newVal);
            logEntry.setUserId(operatorId);
            logEntry.setIpAddress(ipAddress);
            logEntry.setTimestamp(LocalDateTime.now());
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to log config change audit: {}", e.getMessage());
        }
    }

    private String summarizeConfig(TenderSourceConfig config) {
        return String.format(
                "platforms=%s, endpoint=%s, keywords=%s, regions=%s, budget=[%s-%s], autoSync=%s, syncInterval=%s",
                config.getPlatformsJson(),
                maskValue(config.getApiEndpoint()),
                config.getKeywords(),
                config.getRegionsJson(),
                config.getBudgetMin(),
                config.getBudgetMax(),
                config.getAutoSync(),
                config.getSyncIntervalMinutes()
        );
    }

    private String maskValue(String value) {
        if (value == null || value.length() < 4) {
            return value;
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}
