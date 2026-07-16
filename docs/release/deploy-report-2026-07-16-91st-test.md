# 第 91 次测试环境部署报告

> **环境**：测试环境（test）
> **部署时间**：2026-07-16 19:21 CST
> **Release ID**：`57ebd967f-api8080`
> **操作人**：AI Agent（trae worktree）

## 一、部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 目标主机 | `winbid-01` (`172.16.38.78`) |
| Release ID | `57ebd967f-api8080` |
| 基线 commit | `57ebd967f` |
| 上一版本 | `bbe4710fc-api8080`（2026-07-15 16:57 激活） |
| 增量 commit 数 | 52 |
| 新增迁移文件 | V1166 / V1167 / V1168（3 个，均含 rollback U1166/U1167/U1168） |
| 部署结果 | ✅ 成功（健康检查 false negative，服务自恢复 UP） |
| 回滚状态 | 未需要 |

## 二、基线信息

- **当前分支**：`agent/trae-init`（锚点分支，HEAD = origin/main，非开发行为）
- **HEAD = origin/main**：`57ebd967f`（!2101 审批模式下开放计划入围供应商数量和招标文件不利项可编辑）
- **工作区状态**：干净
- **GitHub 镜像**：部署前落后 52 个 commit，部署后已同步（两边 main 完全一致）

## 三、增量 PR 与改动范围

### 3.1 主要增量 PR（bbe4710fc..57ebd967f，共 52 commit）

| commit | PR | 类型 | 说明 |
|---|---|---|---|
| `57ebd967f` | !2101 | feat | 审批模式下开放计划入围供应商数量和招标文件不利项可编辑 |
| `a062333c5` | !2102 | feat | 投标关键节点提醒改为每日重复 + 默认提前3天 (spec 038) |
| `198b30c87` | !2096 | fix | spec 037 修复 CRM 商机关联失败 — fallback 版 + 技术债清理 |
| `784f66c1a` | !2099 | feat | 结果确认回调 feedback 增加立项阶段计划入围供应商数量和招标文件不利项 |
| `f80c21808` | !2098 | feat | 仓库到期提醒接收人新增投标专员角色 |
| `e74c81e06` | !2097 | feat | CA 列表/详情接口返回借用人信息 (CO-579) |
| `d0f140ac2` | !2093 | feat | 业绩导入模板必填字段表头加 * 号标注 (CO-586) |
| `5976bca82` | !2095 | revert | 撤销 PR !2091 的 locked 修改，保留 region cascader 修复 |
| `c51583f51` | !2092 | fix | CO-582 §3.6 严格按需求规范 Word 文档层级 |
| `0c57f5fe8` | !2091 | feat | PENDING_REVIEW 状态下立项表单字段可编辑（后被 !2095 部分撤销） |
| `68289f1dc` | !2090 | feat | 工作台 UI 改造 - 对齐 HTML 参考设计 |
| `db6c95b0f` | !2089 | feat | 业务页接入 AdaptiveFormPage + 配置页加锁定字段和启用开关 + V1167 schema |
| `c969478ca` | !2086 | fix | 修复独立表单点击无反应 |
| `d87d11ec7` | - | fix | application-prod.yml 同步添加 zeroDateTimeBehavior=convertToNull |
| `38f70176e` | - | fix | JDBC URL 添加 zeroDateTimeBehavior=convertToNull 修复表单加载失败 |
| `3bf8e9ad2` | - | docs | 第 90 次测试环境部署报告 |

### 3.2 新增 Flyway 迁移

| 版本 | 文件名 | 说明 | rollback |
|---|---|---|---|
| V1166 | `V1166__align_tender_entry_schema_with_fallback.sql` | 对齐 tender.entry schema 与 fallback 表单字段 | U1166 ✓ |
| V1167 | `V1167__add_enabled_field_to_tender_entry_schema.sql` | V1167 tender.entry schema 加 enabled 开关 + 粘贴识别 + 标讯文件 | U1167 ✓ |
| V1168 | `V1168__tender_reminder_default_72h.sql` | 投标关键节点提醒默认提前 72 小时（spec 038） | U1168 ✓ |

### 3.3 主要功能变更

- **spec 037 CRM 商机关联修复（!2096）**：修复 linkByChanceIdIfPresent 误把 bidId 当 chanceId 的语义错误；OSS 同步填充 crm_sales_no；generateToken 去掉 OSS token 依赖；CrmAuthService 改用 postJson 直接换 JWT
- **spec 038 投标关键节点提醒（!2102）**：报名截止/开标提前3天每日重复提醒；去重逻辑从"只发一次"改为"每24小时发一次"；默认值 24→72
- **工作台 UI 改造（!2090）**：对齐 HTML 参考设计
- **业务页接入 AdaptiveFormPage（!2089）**：配置页加锁定字段和启用开关 + V1167 schema
- **CO-579（!2097）**：CA 列表/详情接口返回借用人信息
- **CO-582（!2092）**：仓库到期提醒严格按需求规范 Word 文档层级
- **CO-586（!2093）**：业绩导入模板必填字段表头加 * 号标注
- **表单加载失败修复**：JDBC URL 添加 `zeroDateTimeBehavior=convertToNull` 修复表单加载失败

