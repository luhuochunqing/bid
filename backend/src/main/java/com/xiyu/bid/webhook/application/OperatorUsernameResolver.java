// Input: 操作者用户主键
// Output: 操作者用户名（username）
// Pos: webhook/application/ - webhook 应用层共享组件
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.webhook.application;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 操作者用户名解析器。
 * <p>通过 userId 反查 username，供 webhook 异步投递时取该用户的 OSS token 调 CRM generateToken，
 * 避免依赖全局共享账号（CO-152）。
 *
 * <p><b>验收预期：</b>
 * <ul>
 *   <li>CRM HTTP 调用实际发生在 AFTER_COMMIT 监听器入队后，由异步投递任务触发，不在入队阶段同步执行。</li>
 *   <li>返回的 username 必须对应一名已登录过 OSS 的用户；若 username 为空、或 OSS token 已过期、
 *       或用户从未在系统内完成 OSS 授权，则 CRM 反查仍会失败，不能仅靠 externalId 在无 token 时换取 CRM JWT。</li>
 *   <li>因此，弃标/项目结果确认等会触发 CRM 回调的操作，必须由已完成 OSS 登录的操作者执行，
 *       否则 webhook 投递侧按自身策略处理（如失败重试或死信）。</li>
 * </ul>
 *
 * <p>查不到或 userId 为空时返回 {@code null}，调用方按自身策略处理。
 */
@Component
@RequiredArgsConstructor
public class OperatorUsernameResolver {

    private final UserRepository userRepository;

    /**
     * 解析操作者用户名。
     *
     * @param operatorId 操作者用户主键
     * @return 用户名；userId 为空或查不到时返回 {@code null}
     */
    public String resolve(Long operatorId) {
        if (operatorId == null) {
            return null;
        }
        return userRepository.findById(operatorId)
                .map(User::getUsername)
                .orElse(null);
    }
}
