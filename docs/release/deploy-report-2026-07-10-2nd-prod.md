# 第 2 次生产环境部署报告

> **环境**：生产（prod）
> **部署类型**：增量升级部署
> **部署日期**：2026-07-10
> **Release ID**：`6a0503e1d`
> **部署状态**：✅ 成功

---

## 1. 部署概览

| 项目 | 值 |
|------|-----|
| 环境 | 生产（prod） |
| 服务器 | `172.16.10.149`（winbid-01.prod） |
| 域名 | `https://winbid.ehsy.com/` |
| Release ID | `6a0503e1d` |
| 部署时间 | 2026-07-10 13:14:20 CST |
| 健康检查通过 | 13:14:48 CST（约 28 秒，3/3 连续通过） |
| 服务状态 | active (running) |
| 部署次数 | 第 2 次（生产环境） |
| 前一次部署 | 2026-07-09 首次生产 (`e88dbd207`) |

---

## 2. 基线信息

| 项目 | 值 |
|------|-----|
| 仓库 | `gitee.com:allinai888/bid.git` |
| 分支 | origin/main |
| HEAD commit | `6a0503e1d`（!1980 refactor(spec-033)） |
| 前一次 commit | `e88dbd207`（!1965 第 68 次部署报告） |
| 前端构建模式 | 同源构建（`VITE_API_BASE_URL=` 空） |
| 后端 profiles | `prod,mysql` |
| 后端端口 | `18080`（Nginx 8080 反代） |
| 数据库 | `winbid` @ `winbid-01.prod.rds.ehsy.com:3306` |

---

## 3. 改动范围

### 3.1 增量 PR 列表（14 个 PR）

| PR | 类型 | 说明 |
|----|------|------|
| !1969 | fix | 跨部门协作人员首页 403 修复（Workbench 权限收窄） |
| !1970 | docs | 首次生产接口联调记录 |
| !1971 | feat | 投标专员可查看全量保证金，项目负责人只读账户/CA |
| !1972 | fix | OSS 密码登录失败误抛 RoleNotAuthorizedException |
| !1973 | docs | 新增 3 份首次上线文档 |
| !1974 | fix(test) | 修复 17 个 standaloneSetup 测试 |
| !1975 | fix | 投标专员可查看全量保证金数据 |
| !1976 | fix | 弃标时 CRM 回调双触发修复 (CO-570) |
| !1977 | fix | OssRoleResolver 遍历 sysRoleList 优先检查 roleCode（04569 生产登录 Bug） |
| !1978 | refactor | spec-033 OSS/本地权限代码路径分离 |
| !1979 | fix | AI json_schema → json_object fallback 扩大触发条件 |
| !1980 | refactor | spec-033 设计评估修复 — 异常类型 + 逻辑重复 + 测试冗余 |

### 3.2 Flyway 迁移

| 版本 | 文件 | 说明 |
|------|------|------|
| V1162 | `V1162__add_margin_permission_to_bid_specialist.sql` | 为 bid-Team 角色追加 resource-margin 菜单权限（幂等 UPDATE） |

### 3.3 后端代码变更（9 个文件）

- `auth/UserDetailsServiceImpl.java`
- `biddraftagent/infrastructure/openai/OpenAiSdkStructuredOutputTransport.java`
- `crm/application/OssLoginFlowService.java`
- `crm/application/OssRoleResolver.java`
- `entity/RoleProfileCatalog.java`
- `integration/organization/domain/policy/JobRoleLookupResolver.java`
- `resources/service/MarginQueryRole.java`
- `service/AuthService.java`
- `tender/service/TenderStatusSyncService.java`

### 3.4 前端代码变更（6 个文件）

- `stores/loginFailureMessage.js`（新增）
- `stores/loginFailureMessage.spec.js`（新增）
- `views/Dashboard/Workbench.vue`（权限收窄）
- `views/Resource/__tests__/Account.spec.js`
- `views/Resource/__tests__/CAManagement.spec.js`
- `views/Resource/__tests__/accountActions.spec.js`

---

## 4. Flyway 预检结果

### Step 1: 服务器 validate

```
Detected resolved migration not applied to database: 1162.
```

V1162 为 pending 状态（预期行为，新迁移待应用）。无 checksum mismatch。

### Step 2: DB 版本对比

| 检查项 | 结果 |
|--------|------|
| DB 最新已应用版本 | V1161 (ca related platforms text) |
| 源码最新版本 | V1162 |
| failed 迁移数 | 0（全部 success=1） |
| checksum mismatch | 无 |

### Step 3: remote-deploy 内置 validate

使用 `SKIP_FLYWAY_VALIDATE=1` 跳过，因为 V1162 是 pending（非 mismatch），新 jar 启动时自动应用。

---

## 5. 部署步骤

| 步骤 | 时间 | 结果 |
|------|------|------|
| 早操三连（sync-env + check-git-wrapper） | 13:05 | ✅ 7 道门禁通过 |
| 环境门禁确认 | 13:06 | ✅ 用户确认生产环境 |
| 服务器现状检查 | 13:06 | ✅ 旧 release e88dbd207 运行中 |
| Flyway 预检 | 13:10 | ✅ V1161 最新，V1162 pending |
| 上传迁移文件到 /tmp/migration-mysql/ | 13:10 | ✅ 完成 |
| 本地打包（RELEASE_ID=6a0503e1d） | 13:12 | ✅ BUILD SUCCESS（27 秒） |
| 产物校验 | 13:12 | ✅ V1162 在 jar 内，224 文件无重复 |
| 上传 release 包 | 13:13 | ✅ scp 完成 |
| 执行 remote-deploy.sh | 13:14 | ✅ DB 备份 + 前端激活 + 后端重启 |
| 健康检查通过 | 13:14:48 | ✅ 3/3 连续（28 秒，无 Kafka 延迟） |
| V1162 迁移应用 | 13:14:26 | ✅ success=1 |
| Smoke 测试 | 13:15 | ✅ 全部通过 |
| GitHub 镜像同步 | 13:16 | ✅ 两边 main 一致 |

