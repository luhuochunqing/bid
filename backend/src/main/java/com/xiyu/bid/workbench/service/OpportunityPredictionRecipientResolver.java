// Input: 本地用户身份缓存 + OSS 批量实时岗位/角色结果
// Output: 一轮商机预测扫描共用的实时在职用户与核心角色接收人快照
// Pos: workbench/service - 外部组织目录与通知用户 ID 的组合边界
// 维护声明: 若 OSS 人员状态、角色码或本地工号关联规则变化，请同步更新本类及对应测试。
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.organization.application.OrganizationDirectoryGateway;
import com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.workbench.domain.OpportunityPredictionRecipientPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 以本地工号枚举身份，再以 OSS 实时响应决定商机预测通知接收人。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpportunityPredictionRecipientResolver {

    private static final Set<String> TARGET_ROLE_CODES = Set.of(
            RoleProfileCatalog.BID_ADMIN_CODE,
            RoleProfileCatalog.BID_LEAD_CODE);

    private final UserRepository userRepository;
    private final OrganizationDirectoryGateway directoryGateway;

    /** 每轮扫描只调用一次批量接口；OSS 未明确返回的人员失败关闭。 */
    public RecipientSnapshot resolve() {
        Map<String, User> userByJobNumber = loadLocalIdentities();
        if (userByJobNumber.isEmpty()) {
            return RecipientSnapshot.empty();
        }
        List<String> jobNumbers = List.copyOf(userByJobNumber.keySet());
        Map<String, OssUserJobAndRoleDto> liveByJobNumber;
        try {
            liveByJobNumber = normalizeLookup(directoryGateway.getUserJobAndRoleListByJobNumbersStrict(jobNumbers));
        } catch (RuntimeException ex) {
            log.error("[商机预测提醒] OSS recipient lookup failed: requested={}", jobNumbers.size(), ex);
            return new RecipientSnapshot(Set.of(), List.of(), jobNumbers.size(), 0);
        }
        int returnedRequested = (int) userByJobNumber.keySet().stream()
                .filter(liveByJobNumber::containsKey)
                .count();
        int missing = jobNumbers.size() - returnedRequested;
        int unexpected = liveByJobNumber.size() - returnedRequested;
        if (missing > 0 || unexpected > 0) {
            log.warn("[商机预测提醒] OSS recipient lookup incomplete: requested={} returned={} missing={} unexpected={}",
                    jobNumbers.size(), returnedRequested, missing, unexpected);
        }

        Set<Long> activeUserIds = new LinkedHashSet<>();
        List<Long> roleRecipientIds = new ArrayList<>();
        for (Map.Entry<String, User> identity : userByJobNumber.entrySet()) {
            OssUserJobAndRoleDto live = liveByJobNumber.get(identity.getKey());
            if (!OpportunityPredictionRecipientPolicy.isActive(live)) {
                continue;
            }
            Long userId = identity.getValue().getId();
            activeUserIds.add(userId);
            if (OpportunityPredictionRecipientPolicy.hasAnyTargetRole(live, TARGET_ROLE_CODES)) {
                roleRecipientIds.add(userId);
            }
        }
        log.info("[商机预测提醒] OSS recipients resolved: requested={} returned={} active={} roles={}",
                jobNumbers.size(), returnedRequested, activeUserIds.size(), roleRecipientIds.size());
        return new RecipientSnapshot(activeUserIds, roleRecipientIds,
                jobNumbers.size(), returnedRequested);
    }

    private Map<String, User> loadLocalIdentities() {
        List<User> users = new ArrayList<>(userRepository.findAll());
        users.sort(Comparator.comparing(User::getId, Comparator.nullsLast(Long::compareTo)));
        Map<String, User> result = new LinkedHashMap<>();
        for (User user : users) {
            String jobNumber = normalize(user.getEmployeeNumber());
            if (!jobNumber.isEmpty() && user.getId() != null) {
                result.putIfAbsent(jobNumber, user);
            }
        }
        return result;
    }

    private Map<String, OssUserJobAndRoleDto> normalizeLookup(Map<String, OssUserJobAndRoleDto> lookup) {
        if (lookup == null || lookup.isEmpty()) {
            return Map.of();
        }
        Map<String, OssUserJobAndRoleDto> result = new LinkedHashMap<>();
        lookup.forEach((key, value) -> {
            String responseJobNumber = value == null ? "" : normalize(value.jobNumber());
            String jobNumber = responseJobNumber.isEmpty() ? normalize(key) : responseJobNumber;
            if (!jobNumber.isEmpty() && value != null) {
                result.putIfAbsent(jobNumber, value);
            }
        });
        return result;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** 一轮扫描内不可变的实时接收人事实。 */
    public record RecipientSnapshot(Set<Long> activeUserIds, List<Long> roleRecipientIds,
                                    int requestedJobNumbers, int returnedJobNumbers) {
        public RecipientSnapshot {
            activeUserIds = Set.copyOf(activeUserIds);
            roleRecipientIds = List.copyOf(roleRecipientIds);
        }

        public static RecipientSnapshot empty() {
            return new RecipientSnapshot(Set.of(), List.of(), 0, 0);
        }

        /** 核心角色在前，实时在职项目经理追加并去重。 */
        public List<Long> recipientsFor(Long managerId) {
            Set<Long> recipients = new LinkedHashSet<>(roleRecipientIds);
            if (managerId != null && activeUserIds.contains(managerId)) {
                recipients.add(managerId);
            }
            return List.copyOf(recipients);
        }
    }
}
