package com.xiyu.bid.approval.entity;

import com.xiyu.bid.approval.enums.ApprovalStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("ApprovalAction实体测试")
class ApprovalActionTest {

    @Test
    @DisplayName("审批前后状态字段应该使用审批状态枚举")
    void statusFields_ShouldUseApprovalStatusEnum() throws NoSuchFieldException {
        Field previousStatus = ApprovalAction.class.getDeclaredField("previousStatus");
        Field newStatus = ApprovalAction.class.getDeclaredField("newStatus");

        assertEquals(ApprovalStatus.class, previousStatus.getType());
        assertEquals(ApprovalStatus.class, newStatus.getType());
    }
}