---

## 6. 验证结果

### 6.1 后端健康

```json
{
  "status": "UP",
  "components": {
    "aiProvider": { "status": "UP", "provider": "custom", "model": "qwen3.7-max", "apiKeyConfigured": true },
    "db": { "status": "UP", "database": "MySQL" },
    "diskSpace": { "status": "UP", "free": "93GB" }
  }
}
```

### 6.2 Smoke 测试

| 接口 | 端口 | HTTP Code | 说明 |
|------|------|-----------|------|
| /actuator/health | 18080 | 200 | 后端健康 |
| /actuator/health/readiness | 18080 | 200 | 就绪检查（无 Kafka 延迟） |
| /api/auth/login (POST empty) | 18080 | 400 | 空请求验证错误（预期） |
| /api/projects (no auth) | 18080 | 403 | 需认证（预期） |
| / (前端首页) | 8080 (Nginx) | 200 | 前端正常 |
| /login | 8080 (Nginx) | 200 | 登录页正常 |
| /api/projects (via Nginx) | 8080 | 403 | API 代理正常 |
| /actuator/health (via Nginx) | 8080 | 200 | actuator 代理正常 |

### 6.3 V1162 迁移验证

```
version: 1162
description: add margin permission to bid specialist
success: 1
installed_on: 2026-07-10 13:14:26
```

### 6.4 权限验证

bid-Team 角色 menu_permissions 末尾已包含 `resource-margin`。

### 6.5 前端一致性

入口 JS: `assets/index-Cdl3qYxE.js`（与 release 一致）

---

## 7. GitHub 镜像同步

| 项目 | 值 |
|------|-----|
| 同步前落后 | 2 commit |
| 同步后状态 | ✅ 完全一致 |
| Gitee main | `6a0503e1d` |
| GitHub main | `6a0503e1d` |

---

## 8. 回滚信息

| 项目 | 值 |
|------|-----|
| 旧 Release ID | `e88dbd207` |
| 旧 release 目录 | `/opt/xiyu-bid/releases/e88dbd207/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-6a0503e1d-*.sql.gz` |
| 回滚方式 | 恢复旧 jar + 手动 REVERSE V1162 |
| V1162 回滚 SQL | `UPDATE roles SET menu_permissions = REPLACE(menu_permissions, ',resource-margin', '') WHERE code = 'bid-Team';` |
| 回滚风险评估 | 低（V1162 是幂等 UPDATE，无 schema 变更） |

---

## 9. 临时配置检查

| 配置项 | 值 | 状态 |
|--------|-----|------|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` | `always` | 已知保留（首次生产部署决定） |
| `DEBUG` / `TRACE` / `LOG_LEVEL` | 未设置 | ✅ 无临时调试配置 |

---

## 10. 经验沉淀应用

| 经验 | 应用情况 |
|------|----------|
| Flyway 预检 3 步法 | ✅ 执行 validate + DB 版本对比 |
| Mac HTTP_PROXY 502 | ✅ 使用 `--noproxy '*'` 绕过 |
| VITE_API_BASE_URL= 同源构建 | ✅ 生产构建模式 |
| SYSTEMCTL_SUDO=true | ✅ jetty 用户 NOPASSWD sudo |
| COPYFILE_DISABLE=1 | ✅ scp 上传时设置 |
| 服务器 /tmp/migration-mysql/ 过时 | ✅ 上传最新迁移文件 |
| SKIP_FLYWAY_VALIDATE=1 | ⚠️ 首次使用（V1162 pending 非 mismatch） |
| Kafka SDK readiness 延迟 | ✅ 本次未出现（28 秒通过） |
| git.properties commit 不准确 | 已知问题（Lesson #9），不影响部署 |

---

## 11. 风险提示

1. **V1162 无 rollback 脚本**：迁移是幂等 UPDATE，可手动 REVERSE，但未按规范配套 U1162 回滚脚本
2. **spec-033 权限路径分离**：涉及 auth 核心逻辑重构，需关注 OSS 用户登录行为
3. **SKIP_FLYWAY_VALIDATE=1**：首次在生产使用此跳过选项，因 V1162 是 pending（非 checksum mismatch），经确认安全后使用
4. **生产有活跃用户**：部署期间后端重启约 28 秒，可能有短暂请求失败

---

## 12. 部署确认清单

- [x] 环境门禁确认（用户显式确认生产环境）
- [x] 早操三连（sync-env + check-git-wrapper）
- [x] Flyway 预检 3 步法
- [x] 本地打包（同源构建）
- [x] 产物校验（jar 内迁移 + 前端入口）
- [x] DB 备份
- [x] 后端重启 + 健康检查
- [x] V1162 迁移应用验证
- [x] Smoke 测试（health + readiness + API + 前端）
- [x] GitHub 镜像同步
- [x] 临时配置检查
- [x] 部署报告生成