## 四、Flyway 预检结果

### Step 1: 服务器 validate（部署前）

```
VALIDATE OK - all checksums match
Successfully validated 228 migrations (execution time 00:00.090s)
```

### Step 2: DB 已应用版本（部署前最近 5 条 success=1）

| version | description | installed_on |
|---|---|---|
| 1165 | add bid system admin role | 2026-07-11 16:43:52 |
| 1164 | lock oss user local passwords | 2026-07-10 21:13:25 |
| 1163 | add operator username to webhook delivery tasks | 2026-07-10 18:23:46 |
| 1162 | add margin permission to bid specialist | 2026-07-10 12:22:43 |
| 1161 | ca related platforms text | 2026-07-09 18:15:12 |

### Step 3: remote-deploy.sh 内置 validate

部署时自动执行，通过（228 migrations validated, all checksums match）。

## 五、部署步骤

| 步骤 | 命令/操作 | 结果 |
|---|---|---|
| 1. 早操三连 | `source dev-env.sh` + `git fetch origin main` + `check-git-wrapper.sh` | ✅ HEAD = origin/main，工作区干净，git wrapper 激活 |
| 2. 基线确认 | `git status` + `git log` + `git diff origin/main` | ✅ 与 origin/main 一致，GitHub 落后 52 个 commit |
| 3. 服务器现状 | `cat deployed-release.json` + `curl /actuator/health` | ✅ 上一版本 bbe4710fc，health UP |
| 4. Flyway 预检 | `flyway-repair-runner.sh validate` + DB 版本对比 | ✅ VALIDATE OK，DB 最新 V1165，待应用 V1166/V1167/V1168 |
| 5. 本地打包 | `RELEASE_ID=57ebd967f-api8080 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 bash scripts/release/package-release.sh` | ✅ BUILD SUCCESS (28.2s) |
| 6. 产物校验 | 检查 release-metadata.json + jar 内迁移文件 + 前端入口 + OBS .upload( 调用数 | ✅ obsEnabled=true, 230迁移无重复, V1166-8齐全, .upload(=2 |
| 7. 上传 + 部署 | `scp` archive + `ssh remote-deploy.sh`（SYSTEMCTL_SUDO=true） | ⚠️ 健康检查 false negative，服务实际已 UP |
| 8. 前端资源保留 | 从上一版本 release 目录 `cp -rn` 旧 assets | ✅ 已保留 270 个 assets 文件 |
| 9. 健康检查 | `curl /actuator/health` + `curl /actuator/health/readiness` | ✅ UP（所有组件正常） |
| 10. 迁移验证 | SQL 查询 `flyway_schema_history` | ✅ V1166/V1167/V1168 全部 success=1 |
| 11. Smoke 测试 | health + readiness + 3 接口路由 + 前端页面 | ✅ 全部通过 |
| 12. GitHub 同步 | `bash scripts/sync-to-github.sh` | ✅ 两边 main 完全一致 |
| 13. 配置清理检查 | `grep SHOW_DETAILS/DEBUG/TRACE backend.env` | ℹ️ MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always（用户决定保留） |

## 六、验证结果

### 6.1 健康检查事件

**⚠️ 健康检查 false negative 事件**：

- **现象**：`remote-deploy.sh` 健康检查在 120 次（约 4 分钟）内未检测到 3 次连续成功，判定失败并退出
- **实际状态**：后端服务 PID 16164 一直运行（非 crash-loop），实际 API 请求正常返回 200（如 `/api/notifications/unread-count`），但 `/actuator/health` 在前 4 分钟持续返回 503
- **根因**：`OrganizationEventSdkKafkaStarter` 使用 `@EventListener(ApplicationReadyEvent.class) @Order(LOWEST_PRECEDENCE)`，Kafka 初始化阻塞主线程导致 readinessState 停留在 OUT_OF_SERVICE 约 4 分钟
- **恢复**：服务在 19:25:36 后自恢复 UP（启动后约 4 分钟），无需回滚
- **历史出现**：第 8、9、10、13、15、91 次均出现，已沉淀为已知行为（Lesson 2）

### 6.2 部署后健康状态（19:26 CST 验证）

```
/actuator/health: UP
  - aiProvider: UP (provider=custom, model=qwen3.7-max)
  - db: UP (MySQL)
  - diskSpace: UP (free 20.9GB)
  - jwt: UP (HMAC-SHA256, secretLength=64, STRONG)
  - livenessState: UP
  - ping: UP
  - readinessState: UP
  - redis: UP (version 6.2.19)
  - sidecar: UP (http://localhost:8000, reachable)
```

### 6.3 API Smoke 测试

| 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `GET /actuator/health` | 200 UP | 200 UP | ✅ |
| `GET /actuator/health/readiness` | 200 UP | 200 UP | ✅ |
| `POST /api/auth/login`（空 body） | 400 | 400 | ✅ |
| `GET /api/projects`（无认证） | 403 | 403 | ✅ |
| `GET /api/integration/crm/health` | 401 | 401 | ✅ |
| `GET /`（首页） | 200 | 200 | ✅ |
| `GET /login` | 200 | 200 | ✅ |
| 前端入口 js | `assets/index-DIwhoIWG.js` | `assets/index-DIwhoIWG.js` | ✅ |

### 6.4 Flyway 迁移应用验证

| version | description | success | installed_on |
|---|---|---|---|
| 1166 | align tender entry schema with fallback | 1 | 2026-07-16 19:21:42 |
| 1167 | add enabled field to tender entry schema | 1 | 2026-07-16 19:21:42 |
| 1168 | tender reminder default 72h | 1 | 2026-07-16 19:21:42 |

## 七、GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 同步前落后 | 52 个 commit |
| 同步命令 | `bash scripts/sync-to-github.sh` |
| 同步结果 | ✅ 两边 main 完全一致（57ebd967f） |

## 八、回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要（服务自恢复 UP） |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/bbe4710fc-api8080` |
| 上一版本 jar | `/opt/xiyu-bid/releases/bbe4710fc-api8080/backend/app.jar` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-57ebd967f-api8080-<timestamp>.sql.gz` |
| 回滚命令（如需） | `cp /opt/xiyu-bid/releases/bbe4710fc-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |

## 九、经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| Lesson 2: Kafka SDK readiness 延迟 | ✅ 已识别 false negative，未误判为 crash-loop，未错误回滚 |
| Lesson 3: 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空 |
| Lesson 10: OBS 直传漏传 | ✅ `VITE_OBS_ENABLED=true` 显式传入 + 产物校验 obsEnabled=true |
| Lesson 14: macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1` 预防 |
| Lesson 16: Mac HTTP_PROXY 502 | ✅ curl 统一加 `--noproxy '*'` |
| Lesson 18: 前端 hash 资源跨版本 404 | ✅ 部署后从上一版本 release 目录 `cp -rn` 旧 assets 保留 24h |
| Flyway 预检 3 步法 | ✅ 部署前 validate + DB 版本对比 + remote-deploy 内置 |
| 产物校验（jar 内迁移无重复 + OBS 启用） | ✅ 打包后校验通过 |

## 十、风险提示

1. **Kafka SDK readiness 延迟（已知行为）**：本次健康检查 false negative 再次出现（第 6 次）。建议后续优化 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池，避免阻塞主线程。同时建议 `remote-deploy.sh` 健康检查窗口从 4 分钟延长到 5 分钟，或降低连续成功次数要求（3→2）。
2. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 保留**：用户决定保留（第 13/14/15/91 次均保留），暴露 health 详情。测试环境可接受，生产环境建议关闭。
3. **本次增量较大（52 commit + 3 迁移）**：建议后续关注 spec 037 CRM 关联、spec 038 提醒改造、工作台 UI 改造、AdaptiveFormPage 业务页等功能的实际使用情况。

## 十一、部署确认清单

- [x] 环境门禁确认（test 环境 172.16.38.78）
- [x] 早操三连完成（git wrapper 激活）
- [x] 基线确认（HEAD = origin/main，工作区干净）
- [x] 服务器现状检查（上一版本 bbe4710fc，health UP）
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（BUILD SUCCESS，obsEnabled=true）
- [x] 产物校验通过（230 迁移无重复，V1166-8 齐全，.upload(=2）
- [x] 上传 + 部署完成
- [x] 前端资源保留（270 个 assets）
- [x] 健康检查 UP（所有组件正常）
- [x] Flyway 迁移应用验证（V1166/V1167/V1168 success=1）
- [x] Smoke 测试全部通过
- [x] GitHub 镜像同步（两边 main 完全一致）
- [x] 配置清理检查（仅 SHOW_DETAILS=always 保留）
- [x] 部署报告生成

---

## 附录：部署元数据

```json
{
  "releaseId": "57ebd967f-api8080",
  "activatedAt": "2026-07-16T11:21:34Z",
  "releaseDir": "/opt/xiyu-bid/releases/57ebd967f-api8080",
  "backendJarPath": "/opt/xiyu-bid/shared/backend/app.jar",
  "packageMetadata": {
    "releaseId": "57ebd967f-api8080",
    "apiBaseUrl": "",
    "jarName": "bid-platform-1.0.3.jar",
    "builtAt": "2026-07-16T11:20:08Z",
    "sentryEnabled": false,
    "obsEnabled": true
  }
}
```
