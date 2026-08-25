# 第 142 次测试环境部署报告 — 2026-08-25

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 标讯变化（创建）事件推送到西域事件库 — 功能开关失效根因修复与真实推送打通 |
| 部署序号 | 第 142 次（测试） |
| 部署时间 | 2026-08-25 18:48 CST（backend jar 激活） |

## 部署概览

| 项目 | 值 |
|---|---|
| 部署方式 | 手动 scp jar + `systemctl restart xiyu-bid-backend`（非 release 流程，改动尚未合 main） |
| 上一版本 artifact | `/opt/xiyu-bid/shared/backend/app.jar`（18:37 旧 jar，含缩进修复但缺后续两处契约修复） |
| 基线 commit | `1c5f92b25`（分支 `agent/trae/fix-tender-event-yaml-indent`，共 3 个修复 commit） |
| 已备份 artifact | `/opt/xiyu-bid/shared/backend/app.jar.bak-20260825-1848` |
| 健康检查 | ✅ `GET /actuator/health` → **HTTP 200 UP**（含 db / redis / sidecar / aiProvider 全组件 UP） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要（备份存在，随时可回滚） |
| 新增 Flyway 迁移 | **无** |

## 变更内容（3 个 commit，1 个 Azure 未合并分支）

> 本分支 `agent/trae/fix-tender-event-yaml-indent` 目前**尚未收到 main**，仅用于测试环境验证。合入需另走 PR 流程。

| commit | 说明 |
|---|---|
| `bcbd2f08b` | fix: 修正 `tender-event` 配置块缩进错位，恢复标讯事件推送开关（YAML 绑定根因修复） |
| `214a2ef60` | fix: 标讯事件 data 负载序列化为 JSON 字符串上报，修复网关 **400 拒收** |
| `1c5f92b25` | fix: 修复事件总线网关成功信号判定，兼容 `code=00000`，避免成功推送被误判失败 |

### 根因闭环（YAML 缩进错位 → 功能开关恒为 false）

- **现象**：`XIYU_TENDER_EVENT_SDK_ENABLED=true` 环境变量已注入，但标讯创建不产生任何推送记录，功能开关始终不生效。
- **根因**：`application.yml` 中 `tender-event:` 块以 **6 空格缩进**嵌套进了 `organization:` 子树，导致 Spring 绑定不到预期属性键 `xiyu.integrations.tender-event.sdk.enabled`，开关恒为 `false`。
- **修复**：将 `tender-event:` 块调整为 **4 空格缩进**，作为 `integrations:` 的直接子节点，恢复配置绑定。
- **契约二修**：修复后真实推送被网关 400 拒收（`data` 字段传了对象而非网关要求的 JSON 字符串，须序列化为字符串）；随后网关返回 `code=00000`/`result=null` 被误判失败，补齐成功信号兼容判定。

## 验证结果

### 功能开关运行时生效

- 服务器 `/etc/xiyu-bid/backend.env` 确认：
  - `XIYU_TENDER_EVENT_SDK_ENABLED=true`
  - `XIYU_TENDER_EVENT_SERVER_REGISTER_URL=http://event-busserver-test.ehsy.com`
  - `XIYU_TENDER_EVENT_SERVICE_NAME=bid`

### 端到端真实推送验证（新 jar）

在测试环境通过 `POST /api/tenders` 创建新标讯（`MANUAL_SINGLE`），触发 `BID_TENDER_CHANGE` 创建事件：

| 检查项 | 结果 |
|---|---|
| 创建标讯 `tenderId=2263` | ✅ HTTP **201** |
| `tender_event_logs` 新增记录（id=`4`） | ✅ status=**SENT**，content=`{"tenderId":2263}` |
| 后端日志：`标讯事件直连推送响应 ... tenderId=2263 result=true`（TenderEventSdkProducer） | ✅ 网关返回成功信号，判定 `result=true` |
| 后端日志：`标讯事件推送成功 eventCode=BID_TENDER_CHANGE tenderId=2263`（TenderEventPublishService） | ✅ |
| 对照：历史 id=3（2262）/ id=2（2261）旧 jar 时期 | ❌ FAILED（data 契约 + 网关判定问题，本次已修复） |

> **结论**：标讯创建事件已真实送达西域事件总线 `event-busserver-test.ehsy.com` 并被成功接受（网关回成功信号），开关生效、契约修复、成功判定修复三处闭环完成。

### 部署后服务健康

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ 200 UP，全组件（aiProvider/db/diskSpace/jwt/redis/sidecar/readiness）UP |
| `systemctl is-active xiyu-bid-backend` | ✅ active |
| 进程启动时间 | 2026-08-25 18:48:49 |

## 其他收尾

| 项目 | 结果 |
|---|---|
| 代码入主仓库 | ⏳ 分支 `agent/trae/fix-tender-event-yaml-indent` 待合入 `origin/main`（需走 PR） |
| 旧 jar 备份 | ✅ `app.jar.bak-20260825-1848` |

## 经验教训

1. **YAML 缩进错位是无报错的配置绑定杀手**：`tender-event:` 块被错误缩进嵌套进邻居子树后，Spring 会**静默**找不到属性键，开关恒为默认 false，且不抛任何异常。排查时优先用启动期探针/运行态检查 Spring 实际绑定的 enabled 值（本次用 Arthas + env 双层确认覆盖源），而非只信 `.env` 注入。
2. **真实推送契约以网关为准，不猜**：`data` 字段要求 JSON 字符串、成功信号是外层 `code="00000"`，与本地测试约定（`data.result==true`）不同。端到端必须看网关真实响应日志，按真实契约修正序列化与判定，并补对应单测（`publish_gatewayCode00000_returnsTrue`）。
3. **复制移植易错**：`sourceType` 枚举在实际部署中为 `MANUAL_SINGLE`（非 `MANUAL`），创建标讯的请求体字段名/枚举需以实体 `Tender$SourceType` 为准。

## 关联

- 上一份报告：`docs/release/deploy-report-2026-08-24-141st-test.md`
- 分支：`agent/trae/fix-tender-event-yaml-indent`（3 commits：`bcbd2f08b`、`214a2ef60`、`1c5f92b25`）
- 待办：分支合入 main 后删除远端分支、释放 hot-path 文件锁、回填 `docs/lessons/lessons-learned.md`