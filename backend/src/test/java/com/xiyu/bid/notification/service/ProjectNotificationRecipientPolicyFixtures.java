// Output: ProjectNotificationRecipientPolicy 测试用共享工厂方法
// Pos: notification/service/ - 测试夹具
package com.xiyu.bid.notification.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;

final class ProjectNotificationRecipientPolicyFixtures {

    private ProjectNotificationRecipientPolicyFixtures() {
    }

    static User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    static ProjectMember member(Long userId) {
        ProjectMember m = new ProjectMember();
        m.setUserId(userId);
        return m;
    }

    static ProjectLeadAssignment assignment(Long primary, Long secondary) {
        ProjectLeadAssignment a = new ProjectLeadAssignment();
        a.setPrimaryLeadUserId(primary);
        a.setSecondaryLeadUserId(secondary);
        return a;
    }

    static ProjectInitiationDetails initiationDetails(Long ownerUserId) {
        ProjectInitiationDetails d = new ProjectInitiationDetails();
        d.setOwnerUserId(ownerUserId);
        return d;
    }

    static BidDocumentReviewEntity review(Long reviewerId) {
        BidDocumentReviewEntity r = new BidDocumentReviewEntity();
        r.setReviewerId(reviewerId);
        return r;
    }
}
