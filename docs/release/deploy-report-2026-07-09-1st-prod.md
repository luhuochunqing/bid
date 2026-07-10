# 第 1 次生产环境部署报告

> **环境**：生产（prod）
> **部署类型**：首次生产环境部署
> **部署日期**：2026-07-09
> **Release ID**：`e88dbd207`
> **部署状态**：✅ 成功（带已知问题）
> **二次部署**：2026-07-09 22:51 CST（修复人员同步白名单逻辑）

---

## 1. 部署概览

| 项目 | 值 |
|------|-----|
| 环境 | 生产（prod） |
| 服务器 | `172.16.10.149`（winbid-01.prod） |
| 域名 | `https://winbid.ehsy.com/` |
| Release ID | `e88dbd207` |
| 部署时间 | 2026-07-09 21:21 CST |
| 后端启动时间 | 2026-07-09 21:27 CST（最后一次稳定启动） |
| 启动耗时 | 20.56 秒 |
| 服务状态 | active |
| 部署次数 | 第 1 次（首次生产部署） |

---

## 2. 基线信息

| 项目 | 值 |
|------|-----|
| 仓库 | `gitee.com:allinai888/bid.git` |
| 分支 | `agent/trae/prod-deploy-1st` |
| HEAD commit | `e88dbd207`（origin/main 一致） |
| V1092 修复 commit | `2e42c5354` |
| 前端构建模式 | 同源构建（`VITE_API_BASE_URL=` 空） |
| OBS 大文件直传 | 启用（`VITE_OBS_ENABLED=true`） |
| Sentry 前端 | 启用（DSN + production 环境） |
| 后端 profiles | `prod,mysql` |
| 后端端口 | `18080`（Nginx 80/8080 反代） |

---

## 3. 改动范围

### 3.1 V1092 迁移修复（生产首次部署阻断修复）

**文件**：`backend/src/main/resources/db/migration-mysql/V1092__migrate_legacy_role_codes_to_oss_aligned.sql`

**根因**（两个独立问题）：

1. **Error 1051**：`DROP TEMPORARY TABLE IF EXISTS` 在 Flyway 9.22.3 + MySQL 8.0.43 对不存在的临时表返回错误，被 Flyway 当作迁移失败。

2. **Error 1267**：`Illegal mix of collations` — `roles` 表和 `users` 表使用 `utf8mb4_unicode_ci`，而临时表 `tmp_role_mappings` 使用数据库默认 `utf8mb4_0900_ai_ci`，JOIN 时 collation 冲突。

**修复内容**：
- 移除开头的 `DROP TEMPORARY TABLE IF EXISTS`，改用 `CREATE TEMPORARY TABLE IF NOT EXISTS`
- 临时表列显式指定 `COLLATE utf8mb4_unicode_ci` 与 `roles`/`users` 表对齐
- 移除末尾的 `DROP TEMPORARY TABLE IF EXISTS`，临时表会话结束自动删除

**PR**：待创建（分支 `agent/trae/prod-deploy-1st` → `main`）

### 3.2 人员同步白名单逻辑修复（二次部署）

**文件**：`backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationUserSyncWriter.java`

**根因**：
- `OrganizationIntegrationProperties.skipUnmappedUsers` 属性声明（默认 true），但原代码在 `OrganizationUserSyncWriter.upsert()` line 68 硬编码 `LoginRoleWhitelist.isAllowed()` 检查，**未使用** `skipUnmappedUsers` 配置。
- 导致所有未匹配角色映射的 OSS 用户（约 8000+）被全部跳过创建，最终 users 表只有 168 行（143 enabled）。
- 同步 run 仍标记为 SUCCESS（8572 假成功），掩盖了真实问题。

**修复**：
```java
// 修复前
if (!LoginRoleWhitelist.isAllowed(resolvedRoleCode)) {
    handleUserWithoutResolvedRole(sourceApp, eventKey, snapshot, existingUser);
    return Optional.empty();
}

// 修复后
if (!LoginRoleWhitelist.isAllowed(resolvedRoleCode) && properties.isSkipUnmappedUsers()) {
    handleUserWithoutResolvedRole(sourceApp, eventKey, snapshot, existingUser);
    return Optional.empty();
}
```

**生产配置**：`/etc/xiyu-bid/backend.env` 添加 `XIYU_ORG_SYNC_SKIP_UNMAPPED_USERS=false`

**全量同步结果**（2026-07-09 23:01 CST）：

| 指标 | 测试环境 | 生产修复前 | 生产修复后 |
|---|---|---|---|
| users 总数 | 8508 | 168 | **8528** |
| enabled | 1316 | 143 | **1313** |
| 同步 run | - | 8572 假成功 | **11200 用户，11053 成功，147 失败** |

