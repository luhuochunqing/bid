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
 * <p>CO-484 v2（2026-07-04 评审中）多人审核校验规则：</p>
 * <ul>
 *   <li>人数 1-3、去重、不含 submittedBy</li>
 *   <li>必须包含项目经理（project.managerId），且项目经理不能是提交人本人</li>
 *   <li>不得选择投标负责人（primaryLead，与提交人同口径）或项目团队成员</li>
 *   <li>投标辅助人员（secondaryLead）<b>可</b>作为审核人（v2 解禁）</li>
 * </ul>
 * <p>纯静态工具，无 Spring 依赖，便于单测与复用。</p>
 */
final class BidReviewReviewerValidator {

    /** CO-484 v2：审核人最多 3 人。 */
    static final int MAX_REVIEWERS = 3;

    private BidReviewReviewerValidator() {
    }

    /**
     * 校验 reviewerIds 入参：人数 1-3、去重、不含 submittedBy。
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
     * CO-484 v2 审核人组成校验：
     * <ul>
     *   <li>必须包含项目经理（project.managerId），且项目经理不能是 submittedBy</li>
     *   <li>不得选择投标负责人（primaryLead）或项目团队成员</li>
     *   <li>投标辅助人员（secondaryLead）允许，不再排除</li>
     * </ul>
     */
    static void validateReviewerComposition(Collection<Long> reviewerIds,
                                             Project project,
                                             ProjectLeadAssignment lead,
                                             Long submittedBy) {
        Long managerId = project.getManagerId();
        if (managerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目未指定项目经理，无法提交审核");
        }
        if (managerId.equals(submittedBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "您是项目经理，不能审核自己提交的标书。请让其他项目负责人作为审核人");
        }
        if (!reviewerIds.contains(managerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "审核人列表中必须包含项目负责人（项目经理），请重新选择");
        }

        Set<Long> excluded = new LinkedHashSet<>();
        if (project.getTeamMembers() != null) excluded.addAll(project.getTeamMembers());
        if (lead != null && lead.getPrimaryLeadUserId() != null) {
            excluded.add(lead.getPrimaryLeadUserId());
        }
        for (Long rid : reviewerIds) {
            if (excluded.contains(rid)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "标书审核人不能选择投标负责人或项目团队成员");
            }
        }
    }
}
