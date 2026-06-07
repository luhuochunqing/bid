package com.xiyu.bid.security;

import com.xiyu.bid.matrixcollaboration.entity.CrmCustomerPermission;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TeamingEntityTest {

    @Test
    void testCrmCustomerPermission() {
        CrmCustomerPermission perm = CrmCustomerPermission.builder()
                .id(1L)
                .customerId("CRM_CUST_001")
                .userId(100L)
                .permissionType("OWNER")
                .build();
        
        assertEquals(1L, perm.getId());
        assertEquals("CRM_CUST_001", perm.getCustomerId());
        assertEquals(100L, perm.getUserId());
        assertEquals("OWNER", perm.getPermissionType());
    }

    @Test
    void testProjectMember() {
        ProjectMember member = ProjectMember.builder()
                .id(1L)
                .projectId(500L)
                .userId(101L)
                .memberRole("TECHNICAL_EXPERT")
                .permissionLevel("EDITOR")
                .isInherited(false)
                .build();
        
        assertEquals(1L, member.getId());
        assertEquals(500L, member.getProjectId());
        assertEquals(101L, member.getUserId());
        assertEquals("TECHNICAL_EXPERT", member.getMemberRole());
        assertEquals("EDITOR", member.getPermissionLevel());
        assertEquals(false, member.isInherited());
    }
}
