package com.xiyu.bid.integration.application;

import com.xiyu.bid.integration.domain.ValidationResult;
import com.xiyu.bid.integration.domain.WeComConnectivityResult;
import com.xiyu.bid.integration.domain.WeComCredential;
import com.xiyu.bid.integration.domain.WeComCredentialValidation;
import com.xiyu.bid.integration.domain.WeComSendResult;
import com.xiyu.bid.integration.dto.WeComConnectivityResponse;
import com.xiyu.bid.integration.dto.WeComIntegrationRequest;
import com.xiyu.bid.integration.dto.WeComIntegrationResponse;
import com.xiyu.bid.integration.dto.WeComSendTestResponse;
import com.xiyu.bid.integration.infrastructure.persistence.entity.WeComIntegrationEntity;
import com.xiyu.bid.integration.infrastructure.persistence.repository.WeComIntegrationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Orchestration service for WeCom integration.
 * Responsibilities: validate → cipher → persist → probe → send.
 * Does NOT contain rule logic or DTO conversion (controller owns that).
 */
@Service
@RequiredArgsConstructor
public class WeComIntegrationAppService {

    private static final long SINGLETON_ID = 1L;

    private final WeComIntegrationJpaRepository repository;
    private final WeComCredentialCipher cipher;
    private final WeComConnectivityProbe connectivityProbe;
    private final WeComMessagePublisher messagePublisher;

    @Transactional(readOnly = true)
    public WeComIntegrationResponse getConfig() {
        Optional<WeComIntegrationEntity> entity = repository.findById(SINGLETON_ID);
        if (entity.isEmpty()) {
            return WeComIntegrationResponse.empty();
        }
        WeComIntegrationEntity e = entity.get();
        return WeComIntegrationResponse.configured(
                e.getCorpId(), e.getAgentId(), e.isSsoEnabled(), e.isMessageEnabled(), e.getNotifyUserIds());
    }

    @Transactional
    public WeComIntegrationResponse saveConfig(WeComIntegrationRequest request, String operator) {
        WeComCredential credential = new WeComCredential(
                request.corpId(), request.agentId(), request.corpSecret(),
                request.ssoEnabled(), request.messageEnabled());

        ValidationResult validation = WeComCredentialValidation.validate(credential);
        if (!validation.valid()) {
            throw new IllegalArgumentException(String.join("; ", validation.errors()));
        }

        String encryptedSecret = cipher.encrypt(credential.corpSecret());

        WeComIntegrationEntity entity = repository.findById(SINGLETON_ID)
                .orElseGet(WeComIntegrationEntity::new);
        entity.setId(SINGLETON_ID);
        entity.setCorpId(credential.corpId());
        entity.setAgentId(credential.agentId());
        entity.setEncryptedSecret(encryptedSecret);
        entity.setSsoEnabled(credential.ssoEnabled());
        entity.setMessageEnabled(credential.messageEnabled());
        entity.setNotifyUserIds(request.notifyUserIds());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(operator);

        WeComIntegrationEntity saved = repository.save(entity);
        return WeComIntegrationResponse.configured(
                saved.getCorpId(), saved.getAgentId(),
                saved.isSsoEnabled(), saved.isMessageEnabled(), saved.getNotifyUserIds());
    }

    @Transactional(readOnly = true)
    public WeComConnectivityResponse testConnectivity() {
        WeComIntegrationEntity entity = repository.findById(SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("企业微信集成尚未配置，请先保存配置后再测试连通性"));

        String plainSecret = cipher.decrypt(entity.getEncryptedSecret());
        WeComConnectivityResult result = connectivityProbe.probe(
                entity.getCorpId(), entity.getAgentId(), plainSecret);

        return new WeComConnectivityResponse(result.success(), result.message(), result.probedAt());
    }

    @Transactional(readOnly = true)
    public WeComSendTestResponse sendTestMessage(String overrideContent) {
        WeComIntegrationEntity entity = repository.findById(SINGLETON_ID)
                .orElseThrow(() -> new IllegalArgumentException("企业微信集成尚未配置，请先保存配置后再发送测试消息"));

        String plainSecret = cipher.decrypt(entity.getEncryptedSecret());

        String content = (overrideContent != null && !overrideContent.isBlank())
                ? overrideContent
                : "西域数智化投标管理平台连接测试 · " + LocalDateTime.now().toString().replace("T", " ").substring(0, 19);

        List<String> toUserIds = parseUserIds(entity.getNotifyUserIds());
        if (toUserIds.isEmpty()) {
            throw new IllegalArgumentException("未配置通知接收人 (notifyUserIds)，请先配置后再发送测试消息");
        }

        WeComSendResult result = messagePublisher.sendTextMessage(
                entity.getCorpId(), entity.getAgentId(), plainSecret, toUserIds, content);

        return new WeComSendTestResponse(
                result.success(), result.errcode(), result.errmsg(), result.sentTo(), LocalDateTime.now());
    }

    private List<String> parseUserIds(String notifyUserIds) {
        if (notifyUserIds == null || notifyUserIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(notifyUserIds.split("[,|]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
