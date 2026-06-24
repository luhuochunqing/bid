// Input: AssignmentContext + 当前用户
// Output: 过滤后的候选人 DTO 列表
// Pos: Service/编排层（Imperative Shell）
// 维护声明: 仅编排候选人查询调用链；过滤逻辑由 AssignmentCandidatePolicy 承载。
package com.xiyu.bid.user.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.service.RoleProfileService;
import com.xiyu.bid.user.core.AssignmentCandidatePolicy;
import com.xiyu.bid.user.core.AssignmentContext;
import com.xiyu.bid.user.dto.AssignmentCandidateDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分配候选人编排服务（Imperative Shell）。
 *
 * <p>编排调用链：
 * <ol>
 *   <li>校验 {@link AssignmentContext#isValidContextType()}，无效抛 IllegalArgumentException</li>
 *   <li>UserRepository.findByEnabledTrue() 获取所有启用用户</li>
 *   <li>RoleProfileService.hasGlobalAccess() 判断全局权限</li>
 *   <li>ProjectAccessScopeService.getAllowedDepartmentCodes() 获取可见部门</li>
 *   <li>AssignmentCandidatePolicy.filter() 执行纯核心过滤</li>
 * </ol>
 */
@Service
public class AssignmentCandidateAppService {

    private final UserRepository userRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final RoleProfileService roleProfileService;
    private final AssignmentCandidatePolicy assignmentCandidatePolicy;

    @Autowired
    public AssignmentCandidateAppService(
            UserRepository userRepository,
            ProjectAccessScopeService projectAccessScopeService,
            RoleProfileService roleProfileService,
            AssignmentCandidatePolicy assignmentCandidatePolicy) {
        this.userRepository = userRepository;
        this.projectAccessScopeService = projectAccessScopeService;
        this.roleProfileService = roleProfileService;
        this.assignmentCandidatePolicy = assignmentCandidatePolicy;
    }

    /**
     * 查询分配候选人。
     *
     * @param context      业务场景上下文（task / tender）
     * @param currentUser  当前登录用户
     * @return 过滤并排序后的候选人列表
     * @throws IllegalArgumentException context 为 null 或 contextType 非 task/tender
     */
    public List<AssignmentCandidateDTO> findCandidates(AssignmentContext context, User currentUser) {
        if (context == null || !context.isValidContextType()) {
            throw new IllegalArgumentException(
                    "Invalid assignment context: " + (context == null ? "null" : context.contextType()));
        }

        List<User> candidates = userRepository.findByEnabledTrue();
        boolean hasGlobalAccess = roleProfileService.hasGlobalAccess(currentUser);
        List<String> allowedDeptCodes = projectAccessScopeService.getAllowedDepartmentCodes(currentUser);

        List<AssignmentCandidateDTO> result = assignmentCandidatePolicy.filter(
                candidates,
                hasGlobalAccess,
                allowedDeptCodes,
                context,
                context.deptCode(),
                context.roleCode());
        return result == null ? List.of() : result;
    }
}
