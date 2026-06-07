// Input: JWT jti 与过期时间
// Output: 撤销/查询撤销状态
// Pos: Auth/Token 撤销层
// 维护声明: 仅维护 access token 撤销契约；存储后端切换不应改动调用方语义.
package com.xiyu.bid.auth;

import java.time.Instant;

public interface TokenRevocationService {

    void revoke(String jti, Instant expiresAt);

    boolean isRevoked(String jti);
}
