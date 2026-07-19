---
title: 生产环境首次部署实战教训
space: engineering
category: guide
tags: [生产部署, 首次上线, 端口, collation, 排障, HTTP_PROXY, 配置漂移]
sources:
  - docs/release/deploy-report-2026-07-09-1st-prod.md
  - docs/release/postmortem-2026-07-09-1st-prod.md
  - docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md
  - docs/lessons/lessons-learned.md
backlinks:
  - _index
  - deployment
  - lessons-learned
created: 2026-07-09
updated: 2026-07-09
health_checked: 2026-07-19
---
# 生产环境首次部署实战教训

> 2026-07-09 西域数智化投标管理平台首次生产环境部署（`172.16.10.149`）的实战经验沉淀。
> 从 35 次测试环境部署到首次生产部署，仍然遇到 5 个问题。本文档记录根因和教训，避免下次复发。

---

## 1. 测试环境通过 ≠ 生产环境通过

### 1.1 隐性配置差异

| 差异点 | 测试环境 | 生产环境 | 影响 |
|--------|---------|---------|------|
| `users` 表 collation | `utf8mb4_unicode_ci` | `utf8mb4_0900_ai_ci` | V1092 迁移 JOIN 时报 `Illegal mix of collations` |
| 数据历史 | 8438 个 NULL 角色 OSS 用户（旧版代码遗留） | 全新数据库 | 历史包袱不同，行为不同 |
| 部署次数 | 68 次 | 首次 | 测试环境已被"驯化"，生产是"处女地" |

### 1.2 教训

- **collation、字符集、时区、SQL mode 这些隐性配置差异，只有在生产环境才会暴露**
- **临时表必须显式指定 `COLLATE` 与关联表对齐**，不能依赖数据库默认值
- **测试环境的"历史包袱"会掩盖新部署的问题**（如测试环境 8438 个 NULL 角色用户让代码看起来"工作正常"）

---

## 2. 配置项声明 ≠ 配置生效

### 2.1 事故：skipUnmappedUsers 声明但代码未使用

`OrganizationIntegrationProperties.java` 声明了 `skipUnmappedUsers` 字段（默认 true），但 `OrganizationUserSyncWriter.upsert()` 中硬编码了 `LoginRoleWhitelist.isAllowed()` 检查，**完全没有使用 `properties.isSkipUnmappedUsers()`**。

结果：即使设置环境变量 `XIYU_ORG_SYNC_SKIP_UNMAPPED_USERS=false`，代码仍按白名单模式跳过未匹配角色的用户，导致 8528 个 OSS 用户只同步了 168 个。

### 2.2 修复

1 行代码：

```java
// 修复前
if (!LoginRoleWhitelist.isAllowed(resolvedRoleCode)) {

// 修复后
if (!LoginRoleWhitelist.isAllowed(resolvedRoleCode) && properties.isSkipUnmappedUsers()) {
```

### 2.3 教训

- **配置项在 Properties 类中声明 ≠ 配置项在代码中生效**
- **单元测试只测了声明层（getter 返回正确值），没测行为层（代码是否真的用了这个配置）**
- **改进**：集成测试必须覆盖"配置=true"和"配置=false"两种行为，不能只测"配置能被读取"

---

## 3. "跳过"不等于"成功" — 统计口径陷阱

### 3.1 事故：8572 条假成功

全量同步 11200 用户后，框架报告 `successCount=11053`，但实际入库只有 168 条。根因：同步框架把 `upsert()` 返回 `Optional.empty()`（跳过）也标记为 `successItem`。

### 3.2 正确的统计口径

| 状态 | 含义 | 是否应计入 success |
|------|------|-------------------|
| `CREATED` | 新建用户 | ✅ |
| `UPDATED` | 更新已有用户 | ✅ |
| `SKIPPED` | 跳过（如角色未匹配） | ❌ 应单独统计 |
| `FAILED` | 失败（如手机号为空） | ❌ |

### 3.3 教训

- **"跳过"不等于"成功"**，统计必须区分 created/updated/skipped/failed
- **同步框架的 successCount 不能盲信**，必须用 `SELECT COUNT(*) FROM users` 交叉验证
- **改进**：在同步报告中增加 `actualDbCount` 字段，与 `successCount` 对比

---

## 4. 端口认知陷阱

### 4.1 实际端口架构

| 层 | 端口 | 说明 |
|----|------|------|
| Nginx 对外 | 80 / 8080 | 浏览器和外部 curl 访问入口（测试和生产一致） |
| 后端内部 | 18080 | `SERVER_PORT=18080`，通过 `/etc/xiyu-bid/backend.env` 注入 |
| `application-prod.yml` 默认 | 8080 | 仅是 fallback 默认值，**从未实际使用** |
| 本地开发环境 | 18089 | 仅主工作区 trae 本地开发用 |

### 4.2 排障时的认知陷阱

- `application-prod.yml` 中 `server.port: ${SERVER_PORT:8080}` 默认 8080，容易让人误读
- 排障时混淆了"Nginx 对外端口"和"后端内部端口"
- `curl http://172.16.10.149:8080/actuator/health`（经 Nginx）和 `curl http://127.0.0.1:18080/actuator/health`（直连后端）都能通，但排障时容易搞混

### 4.3 教训

- **部署文档必须明确区分 Nginx 对外端口和后端内部端口**
- **排障时优先用 `127.0.0.1:18080` 直连后端**，排除 Nginx 层干扰
- **`application-prod.yml` 的默认值 8080 是 fallback，实际靠 `SERVER_PORT=18080` 覆盖**

