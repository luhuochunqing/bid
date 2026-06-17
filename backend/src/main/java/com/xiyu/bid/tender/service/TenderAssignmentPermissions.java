// Input: tenderId + 当前用户 id
// Output: 实例级权限判定 (boolean) — 是否可填 / 是否可决策
// Pos: Service/权限支撑层（命令式外壳）
// 维护声明: 仅做数据访问 + 委托纯规则；判定逻辑统一在 AssignmentPermissionRules。
package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.RoleProfileService;
import com.xiyu.bid.tender.core.AssignmentPermissionRules;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 标讯实例级权限的应用外壳。
 *
 * <p>规则统一在 {@link AssignmentPermissionRules}；本类只做：
 * <ol>
 *   <li>查询 latest {@link TenderAssignmentRecord}</li>
 *   <li>把记录与用户 id 委托给纯规则</li>
 * </ol>
 */
@Component
public class TenderAssignmentPermissions {

    private final TenderAssignmentRecordRepository repository;
    private final TenderRepository tenderRepository;
    private final UserRepository userRepository;
    private final RoleProfileService roleProfileService;

    public TenderAssignmentPermissions(
            TenderAssignmentRecordRepository repository,
            TenderRepository tenderRepository,
            UserRepository userRepository,
            RoleProfileService roleProfileService) {
        this.repository = repository;
        this.tenderRepository = tenderRepository;
        this.userRepository = userRepository;
        this.roleProfileService = roleProfileService;
    }

    /** 用户是否为该标讯的 latest assignee（可填 / 提交评估表）。 */
    public boolean canFill(Long tenderId, Long userId) {
        if (tenderId == null) return false;
        Optional<Tender> tenderOpt = tenderRepository.findById(tenderId);
        if (tenderOpt.isPresent()) {
            Tender tender = tenderOpt.get();
            Tender.Status status = tender.getStatus();
            if (status == Tender.Status.EVALUATED ||
                status == Tender.Status.BIDDING ||
                status == Tender.Status.WON ||
                status == Tender.Status.LOST ||
                status == Tender.Status.ABANDONED) {
                return false;
            }
            // 已关联CRM商机时，评估表第一、二部分数据来自CRM，不允许手动编辑
            if (tender.getCrmOpportunityName() != null || tender.getEvaluationSource() != null) {
                return false;
            }
        }
        return AssignmentPermissionRules.canFill(latest(tenderId), userId);
    }

    /** 投标管理员/投标组长有投标权限，或标讯的分配人可投标/弃标。 */
    public boolean canDecide(Long tenderId, Long userId) {
        if (userId != null) {
            Optional<User> user = userRepository.findById(userId);
            if (user.isPresent() && roleProfileService.hasGlobalAccess(user.get())) {
                return true;
            }
        }
        return AssignmentPermissionRules.canDecide(latest(tenderId), userId);
    }

    private Optional<TenderAssignmentRecord> latest(Long tenderId) {
        if (tenderId == null) return Optional.empty();
        return repository.findFirstByTenderIdOrderByAssignedAtDesc(tenderId);
    }
}