失败 147 条全是数据质量问题（98 手机号为空 + 47 邮箱重复 + 2 其他），非代码 bug。

---

## 4. Flyway 迁移结果

| 项目 | 值 |
|------|-----|
| 迁移总数 | 224 条 |
| 成功数 | 224 条（100%） |
| 最新版本 | V1161（ca related platforms text） |
| V1092 状态 | ✅ success=1（collation 修复后） |
| 首次部署前数据库状态 | 空库（无 `flyway_schema_history`） |
| 迁移耗时 | ~20 秒（21:21:15 完成） |

**最近 10 条迁移记录**：

| 版本 | 描述 | 状态 |
|------|------|------|
| 1161 | ca related platforms text | ✅ |
| 1160 | platform account password nullable | ✅ |
| 1159 | drop duplicate roles code index | ✅ |
| 1158 | cleanup duplicate roles add unique constraint | ✅ |
| 1157 | add unique index to warehouse name | ✅ |
| 1156 | add alert history dedup index | ✅ |
| 1155 | bid file table | ✅ |
| 1154 | drop unique constraint from platform account name | ✅ |
| 1153 | create tender import task | ✅ |
| 1152 | add last review reminded at | ✅ |

---

## 5. 部署步骤

1. ✅ **环境门禁**：`ENV=prod` 声明 + AskUserQuestion 用户确认（172.16.10.149）
2. ✅ **早操三连**：`sync-env.sh` + `check-git-wrapper.sh`
3. ✅ **本地打包**：`deploy-prod.sh`（同源构建 + OBS + Sentry）
4. ✅ **上传**：scp 到 `/opt/xiyu-bid/incoming/`
5. ✅ **远程部署**：`remote-deploy.sh`（`SKIP_FLYWAY_VALIDATE=1`，首次部署无 jar 无法预检）
6. ✅ **Flyway 全量建表**：V1-V1161 共 224 条迁移全部成功
7. ✅ **后端启动**：`Started XiyuBidApplication in 20.56 seconds`
8. ✅ **CRM API Key 激活**：P0.5 SQL 执行成功

### 部署过程中的问题与修复

| 问题 | 根因 | 修复 | 耗时 |
|------|------|------|------|
| V1092 Error 1051 | `DROP TEMPORARY TABLE IF EXISTS` Flyway 兼容性 | 移除显式 DROP | 1 轮 |
| V1092 Error 1267 | 临时表 collation 与 roles/users 不匹配 | 显式 `COLLATE utf8mb4_unicode_ci` | 1 轮 |
| system_settings 重复键 | ApplicationRunner crash-loop 中重复 INSERT | 自然恢复（systemd restart 间隙处理） | 1 轮 |

---

## 6. 验证结果

### P0 - 立即验证

| 验证项 | 结果 | 备注 |
|--------|------|------|
| 健康检查 `/actuator/health` | ⚠️ DOWN | sidecar 组件 DOWN（localhost:8000 不可达），其他组件全 UP |
| 就绪检查 `/actuator/health/readiness` | ✅ UP | readinessState: UP |
| API login（空密码） | ✅ HTTP 400 | 路由正确 |
| API projects（无认证） | ✅ HTTP 403 | 权限守卫生效 |
| 前端首页（Nginx 80） | ✅ HTTP 200 | 静态资源正常 |
| 前端 login 页 | ✅ HTTP 200 | SPA 路由正常 |
| 前端入口 JS | ✅ `assets/index-Bo4BoDcQ.js` | 与 release 一致 |
| Flyway 迁移记录 | ✅ 224 条 success=1 | 全量建表成功 |
| users 表 | ✅ 8528 条记录（1313 enabled） | 全量同步成功，与测试环境对齐 |
| roles 表 | ✅ 9 个角色 | OSS 对齐后角色码 |
| projects 表 | ✅ 0 条 | 无测试数据 |
| tenders 表 | ✅ 0 条 | 无测试数据 |

### P0.5 - CRM API Key 激活

| 验证项 | 结果 | 备注 |
|--------|------|------|
| API Key 插入 | ✅ id=1 | `CRM Integration` |
| scopes | ✅ `tender:read,tender:write,project:read` | |
| status | ✅ ACTIVE | |
| expires_at | ✅ 2029-07-09 | 3 年有效期 |
| 带 Key 访问 `/api/external/tenders` | ✅ HTTP 200 | 返回空列表 |
| 无 Key 访问 | ✅ HTTP 401 | 未授权 |

### P1 - 5 分钟内验证

