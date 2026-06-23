# Quickstart: 验证 003-remove-staff-unify-oss-enabled

## 后端验证

```bash
cd /Users/user/xiyu/worktrees/trae/backend

# 1. 架构测试
mvn test -Dtest=ArchitectureTest

# 2. 登录与权限相关测试
mvn test -Dtest=AuthServiceTest,OssLoginFlowServiceTest,UserDetailsServiceImplTest

# 3. 全量测试
mvn test
```

## 前端验证

```bash
cd /Users/user/xiyu/worktrees/trae

# 1. 静态检查
npm run check:front-data-boundaries
npm run check:doc-governance
npm run check:line-budgets

# 2. 单元测试
npm run test:unit

# 3. 构建
npm run build
```

## 手动验证清单

### 场景 1：staff 用户无法登录

1. 在 OSS 侧准备一个未映射到 7 个业务角色的账号（或等待同步一个普通员工）。
2. 尝试登录系统。
3. 期望：收到"当前账号无系统访问权限"，无 JWT 返回。

### 场景 2：OSS 离职用户无法登录

1. 在 OSS 侧将一个已映射业务角色的账号标记为离职/禁用。
2. 等待同步完成（或触发同步）。
3. 尝试登录。
4. 期望：收到"账号已停用"。

### 场景 3：启用用户出现在所有选人控件

1. 确认一个业务角色有效且 `enabled=true` 的账号。
2. 打开项目负责人转派、任务分配、评审人选择等弹窗。
3. 期望：该账号均出现在候选人列表中。

### 场景 4：系统设置页不再启用/停用 OSS 用户

1. 以 admin 登录，进入系统设置-账户管理。
2. 选择一个 OSS 同步用户。
3. 期望：看不到"启用/停用"开关，仅显示同步状态。

### 场景 5：本地管理员账号仍可登录

1. 断开 OSS 或仅启动本地环境。
2. 使用 `admin` / `DefaultAdminInitializer` 密码登录。
3. 期望：登录成功。
