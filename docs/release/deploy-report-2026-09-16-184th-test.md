# 第 184 次部署报告（test 环境）

**日期**：2026-09-16

**环境**：test（172.16.38.78 / winbid-01）

**Release ID**：`bd432f486-api8080`

**前次 Release**：`23d58f0e0-api8080`

**状态**：⚠️ 部署成功；站内历史补发通过，企业微信送达部分失败

**生产环境**：未访问、未部署、未改配置或数据

## 部署目标与边界

本次只在测试环境执行一次性商机预测历史补发：对 `2026-09-16` 当天及以前已经存在提醒日志、但当天尚未创建历史补发通知的项目，按 OSS 实时组织事实发送给“当前在职项目经理 + 投标管理员 + 投标组长”。

安全边界：

- 一次性任务默认关闭；启动只能 `PREVIEW`，仅指定执行日 09:00 的 `EXECUTE` 可写入。
- 不修改原 `opportunity_prediction_reminder_log`，不重发 CRM 事件，不新增表、迁移、脚本或管理端点。
- 每项目使用独立 `REQUIRES_NEW` 短事务；单项目失败不回滚已完成项目。
- 本次没有生产授权，所有服务器操作均限定在 `172.16.38.78`。

## 发布与回滚证据

| 项目 | 结果 |
| --- | --- |
| Git commit | `bd432f486`（`fix(workbench): 补发历史商机预测提醒`） |
| 发布命令 | `ENV=test bash scripts/release/deploy-test.sh bd432f486` |
| 后端产物一致性 | 服务器 `app.jar` 与上传包内 JAR SHA-256 均为 `ac0b8622c648aca56c13b90a60f7e32fd81753cbb2e996dc58a0abd7eb8b3097` |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-bd432f486-api8080-20260916083708.sql.gz`（13MB，SHA-256 `f62bbacfd299545bdf47b8eeecbb950858c0797769938610549fecd2a62b22f8`） |
| 配置备份 | `/etc/xiyu-bid/backend.env.before-history-replay-20260916080733.bak`、`backend.env.after-history-execute-20260916090936.bak`、`backend.env.after-history-preview-20260916091306.bak` |
| 回滚锚点 | 前次 Release `23d58f0e0-api8080` + 上述数据库/配置备份 |

首次部署尝试在激活前安全失败：发布脚本由 `jetty` 读取 `/etc/xiyu-bid/backend.env` 生成备份，而文件当时为 `root:root 600`。测试机修正为 `root:jetty 640` 后重新走完整发布脚本成功；失败尝试未替换旧版本。最终配置仍为 `root:jetty 640`。

## 构建与门禁

| 检查项 | 结果 |
| --- | --- |
| 聚焦后端测试 | ✅ 68 tests passed |
| 架构测试 | ✅ 12 tests passed |
| 聚焦 staged 验证 | ✅ 23 tests passed |
| 改动 Java Checkstyle | ✅ 通过 |
| 前端构建 | ✅ `npm run build` 通过 |
| pre-push gate | ✅ 27 pass / 0 fail / 12 skip；ESLint 为存量 231 warnings / 0 errors |
| Flyway validate | ✅ 274 个迁移 checksum 匹配；无新增迁移 |
| 完整后端测试 | ⚠️ 9,735 tests / 2 failures / 29 errors / 11 skipped；失败集中在未改动的 competition-intel、BidDocumentQualityEngine 与共享 MySQL Testcontainers 生命周期，不声明全绿 |

全局 Checkstyle 仍会命中存量 Java 21 guarded switch 解析问题 `ProjectDocumentController.java:63`；改动范围 Checkstyle 已通过。

## 启动预演

部署后启动强制执行 `PREVIEW`，没有数据库写入：

```text
startup start mode=PREVIEW runDate=2026-09-16 cutoff=2026-09-16
candidates=274 eligible=274 created=0 relations=938 alreadyReplayed=0
missingProjects=0 noRecipients=0 failed=0 activeManagers=117 roleRecipients=3
ossRequested=8509 ossReturned=1163
```

预演后数据库基线：历史补发通知/关系/任务均为 0；原提醒日志 274 条，按默认连接字符集导出的 SHA-256 为 `dbf60a6a9966bc0f5fbd6c2be0760eaaba1f79d7175a186c64666e2bbe4a7f37`。

## 09:00 实际执行

09:00 两条任务均按时触发：

- 日常扫描：`scanned=598 notified=0 eventRetried=0 skipped=598 roleRecipients=3`。
- 历史补发：`candidates=274 eligible=274 created=274 relations=938 alreadyReplayed=0 missingProjects=0 noRecipients=0 failed=0 activeManagers=117 roleRecipients=3`。

### 站内通知与接收关系

| 检查项 | 实际结果 | 判定 |
| --- | ---: | --- |
| 历史补发通知 | 274 | ✅ 每项目一条 |
| 不同 source project | 274 | ✅ 无项目重复 |
| 重复 source project | 0 | ✅ |
| `user_notification` 关系 | 938 | ✅ |
| 不同站内接收人 | 43 | ✅ |
| 每通知 3 人 | 158 条 | ✅ |
| 每通知 4 人 | 116 条 | ✅；项目经理与全局角色重叠时已去重 |

关系总数与预演一致：`274 × 3` 个全局角色关系 + `117` 个在职项目经理关系 - `1` 个角色/经理重叠关系 = `938`。

### 企业微信异步送达

938 条投递任务均已收敛，无 `PENDING`、`PROCESSING` 或 `PENDING_RETRY`：

| 终态 | 数量 | 解释 |
| --- | ---: | --- |
| `DELIVERED` / outbound `SENT` | 276 | ✅ 统一消息中心确认发送成功 |
| `DEAD_LETTER` / outbound `FAILED(ERROR)` | 662 | ⚠️ 统一消息中心返回“该用户还未绑定企业微信ID，请联系IT” |
| 未完成 | 0 | ✅ 可安全重启 |

43 名接收人中，2 名全部发送成功，41 名全部因企微绑定缺失失败。三名全局角色均已获得 274 条站内关系；企微结果为 1 名全部成功、2 名全部失败。失败任务当前被分类为 `reason_code=BUG`，但真实上游响应明确是企微账号绑定缺失；在 IT 完成绑定并明确重放策略前，不得声称“所有人已收到企微”。

## 幂等与关闭验证

队列完全收敛后，将测试配置改为 `MODE=PREVIEW` 并重启。启动预演结果：

```text
candidates=274 eligible=0 created=0 relations=0 alreadyReplayed=274 failed=0
```

重启后通知/关系/任务仍为 `274 / 938 / 938`，原提醒日志仍为 274 条且校验和不变。随后关闭一次性开关并最终重启：

```text
XIYU_OPPORTUNITY_HISTORY_REPLAY_ENABLED=false
XIYU_OPPORTUNITY_HISTORY_REPLAY_MODE=PREVIEW
XIYU_OPPORTUNITY_HISTORY_REPLAY_RUN_DATE=2026-09-16
XIYU_OPPORTUNITY_HISTORY_REPLAY_CUTOFF=2026-09-16
```

最终状态：

- 后端 `active`，PID 12115；`/actuator/health` 为 `UP`。
- 关闭后的启动日志中没有历史补发 start/complete，证明任务未再次运行。
- 后端正在运行的 JAR 与 `bd432f486-api8080` 上传包 hash 一致。
- 两次重启均被既有组织事件 SDK 的同步注册调用阻塞 readiness 约 155 秒；主线程栈定位到 `ClientRegisterComponent.register()` 等待测试事件总线 HTTP 响应，返回后自动恢复，不是历史补发死锁。

## 结论与生产门禁

- ✅ 测试环境一次性历史站内补发、实时收件人、项目级幂等和关闭机制均通过。
- ⚠️ 企业微信只实际送达 276/938 人次；662 人次因 41 名接收人未绑定企微进入死信。
- ⚠️ 完整后端套件存在未改动模块的基线失败，已如实记录。
- ⛔ 生产部署不在授权范围，且在“企微绑定补齐 + 失败任务重放口径确认 + 生产日期重新授权”前，生产风险审查结论为 HOLD。