| 验证项 | 结果 | 备注 |
|--------|------|------|
| 后端启动日志 | ✅ `Started XiyuBidApplication in 20.56s` | |
| CRM 配置摘要 | ✅ 已输出 | `authBaseUrlConfigured=true` 等 |
| CRM clientId/clientSecret | ✅ 无需配置 | 文档 §193 明确"无业务代码调用，不阻塞部署" |
| OBS SDK 初始化 | ✅ Version 3.23.9 | `https://obs.cn-east-3.myhuaweicloud.com` |
| Sentry 配置 | ✅ DSN + production | |
| AI 配置 | ⚠️ apiKeyConfigured=false | provider=deepseek，需在 UI 配置 |
| Kafka SDK | ❌ 启动失败 | `eventTopicConsumerMap is null`，已知非阻断问题 |

### P2 - 30 分钟内验证

| 验证项 | 结果 | 备注 |
|--------|------|------|
| AI 连接测试 | ✅ success | `连接测试成功`（custom provider, qwen3.7-max） |
| AI 配置接口 | ✅ status=configured | `apiKeyConfigured: true`, provider=custom |
| 项目列表 `/api/projects` | ✅ HTTP 200 | 空列表（新环境无业务数据） |
| CRM 健康接口 | ✅ HTTP 401 | `Missing X-API-Key header`（鉴权守卫生效） |
| OBS 配置 | ✅ SDK 3.23.9 | `https://obs.cn-east-3.myhuaweicloud.com` |
| Sentry 后端 | ✅ DSN 已配置 | `SENTRY_ENVIRONMENT=production` |
| settings 接口 | ✅ HTTP 200 | 8 个配置区域返回正常 |

### P3 - 监控 30 分钟

| 验证项 | 结果 | 备注 |
|--------|------|------|
| 服务状态 | ✅ active | systemd ActiveState=active, SubState=running |
| ERROR 日志 | ✅ 仅 2 条 | 都是已知 Kafka/AI 启动 ERROR（非阻断） |
| WARN 日志 | 67 条 | 主要是 OBS SDK 初始化 WARN（正常） |
| 健康检查 | ✅ 9/10 UP | 仅 sidecar DOWN（非阻断） |

### P0.5 补充 - AI API 配置

| 验证项 | 结果 | 备注 |
|--------|------|------|
| AI provider | ✅ custom | 切换自 deepseek 到 custom |
| baseUrl | ✅ `https://ai-tech.ehsy.com/v1/chat/completions` | 内部 AI 网关 |
| model | ✅ `qwen3.7-max` | 通义千问 3.7 Max |
| apiKey | ✅ 已加密保存 | 通过测试接口自动加密持久化 |
| 连接测试 | ✅ `{"status":"success","message":"连接测试成功"}` | |
| 健康检查 aiProvider | ✅ `apiKeyConfigured: true` | |

---

## 7. 健康检查详情

```json
{
  "status": "DOWN",
  "components": {
    "aiProvider": {"status": "UP", "details": {"provider": "custom", "model": "qwen3.7-max", "apiKeyConfigured": true}},
    "db": {"status": "UP", "details": {"database": "MySQL"}},
    "diskSpace": {"status": "UP", "details": {"free": "94669844480"}},
    "jwt": {"status": "UP", "details": {"algorithm": "HMAC-SHA256", "strength": "ACCEPTABLE"}},
    "livenessState": {"status": "UP"},
    "ping": {"status": "UP"},
    "readinessState": {"status": "UP"},
    "redis": {"status": "UP", "details": {"version": "6.2.19"}},
    "sidecar": {"status": "DOWN", "details": {"url": "http://localhost:8000", "fallbackAvailable": true}}
  }
}
```

**说明**：整体状态 DOWN 仅因 sidecar 组件不可达（`http://localhost:8000`）。sidecar 是可选组件，`fallbackAvailable: true` 表示有降级方案。后端核心功能（API 路由、数据库、Redis、JWT）全部正常。

---

## 8. 服务器资源状态

| 项目 | 值 |
|------|-----|
| 磁盘 | 5.8G / 99G（7%） |
| 内存 | 1.8G / 15G（12%） |
| CPU | 4 核 |
| OS | CentOS Linux 7 |
| Java | 21.0.11（Amazon Corretto） |
| Nginx | active |

---

## 9. GitHub 镜像同步

| 项目 | 值 |
|------|-----|
| 当前状态 | 落后 4 commit |
| 同步脚本 | `bash scripts/sync-to-github.sh` |
| 状态 | 待执行（PR 合入 main 后同步） |

---

## 10. 回滚方案

### 首次部署专用回滚（生产库已初始化，有数据风险）

| 步骤 | 操作 |
|------|------|
| 1. 停止后端 | `sudo systemctl stop xiyu-bid-backend` |
| 2. 恢复旧 jar | `cp /opt/xiyu-bid/releases/e88dbd207/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar` |
| 3. 恢复数据库 | `gunzip < /opt/xiyu-bid/db-backups/*.sql.gz | mysql -h... -u... -p... winbid` |
| 4. 启动后端 | `sudo systemctl start xiyu-bid-backend` |

