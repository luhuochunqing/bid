// Input: mocked SubscriptionRepository interactions
// Output: SubscriptionApplicationService orchestration coverage
// Pos: Test/订阅应用服务单元测试
package com.xiyu.bid.subscription.service;

import com.xiyu.bid.subscription.dto.SubscriptionSummary;
import com.xiyu.bid.subscription.entity.Subscription;
import com.xiyu.bid.subscription.repository.SubscriptionRepository;
import com.xiyu.bid.subscription.service.SubscriptionApplicationService.SubscribeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionApplicationService orchestration")
class SubscriptionApplicationServiceTest {

    @Mock
    private SubscriptionRepository repository;

    @InjectMocks
    private SubscriptionApplicationService service;

    private Subscription persisted;

    @BeforeEach
    void setUp() {
        persisted = Subscription.builder()
            .id(100L)
            .userId(7L)
            .targetEntityType("PROJECT")
            .targetEntityId(42L)
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("subscribe valid saves entity and returns success")
    void subscribe_Valid_SavesAndReturnsSuccess() {
        when(repository.findByUserIdAndTargetEntityTypeAndTargetEntityId(7L, "PROJECT", 42L))
            .thenReturn(Optional.empty());
        when(repository.save(any(Subscription.class))).thenReturn(persisted);

        SubscribeResult result = service.subscribe(7L, "PROJECT", 42L);

        assertThat(result.success()).isTrue();
        assertThat(result.subscriptionId()).isEqualTo(100L);
        verify(repository).save(any(Subscription.class));
    }

    @Test
    @DisplayName("subscribe invalid entityType returns policy error without saving")
    void subscribe_InvalidEntityType_ReturnsPolicyError() {
        SubscribeResult result = service.subscribe(7L, "FOOBAR", 42L);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_ENTITY_TYPE");
        verify(repository, never()).save(any(Subscription.class));
    }

    @Test
    @DisplayName("subscribe when already exists is idempotent and does not duplicate")
    void subscribe_Existing_IsIdempotent() {
        when(repository.findByUserIdAndTargetEntityTypeAndTargetEntityId(7L, "PROJECT", 42L))
            .thenReturn(Optional.of(persisted));

        SubscribeResult result = service.subscribe(7L, "PROJECT", 42L);

        assertThat(result.success()).isTrue();
        assertThat(result.subscriptionId()).isEqualTo(100L);
        verify(repository, never()).save(any(Subscription.class));
    }

    @Test
    @DisplayName("unsubscribe existing deletes and returns affected count")
    void unsubscribe_Existing_ReturnsCount() {
        when(repository.deleteByUserIdAndTarget(7L, "PROJECT", 42L)).thenReturn(1);

        int affected = service.unsubscribe(7L, "PROJECT", 42L);

        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("unsubscribe non-existent is idempotent success with count zero")
    void unsubscribe_Missing_IsIdempotentZero() {
        when(repository.deleteByUserIdAndTarget(7L, "PROJECT", 42L)).thenReturn(0);

        int affected = service.unsubscribe(7L, "PROJECT", 42L);

        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("isSubscribed delegates to repository existsBy...")
    void isSubscribed_DelegatesToRepo() {
        when(repository.existsByUserIdAndTargetEntityTypeAndTargetEntityId(7L, "PROJECT", 42L))
            .thenReturn(true);

        assertThat(service.isSubscribed(7L, "PROJECT", 42L)).isTrue();
    }

    @Test
    @DisplayName("listMySubscriptions returns paged summaries")
    void listMySubscriptions_ReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Subscription> page = new PageImpl<>(List.of(persisted));
        when(repository.findByUserIdOrderByCreatedAtDesc(7L, pageable)).thenReturn(page);

        Page<SubscriptionSummary> result = service.listMySubscriptions(7L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).targetEntityType()).isEqualTo("PROJECT");
        assertThat(result.getContent().get(0).targetEntityId()).isEqualTo(42L);
    }
}
