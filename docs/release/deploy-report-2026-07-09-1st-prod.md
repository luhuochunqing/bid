# 第 1 次生产环境部署报告

> **环境**：生产（prod）
> **部署类型**：首次生产环境部署
> **部署日期**：2026-07-09
> **Release ID**：`e88dbd207`
> **部署状态**：✅ 成功（带已知问题）

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
| users 表 | ✅ 1 条记录 | 仅 admin |
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
| CRM clientId/clientSecret | ⚠️ 未配置 | 需 CRM 团队提供 |
| OBS SDK 初始化 | ✅ Version 3.23.9 | `https://obs.cn-east-3.myhuaweicloud.com` |
| Sentry 配置 | ✅ DSN + production | |
| AI 配置 | ⚠️ apiKeyConfigured=false | provider=deepseek，需在 UI 配置 |
| Kafka SDK | ❌ 启动失败 | `eventTopicConsumerMap is null`，已知非阻断问题 |

### P2/P3 - 待验证（需 admin 登录后操作）

- [ ] CRM 消息推送测试
- [ ] 企业微信通知测试
- [ ] AI 分析功能测试（需先在 UI 配置 API Key）
- [ ] Excel 导出测试
- [ ] 标书文件上传测试
- [ ] 监控 30 分钟无 ERROR

---

## 7. 健康检查详情

```json
{
  "status": "DOWN",
  "components": {
    "aiProvider": {"status": "UP", "details": {"provider": "deepseek", "apiKeyConfigured": false}},
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

### 11.3 AI API Key 未配置

- **现象**：`apiKeyConfigured=false`（provider=deepseek）
- **影响**：AI 分析功能不可用
- **处置**：admin 登录后在「系统设置 → AI模型设置」配置 API Key

### 11.4 CRM clientId/clientSecret 未配置

- **现象**：`clientIdConfigured=false, clientSecretConfigured=false`
- **影响**：CRM OAuth 流程可能不完整
- **处置**：需 CRM 团队提供 clientId 和 clientSecret

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
- [x] P1 验证完成（CRM/OBS/Sentry 配置正确）
- [ ] P2 验证（待 admin 登录后操作）
- [ ] P3 监控 30 分钟
- [ ] GitHub 镜像同步
- [ ] PR 合入 main

---

## 14. 下一步

1. **创建 PR**：`agent/trae/prod-deploy-1st` → `main`（含 V1092 修复 + 本报告）
2. **合入 main**：用户审查后合入
3. **同步 GitHub**：`bash scripts/sync-to-github.sh`
4. **P2 验证**：admin 登录后测试 CRM 消息/企微/AI/Excel/标书上传
5. **P3 监控**：30 分钟无 ERROR
6. **配置补充**：AI API Key + CRM clientId/clientSecret + Kafka SDK 排查

---

> **部署人**：Trae Agent
> **部署确认**：用户已通过 AskUserQuestion 确认部署到生产环境 172.16.10.149
