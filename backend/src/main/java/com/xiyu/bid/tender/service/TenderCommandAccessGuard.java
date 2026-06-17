package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.core.TenderEditPermissionPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenderCommandAccessGuard {

    private final UserRepository userRepository;

    /**
     * 校验用户是否有权更新该标讯。
     * 规则计算委托给 {@link TenderEditPermissionPolicy}，本类仅做编排与异常抛出。
     */
    public void assertCanUpdateTender(Tender tender, Long userId) {
        User user = resolveUser(userId);
        if (!TenderEditPermissionPolicy.canEdit(
                user.getRoleCode(),
                user.getRole(),
                userId,
                tender.getCreatorId(),
                tender.getProjectManagerId(),
                tender.getStatus())) {
            throw new AccessDeniedException("当前用户无权编辑该标讯");
        }
    }

    /**
     * 校验用户是否有权删除该标讯。
     * 规则计算委托给 {@link TenderEditPermissionPolicy}，本类仅做编排与异常抛出。
     */
    public void assertCanDeleteTender(Tender tender, Long userId) {
        User user = resolveUser(userId);
        if (!TenderEditPermissionPolicy.canDelete(
                user.getRoleCode(),
                user.getRole(),
                userId,
                tender.getCreatorId(),
                tender.getProjectManagerId(),
                tender.getStatus())) {
            throw new AccessDeniedException("当前用户无权删除该标讯");
        }
    }

    private User resolveUser(Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("无法识别当前用户");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("无法识别当前用户"));
    }
}
