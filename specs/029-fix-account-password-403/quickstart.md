# Quickstart: 修复平台账号密码查看权限异常类型误用

**Date**: 2026-07-05

## 验证步骤

### 1. 单元测试

```bash
cd /Users/user/xiyu/worktrees/trae/backend
mvn test -Dtest=PlatformAccountServiceTest
```

预期：所有测试通过，包括新增的 5 个权限校验测试用例。

### 2. 集成测试（如已存在）

```bash
cd /Users/user/xiyu/worktrees/trae/backend
mvn test -Dtest=PlatformAccount*IntegrationTest
```

### 3. 手动验证（主工作区 trae）

1. 启动开发环境：`cd /Users/user/xiyu/worktrees/trae && export XIYU_DEV_CONFIRMED=1 && npm run dev:all`
2. 以非管理员账号登录（如 `bid_specialist` / `Test@123`）
3. 尝试查看某个非自己绑定联系人的平台账号密码
4. 预期：
   - 响应状态码：403
   - 响应 message："权限不足，无法访问该资源"
   - 后端日志：WARN 级"权限不足 - URI: /api/platform/accounts/{id}/password"
   - **不上报 Sentry**

### 4. 回归验证

```bash
cd /Users/user/xiyu/worktrees/trae/backend
mvn test -Dtest=ArchitectureTest
mvn test -Dtest=GlobalExceptionHandlerTest
```

## 影响范围

- 仅修改 `PlatformAccountService.getPassword` 方法（3 处异常类型替换）
- 不影响其他 Service 方法
- 不影响 `GlobalExceptionHandler`（已有 handler 复用）
- 不影响 `@Auditable` 切面
- 不影响前端代码（前端只识别 4xx 状态码）

## 回滚方案

如出现问题，直接 revert 本次 PR 即可。无数据库迁移，无外部依赖变更，回滚零风险。
