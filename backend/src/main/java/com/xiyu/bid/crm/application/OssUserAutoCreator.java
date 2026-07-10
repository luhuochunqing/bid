package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.organization.application.OrganizationUserSyncWriter;
import com.xiyu.bid.repository.RoleProfileRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * OSS 登录成功后自动创建本地 User 记录。
 * <p>
 * <b>根因背景</b>：设计意图是"OSS 实时鉴权是唯一真相源"（{@link OssLoginFlowService#authenticateDirect}
 * 走完后 roleCode 已写入 {@link OssPermissionCache}），但 {@code AuthService.login} 和
 * {@code HomeSsoService.ssoLogin} 都要求本地 {@code users} 表有记录才能生成 JWT/RefreshSession。
 * 当 OSS 同步（Kafka 事件）未为某用户推送事件时（如新员工、角色未映射被跳过），
 * 登录会因本地无记录而失败，违反"OSS 实时鉴权"的设计意图。
 * <p>
 * <b>修复方案</b>：OSS 实时鉴权成功后，若本地无记录，自动创建最小 User 记录，
 * 仅用于满足 JWT/RefreshSession 的外键约束。角色信息由 OSS 实时抓取决定，
 * 不依赖本地 roleProfile。
 * <p>
 * <b>标识字段</b>：{@code externalOrgSourceApp = "oss-login"} 标识自动创建，
 * 后续 Kafka 同步事件到达时，{@code OrganizationUserSyncWriter} 通过 username fallback
 * 找到该记录并更新为正式同步用户。
 *
 * @see OssLoginFlowService#authenticateDirect
 * @see com.xiyu.bid.integration.organization.application.OrganizationUserSyncWriter
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssUserAutoCreator {

    /** 标识通过登录自动创建的 OSS 用户，区别于 Kafka 同步的 sourceApp（如 "home"）。 */
    public static final String AUTO_CREATE_SOURCE_APP = "oss-login";

    private final UserRepository userRepository;
    private final RoleProfileRepository roleProfileRepository;
    private final OssPermissionCache ossPermissionCache;

    /**
     * OSS 登录成功后，若本地无 User 记录则自动创建。
     * <p>
     * 方法幂等：若并发登录或重复调用，已存在的 User 会被复用，不会重复创建。
     *
     * @param loginResult OSS 登录结果（必须已通过鉴权）
     * @return 新建或已存在的本地 User
     * @throws IllegalStateException 当 OSS 缓存无 roleCode 时（fail-closed，不允许无角色用户自动创建）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User autoCreateIfAbsent(OssLoginResult loginResult) {
        String username = loginResult.username();
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("OSS login result has no username, cannot auto-create user");
        }

        Optional<User> existing = userRepository.findByUsername(username);
        if (existing.isPresent()) {
            log.debug("OSS auto-create skipped, user already exists: username={}", username);
            return existing.get();
        }

        String roleCode = ossPermissionCache.getRoleCode(username)
                .orElseThrow(() -> new IllegalStateException(
                        "OSS auto-create refused: no cached roleCode for user=" + username
                                + " (OSS real-time auth must populate cacheOssPermissions first)"));

        JsonNode employeeInfo = loginResult.employeeInfo();
        String fullName = extractText(employeeInfo, "name", "userName", "fullName", "nickName");
        String email = extractText(employeeInfo, "email", "mail");
        String phone = extractText(employeeInfo, "mobilePhone", "mobile", "phone", "telephone");
        String deptCode = extractText(employeeInfo, "deptCode", "departmentCode", "deptId");
        String deptName = extractText(employeeInfo, "deptName", "departmentName");

        if (fullName == null || fullName.isBlank()) {
            fullName = username;
        }
        // email unique+not null，OSS 未返回时用占位符（后续 Kafka 同步可覆盖）
        if (email == null || email.isBlank()) {
            email = username + "@oss-login.local";
        }

        RoleProfile roleProfile = roleProfileRepository.findByCodeIgnoreCase(roleCode).orElse(null);
        User.Role legacyRole = RoleProfileCatalog.legacyRoleForCode(roleCode);
        if (legacyRole == null) {
            // 不应发生：roleCode 已通过 LoginRoleWhitelist 校验
            legacyRole = User.Role.MANAGER;
            log.warn("OSS auto-create: legacyRoleForCode returned null for roleCode={}, falling back to MANAGER", roleCode);
        }

        User user = User.builder()
                .username(username)
                .password(OrganizationUserSyncWriter.LOCKED_PASSWORD_HASH)
                .email(email)
                .fullName(fullName)
                .phone(phone)
                .role(legacyRole)
                .roleProfile(roleProfile)
                .enabled(true)
                .departmentCode(deptCode)
                .departmentName(deptName)
                .employeeNumber(username)
                .externalOrgUserId(username)
                .externalOrgSourceApp(AUTO_CREATE_SOURCE_APP)
                .build();

        User saved = userRepository.save(user);
        log.info("OSS auto-created local user: username={}, userId={}, roleCode={}",
                username, saved.getId(), roleCode);
        return saved;
    }

    /** 提取 JsonNode 多字段首个非空文本值。 */
    private String extractText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value != null && !value.isMissingNode() && !value.isNull()) {
                String text = value.asText("");
                if (!text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }
}
