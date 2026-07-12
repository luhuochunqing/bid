package com.xiyu.bid.webhook.application;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OperatorUsernameResolver 单元测试（CO-571 Phase B）。
 * <p>覆盖 resolveDeliveryUsername 的解析顺序：creatorId → projectManagerId → eventOperatorId。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OperatorUsernameResolver — CO-571 Phase B 投递 username 解析")
class OperatorUsernameResolverTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private OperatorUsernameResolver resolver;

    private User user(String username) {
        User u = new User();
        u.setUsername(username);
        return u;
    }

    private Tender tender(Long creatorId, Long pmId) {
        Tender t = new Tender();
        t.setCreatorId(creatorId);
        t.setProjectManagerId(pmId);
        return t;
    }

    @Test
    @DisplayName("delivery_prefersCreatorOverEventOperator：creator 有 username → 用 creator（不查 event）")
    void delivery_prefersCreatorOverEventOperator() {
        when(userRepository.findById(100L)).thenReturn(Optional.of(user("creator-user")));

        String result = resolver.resolveDeliveryUsername(tender(100L, null), 200L);

        assertThat(result).isEqualTo("creator-user");
        // creator 命中后不应再查 event operator（避免 N+1）
        verify(userRepository, never()).findById(200L);
    }

    @Test
    @DisplayName("delivery_fallsBackToPm：creator 为空/查不到 → fallback 到 PM")
    void delivery_fallsBackToPm() {
        when(userRepository.findById(100L)).thenReturn(Optional.empty());
        when(userRepository.findById(300L)).thenReturn(Optional.of(user("pm-user")));

        String result = resolver.resolveDeliveryUsername(tender(100L, 300L), 200L);

        assertThat(result).isEqualTo("pm-user");
    }

    @Test
    @DisplayName("delivery_fallsBackToEvent：creator 和 PM 都 miss → fallback 到 event operator")
    void delivery_fallsBackToEvent() {
        when(userRepository.findById(200L)).thenReturn(Optional.of(user("event-user")));

        String result = resolver.resolveDeliveryUsername(tender(null, null), 200L);

        assertThat(result).isEqualTo("event-user");
    }

    @Test
    @DisplayName("delivery_allMiss_returnsNull：三个 ID 全 miss → 返回 null")
    void delivery_allMiss_returnsNull() {
        when(userRepository.findById(100L)).thenReturn(Optional.empty());
        when(userRepository.findById(300L)).thenReturn(Optional.empty());
        when(userRepository.findById(200L)).thenReturn(Optional.empty());

        String result = resolver.resolveDeliveryUsername(tender(100L, 300L), 200L);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("delivery_creatorNull_pmNull_eventNull → 返回 null（不做 DB 查询）")
    void delivery_allNull_returnsNullWithoutDbQuery() {
        String result = resolver.resolveDeliveryUsername(tender(null, null), null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("delivery_tenderNull → 仅用 eventOperatorId")
    void delivery_tenderNull_usesEventOnly() {
        when(userRepository.findById(200L)).thenReturn(Optional.of(user("event-user")));

        String result = resolver.resolveDeliveryUsername(null, 200L);

        assertThat(result).isEqualTo("event-user");
    }

    @Test
    @DisplayName("resolve(null) → null（不查 DB）")
    void resolve_nullId_returnsNull() {
        assertThat(resolver.resolve(null)).isNull();
    }

    @Test
    @DisplayName("resolve(查不到) → null")
    void resolve_notFound_returnsNull() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(999L)).isNull();
    }

    // ── resolveForCrmLookup（CO-277 第 6 次修复）── projectManagerId → creatorId → fallbackUserId ──

    @Test
    @DisplayName("crmLookup_prefersPmOverCreator：PM 和 creator 都有 username → 用 PM（不查 creator）")
    void crmLookup_prefersPmOverCreator() {
        when(userRepository.findById(300L)).thenReturn(Optional.of(user("pm-user")));

        String result = resolver.resolveForCrmLookup(tender(100L, 300L), 200L);

        assertThat(result).isEqualTo("pm-user");
        // PM 命中后不应再查 creator（避免 N+1）
        verify(userRepository, never()).findById(100L);
    }

    @Test
    @DisplayName("crmLookup_fallsBackToCreator：PM 为空/查不到 → fallback 到 creator")
    void crmLookup_fallsBackToCreator() {
        when(userRepository.findById(100L)).thenReturn(Optional.of(user("creator-user")));

        String result = resolver.resolveForCrmLookup(tender(100L, null), 200L);

        assertThat(result).isEqualTo("creator-user");
    }

    @Test
    @DisplayName("crmLookup_fallsBackToUserId：PM 和 creator 都 miss → fallback 到 userId（admin）")
    void crmLookup_fallsBackToUserId() {
        when(userRepository.findById(200L)).thenReturn(Optional.of(user("admin")));

        String result = resolver.resolveForCrmLookup(tender(null, null), 200L);

        assertThat(result).isEqualTo("admin");
    }

    @Test
    @DisplayName("crmLookup_pmNull_creatorNull_userIdNull → 返回 null（不做 DB 查询）")
    void crmLookup_allNull_returnsNullWithoutDbQuery() {
        String result = resolver.resolveForCrmLookup(tender(null, null), null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("crmLookup_tenderNull → 仅用 fallbackUserId")
    void crmLookup_tenderNull_usesFallbackOnly() {
        when(userRepository.findById(200L)).thenReturn(Optional.of(user("admin")));

        String result = resolver.resolveForCrmLookup(null, 200L);

        assertThat(result).isEqualTo("admin");
    }

    @Test
    @DisplayName("crmLookup_adminCreator_pmOssUser → 用 PM（核心场景：admin 无 OSS token，PM 有）")
    void crmLookup_adminCreator_pmOssUser_prefersPm() {
        // 生产场景：creatorId=1(admin, 无OSS token), projectManagerId=5181(08152, OSS用户有OSS token)
        when(userRepository.findById(5181L)).thenReturn(Optional.of(user("08152")));

        String result = resolver.resolveForCrmLookup(tender(1L, 5181L), 1L);

        // 必须返回 PM 的 username，不能返回 admin
        assertThat(result).isEqualTo("08152");
        verify(userRepository, never()).findById(1L);
    }
}