---

## 5. 本地代理干扰排障 — 反复复发的隐形杀手

### 5.1 问题

macOS 本地设置了 `HTTP_PROXY=127.0.0.1:7897`（Clash/V2Ray 等代理工具），导致：

```bash
# 失败：curl 走代理隧道，连接超时
curl http://172.16.10.149:18080/actuator/health
curl: (7) Failed to connect to 172.16.10.149 port 18080: Operation timed out

# 成功：加 --noproxy 绕过代理
curl --noproxy '*' http://172.16.10.149:18080/actuator/health
{"status":"UP"}
```

### 5.2 历史复发记录

这个问题在以下场景反复复发：
- 第 19 次部署排障（2026-06-30）
- 第 23 次部署排障（2026-06-30）
- 第 N 次部署排障
- 首次生产部署排障（2026-07-09）

### 5.3 教训

- **排障 HTTP 请求统一加 `--noproxy '*'`**，或直接 ssh 到服务器内部执行 curl
- **不要相信本地 curl 的超时报错**，先排除代理干扰
- **改进**：在部署 SOP 中固化"排障 curl 必须加 `--noproxy`"

---

## 6. admin 登录 403 排障实录

### 6.1 问题

部署新 JAR 后，`POST /api/auth/sessions` 返回 403 Forbidden，admin 无法登录。日志中没有 auth/login 相关错误。

### 6.2 排障过程

1. ❌ 怀疑新 JAR 安全配置变化 → 检查 SecurityConfig 无变化
2. ❌ 怀疑 `SPRING_CONFIG_IMPORT` 外部配置覆盖 → 检查无覆盖
3. ❌ 怀疑 Spring Security 拦截 → 提高日志级别无输出
4. ✅ **真相：本地 HTTP_PROXY 干扰 curl，后端实际正常** — ssh 到服务器内部 `curl 127.0.0.1:18080/api/auth/sessions` 一切正常

### 6.3 教训

- **排障的第一步：排除本地环境干扰**（代理、DNS、网络）
- **不要在本地 curl 排障生产服务**，直接 ssh 到服务器内部执行
- **"看起来像服务端问题"往往是客户端问题**

---

## 7. 部署预检清单的不足

### 7.1 现有预检清单的盲区

现有 `GO_LIVE_CHECKLIST.md` 和 `xiyu-server-deploy` 技能的预检清单**缺少以下检查**：

| 检查项 | 说明 |
|--------|------|
| **collation 一致性** | 检查关联表的 collation 是否一致 |
| **配置-代码契约** | 检查 Properties 类的配置项是否真的被代码使用 |
| **同步统计交叉验证** | `SELECT COUNT(*) FROM users` vs 同步报告的 successCount |
| **本地代理检查** | `echo $HTTP_PROXY` 是否设置 |
| **端口实际值** | `grep SERVER_PORT /etc/xiyu-bid/backend.env` 确认实际端口 |

### 7.2 改进

在部署后验证清单中新增：

```bash
# 1. 同步统计交叉验证
mysql -e "SELECT COUNT(*) FROM users WHERE external_org_source_app='oss'"
# 与同步报告的 successCount 对比

# 2. 本地代理检查
echo "HTTP_PROXY=$HTTP_PROXY"
# 如果非空，curl 必须加 --noproxy '*'

# 3. 端口实际值
ssh server 'grep SERVER_PORT /etc/xiyu-bid/backend.env'
```

---

## 8. CRM/OSS 集成实战经验

### 8.1 CRM clientId/clientSecret 不需要配置

**问题**：部署时提示 "CRM clientId/clientSecret 未配置"，以为是阻塞问题。

**真相**：CRM 通信使用 `OAUTH_USERNAME + OAUTH_PASSWORD + API Key`，**不依赖 clientId/clientSecret**。启动日志中打印 `clientId=` 只是信息性日志，不影响功能。

**教训**：部署清单中的"未配置"提示**必须区分"信息性"和"阻塞性"**。

### 8.2 OSS 接口返回数据格式

`getUserByTimeWindow` 接口实测返回：

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 姓名 |
| `email` | string | 邮箱 |
| `mobilePhone` | string | 手机号 |
| `deptId` | int | 部门 ID（整数，**不是部门编码字符串**） |
| `jobNumber` | string | 工号 |

**不返回的字段**：`deptName`、`departmentCode`、`positionName`

**影响**：`enrichDepartmentName` 方法需要用 `deptId` 反查 `organization_departments` 表补充部门名称。

### 8.3 Kafka 事件 SDK 失败不阻塞首次部署

**问题**：Kafka 启动失败（`eventTopicConsumerMap is null`）。

**真相**：event-busserver 上未配置 `BidSystemOrgConsumer` 的事件订阅，导致 SDK 拉不到事件配置。

**处理**：首次部署通过全量同步 API 拉取人员数据，Kafka 仅用于后续增量同步，**不阻塞首次部署**。

---

## 9. 相关文档

- [[deployment]] — 部署与上线（含端口对照表）
- [[lessons-learned]] — 工程经验总结
- `docs/release/deploy-report-2026-07-09-1st-prod.md` — 首次生产部署报告
- `docs/release/postmortem-2026-07-09-1st-prod.md` — 复盘文档
- `docs/lessons/lessons-learned.md` §51 — 首次生产部署复盘教训

---

## 10. 变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-07-09 | 首次创建，沉淀首次生产部署的 8 个实战教训 |
