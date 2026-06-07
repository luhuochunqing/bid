// Input: daily job trigger
// Output: keyword matching execution, match log persistence, and aggregated notification dispatch
// Pos: Service/标讯关键词匹配执行应用服务
package com.xiyu.bid.tenderkeyword.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tenderkeyword.domain.KeywordMatchPolicy;
import com.xiyu.bid.tenderkeyword.entity.TenderKeywordMatchLog;
import com.xiyu.bid.tenderkeyword.entity.TenderKeywordSubscription;
import com.xiyu.bid.tenderkeyword.entity.TenderKeywordSubscriptionKeyword;
import com.xiyu.bid.tenderkeyword.repository.TenderKeywordMatchLogRepository;
import com.xiyu.bid.tenderkeyword.repository.TenderKeywordSubscriptionKeywordRepository;
import com.xiyu.bid.tenderkeyword.repository.TenderKeywordSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class TenderKeywordMatchService {

    private static final Logger log = LoggerFactory.getLogger(TenderKeywordMatchService.class);

    private final TenderKeywordSubscriptionRepository subscriptionRepository;
    private final TenderKeywordSubscriptionKeywordRepository keywordRepository;
    private final TenderKeywordMatchLogRepository matchLogRepository;
    private final TenderRepository tenderRepository;
    private final NotificationApplicationService notificationService;
    private final ObjectMapper objectMapper;

    public TenderKeywordMatchService(
            TenderKeywordSubscriptionRepository subscriptionRepository,
            TenderKeywordSubscriptionKeywordRepository keywordRepository,
            TenderKeywordMatchLogRepository matchLogRepository,
            TenderRepository tenderRepository,
            NotificationApplicationService notificationService,
            ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.keywordRepository = keywordRepository;
        this.matchLogRepository = matchLogRepository;
        this.tenderRepository = tenderRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    public MatchJobResult executeMatching() {
        log.info("开始执行标讯关键词匹配任务");
        List<TenderKeywordSubscription> activeSubscriptions = subscriptionRepository.findAllActive();
        if (activeSubscriptions.isEmpty()) {
            log.info("没有启用中的关键词订阅，跳过匹配");
            return new MatchJobResult(0, 0, 0);
        }

        int totalSubscriptions = activeSubscriptions.size();
        int totalMatched = 0;
        int totalNotifications = 0;

        Map<Long, List<TenderKeywordSubscription>> subsByUser = activeSubscriptions.stream()
                .collect(Collectors.groupingBy(TenderKeywordSubscription::getUserId));

        for (Map.Entry<Long, List<TenderKeywordSubscription>> entry : subsByUser.entrySet()) {
            Long userId = entry.getKey();
            List<TenderKeywordSubscription> userSubs = entry.getValue();

            List<MatchNotificationItem> allMatches = new ArrayList<>();
            for (TenderKeywordSubscription sub : userSubs) {
                List<MatchNotificationItem> matches = matchForSubscription(sub);
                allMatches.addAll(matches);
                totalMatched += matches.size();
            }

            if (!allMatches.isEmpty()) {
                sendAggregatedNotification(userId, userSubs, allMatches);
                totalNotifications++;
            }
        }

        log.info("标讯关键词匹配任务完成: subscriptions={}, matched={}, notifications={}",
                totalSubscriptions, totalMatched, totalNotifications);
        return new MatchJobResult(totalSubscriptions, totalMatched, totalNotifications);
    }

    private List<MatchNotificationItem> matchForSubscription(TenderKeywordSubscription sub) {
        List<TenderKeywordSubscriptionKeyword> keywords = keywordRepository.findBySubscriptionId(sub.getId());
        if (keywords.isEmpty()) {
            return List.of();
        }

        List<String> keywordTexts = keywords.stream()
                .map(TenderKeywordSubscriptionKeyword::getKeyword)
                .toList();

        LocalDateTime since = sub.getLastMatchedAt() != null
                ? sub.getLastMatchedAt() : LocalDateTime.of(2020, 1, 1, 0, 0);
        List<Tender> newTenders = tenderRepository.findTendersCreatedAfter(since);
        if (newTenders.isEmpty()) {
            return List.of();
        }

        List<MatchNotificationItem> matchedItems = new ArrayList<>();
        for (Tender tender : newTenders) {
            String searchText = tender.getSearchTextNormalized() != null
                    ? tender.getSearchTextNormalized()
                    : buildFallbackSearchText(tender);

            var result = KeywordMatchPolicy.evaluate(searchText, keywordTexts, sub.getLogicOperator());
            if (!result.matched() || matchLogRepository.existsByTenderIdAndSubscriptionId(tender.getId(), sub.getId())) {
                continue;
            }

            String matchedKeywordsJson = serializeMatchedKeywords(result.matchedKeywords());
            TenderKeywordMatchLog matchLog = TenderKeywordMatchLog.builder()
                    .subscriptionId(sub.getId()).userId(sub.getUserId())
                    .tenderId(tender.getId()).tenderTitle(tender.getTitle())
                    .matchedKeywords(matchedKeywordsJson).notified(false)
                    .build();
            matchLogRepository.save(matchLog);

            matchedItems.add(new MatchNotificationItem(
                    sub.getId(), tender.getId(), tender.getTitle(), matchedKeywordsJson));
        }

        sub.setLastMatchedAt(LocalDateTime.now());
        subscriptionRepository.save(sub);

        return matchedItems;
    }

    private void sendAggregatedNotification(
            Long userId, List<TenderKeywordSubscription> userSubs, List<MatchNotificationItem> matches) {

        int totalMatches = matches.size();
        long uniqueTenders = matches.stream().map(MatchNotificationItem::tenderId).distinct().count();
        long activeCount = userSubs.stream().filter(s -> "ACTIVE".equals(s.getStatus())).count();

        String title = String.format("标讯关键词匹配提醒：今日有 %d 条新标讯匹配你的订阅", totalMatches);

        StringBuilder body = new StringBuilder();
        body.append("你共设置了 ").append(userSubs.size()).append(" 个关键词订阅，当前启用 ")
                .append(activeCount).append(" 个。\n\n");

        Map<Long, List<MatchNotificationItem>> bySub = matches.stream()
                .collect(Collectors.groupingBy(MatchNotificationItem::subscriptionId));

        for (Map.Entry<Long, List<MatchNotificationItem>> entry : bySub.entrySet()) {
            Long subId = entry.getKey();
            List<MatchNotificationItem> items = entry.getValue();
            String subName = userSubs.stream().filter(s -> s.getId().equals(subId))
                    .map(TenderKeywordSubscription::getName).findFirst().orElse("未命名订阅");
            body.append("【").append(subName).append("】匹配 ").append(items.size()).append(" 条标讯：\n");
            items.stream().limit(5).forEach(item ->
                    body.append("  - ").append(item.tenderTitle()).append("\n"));
            if (items.size() > 5) {
                body.append("  ... 还有 ").append(items.size() - 5).append(" 条\n");
            }
            body.append("\n");
        }

        body.append("共 ").append(totalMatches).append(" 条匹配，涉及 ").append(uniqueTenders).append(" 条标讯。");

        CreateNotificationRequest notifyRequest = new CreateNotificationRequest(
                NotificationType.TENDER_MATCH.name(), "TENDER_KEYWORD_SUBSCRIPTION", null,
                title, body.toString(),
                Map.of("matchCount", totalMatches, "subscriptionCount", activeCount),
                List.of(userId));

        var dispatchResult = notificationService.createNotification(notifyRequest, 0L);
        if (!dispatchResult.isValid()) {
            log.warn("发送标讯关键词匹配通知失败: userId={}, error={}", userId, dispatchResult.errorMessage());
        }

        for (MatchNotificationItem item : matches) {
            matchLogRepository.markAsNotified(item.subscriptionId(), LocalDateTime.now());
        }
    }

    private String serializeMatchedKeywords(List<String> keywords) {
        try {
            return objectMapper.writeValueAsString(keywords);
        } catch (JsonProcessingException e) {
            log.warn("序列化匹配关键词失败: {}", e.getMessage());
            return "[]";
        }
    }

    private String buildFallbackSearchText(Tender tender) {
        return String.join(" ",
                nullToBlank(tender.getTitle()), nullToBlank(tender.getDescription()),
                nullToBlank(tender.getPurchaserName()), nullToBlank(tender.getTags()),
                nullToBlank(tender.getRegion()), nullToBlank(tender.getIndustry()),
                nullToBlank(tender.getBidNotice())).toLowerCase();
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    public record MatchJobResult(int subscriptionsProcessed, int totalMatched, int notificationsSent) {}

    private record MatchNotificationItem(Long subscriptionId, Long tenderId,
                                         String tenderTitle, String matchedKeywords) {}
}
