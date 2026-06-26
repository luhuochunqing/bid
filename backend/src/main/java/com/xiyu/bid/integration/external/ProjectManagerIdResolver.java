package com.xiyu.bid.integration.external;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

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

    /**
     * @param fullName 全名（null/空返回 null）
     * @return 唯一匹配返回 id；否则 null
     */
    public Long resolveByFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String trimmed = fullName.trim();

        // 1. 精确匹配
        List<User> matches = userRepository.findByFullName(trimmed);
        if (matches.size() == 1) {
            return matches.get(0).getId();
        }

        // 2. 精确匹配失败，尝试标准化后匹配
        String normalized = normalize(trimmed);
        if (!normalized.equals(trimmed)) {
            matches = userRepository.findByFullName(normalized);
            if (matches.size() == 1) {
                return matches.get(0).getId();
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
