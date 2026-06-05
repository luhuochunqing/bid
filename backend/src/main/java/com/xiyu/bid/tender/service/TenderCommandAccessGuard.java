package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenderCommandAccessGuard {

    private final TenderProjectAccessGuard accessGuard;

    /**
     * 校验用户是否有权更新该标讯。
     * - ADMIN/MANAGER：校验项目访问权限（accessGuard）
     * - STAFF：仅可编辑自己创建的、且状态为 PENDING_ASSIGNMENT 的标讯
     */
    public void assertCanUpdateTender(Tender tender, Long userId) {
        if (isStaff()) {
            assertStaffCanUpdateTender(tender, userId);
        } else {
            accessGuard.assertCanAccessTender(tender);
        }
    }

    /**
     * 校验用户是否有权删除该标讯。
     * - ADMIN/MANAGER：校验项目访问权限 + 仅 PENDING_ASSIGNMENT 可删除
     * - STAFF：仅可删除自己创建的、且状态为 PENDING_ASSIGNMENT 的标讯
     */
    public void assertCanDeleteTender(Tender tender, Long userId) {
        if (tender.getStatus() != Tender.Status.PENDING_ASSIGNMENT) {
            throw new AccessDeniedException("仅未分配的标讯可删除");
        }

        if (isStaff()) {
            assertStaffCanDeleteTender(tender, userId);
        } else {
            accessGuard.assertCanAccessTender(tender);
        }
    }

    private void assertStaffCanUpdateTender(Tender tender, Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("无法识别当前用户");
        }
        if (!userId.equals(tender.getCreatorId())) {
            throw new AccessDeniedException("仅能编辑自己创建的标讯");
        }
        if (tender.getStatus() != Tender.Status.PENDING_ASSIGNMENT) {
            throw new AccessDeniedException("仅能编辑未分配的标讯");
        }
    }

    private void assertStaffCanDeleteTender(Tender tender, Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("无法识别当前用户");
        }
        if (!userId.equals(tender.getCreatorId())) {
            throw new AccessDeniedException("仅能删除自己创建的标讯");
        }
    }

    private boolean isStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));
    }
}
