# Quickstart: 修复 OSS 用户权限扩散导致越权看所有菜单

**Feature**: specs/032-fix-oss-permission-diffusion
**Date**: 2026-07-08

## 前置条件

- 主工作区 `/Users/user/xiyu/worktrees/trae` 开发环境已启动（前端 1323 / 后端 18089）
- 数据库 `xiyu_bid_main` 已有 03063/06234 用户记录（或可手动创建测试用户）
- OSS Mock 环境或真实 OSS 可用（用于触发 OSS 登录链路）

## 验证步骤

### 1. 后端单元测试（TDD Red → Green）

```bash
cd /Users/user/xiyu/worktrees/claude/backend

# 先跑测试（Red 阶段，预期失败）
mvn test -Dtest=UserDetailsServiceImplTest#ossAdminUser_shouldNotHaveAllPermission
mvn test -Dtest=UserDetailsServiceImplTest#ossAdminUser_shouldNotHaveSystemAdminPermission
mvn test -Dtest=UserDetailsServiceImplTest#localAdminUser_shouldHaveAllPermission_regression
mvn test -Dtest=DataScopeConfigServiceTest#ossAdminUser_menuPermissionsShouldNotContainAll
mvn test -Dtest=DataScopeConfigServiceTest#localAdminUser_menuPermissionsShouldContainAll_regression

# 实现修复后（Green 阶段，预期通过）
mvn test -Dtest=UserDetailsServiceImplTest,DataScopeConfigServiceTest
```

### 2. 前端单元测试

```bash
cd /Users/user/xiyu/worktrees/claude

# 先跑测试（Red 阶段，预期失败）
npm run test:unit -- --grep "hasPermission.*ossUser"

# 实现修复后（Green 阶段，预期通过）
npm run test:unit
```

### 3. 架构边界测试（确保不破坏现有架构）

```bash
cd /Users/user/xiyu/worktrees/claude/backend
mvn test -Dtest=ArchitectureTest
mvn test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest
```

### 4. 前端构建验证

```bash
cd /Users/user/xiyu/worktrees/claude
npm run build
```

### 5. 本地联调验证（仅主工作区 trae）

```bash
cd /Users/user/xiyu/worktrees/trae
export XIYU_DEV_CONFIRMED=1
npm run dev:all
```

**验证场景 A: 本地 admin 登录（回归测试）**
1. 访问 `http://127.0.0.1:1323`
2. 用 `admin` / `XiyuAdmin2026!` 登录
3. 预期：看到系统所有菜单（行为不变）
4. 访问需要 `system.admin` 权限的接口（如系统设置）→ 预期 200

**验证场景 B: OSS 用户登录（修复验证，需准备 OSS Mock 或真实 OSS）**
1. 用 OSS 用户登录（OSS 端配 "投标系统管理员" 角色）
2. 预期：只看到 OSS 返回的菜单 codes 对应的菜单项
3. 访问需要 `system.admin` 权限的接口 → 预期 403

### 6. 生产环境验证（部署后）

```bash
# SSH 到生产服务器，查看 03063/06234 登录链路日志
ssh jetty@172.16.38.78 '
  sudo grep -h "03063\|06234" /var/log/xiyu-bid/application.json.log \
    | grep "UserDetails authorities built" \
    | tail -n 4 \
    | python3 -c "
import sys, json
for line in sys.stdin:
    d = json.loads(line)
    msg = d.get(\"message\",\"\")
    # 检查 authorities 是否含 all / system.admin
    has_all = \"'all'\" in msg
    has_sysadmin = \"system.admin\" in msg
    print(f\"{d.get(\"@timestamp\",\"\")[:19]} all={has_all} system.admin={has_sysadmin}\")
"
'
```

**预期输出**：
- `all=False system.admin=False`（修复生效）
- 不应出现 `all=True` 或 `system.admin=True`

## 故障排查

| 症状 | 可能原因 | 解决方案 |
|---|---|---|
| 本地 admin 看不到所有菜单 | 修改点 1/2 过度收紧 | 检查 `!isOssUser` 守卫是否误伤本地 admin |
| OSS 用户仍看到所有菜单 | 缓存未刷新 | 让用户重新登录（清 Redis OSS 缓存 key） |
| OSS 用户登录失败 | `isOssUser` 判断错误 | 检查 `externalOrgSourceApp` 字段是否为空 |
| 前端 hasPermission 报错 | `isOssUser` 字段未传到前端 | 检查 `AuthResponse` 是否新增字段并正确填充 |
