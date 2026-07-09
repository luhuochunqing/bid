// Input: CreateMentionRequest, mentioner id; collaborates with NotificationApplicationService + MentionRepository + UserRepository
// Output: MentionResult value capturing count + (optional) notification id
// Pos: Service/提及编排（parse + dispatch + persist，Split-First，<200 行、<5 依赖）
package com.xiyu.bid.mention.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.mention.core.MentionParsingPolicy;
import com.xiyu.bid.mention.core.MentionParsingPolicy.ParsedContent;
import com.xiyu.bid.mention.dto.CreateMentionRequest;
import com.xiyu.bid.mention.entity.Mention;
import com.xiyu.bid.mention.repository.MentionRepository;
import com.xiyu.bid.notification.core.DispatchResult;
import com.xiyu.bid.notification.core.NotificationMessagePolicy;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the @-mention flow.
 *
 * <p>Pure parsing lives in {@link MentionParsingPolicy}; message title/body/payload
 * live in {@link NotificationMessagePolicy}; dispatch is delegated
 * to {@link NotificationApplicationService} so there is no parallel
 * notification path. This class only assembles inputs, forwards decisions as
 * values and persists mention audit rows when dispatch succeeds.
 */
@Service
@Transactional(readOnly = true)
public class MentionApplicationService {

    private final MentionRepository mentionRepository;
    private final NotificationApplicationService notificationService;
    private final UserRepository userRepository;
    private final NotificationRecipientResolver recipientResolver;

    public MentionApplicationService(
        MentionRepository mentionRepository,
        NotificationApplicationService notificationService,
        UserRepository userRepository,
        NotificationRecipientResolver recipientResolver
    ) {
        this.mentionRepository = mentionRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.recipientResolver = recipientResolver;
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

        Long projectId = resolveProjectId(request.payload());
        if (projectId != null) {
            recipients = recipientResolver.filterByProjectAccess(recipients, projectId);
            if (recipients.isEmpty()) {
                return MentionResult.noop();
            }
        }

        String projectName = resolveProjectName(request.payload());
        String mentionerName = resolveMentionerName(mentionerUserId);
        String scene = resolveScene(request.sourceEntityType());
        String targetUrl = resolveTargetUrl(request.sourceEntityType(), request.sourceEntityId(), request.payload());

        NotificationMessagePolicy.NotificationMessage message =
                NotificationMessagePolicy.forMention(
                        projectName, mentionerName, scene,
                        request.sourceEntityType(), request.sourceEntityId(), targetUrl);

        Map<String, Object> payload = mergePayload(request.payload(), message.payload(), parsed.plainText());

        CreateNotificationRequest notificationRequest = new CreateNotificationRequest(
            message.type(),
            request.sourceEntityType(),
            request.sourceEntityId(),
            message.title(),
            message.body(),
            payload,
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

    private String resolveMentionerName(Long mentionerUserId) {
        if (mentionerUserId == null) {
            return "";
        }
        return userRepository.findById(mentionerUserId)
                .map(User::getFullName)
                .orElse("");
    }

    private static String resolveScene(String sourceEntityType) {
        if ("TASK".equalsIgnoreCase(sourceEntityType)) {
            return "任务评论";
        }
        return sourceEntityType;
    }

    private static String resolveProjectName(Map<String, Object> payload) {
        if (payload == null) {
            return "";
        }
        Object value = payload.get("projectName");
        return value != null ? value.toString() : "";
    }

    private static Long resolveProjectId(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get("projectId");
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String resolveTargetUrl(String sourceEntityType, Long sourceEntityId, Map<String, Object> payload) {
        if (payload == null) {
            return "";
        }
        if ("TASK".equalsIgnoreCase(sourceEntityType)) {
            Object projectId = payload.get("projectId");
            Object taskId = payload.get("sourceEntityId");
            if (taskId == null) {
                taskId = sourceEntityId;
            }
            if (projectId != null && taskId != null) {
                return "/project/" + projectId + "/drafting?taskId=" + taskId;
            }
            return "";
        }
        Object targetUrl = payload.get("targetUrl");
        return targetUrl != null ? targetUrl.toString() : "";
    }

    private static Map<String, Object> mergePayload(Map<String, Object> requestPayload,
                                                    Map<String, Object> messagePayload,
                                                    String plainText) {
        Map<String, Object> payload = new HashMap<>();
        if (requestPayload != null) {
            payload.putAll(requestPayload);
        }
        if (messagePayload != null) {
            payload.putAll(messagePayload);
        }
        payload.put("projectName", messagePayload.get("projectName"));
        if (!payload.containsKey("targetUrl") || payload.get("targetUrl") == null) {
            payload.put("targetUrl", messagePayload.get("targetUrl"));
        }
        Object projectId = requestPayload != null ? requestPayload.get("projectId") : null;
        if (projectId != null) {
            payload.put("projectId", projectId);
        }
        payload.put("plainText", plainText);
        return payload;
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
