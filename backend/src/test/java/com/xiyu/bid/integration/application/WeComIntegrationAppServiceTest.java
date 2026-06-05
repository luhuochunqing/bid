package com.xiyu.bid.integration.application;

import com.xiyu.bid.integration.domain.WeComConnectivityResult;
import com.xiyu.bid.integration.domain.WeComCredential;
import com.xiyu.bid.integration.dto.WeComIntegrationRequest;
import com.xiyu.bid.integration.dto.WeComIntegrationResponse;
import com.xiyu.bid.integration.dto.WeComConnectivityResponse;
import com.xiyu.bid.integration.infrastructure.persistence.entity.WeComIntegrationEntity;
import com.xiyu.bid.integration.infrastructure.persistence.repository.WeComIntegrationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeComIntegrationAppService — orchestration")
class WeComIntegrationAppServiceTest {

    @Mock
    private WeComIntegrationJpaRepository repository;

    @Mock
    private WeComCredentialCipher cipher;

    @Mock
    private WeComConnectivityProbe probe;

    @Mock
    private WeComMessagePublisher messagePublisher;

    private WeComIntegrationAppService service;

    @BeforeEach
    void setUp() {
        service = new WeComIntegrationAppService(repository, cipher, probe, messagePublisher);
    }

    // ---- GET ----

    @Test
    @DisplayName("getConfig returns empty response when no row exists")
    void getConfig_noRow_returnsEmpty() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        WeComIntegrationResponse response = service.getConfig();
        assertThat(response.configured()).isFalse();
        assertThat(response.secretConfigured()).isFalse();
    }

    @Test
    @DisplayName("getConfig never returns plain corpSecret")
    void getConfig_doesNotLeakSecret() {
        WeComIntegrationEntity entity = buildEntity("ww123", "1000001", "ENC:abc==");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        WeComIntegrationResponse response = service.getConfig();

        // corpSecret field removed from response DTO (CRITICAL-1); only secretConfigured flag is returned
        assertThat(response.secretConfigured()).isTrue();
    }

    @Test
    @DisplayName("getConfig returns corpId and agentId")
    void getConfig_returnsIdentifiers() {
        WeComIntegrationEntity entity = buildEntity("wwcorp", "1000002", "ENC:xyz==");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        WeComIntegrationResponse response = service.getConfig();
        assertThat(response.corpId()).isEqualTo("wwcorp");
        assertThat(response.agentId()).isEqualTo("1000002");
    }

    // ---- SAVE ----

    @Test
    @DisplayName("saveConfig encrypts corpSecret before persisting")
    void saveConfig_encryptsSecret() {
        WeComIntegrationRequest request = new WeComIntegrationRequest(
                "wwcorp", "1000001", "plain-secret", false, false, null);
        when(cipher.encrypt("plain-secret")).thenReturn("ENC:encrypted==");
        when(repository.findById(1L)).thenReturn(Optional.empty());
        WeComIntegrationEntity saved = buildEntity("wwcorp", "1000001", "ENC:encrypted==");
        when(repository.save(any())).thenReturn(saved);

        service.saveConfig(request, "admin");

        ArgumentCaptor<WeComIntegrationEntity> captor = ArgumentCaptor.forClass(WeComIntegrationEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedSecret()).isEqualTo("ENC:encrypted==");
    }

    @Test
    @DisplayName("saveConfig upserts at fixed id=1")
    void saveConfig_usesFixedId() {
        WeComIntegrationRequest request = new WeComIntegrationRequest(
                "wwcorp", "1000001", "plain-secret", false, false, null);
        when(cipher.encrypt(anyString())).thenReturn("ENC:x");
        when(repository.findById(1L)).thenReturn(Optional.empty());
        WeComIntegrationEntity saved = buildEntity("wwcorp", "1000001", "ENC:x");
        when(repository.save(any())).thenReturn(saved);

        service.saveConfig(request, "admin");

        ArgumentCaptor<WeComIntegrationEntity> captor = ArgumentCaptor.forClass(WeComIntegrationEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("saveConfig rejects invalid credential")
    void saveConfig_invalidCredential_throws() {
        WeComIntegrationRequest request = new WeComIntegrationRequest(
                "", "not-numeric", "", false, false, null);
        assertThatThrownBy(() -> service.saveConfig(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- TEST CONNECTIVITY ----

    @Test
    @DisplayName("testConnectivity calls probe with decrypted secret")
    void testConnectivity_callsProbeWithDecryptedSecret() {
        WeComIntegrationEntity entity = buildEntity("wwcorp", "1000001", "ENC:abc==");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(cipher.decrypt("ENC:abc==")).thenReturn("plain-secret");
        WeComConnectivityResult result = new WeComConnectivityResult(true, "OK", LocalDateTime.now());
        when(probe.probe("wwcorp", "1000001", "plain-secret")).thenReturn(result);

        WeComConnectivityResponse response = service.testConnectivity();

        verify(probe).probe("wwcorp", "1000001", "plain-secret");
        assertThat(response.success()).isTrue();
    }

    @Test
    @DisplayName("testConnectivity throws when no config exists")
    void testConnectivity_noConfig_throws() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.testConnectivity())
                .isInstanceOf(IllegalStateException.class);
    }

    private WeComIntegrationEntity buildEntity(String corpId, String agentId, String encryptedSecret) {
        WeComIntegrationEntity e = new WeComIntegrationEntity();
        e.setId(1L);
        e.setCorpId(corpId);
        e.setAgentId(agentId);
        e.setEncryptedSecret(encryptedSecret);
        e.setSsoEnabled(false);
        e.setMessageEnabled(false);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy("admin");
        return e;
    }
}
