package com.xiyu.bid.admin.service;

import com.xiyu.bid.dto.ProjectGroupConfigRequest;
import com.xiyu.bid.dto.ProjectGroupConfigResponse;
import com.xiyu.bid.entity.ProjectGroup;
import com.xiyu.bid.entity.ProjectGroup.AccessRole;
import com.xiyu.bid.entity.ProjectGroup.Visibility;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.ProjectGroupRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectGroupService {

    private static final Visibility DEFAULT_VISIBILITY = Visibility.MEMBERS;

    private final ProjectGroupRepository projectGroupRepository;
    private final UserRepository userRepository;

    public ProjectGroupConfigResponse getProjectGroups() {
        List<User> users = userRepository.findAll();
        Map<Long, User> usersById = users.stream()
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));

        return ProjectGroupConfigResponse.builder()
                .projectGroups(projectGroupRepository.findAll().stream()
                        .sorted(Comparator.comparing(ProjectGroup::getGroupName, String.CASE_INSENSITIVE_ORDER))
                        .map(group -> toResponseItem(group, usersById))
                        .toList())
                .userOptions(users.stream()
                        .map(this::toUserOptionItem)
                        .toList())
                .build();
    }

    @Transactional
    public ProjectGroupConfigResponse saveProjectGroups(ProjectGroupConfigRequest request) {
        List<ProjectGroupConfigRequest.ProjectGroupItem> groups = request == null ? List.of() : request.getProjectGroups();
        projectGroupRepository.deleteAll();
        List<ProjectGroup> entities = groups == null ? List.of() : groups.stream()
                .filter(item -> hasText(item.getGroupName()) || hasText(item.getGroupCode()))
                .map(item -> toEntity(item, null))
                .toList();
        projectGroupRepository.saveAll(entities);
        return getProjectGroups();
    }

    @Transactional
    public ProjectGroupConfigResponse.ProjectGroupItem createProjectGroup(ProjectGroupConfigRequest.ProjectGroupItem request) {
        ProjectGroup entity = toEntity(request, null);
        validateGroupCodeUniqueness(entity.getGroupCode(), null);
        return toResponseItem(projectGroupRepository.save(entity), loadUsersById());
    }

    @Transactional
    public ProjectGroupConfigResponse.ProjectGroupItem updateProjectGroup(Long id, ProjectGroupConfigRequest.ProjectGroupItem request) {
        ProjectGroup existing = projectGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProjectGroup", String.valueOf(id)));
        ProjectGroup updated = toEntity(request, existing);
        validateGroupCodeUniqueness(updated.getGroupCode(), id);
        return toResponseItem(projectGroupRepository.save(updated), loadUsersById());
    }

    @Transactional
    public void deleteProjectGroup(Long id) {
        if (id == null || !projectGroupRepository.existsById(id)) {
            throw new ResourceNotFoundException("ProjectGroup", String.valueOf(id));
        }
        projectGroupRepository.deleteById(id);
    }

    public List<Long> getGrantedProjectIds(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }
        String roleCode = String.valueOf(user.getRole()).toLowerCase(Locale.ROOT);
        return projectGroupRepository.findAll().stream()
                .filter(group -> matchesGroup(group, user.getId(), roleCode))
                .flatMap(group -> normalizeLongIds(group.getProjectIds()).stream())
                .distinct()
                .sorted()
                .toList();
    }

    private ProjectGroup toEntity(ProjectGroupConfigRequest.ProjectGroupItem item, ProjectGroup existing) {
        ProjectGroup.ProjectGroupBuilder builder = (existing == null ? ProjectGroup.builder() : ProjectGroup.builder()
                .id(existing.getId())
                .createdAt(existing.getCreatedAt()));
        return builder
                .groupCode(normalizeGroupCode(item == null ? null : item.getGroupCode(), item == null ? null : item.getGroupName()))
                .groupName(normalizeGroupName(item == null ? null : item.getGroupName()))
                .managerUserId(item == null ? null : item.getManagerUserId())
                .visibility(normalizeVisibility(item == null ? null : item.getVisibility()))
                .memberUserIds(normalizeLongIds(item == null ? null : item.getMemberUserIds()))
                .allowedRoles(normalizeRolesForEntity(item == null ? null : item.getAllowedRoles()))
                .projectIds(normalizeLongIds(item == null ? null : item.getProjectIds()))
                .build();
    }

    private ProjectGroupConfigResponse.ProjectGroupItem toResponseItem(ProjectGroup group, Map<Long, User> usersById) {
        User manager = group.getManagerUserId() == null ? null : usersById.get(group.getManagerUserId());
        List<Long> memberIds = normalizeLongIds(group.getMemberUserIds());
        return ProjectGroupConfigResponse.ProjectGroupItem.builder()
                .id(group.getId())
                .groupCode(group.getGroupCode())
                .groupName(group.getGroupName())
                .managerUserId(group.getManagerUserId())
                .manager(manager == null ? "" : manager.getFullName())
                .memberCount(memberIds.size())
                .visibility(group.getVisibility() == null ? DEFAULT_VISIBILITY.name().toLowerCase(Locale.ROOT) : group.getVisibility().name().toLowerCase(Locale.ROOT))
                .memberUserIds(memberIds)
                .allowedRoles(normalizeRoles(group.getAllowedRoles()))
                .projectIds(normalizeLongIds(group.getProjectIds()))
                .build();
    }

    private ProjectGroupConfigResponse.UserOptionItem toUserOptionItem(User user) {
        return ProjectGroupConfigResponse.UserOptionItem.builder()
                .id(user.getId())
                .name(user.getFullName())
                .role(String.valueOf(user.getRole()).toLowerCase(Locale.ROOT))
                .deptCode(user.getDepartmentCode())
                .dept(user.getDepartmentName())
                .build();
    }

    private Map<Long, User> loadUsersById() {
        return userRepository.findAll().stream()
                .filter(user -> user.getId() != null)
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
    }

    private List<Long> normalizeLongIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }

    private boolean matchesGroup(ProjectGroup group, Long userId, String roleCode) {
        Visibility visibility = group.getVisibility() == null ? DEFAULT_VISIBILITY : group.getVisibility();
        List<Long> memberIds = normalizeLongIds(group.getMemberUserIds());
        List<String> allowedRoles = normalizeRoles(group.getAllowedRoles());

        return switch (visibility) {
            case ALL -> true;
            case MANAGER -> Objects.equals(group.getManagerUserId(), userId);
            case CUSTOM -> Objects.equals(group.getManagerUserId(), userId) || allowedRoles.contains(roleCode);
            default -> Objects.equals(group.getManagerUserId(), userId) || memberIds.contains(userId);
        };
    }

    private List<AccessRole> normalizeRolesForEntity(List<String> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .filter(this::hasText)
                .map(this::parseAccessRole)
                .distinct()
                .toList();
    }

    private List<String> normalizeRoles(List<AccessRole> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .map(role -> role.name().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private Visibility normalizeVisibility(String visibility) {
        if (!hasText(visibility)) {
            return DEFAULT_VISIBILITY;
        }
        try {
            return Visibility.valueOf(visibility.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("项目组可见范围非法");
        }
    }

    private AccessRole parseAccessRole(String role) {
        try {
            return AccessRole.valueOf(role);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("项目组角色范围非法");
        }
    }

    private String normalizeGroupName(String groupName) {
        return hasText(groupName) ? groupName.trim() : "未命名项目组";
    }

    private String normalizeGroupCode(String groupCode, String groupName) {
        if (hasText(groupCode)) {
            return groupCode.trim();
        }
        if (hasText(groupName)) {
            return groupName.trim().replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
        }
        return UUID.randomUUID().toString();
    }

    private void validateGroupCodeUniqueness(String groupCode, Long currentId) {
        if (currentId == null) {
            if (projectGroupRepository.existsByGroupCode(groupCode)) {
                throw new IllegalArgumentException("项目组编码已存在");
            }
            return;
        }

        if (projectGroupRepository.existsByGroupCodeAndIdNot(groupCode, currentId)) {
            throw new IllegalArgumentException("项目组编码已存在");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
