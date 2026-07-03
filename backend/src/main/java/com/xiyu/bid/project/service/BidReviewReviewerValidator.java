// Input: reviewerIds、submittedBy、project、leadAssignment
// Output: 校验通过或抛 ResponseStatusException
// Pos: project/service/ - 标书审核人入参校验工具
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.entity.ProjectLeadAssignment;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 标书审核人入参校验工具。
 * <p>CO-483 + CO-484 多人审核校验规则：</p>
 * <ul>
 *   <li>人数 1-2、去重、不含 submittedBy</li>
 *   <li>不含项目经理 / 团队成员 / primaryLead / secondaryLead</li>
 * </ul>
 * <p>纯静态工具，无 Spring 依赖，便于单测与复用。</p>
 */
final class BidReviewReviewerValidator {

    /** CO-484 调整后需求：审核人最多 2 人。 */
    static final int MAX_REVIEWERS = 2;

    private BidReviewReviewerValidator() {
    }

    /**
     * 校验 reviewerIds 入参：人数 1-2、去重、不含 submittedBy。
     */
    static void validateReviewerIds(Collection<Long> reviewerIds, Long submittedBy) {
        if (reviewerIds == null || reviewerIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标书审核人不能为空");
        }
        Set<Long> deduped = new LinkedHashSet<>(reviewerIds);
        if (deduped.size() != reviewerIds.size()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标书审核人不能重复");
        }
        if (deduped.size() > MAX_REVIEWERS) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "标书审核人最多 " + MAX_REVIEWERS + " 人");
        }
        if (submittedBy != null && deduped.contains(submittedBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标书审核人不能选择自己");
        }
    }

    /**
     * CO-483 排除范围校验：审核人不得是项目经理 / 团队成员 / primaryLead / secondaryLead。
     */
    static void validateReviewersNotProjectParticipants(Collection<Long> reviewerIds,
                                                         Project project,
                                                         ProjectLeadAssignment lead) {
        Set<Long> participantIds = new LinkedHashSet<>();
        if (project.getManagerId() != null) participantIds.add(project.getManagerId());
        if (project.getTeamMembers() != null) participantIds.addAll(project.getTeamMembers());

        if (lead != null) {
            if (lead.getPrimaryLeadUserId() != null) participantIds.add(lead.getPrimaryLeadUserId());
            if (lead.getSecondaryLeadUserId() != null) participantIds.add(lead.getSecondaryLeadUserId());
        }

        for (Long rid : reviewerIds) {
            if (participantIds.contains(rid)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "标书审核人必须是未参与本项目的人员（含投标负责人/辅助人员）");
            }
        }
    }
}
