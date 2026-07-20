package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.service.UserEnabledStatusService;
import com.xiyu.bid.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * CO-333: 按全名解析投标负责人 user_id。
 * 唯一匹配才返回 id，0 个 / 多个（重名）跳过避免误绑，
 * 解析失败不阻断主流程，仅 log warn。
 *
 * <p>增强匹配策略：
 * <ul>
 *   <li>先精确匹配 fullName</li>
 *   <li>0 个结果时，标准化姓名后再次匹配（去除所有空白、统一中间点·为半角.、全角转半角）</li>
 *   <li>标准化后重名仍返回 null，避免误绑</li>
 * </ul>
 *
 * <p>工号优先策略（v3.10 新增）：
 * <ul>
 *   <li>{@link #resolveByEmployeeNumber(String)} 按 User.employeeNumber 精确匹配，工号全局唯一</li>
 *   <li>{@link #resolveByEmployeeNumberThenName(String, String)} 工号优先；工号命中但姓名不符仅告警不阻断；
 *       工号未命中时按 username 回落（与 {@code TenderAutoAssignmentService} 对齐），
 *       仍找不到时回落到 {@link #resolveByFullName(String)}</li>
 * </ul>
 *
 * <p>停用过滤（v3.10 新增）：所有解析路径命中后统一通过 {@link UserEnabledStatusService#isEnabled(User)}
 * 检查用户启用状态，停用用户返回 null，与 {@code TenderAutoAssignmentService.resolveManagerNameBySaleNo} 行为一致。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectManagerIdResolver {

    /** 全角中间点 */
    private static final char FULLWIDTH_MIDDLE_DOT = '·';
    /** 半角中间点 */
    private static final char HALFWIDTH_MIDDLE_DOT = '.';
    /** 全角空格 */
    private static final char FULLWIDTH_SPACE = '\u3000';

    private final UserRepository userRepository;
    private final UserEnabledStatusService userEnabledStatusService;

    /**
     * @param fullName 全名（null/空返回 null）
     * @return 唯一匹配且启用返回 id；否则 null
     */
    public Long resolveByFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String trimmed = fullName.trim();

        // 1. 精确匹配
        List<User> matches = userRepository.findByFullName(trimmed);
        if (matches.size() == 1) {
            return resolveEnabledOrNull(matches.get(0), "fullName", trimmed);
        }

        // 2. 精确匹配失败，尝试标准化后匹配
        String normalized = normalize(trimmed);
        if (!normalized.equals(trimmed)) {
            matches = userRepository.findByFullName(normalized);
            if (matches.size() == 1) {
                return resolveEnabledOrNull(matches.get(0), "fullName(normalized)", normalized);
            }
        }

        // 3. 匹配失败或重名
        if (matches.isEmpty()) {
            log.warn("CO-333: projectManagerName '{}' 无匹配用户（含标准化后），projectManagerId 保持 null", fullName);
        } else {
            log.warn("CO-333: projectManagerName '{}' 匹配到 {} 个用户（重名，含标准化后），跳过 id 绑定避免误绑",
                    fullName, matches.size());
        }
        return null;
    }

    /**
     * 按工号解析 user_id（v3.10 新增）。
     * <p>工号全局唯一，按 {@link User#getEmployeeNumber()} 精确匹配。
     * 工号为空或无匹配返回 null，不阻断主流程。
     *
     * @param employeeNumber 工号（null/空返回 null）
     * @return 唯一匹配且启用返回 id；否则 null
     */
    public Long resolveByEmployeeNumber(String employeeNumber) {
        if (employeeNumber == null || employeeNumber.isBlank()) {
            return null;
        }
        String trimmed = employeeNumber.trim();
        return userRepository.findByEmployeeNumber(trimmed)
                .map(u -> resolveEnabledOrNull(u, "employeeNumber", trimmed))
                .orElseGet(() -> {
                    log.warn("v3.10: projectManagerEmployeeId '{}' 无匹配用户，projectManagerId 保持 null", trimmed);
                    return null;
                });
    }

    /**
     * 工号优先 + 姓名作校验的组合解析（v3.10 新增）。
     * <p>策略：
     * <ul>
     *   <li>工号非空 → 按工号精确匹配，命中即返回 user_id；
     *       若同时传入姓名且与库中 user.fullName 不一致，仅 log warn 不阻断（工号优先级高于姓名）</li>
     *   <li>工号未命中 → 按 username 回落（与 {@code TenderAutoAssignmentService.resolveManagerNameBySaleNo} 对齐）</li>
     *   <li>工号和 username 都未命中 → 回落到 {@link #resolveByFullName(String)}（含重名跳过逻辑）</li>
     * </ul>
     *
     * <p>所有路径命中的 user 都会经过 {@link UserEnabledStatusService#isEnabled(User)} 检查，停用用户返回 null。
     *
     * @param employeeNumber 工号（可空）
     * @param fullName       姓名（可空，仅作校验）
     * @return 解析到的 user_id；都为空或都未命中返回 null
     */
    public Long resolveByEmployeeNumberThenName(String employeeNumber, String fullName) {
        if (employeeNumber != null && !employeeNumber.isBlank()) {
            String trimmedEmpNo = employeeNumber.trim();
            var matched = userRepository.findByEmployeeNumber(trimmedEmpNo);
            if (matched.isPresent()) {
                User user = matched.get();
                warnIfNameMismatch(user, fullName, trimmedEmpNo, "employeeNumber");
                return resolveEnabledOrNull(user, "employeeNumber", trimmedEmpNo);
            }
            // 工号未命中，尝试 username 回落（与 TenderAutoAssignmentService 对齐）
            var byUsername = userRepository.findByUsername(trimmedEmpNo);
            if (byUsername.isPresent()) {
                User user = byUsername.get();
                warnIfNameMismatch(user, fullName, trimmedEmpNo, "username");
                return resolveEnabledOrNull(user, "username", trimmedEmpNo);
            }
            // 工号和 username 都未命中，回落姓名匹配
            log.warn("v3.10: projectManagerEmployeeId '{}' 无匹配用户（employeeNumber/username 均未命中），回落姓名匹配", trimmedEmpNo);
        }
        return resolveByFullName(fullName);
    }

    /**
     * 姓名不符告警（仅 log，不阻断）。工号/username 命中后调用，保持两条路径行为一致。
     * <p>使用 {@link Objects#hashCode} 兜底 null，避免库中 fullName 为 null 时 NPE。
     */
    private void warnIfNameMismatch(User user, String fullName, String keyValue, String matchBy) {
        if (fullName == null || fullName.isBlank()) {
            return;
        }
        String trimmedName = fullName.trim();
        if (!trimmedName.equals(user.getFullName())) {
            log.warn("v3.10: projectManagerEmployeeId '{}' 按 {} 命中用户但姓名不符（hash 库中={}, hash 传入={}），按 {} 落库",
                    keyValue, matchBy,
                    Integer.toHexString(Objects.hashCode(user.getFullName())),
                    Integer.toHexString(trimmedName.hashCode()),
                    matchBy);
        }
    }

    /**
     * 统一处理命中用户的停用过滤。
     * <p>启用 → 返回 user.id；停用 → 返回 null 并告警（与 {@code TenderAutoAssignmentService} 行为一致）。
     *
     * @param user    命中的用户（非 null）
     * @param matchBy 命中字段（用于日志，如 "employeeNumber" / "fullName"）
     * @param keyValue 命中键值（用于日志）
     * @return 启用返回 user.id；停用返回 null
     */
    private Long resolveEnabledOrNull(User user, String matchBy, String keyValue) {
        if (!userEnabledStatusService.isEnabled(user)) {
            log.warn("v3.10: {} '{}' 命中用户 id={} 但已停用，跳过 id 绑定避免绑定离职员工",
                    matchBy, keyValue, user.getId());
            return null;
        }
        return user.getId();
    }

    /**
     * 应用项目负责人字段到 Tender 实体（清洗姓名 + 反查 user_id + 写入）。
     * <p>CO-333: 同步解析 user_id，命中可见性锚点；重名/无匹配跳过避免误绑。
     * <p>v3.10: 工号优先解析。employeeId 非空时按工号精确匹配 + username 回落；
     * 工号空或未命中时回落姓名匹配（重名仍跳过避免误绑）。
     * <p>仅当 projectManagerName 非空时更新姓名，避免覆盖已有值；
     * employeeId 非空但 name 为空时，仍尝试按工号解析 user_id（不更新姓名字段）。
     *
     * @param tender                   目标 Tender 实体（非 null）
     * @param projectManagerEmployeeId 项目负责人工号（可空）
     * @param projectManagerName       项目负责人姓名（可空）
     */
    public void applyTo(Tender tender, String projectManagerEmployeeId, String projectManagerName) {
        // 一次性清洗姓名，后续复用（避免重复调用 sanitizeString）
        String managerName = projectManagerName != null
                ? InputSanitizer.sanitizeString(projectManagerName, 100)
                : null;
        if (managerName != null) {
            tender.setProjectManagerName(managerName);
        }
        String employeeId = projectManagerEmployeeId != null
                ? projectManagerEmployeeId.trim()
                : null;
        if (employeeId == null || employeeId.isBlank()) {
            // 无工号，仅按姓名反查
            if (managerName != null) {
                tender.setProjectManagerId(resolveByFullName(managerName));
            }
            return;
        }
        // 工号优先 + 姓名作校验
        tender.setProjectManagerId(resolveByEmployeeNumberThenName(employeeId, managerName));
    }

    /**
     * 标准化姓名：去除所有空白字符、统一中间点为半角、全角转半角。
     */
    String normalize(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name;
        // 全角空格 → 半角空格
        normalized = normalized.replace(FULLWIDTH_SPACE, ' ');
        // 去除所有空白字符（前后 + 中间）
        normalized = normalized.replaceAll("\\s+", "");
        // 统一中间点：全角· → 半角.
        normalized = normalized.replace(FULLWIDTH_MIDDLE_DOT, HALFWIDTH_MIDDLE_DOT);
        // 全角转半角（适用于全角字母/数字等）
        normalized = toHalfWidth(normalized);
        return normalized;
    }

    /**
     * 全角转半角。
     */
    private String toHalfWidth(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            // 全角范围：\uFF01-\uFF5E 对应半角 !-~
            if (c >= '\uFF01' && c <= '\uFF5E') {
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
