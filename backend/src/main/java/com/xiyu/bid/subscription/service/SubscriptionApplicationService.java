// Input: subscribe/unsubscribe requests, authenticated user id, paging
// Output: SubscribeResult values, idempotent writes, paged summaries
// Pos: Service/订阅应用服务（Split-First 编排层）
package com.xiyu.bid.subscription.service;

import com.xiyu.bid.subscription.core.SubscriptionPolicy;
import com.xiyu.bid.subscription.core.SubscriptionPolicy.ValidationResult;
import com.xiyu.bid.subscription.dto.SubscriptionSummary;
import com.xiyu.bid.subscription.entity.Subscription;
import com.xiyu.bid.subscription.repository.SubscriptionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Orchestrates subscribe / unsubscribe flows.
 *
 * <p>Pure validation lives in {@link SubscriptionPolicy}. This class only
 * loads/saves and forwards decisions as values.
 */
@Service
@Transactional(readOnly = true)
public class SubscriptionApplicationService {

    private final SubscriptionRepository repository;

    public SubscriptionApplicationService(SubscriptionRepository repository) {
        this.repository = repository;
    }

    public record SubscribeResult(boolean success, String errorCode, String errorMessage, Long subscriptionId) {

        public static SubscribeResult ok(Long id) {
            return new SubscribeResult(true, null, null, id);
        }

        public static SubscribeResult error(String errorCode, String errorMessage) {
            return new SubscribeResult(false, errorCode, errorMessage, null);
        }
    }

    @Transactional
    public SubscribeResult subscribe(Long userId, String entityType, Long entityId) {
        ValidationResult result = SubscriptionPolicy.validate(userId, entityType, entityId);
        if (!result.isValid()) {
            return SubscribeResult.error(result.errorCode(), result.errorMessage());
        }
        Optional<Subscription> existing =
            repository.findByUserIdAndTargetEntityTypeAndTargetEntityId(userId, entityType, entityId);
        if (existing.isPresent()) {
            return SubscribeResult.ok(existing.get().getId());
        }
        Subscription toPersist = Subscription.builder()
            .userId(userId)
            .targetEntityType(entityType)
            .targetEntityId(entityId)
            .build();
        try {
            return SubscribeResult.ok(repository.save(toPersist).getId());
        } catch (DataIntegrityViolationException concurrentInsert) {
            return repository
                .findByUserIdAndTargetEntityTypeAndTargetEntityId(userId, entityType, entityId)
                .map(s -> SubscribeResult.ok(s.getId()))
                .orElseThrow(() -> concurrentInsert);
        }
    }

    @Transactional
    public int unsubscribe(Long userId, String entityType, Long entityId) {
        return repository.deleteByUserIdAndTarget(userId, entityType, entityId);
    }

    public boolean isSubscribed(Long userId, String entityType, Long entityId) {
        return repository.existsByUserIdAndTargetEntityTypeAndTargetEntityId(userId, entityType, entityId);
    }

    public Page<SubscriptionSummary> listMySubscriptions(Long userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(s -> new SubscriptionSummary(
                s.getId(), s.getTargetEntityType(), s.getTargetEntityId(), s.getCreatedAt()));
    }
}
