# 第 147 次部署报告（test 环境）

**日期**：2026-08-28
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`c57a1062e-api8080`
**部署时间**：11:53 - 12:10
**状态**：✅ 成功

> 序号说明：`docs/release/` 最后一份报告为 146th（2026-08-27 10:47，`5eaa167b0`）。但服务器 `releases/` 目录显示 8-27 晚间还有 3 次未写报告的部署：`99bcb28c2`（18:05）、`8951503cf`（18:54）、`f242bdd9e`（19:05，本次部署前线上版本）。按部署实际发生顺序，本次为第 147 次。

## 基线信息

| 项目 | 值 |
|------|-----|
| 基线 commit | `c57a1062e`（origin/main，detached HEAD 部署） |
| 前次部署 | `f242bdd9e`（2026-08-27 19:05 CST） |
| 增量 commit | 51 个 |
| 新增迁移 | **V1204**（score_parse_task 加 `run_summary TEXT NULL`，rollback `U1204` 在位） |
| 工作区状态 | 干净（与 origin/main 零差异） |

## 改动范围（51 个增量 commit，重点 PR）

| PR | 描述 |
|----|------|
| !2437 | **feat(analytics): 部门筛选两级展示口径**——勾一级展开后代，有二级按二级成桶（含 source_app 分区口径修复、同名部门跨分支核查） |
| !2440 | **fix(integration): 接口六契约修正**——PUT /api/integration/projects 定位参数 sourceId 改为 tenderId（含 ApiKeyUserResolver 去重、403 审计守卫） |
| !2436 | **fix(project): 立项表单数值字段空值改为 null**——清除 focus/blur 补 0 误导显示（lessons §169，代码已合入 main、本次首次上线测试环境） |
| !2441 | docs(wiki): 回填 M4 折扣图并排口径与 0 占位标签抑制陷阱 |

## Flyway 预检（3 步法）

| 步骤 | 结果 |
|---|---|
| 1. 服务器 validate | ✅ VALIDATE OK，264 migrations checksum 全部一致 |
| 2. DB 版本对比 | ✅ DB 已应用至 V1203（全 success=1），V1204 未应用（预期） |
| 3. remote-deploy 内置 validate | ✅ 激活前再次通过（"仅 pending 新迁移为预期状态"） |

V1204 为非破坏性加列（`ALTER TABLE ... ADD COLUMN run_summary TEXT NULL`），配套 rollback `U1204__add_score_task_run_summary.sql` 已核查在位。

## 打包与产物校验

```bash
RELEASE_ID="c57a1062e-api8080" VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 \
  bash scripts/release/package-release.sh
```

| 校验项 | 结果 |
|---|---|
| jar 内 Flyway 迁移版本无重复 | ✅ |
| OBS 直传启用（Detail chunk `.upload(` =2） | ✅ |
| `release-metadata.json` 中 `obsEnabled` | `true` ✅ |
| 前端入口 chunk | `assets/index-dzqkyp3D.js` ✅ |
| 包大小 | 154M |

## 部署步骤

| 步骤 | 结果 | 备注 |
|------|------|------|
| 0. 环境门禁 | ✅ | AskUserQuestion 确认 test（172.16.38.78） |
| 1. 早操三连 | ✅ | sync-env 7 通过 0 失败，git wrapper 正常 |
| 2. Flyway 预检 | ✅ | 见上表 |
| 3. 本地打包 | ✅ | Maven clean 构建 + Vite 构建，39.8s |
| 4. 上传 | ✅ | scp 归档 + remote-deploy.sh → incoming/ |
| 5. remote-deploy.sh | ✅ | validate → DB 备份 → 替换 jar → 写 release 记录 → 重启 |
| 6. 服务重启 | ✅ | 11:59:12 CST active (running)，PID 5753 |
| 7. 健康检查 | ⚠️→✅ | readiness OUT_OF_SERVICE 约 6 分钟后自恢复（见下） |
| 8. Smoke 测试 | ✅ | 8/8 通过 |
| 9. 前端旧 assets 保留 | ✅ | 从 `f242bdd9e` release 目录 `cp -rn` 补回旧 hash 文件 |

