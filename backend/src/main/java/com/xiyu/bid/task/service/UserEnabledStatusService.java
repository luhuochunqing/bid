// Input: User entity
// Output: enabled status decision (considering OSS vs local users)
// Pos: Service/用户状态判断层
// 维护声明: 统一判断用户启用状态；OSS 用户以认证成功为准，本地用户检查 enabled 字段。
package com.xiyu.bid.task.service;

import com.xiyu.bid.entity.User;
import org.springframework.stereotype.Service;

@Service
public class UserEnabledStatusService {

    public boolean isEnabled(User user) {
        if (user == null) {
            return false;
        }
        if (isOssUser(user)) {
            return true;
        }
        return Boolean.TRUE.equals(user.getEnabled());
    }

    private boolean isOssUser(User user) {
        String sourceApp = user.getExternalOrgSourceApp();
        return sourceApp != null && !sourceApp.isBlank();
    }
}