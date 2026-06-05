// Input: CreateMentionRequest, mentioner id; collaborates with NotificationApplicationService + MentionRepository
// Output: MentionResult value capturing count + (optional) notification id
// Pos: Service/提及编排（parse + dispatch + persist，Split-First，<200 行、<5 依赖）
package com.xiyu.bid.mention.service;

import com.xiyu.bid.mention.core.MentionParsingPolicy;
import com.xiyu.bid.mention.core.MentionParsingPolicy.ParsedContent;
import com.xiyu.bid.mention.dto.CreateMentionRequest;
import com.xiyu.bid.mention.entity.Mention;
import com.xiyu.bid.mention.repository.MentionRepository;
import com.xiyu.bid.notification.core.NotificationDispatchPolicy.DispatchResult;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the @-mention flow.
 *
 * <p>Pure parsing lives in {@link MentionParsingPolicy}; dispatch is delegated
 * to {@link NotificationApplicationService} so there is no parallel
 * notification path. This class only assembles inputs, forwards decisions as
 * values and persists mention audit rows when dispatch succeeds.
 */
@Service
@Transactional(readOnly = true)
public class MentionApplicationService {

    private static final String NOTIFICATION_TYPE = "MENTION";
    private static final String DEFAULT_TITLE = "@ 提及";

    private final MentionRepository mentionRepository;
    private final NotificationApplicationService notificationService;

    public MentionApplicationService(
        MentionRepository mentionRepository,
        NotificationApplicationService notificationService
    ) {
        this.mentionRepository = mentionRepository;
        this.notificationService = notificationService;
    }

    public record MentionResult(int mentionCount, Long notificationId) {

        public static MentionResult noop() {
            return new MentionResult(0, null);
        }
    }

    @Transactional
    public MentionResult createMention(CreateMentionRequest request, Long mentionerUserId) {
        if (!MentionParsingPolicy.isAllowedSourceType(request.sourceEntityType())) {
            return MentionResult.noop();
        }
        ParsedContent parsed = MentionParsingPolicy.parse(request.content());
        List<Long> recipients = filterRecipients(parsed.mentionedUserIds(), mentionerUserId);
        if (recipients.isEmpty()) {
            return MentionResult.noop();
        }

        CreateNotificationRequest notificationRequest = new CreateNotificationRequest(
            NOTIFICATION_TYPE,
            request.sourceEntityType(),
            request.sourceEntityId(),
            resolveTitle(request.title()),
            parsed.plainText(),
            null,
            recipients
        );
        DispatchResult dispatch =
            notificationService.createNotification(notificationRequest, mentionerUserId);
        if (!dispatch.isValid() || dispatch.notificationId() == null) {
            return MentionResult.noop();
        }

        persistMentions(recipients, mentionerUserId, request, dispatch.notificationId());
        return new MentionResult(recipients.size(), dispatch.notificationId());
    }

    private static List<Long> filterRecipients(List<Long> ids, Long mentionerUserId) {
        List<Long> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            if (id != null && !id.equals(mentionerUserId)) {
                out.add(id);
            }
        }
        return out;
    }

    private static String resolveTitle(String raw) {
        return (raw == null || raw.isBlank()) ? DEFAULT_TITLE : raw;
    }

    private void persistMentions(List<Long> recipients, Long mentionerUserId,
                                 CreateMentionRequest request, Long notificationId) {
        List<Mention> rows = new ArrayList<>(recipients.size());
        for (Long mentionedId : recipients) {
            rows.add(Mention.builder()
                .notificationId(notificationId)
                .mentionerUserId(mentionerUserId)
                .mentionedUserId(mentionedId)
                .sourceEntityType(request.sourceEntityType())
                .sourceEntityId(request.sourceEntityId())
                .build());
        }
        mentionRepository.saveAll(rows);
    }
}
