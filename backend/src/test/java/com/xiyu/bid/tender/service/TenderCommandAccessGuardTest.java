package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderCommandAccessGuardTest {

    private static final Long USER_ID = 42L;
    private static final Long CREATOR_ID = 42L;
    private static final Long OTHER_USER_ID = 99L;

    @Mock
    private UserRepository userRepository;

    private TenderCommandAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new TenderCommandAccessGuard(userRepository);
    }

    @Test
    @DisplayName("assertCanUpdateTender: userId 为 null → 拒绝")
    void updateTender_nullUserId_denies() {
        Tender tender = tender(Tender.Status.PENDING_ASSIGNMENT, CREATOR_ID, null);

        assertThatThrownBy(() -> guard.assertCanUpdateTender(tender, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("无法识别当前用户");
    }

    @Test
    @DisplayName("assertCanUpdateTender: 用户不存在 → 拒绝")
    void updateTender_userNotFound_denies() {
        Tender tender = tender(Tender.Status.PENDING_ASSIGNMENT, CREATOR_ID, null);
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.assertCanUpdateTender(tender, OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("无法识别当前用户");
    }

    @Test
    @DisplayName("assertCanUpdateTender: admin 且状态 PENDING_ASSIGNMENT → 通过")
    void updateTender_adminPendingAssignment_allows() {
        Tender tender = tender(Tender.Status.PENDING_ASSIGNMENT, CREATOR_ID, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("admin")));

        assertThatNoException().isThrownBy(() -> guard.assertCanUpdateTender(tender, USER_ID));
    }

    @Test
    @DisplayName("assertCanUpdateTender: bidAdmin 且状态 TRACKING → 通过")
    void updateTender_bidAdminTracking_allows() {
        Tender tender = tender(Tender.Status.TRACKING, CREATOR_ID, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("/bidAdmin")));

        assertThatNoException().isThrownBy(() -> guard.assertCanUpdateTender(tender, USER_ID));
    }

    @Test
    @DisplayName("assertCanUpdateTender: bid-TeamLeader 且状态 EVALUATED → 通过")
    void updateTender_bidLeadEvaluated_allows() {
        Tender tender = tender(Tender.Status.EVALUATED, CREATOR_ID, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("bid-TeamLeader")));

        assertThatNoException().isThrownBy(() -> guard.assertCanUpdateTender(tender, USER_ID));
    }

    @Test
    @DisplayName("assertCanUpdateTender: bid_senior 且状态 BIDDING → 拒绝")
    void updateTender_bidSeniorBidding_denies() {
        Tender tender = tender(Tender.Status.BIDDING, CREATOR_ID, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("bid_senior")));

        assertThatThrownBy(() -> guard.assertCanUpdateTender(tender, USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("当前用户无权编辑该标讯");
    }

    @Test
    @DisplayName("assertCanUpdateTender: bid-projectLeader 是 creator 且状态 PENDING_ASSIGNMENT → 通过")
    void updateTender_salesCreatorPendingAssignment_allows() {
        Tender tender = tender(Tender.Status.PENDING_ASSIGNMENT, USER_ID, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("bid-projectLeader")));

        assertThatNoException().isThrownBy(() -> guard.assertCanUpdateTender(tender, USER_ID));
    }

    @Test
    @DisplayName("assertCanUpdateTender: bid-projectLeader 是 projectManager 且状态 TRACKING → 通过")
    void updateTender_salesProjectManagerTracking_allows() {
        Tender tender = tender(Tender.Status.TRACKING, CREATOR_ID, USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("bid-projectLeader")));

        assertThatNoException().isThrownBy(() -> guard.assertCanUpdateTender(tender, USER_ID));
    }

    @Test
    @DisplayName("assertCanUpdateTender: bid-projectLeader 非 creator/projectManager → 拒绝")
    void updateTender_salesNotOwner_denies() {
        Tender tender = tender(Tender.Status.TRACKING, OTHER_USER_ID, OTHER_USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("bid-projectLeader")));

        assertThatThrownBy(() -> guard.assertCanUpdateTender(tender, USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("当前用户无权编辑该标讯");
    }

    @Test
    @DisplayName("assertCanUpdateTender: 未注册角色 → 拒绝")
    void updateTender_unregisteredRole_denies() {
        Tender tender = tender(Tender.Status.PENDING_ASSIGNMENT, CREATOR_ID, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("unknown_role")));

        assertThatThrownBy(() -> guard.assertCanUpdateTender(tender, USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("当前用户无权编辑该标讯");
    }

    @Test
    @DisplayName("assertCanDeleteTender: admin 且状态 PENDING_ASSIGNMENT → 通过")
    void deleteTender_adminPendingAssignment_allows() {
        Tender tender = tender(Tender.Status.PENDING_ASSIGNMENT, CREATOR_ID, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("admin")));

        assertThatNoException().isThrownBy(() -> guard.assertCanDeleteTender(tender, USER_ID));
    }

    @Test
    @DisplayName("assertCanDeleteTender: bidAdmin 且状态 TRACKING → 通过")
    void deleteTender_bidAdminTracking_allows() {
        Tender tender = tender(Tender.Status.TRACKING, CREATOR_ID, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("/bidAdmin")));

        assertThatNoException().isThrownBy(() -> guard.assertCanDeleteTender(tender, USER_ID));
    }

    @Test
    @DisplayName("assertCanDeleteTender: bid-TeamLeader 且状态 EVALUATED → 拒绝")
    void deleteTender_bidLeadEvaluated_denies() {
        Tender tender = tender(Tender.Status.EVALUATED, CREATOR_ID, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("bid-TeamLeader")));

        assertThatThrownBy(() -> guard.assertCanDeleteTender(tender, USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("当前用户无权删除该标讯");
    }

    @Test
    @DisplayName("assertCanDeleteTender: bid-projectLeader 是 creator 且状态 PENDING_ASSIGNMENT → 通过")
    void deleteTender_salesCreatorPendingAssignment_allows() {
        Tender tender = tender(Tender.Status.PENDING_ASSIGNMENT, USER_ID, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("bid-projectLeader")));

        assertThatNoException().isThrownBy(() -> guard.assertCanDeleteTender(tender, USER_ID));
    }

    @Test
    @DisplayName("assertCanDeleteTender: bid-projectLeader 非 creator → 拒绝")
    void deleteTender_salesNotCreator_denies() {
        Tender tender = tender(Tender.Status.PENDING_ASSIGNMENT, OTHER_USER_ID, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("bid-projectLeader")));

        assertThatThrownBy(() -> guard.assertCanDeleteTender(tender, USER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("当前用户无权删除该标讯");
    }

    private User user(String roleCode) {
        return User.builder()
                .id(USER_ID)
                .username("user" + USER_ID)
                .roleProfile(RoleProfile.builder().code(roleCode).build())
                .role(User.Role.MANAGER)
                .enabled(true)
                .build();
    }

    private Tender tender(Tender.Status status, Long creatorId, Long projectManagerId) {
        return Tender.builder()
                .id(1L)
                .status(status)
                .creatorId(creatorId)
                .projectManagerId(projectManagerId)
                .build();
    }
}
