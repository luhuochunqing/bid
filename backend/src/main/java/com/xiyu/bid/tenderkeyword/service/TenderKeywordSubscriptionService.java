// Input: CRUD requests
// Output: orchestrated persistence for keyword subscriptions and match result queries
// Pos: Service/标讯关键词订阅应用服务
package com.xiyu.bid.tenderkeyword.service;

import com.xiyu.bid.tenderkeyword.domain.TenderKeywordSubscriptionPolicy;
import com.xiyu.bid.tenderkeyword.domain.TenderKeywordSubscriptionPolicy.ValidationResult;
import com.xiyu.bid.tenderkeyword.dto.CreateSubscriptionRequest;
import com.xiyu.bid.tenderkeyword.dto.MatchResultDTO;
import com.xiyu.bid.tenderkeyword.dto.SubscriptionDTO;
import com.xiyu.bid.tenderkeyword.dto.UpdateSubscriptionRequest;
import com.xiyu.bid.tenderkeyword.entity.TenderKeywordMatchLog;
import com.xiyu.bid.tenderkeyword.entity.TenderKeywordSubscription;
import com.xiyu.bid.tenderkeyword.entity.TenderKeywordSubscriptionKeyword;
import com.xiyu.bid.tenderkeyword.repository.TenderKeywordMatchLogRepository;
import com.xiyu.bid.tenderkeyword.repository.TenderKeywordSubscriptionKeywordRepository;
import com.xiyu.bid.tenderkeyword.repository.TenderKeywordSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TenderKeywordSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(TenderKeywordSubscriptionService.class);

    private final TenderKeywordSubscriptionRepository subscriptionRepository;
    private final TenderKeywordSubscriptionKeywordRepository keywordRepository;
    private final TenderKeywordMatchLogRepository matchLogRepository;

    public TenderKeywordSubscriptionService(
            TenderKeywordSubscriptionRepository subscriptionRepository,
            TenderKeywordSubscriptionKeywordRepository keywordRepository,
            TenderKeywordMatchLogRepository matchLogRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.keywordRepository = keywordRepository;
        this.matchLogRepository = matchLogRepository;
    }

    // ==================== CRUD ====================

    @Transactional
    public SubscriptionDTO create(Long userId, CreateSubscriptionRequest request) {
        ValidationResult validation = TenderKeywordSubscriptionPolicy.validateCreate(
                userId, request.name(), request.keywords(), request.logicOperator());
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.errorMessage());
        }

        String operator = request.logicOperator() != null ? request.logicOperator() : "OR";
        TenderKeywordSubscription entity = TenderKeywordSubscription.builder()
                .userId(userId).name(request.name().trim())
                .logicOperator(operator).status("ACTIVE").build();
        final TenderKeywordSubscription savedEntity = subscriptionRepository.save(entity);

        List<TenderKeywordSubscriptionKeyword> keywordEntities = request.keywords().stream()
                .filter(k -> k != null && !k.isBlank())
                .map(k -> TenderKeywordSubscriptionKeyword.builder()
                        .subscriptionId(savedEntity.getId()).keyword(k.trim()).build())
                .toList();
        keywordRepository.saveAll(keywordEntities);

        log.info("创建关键词订阅成功: id={}, userId={}, name={}, keywords={}",
                savedEntity.getId(), userId, savedEntity.getName(), request.keywords());
        return toDTO(savedEntity, keywordEntities, 0);
    }

    public Page<SubscriptionDTO> listByUser(Long userId, Pageable pageable) {
        Page<TenderKeywordSubscription> page = subscriptionRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable);
        List<SubscriptionDTO> dtos = page.getContent().stream()
                .map(this::enrichSubscriptionDTO).toList();
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    public Optional<SubscriptionDTO> getById(Long id, Long userId) {
        return subscriptionRepository.findById(id)
                .filter(s -> s.getUserId().equals(userId))
                .map(this::enrichSubscriptionDTO);
    }

    @Transactional
    public Optional<SubscriptionDTO> update(Long id, Long userId, UpdateSubscriptionRequest request) {
        return subscriptionRepository.findById(id).filter(s -> s.getUserId().equals(userId)).map(entity -> {
            if (request.name() != null) entity.setName(request.name().trim());
            if (request.logicOperator() != null) entity.setLogicOperator(request.logicOperator());
            if (request.status() != null) entity.setStatus(request.status());
            subscriptionRepository.save(entity);

            if (request.keywords() != null) {
                keywordRepository.deleteBySubscriptionId(id);
                List<TenderKeywordSubscriptionKeyword> keywordEntities = request.keywords().stream()
                        .filter(k -> k != null && !k.isBlank()).map(String::trim)
                        .map(k -> TenderKeywordSubscriptionKeyword.builder()
                                .subscriptionId(id).keyword(k).build())
                        .toList();
                keywordRepository.saveAll(keywordEntities);
            }
            log.info("更新关键词订阅成功: id={}, userId={}", id, userId);
            return enrichSubscriptionDTO(entity);
        });
    }

    @Transactional
    public boolean delete(Long id, Long userId) {
        Optional<TenderKeywordSubscription> entity = subscriptionRepository.findById(id);
        if (entity.isEmpty() || !entity.get().getUserId().equals(userId)) return false;
        keywordRepository.deleteBySubscriptionId(id);
        subscriptionRepository.delete(entity.get());
        log.info("删除关键词订阅成功: id={}, userId={}", id, userId);
        return true;
    }

    @Transactional
    public Optional<SubscriptionDTO> toggleStatus(Long id, Long userId) {
        return subscriptionRepository.findById(id).filter(s -> s.getUserId().equals(userId)).map(entity -> {
            String newStatus = "ACTIVE".equals(entity.getStatus()) ? "PAUSED" : "ACTIVE";
            entity.setStatus(newStatus);
            subscriptionRepository.save(entity);
            log.info("切换订阅状态: id={}, userId={}, newStatus={}", id, userId, newStatus);
            return enrichSubscriptionDTO(entity);
        });
    }

    // ==================== Match Results ====================

    public Page<MatchResultDTO> listMatchResults(Long userId, Pageable pageable) {
        return matchLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toMatchResultDTO);
    }

    public Page<MatchResultDTO> listMatchResultsBySubscription(Long subscriptionId, Long userId, Pageable pageable) {
        Optional<TenderKeywordSubscription> sub = subscriptionRepository.findById(subscriptionId);
        if (sub.isEmpty() || !sub.get().getUserId().equals(userId)) return Page.empty();
        String subName = sub.get().getName();
        return matchLogRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId, pageable)
                .map(log -> toMatchResultDTO(log, subName));
    }

    public long getUnreadMatchCount(Long userId) {
        return matchLogRepository.countByUserIdAndCreatedAtAfter(userId, LocalDateTime.now().minusDays(30));
    }

    // ==================== DTO Mapping ====================

    private SubscriptionDTO enrichSubscriptionDTO(TenderKeywordSubscription entity) {
        List<TenderKeywordSubscriptionKeyword> keywords = keywordRepository.findBySubscriptionId(entity.getId());
        long matchCount = matchLogRepository.countByUserIdAndCreatedAtAfter(
                entity.getUserId(), LocalDateTime.now().minusDays(30));
        return toDTO(entity, keywords, (int) matchCount);
    }

    private SubscriptionDTO toDTO(TenderKeywordSubscription entity,
                                   List<TenderKeywordSubscriptionKeyword> keywords, int matchCount) {
        return new SubscriptionDTO(entity.getId(), entity.getName(), entity.getLogicOperator(),
                entity.getStatus(), keywords.stream().map(TenderKeywordSubscriptionKeyword::getKeyword).toList(),
                matchCount, entity.getLastMatchedAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private MatchResultDTO toMatchResultDTO(TenderKeywordMatchLog log) {
        String subName = subscriptionRepository.findById(log.getSubscriptionId())
                .map(TenderKeywordSubscription::getName).orElse("未知订阅");
        return toMatchResultDTO(log, subName);
    }

    private MatchResultDTO toMatchResultDTO(TenderKeywordMatchLog log, String subscriptionName) {
        return new MatchResultDTO(log.getId(), log.getSubscriptionId(), subscriptionName,
                log.getTenderId(), log.getTenderTitle(), log.getMatchedKeywords(),
                log.getNotified(), log.getCreatedAt());
    }
}
