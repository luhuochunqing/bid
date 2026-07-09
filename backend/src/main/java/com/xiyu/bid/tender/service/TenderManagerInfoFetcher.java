package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.service.ProjectManagerDepartmentEnricher;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 批量查询 tenderId → {managerName, managerDeptName}。
 *
 * <p>两个字段用不同数据源（方向正确：部门是标讯自己的字段，不从项目反查）：</p>
 * <ul>
 *   <li>managerName：{@code Project.managerId}（CO-333，标讯无姓名时从项目反查）</li>
 *   <li>managerDeptName：{@code Tender.projectManagerId}（标讯自己的负责人）</li>
 * </ul>
 * <p>合并 userIds 一次 user 查询，不增加 DB 调用次数。</p>
 *
 * <p>从 TenderQueryService 拆出（原 303 行超 300 行限制）。</p>
 * <p>部门反查逻辑复用 {@link ProjectManagerDepartmentEnricher}，避免重复造轮子。</p>
 */
@Component
@RequiredArgsConstructor
public class TenderManagerInfoFetcher {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectManagerDepartmentEnricher departmentEnricher;

    /** tenderId → 项目负责人姓名/部门信息（同一 user 查询同时解析两个字段，避免重复 DB 调用）。 */
    public record ManagerInfo(String managerName, String managerDeptName) {
    }

    /**
     * 批量查询 tenderId → ManagerInfo。
     *
     * @param dtos 标讯 DTO 列表（读取 id 和 projectManagerId）
     * @return tenderId → ManagerInfo 映射；无数据时返回空 Map
     */
    public Map<Long, ManagerInfo> fetch(List<TenderDTO> dtos) {
        Set<Long> tenderIds = dtos.stream().map(TenderDTO::getId).collect(Collectors.toSet());

        // managerName 数据源：Project.managerId（CO-333）
        Map<Long, Long> tenderToProjectManager = projectRepository.findByTenderIdIn(tenderIds).stream()
                .filter(p -> p.getManagerId() != null)
                .collect(Collectors.toMap(Project::getTenderId, Project::getManagerId, (a, b) -> a));

        // deptName 数据源：Tender.projectManagerId（标讯自己的字段，方向正确）
        Map<Long, Long> tenderToTenderManager = new HashMap<>();
        for (TenderDTO dto : dtos) {
            if (dto.getProjectManagerId() != null) {
                tenderToTenderManager.put(dto.getId(), dto.getProjectManagerId());
            }
        }

        if (tenderToProjectManager.isEmpty() && tenderToTenderManager.isEmpty()) {
            return new HashMap<>();
        }

        // 合并 userIds，一次 user 查询
        Set<Long> allUserIds = new HashSet<>();
        allUserIds.addAll(tenderToProjectManager.values());
        allUserIds.addAll(tenderToTenderManager.values());
        Map<Long, User> userMap = allUserIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : userRepository.findByIdIn(allUserIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        // 复用 ProjectManagerDepartmentEnricher 批量查部门名（避免重复造轮子）
        Map<Long, String> userIdToDeptName = departmentEnricher.buildManagerDepartmentMap(allUserIds, userMap);

        // 构建 tenderId → ManagerInfo
        // managerName 从 project.managerId 取，deptName 从 tender.projectManagerId 取
        Map<Long, ManagerInfo> result = new HashMap<>(tenderIds.size());
        for (TenderDTO dto : dtos) {
            Long tenderId = dto.getId();
            Long projectMgrId = tenderToProjectManager.get(tenderId);
            Long tenderMgrId = tenderToTenderManager.get(tenderId);

            User nameUser = userMap.get(projectMgrId);
            String managerName = nameUser != null ? nameUser.getFullName() : null;
            String managerDeptName = tenderMgrId != null ? userIdToDeptName.get(tenderMgrId) : null;
            result.put(tenderId, new ManagerInfo(managerName, managerDeptName));
        }
        return result;
    }
}