### ⚠️ 部署过程中两个插曲

1. **SSH 连接中断（第一次部署尝试失败，无副作用）**：首次执行 remote-deploy.sh 时连接被服务器关闭（`Connection closed by 172.16.38.78 port 22`）。事后核查：服务 active、健康 UP、`deployed-release.json` 未变、releases 无新目录、无残留部署进程——**部署完全未启动，旧版本完好**。加 `-o ServerAliveInterval=30` 重试即成功。
   → **经验**：长事务 SSH（含 mysqldump 备份）建议始终带 ServerAliveInterval，防 NAT/中间设备静默断连。
2. **readiness OUT_OF_SERVICE 约 6 分钟**：启动后所有组件（db/jwt/aiProvider/disk/liveness）均 UP，仅 readiness 503。Kafka SDK（`OrganizationEventSdkKafkaStarter`）在 main 线程初始化阻塞 `ApplicationReadyEvent`——第 8/9/10/13/15 次已知行为，本次约 6 分钟自恢复（历史 2-5 分钟，本次略长）。未回滚，符合"Kafka broker 可达后自恢复，不必急于回滚"预案。

## 验证结果

### 迁移应用

```
SELECT version, description, success, installed_on FROM flyway_schema_history WHERE version='1204';
→ 1204 | add score task run summary | 1 | 2026-08-28 11:59:20
SHOW COLUMNS FROM score_parse_task LIKE 'run_summary';
→ run_summary | text | YES | NULL（列已在位）
```

### Smoke（经 Nginx 8080，`--noproxy '*'`）

| 检查项 | 期望 | 实际 |
|---|---|---|
| /actuator/health | 200 UP | ✅ 200 |
| /actuator/health/readiness | 200 UP | ✅ 200 |
| POST /api/auth/login（空体） | 400 | ✅ 400 |
| /api/projects（未认证） | 403 | ✅ 403 |
| /api/integration/crm/health（未认证） | 401 | ✅ 401 |
| /（前端入口） | 200 | ✅ 200 |
| /login | 200 | ✅ 200 |
| 前端入口 chunk 与构建一致 | index-dzqkyp3D.js | ✅ 一致 |

### 配置清理检查

`/etc/xiyu-bid/backend.env` 仅含 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（第 13-15 次部署用户决定长期保留，本次诊断 readiness 延迟正是依赖它），无临时 DEBUG/TRACE 残留。

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚 artifact | `/opt/xiyu-bid/releases/f242bdd9e-api8080/backend/app.jar` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-c57a1062e-<ts>.sql.gz`（remote-deploy 自动执行） |
| 迁移回滚 | `U1204__add_score_task_run_summary.sql`（TEXT NULL 加列，回滚风险低） |
| 回滚状态 | **ready（未需要）** |

## 风险提示

- V1204 落地后，score_parse_task 单次解析将持久化运行档案 JSON（spec 057 FR-04），建议关注 TEXT 列写入量。
- !2437 部门两级口径涉及统计口径变更，建议业务侧在测试环境复核部门筛选数字。
- GitHub 镜像落后 Gitee（本次早操时 69 个 commit），建议择机 `SYNC_TO_GITHUB=1 bash scripts/sync-env.sh .`。

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）
- [x] 基线与 origin/main 一致，工作区干净
- [x] Flyway 预检 3 步全过
- [x] 产物校验（obsEnabled / 入口 chunk / jar 迁移无重复）
- [x] DB 备份完成
- [x] 健康检查 UP（readiness 延迟自恢复，已沉淀）
- [x] Smoke 8/8 通过
- [x] 旧前端 assets 保留 24h
- [x] 临时配置清理检查通过
- [x] 本报告已生成
