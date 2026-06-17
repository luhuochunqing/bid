package com.xiyu.bid.tender.core;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TenderEditPermissionPolicyTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long CREATOR_ID = USER_ID;
    private static final Long PROJECT_MANAGER_ID = USER_ID;

    @ParameterizedTest(name = "canEdit: role={0}, status={1}, ownership={2} -> {3}")
    @MethodSource("editMatrix")
    @DisplayName("canEdit 覆盖所有角色 × 状态 × 所有权组合")
    void canEdit_matrix(String roleCode, Tender.Status status, Ownership ownership, boolean expected) {
        Long creator = ownership.creatorId;
        Long pm = ownership.projectManagerId;

        boolean result = TenderEditPermissionPolicy.canEdit(
                roleCode, User.Role.MANAGER, USER_ID, creator, pm, status);

        assertThat(result)
                .as("role=%s, status=%s, ownership=%s 应返回 %s", roleCode, status, ownership, expected)
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "canDelete: role={0}, status={1}, ownership={2} -> {3}")
    @MethodSource("deleteMatrix")
    @DisplayName("canDelete 覆盖所有角色 × 状态 × 所有权组合")
    void canDelete_matrix(String roleCode, Tender.Status status, Ownership ownership, boolean expected) {
        Long creator = ownership.creatorId;
        Long pm = ownership.projectManagerId;

        boolean result = TenderEditPermissionPolicy.canDelete(
                roleCode, User.Role.MANAGER, USER_ID, creator, pm, status);

        assertThat(result)
                .as("role=%s, status=%s, ownership=%s 应返回 %s", roleCode, status, ownership, expected)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("canEdit: null userId -> false")
    void canEdit_nullUserId_returnsFalse() {
        boolean result = TenderEditPermissionPolicy.canEdit(
                "admin", User.Role.ADMIN, null, OTHER_USER_ID, OTHER_USER_ID, Tender.Status.PENDING_ASSIGNMENT);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canEdit: null status -> false")
    void canEdit_nullStatus_returnsFalse() {
        boolean result = TenderEditPermissionPolicy.canEdit(
                "admin", User.Role.ADMIN, USER_ID, OTHER_USER_ID, OTHER_USER_ID, null);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canEdit: null roleCode -> false")
    void canEdit_nullRoleCode_returnsFalse() {
        boolean result = TenderEditPermissionPolicy.canEdit(
                null, User.Role.ADMIN, USER_ID, CREATOR_ID, PROJECT_MANAGER_ID, Tender.Status.PENDING_ASSIGNMENT);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canEdit: blank roleCode -> false")
    void canEdit_blankRoleCode_returnsFalse() {
        boolean result = TenderEditPermissionPolicy.canEdit(
                "   ", User.Role.ADMIN, USER_ID, CREATOR_ID, PROJECT_MANAGER_ID, Tender.Status.PENDING_ASSIGNMENT);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canEdit: unknown roleCode -> false")
    void canEdit_unknownRoleCode_returnsFalse() {
        boolean result = TenderEditPermissionPolicy.canEdit(
                "unknown_role", User.Role.MANAGER, USER_ID, CREATOR_ID, PROJECT_MANAGER_ID, Tender.Status.PENDING_ASSIGNMENT);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canEdit: roleCode 大小写不敏感")
    void canEdit_roleCodeCaseInsensitive() {
        assertThat(TenderEditPermissionPolicy.canEdit(
                "ADMIN", User.Role.ADMIN, USER_ID, OTHER_USER_ID, OTHER_USER_ID, Tender.Status.PENDING_ASSIGNMENT))
                .isTrue();
        assertThat(TenderEditPermissionPolicy.canEdit(
                "  BID_ADMIN  ", User.Role.ADMIN, USER_ID, OTHER_USER_ID, OTHER_USER_ID, Tender.Status.TRACKING))
                .isTrue();
        assertThat(TenderEditPermissionPolicy.canEdit(
                "Sales", User.Role.MANAGER, USER_ID, CREATOR_ID, PROJECT_MANAGER_ID, Tender.Status.TRACKING))
                .isTrue();
    }

    @Test
    @DisplayName("canDelete: null userId -> false")
    void canDelete_nullUserId_returnsFalse() {
        boolean result = TenderEditPermissionPolicy.canDelete(
                "admin", User.Role.ADMIN, null, OTHER_USER_ID, OTHER_USER_ID, Tender.Status.PENDING_ASSIGNMENT);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canDelete: null status -> false")
    void canDelete_nullStatus_returnsFalse() {
        boolean result = TenderEditPermissionPolicy.canDelete(
                "admin", User.Role.ADMIN, USER_ID, OTHER_USER_ID, OTHER_USER_ID, null);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canDelete: sales 非创建人不可删除")
    void canDelete_salesNotCreator_returnsFalse() {
        boolean result = TenderEditPermissionPolicy.canDelete(
                "sales", User.Role.MANAGER, USER_ID, OTHER_USER_ID, USER_ID, Tender.Status.PENDING_ASSIGNMENT);
        assertThat(result).isFalse();
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> editMatrix() {
        Stream.Builder<org.junit.jupiter.params.provider.Arguments> builder = Stream.builder();

        for (String globalRole : new String[]{"admin", "bid_admin", "bid_lead", "bid_senior"}) {
            builder.add(args(globalRole, Tender.Status.PENDING_ASSIGNMENT, Ownership.OTHER, true));
            builder.add(args(globalRole, Tender.Status.TRACKING, Ownership.OTHER, true));
            builder.add(args(globalRole, Tender.Status.EVALUATED, Ownership.OTHER, true));
            builder.add(args(globalRole, Tender.Status.BIDDING, Ownership.OTHER, false));
            builder.add(args(globalRole, Tender.Status.WON, Ownership.OTHER, false));
            builder.add(args(globalRole, Tender.Status.LOST, Ownership.OTHER, false));
            builder.add(args(globalRole, Tender.Status.ABANDONED, Ownership.OTHER, false));
        }

        // sales: PENDING_ASSIGNMENT 仅 creator 可编辑
        builder.add(args("sales", Tender.Status.PENDING_ASSIGNMENT, Ownership.SELF_CREATOR, true));
        builder.add(args("sales", Tender.Status.PENDING_ASSIGNMENT, Ownership.SELF_PM, false));
        builder.add(args("sales", Tender.Status.PENDING_ASSIGNMENT, Ownership.OTHER, false));

        // sales: TRACKING/EVALUATED creator 或 projectManager 可编辑
        for (Tender.Status status : new Tender.Status[]{Tender.Status.TRACKING, Tender.Status.EVALUATED}) {
            builder.add(args("sales", status, Ownership.SELF_CREATOR, true));
            builder.add(args("sales", status, Ownership.SELF_PM, true));
            builder.add(args("sales", status, Ownership.OTHER, false));
        }

        // sales: BIDDING 及之后不可编辑
        for (Tender.Status status : new Tender.Status[]{Tender.Status.BIDDING, Tender.Status.WON, Tender.Status.LOST, Tender.Status.ABANDONED}) {
            builder.add(args("sales", status, Ownership.SELF_CREATOR, false));
            builder.add(args("sales", status, Ownership.SELF_PM, false));
        }

        // 无编辑权限角色
        for (String role : new String[]{"bid_specialist", "task_executor", "admin_staff"}) {
            for (Tender.Status status : Tender.Status.values()) {
                builder.add(args(role, status, Ownership.SELF_CREATOR, false));
            }
        }

        return builder.build();
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> deleteMatrix() {
        Stream.Builder<org.junit.jupiter.params.provider.Arguments> builder = Stream.builder();

        for (String globalRole : new String[]{"admin", "bid_admin", "bid_lead", "bid_senior"}) {
            builder.add(args(globalRole, Tender.Status.PENDING_ASSIGNMENT, Ownership.OTHER, true));
            builder.add(args(globalRole, Tender.Status.TRACKING, Ownership.OTHER, true));
            builder.add(args(globalRole, Tender.Status.EVALUATED, Ownership.OTHER, false));
            builder.add(args(globalRole, Tender.Status.BIDDING, Ownership.OTHER, false));
            builder.add(args(globalRole, Tender.Status.WON, Ownership.OTHER, false));
            builder.add(args(globalRole, Tender.Status.LOST, Ownership.OTHER, false));
            builder.add(args(globalRole, Tender.Status.ABANDONED, Ownership.OTHER, false));
        }

        // sales: 自己创建且未评估前可删除
        for (Tender.Status status : new Tender.Status[]{Tender.Status.PENDING_ASSIGNMENT, Tender.Status.TRACKING}) {
            builder.add(args("sales", status, Ownership.SELF_CREATOR, true));
            builder.add(args("sales", status, Ownership.SELF_PM, false));
            builder.add(args("sales", status, Ownership.OTHER, false));
        }

        // sales: EVALUATED 及之后不可删除
        for (Tender.Status status : new Tender.Status[]{Tender.Status.EVALUATED, Tender.Status.BIDDING, Tender.Status.WON, Tender.Status.LOST, Tender.Status.ABANDONED}) {
            builder.add(args("sales", status, Ownership.SELF_CREATOR, false));
        }

        // 无删除权限角色
        for (String role : new String[]{"bid_specialist", "task_executor", "admin_staff"}) {
            for (Tender.Status status : Tender.Status.values()) {
                builder.add(args(role, status, Ownership.SELF_CREATOR, false));
            }
        }

        return builder.build();
    }

    private static org.junit.jupiter.params.provider.Arguments args(
            String roleCode, Tender.Status status, Ownership ownership, boolean expected) {
        return org.junit.jupiter.params.provider.Arguments.of(roleCode, status, ownership, expected);
    }

    enum Ownership {
        SELF_CREATOR(CREATOR_ID, OTHER_USER_ID),
        SELF_PM(OTHER_USER_ID, PROJECT_MANAGER_ID),
        OTHER(OTHER_USER_ID, OTHER_USER_ID);

        final Long creatorId;
        final Long projectManagerId;

        Ownership(Long creatorId, Long projectManagerId) {
            this.creatorId = creatorId;
            this.projectManagerId = projectManagerId;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }
}