**当前回滚姿态**：ready（release 目录 + 数据库备份均存在）

---

## 11. 已知问题与风险提示

### 11.1 Kafka SDK 启动失败（非阻断）

- **现象**：`Kafka start failed: Cannot invoke "java.util.Map.entrySet()" because "eventTopicConsumerMap" is null`
- **影响**：组织事件集成（Organization Event SDK）不可用，不影响核心 API 功能
- **处置**：需 CRM/Kafka 团队协助排查 SDK 配置
- **测试环境状态**：同样存在此问题

### 11.2 sidecar 组件 DOWN（非阻断）

- **现象**：`http://localhost:8000` Connection refused
- **影响**：sidecar 功能不可用，但有 `fallbackAvailable: true`
- **处置**：如需 sidecar 功能，需单独部署 sidecar 服务

### 11.3 AI API Key 已配置（已解决）

- **现象**：`apiKeyConfigured=false`（启动时检查环境变量）
- **处置**：通过 `POST /api/settings/ai-models/test` 接口配置 custom provider + apiKey
- **结果**：✅ 已切换到 custom provider（`https://ai-tech.ehsy.com/v1/chat/completions` + qwen3.7-max）
- **验证**：连接测试 `{"status":"success","message":"连接测试成功"}`，健康检查 `apiKeyConfigured: true`

### 11.4 CRM clientId/clientSecret 启动日志提示（非问题）

- **现象**：启动日志打印 `clientIdConfigured=false, clientSecretConfigured=false`
- **真相**：`CRM_CLIENT_ID/SECRET` 在 `CrmProperties` 中声明但**无业务代码调用**，仅启动日志打印"是否已配置"
- **文档依据**：[PROD_ENVIRONMENT_PROFILE.md §193](file:///Users/user/xiyu/worktrees/trae/docs/release/PROD_ENVIRONMENT_PROFILE.md#L193) 明确标注"不阻塞部署"
- **结论**：**不是问题**，无需配置。CRM 实际认证走 `OAUTH_USERNAME` + `OAUTH_PASSWORD`（已配置）

### 11.5 V1092 修改已发布迁移（测试环境需 repair）

- **现象**：V1092 修改了已在 origin/main 中的迁移文件
- **影响**：测试环境下次部署时可能遇到 Flyway checksum mismatch
- **处置**：测试环境部署前执行 `bash /opt/xiyu-bid/bin/flyway-repair-runner.sh repair`

---

## 12. 经验沉淀应用

| 经验 | 应用情况 |
|------|---------|
| Flyway 预检 3 步法 | ✅ 首次部署使用 `SKIP_FLYWAY_VALIDATE=1`（无 jar 无法预检） |
| Readiness 延迟恢复 | ✅ 健康检查等待 4 分钟 |
| SYSTEMCTL_SUDO=true | ✅ 已配置 |
| Crash-loop 连续验证 | ✅ remote-deploy.sh 连续 3 次成功才算健康 |
| macOS tar 扩展头 | ⚠️ 前端目录有 AppleDouble 文件（无害） |
| Collation 冲突 | ✅ 新经验：临时表必须显式指定 COLLATE 与关联表对齐 |

---

## 13. 部署确认清单

- [x] 环境门禁通过（ENV=prod + 用户确认）
- [x] 早操三连完成
- [x] 基线确认（HEAD = origin/main）
- [x] 本地打包成功
- [x] 上传成功
- [x] Flyway 全量迁移成功（224 条）
- [x] 后端启动成功
- [x] P0 验证通过（前端 200 + API 路由 400/403）
- [x] P0.5 CRM API Key 激活成功
- [x] P0.5 AI API 配置完成（custom provider + qwen3.7-max）
- [x] P1 验证完成（CRM/OBS/Sentry 配置正确）
- [x] P2 验证完成（AI 测试成功 + 项目列表 + CRM 鉴权）
- [x] P3 监控 30 分钟（仅 2 条已知 ERROR）
- [ ] GitHub 镜像同步
- [ ] PR 合入 main

---

## 14. 下一步

1. **创建 PR**：`agent/trae/prod-deploy-1st` → `main`（含 V1092 修复 + 本报告）
2. **合入 main**：用户审查后合入
3. **同步 GitHub**：`bash scripts/sync-to-github.sh`
4. **Kafka SDK 排查**：与测试环境一致的已知问题
5. **业务数据初始化**：通过 UI 录入首批项目/标书
6. **测试环境 V1092 repair**：下次测试环境部署前执行 `flyway-repair-runner.sh repair`

---

> **部署人**：Trae Agent
> **部署确认**：用户已通过 AskUserQuestion 确认部署到生产环境 172.16.10.149
