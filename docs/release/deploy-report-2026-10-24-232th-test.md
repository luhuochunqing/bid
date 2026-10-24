# 第 232 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-24 19:48 (UTC+8) |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `248bbf457` |
| 部署分支 | `agent/trae-init`（锚点分支，HEAD = origin/main） |
| HEAD Commit（本次部署的代码版本） | `248bbf457` |
| 旧版本 | `4ea606791`（第 231 次，2026-10-23 08:01） |
| 构建包 | `.release/xiyu-bid-release-248bbf457.tar.gz`（155M，MD5 `12dc60e01f03ebd9151c414f461d6250`） |
| 增量 | 33 个 commit，119 个文件（+4166/-553） |
| 激活时间 | 2026-10-24T11:48:13Z（19:48:13 UTC+8） |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-248bbf457-20261024194803.sql.gz`（15M） |

## 部署前阻断与修复（本次特有）

首次打包时容器门禁 `FlywayMysqlContainerTest` 失败，根异常：

```
Schema-validation: wrong column type encountered in column [task_id] in table
[case_precipitation_task]; found [char], but expecting [varchar(36)]
```

- **根因**：V1222 建表把 `task_id` 定为 `CHAR(36)`，实体 `CasePrecipitationTask` 映射 `varchar(36)`；`ddl-auto=validate` 下服务启动即 crash-loop。
- **修复**（PR !2861）：新增 `V1223` 前向迁移 `MODIFY COLUMN task_id VARCHAR(36)` + `U1223` 回滚脚本；不改已发布 V1222，不改实体映射。前置已核实测试 DB 最新 V1220，V1221/V1222 未应用到任何真实库。
- **验证**：`FlywayMysqlContainerTest` 5/5 通过；`FlywayRollbackScriptCoverageTest` 通过；pre-push 门禁 32 通过 / 0 失败。

## PR 列表（增量 33 个 commit，自 4ea606791..248bbf457）

| Commit | 说明 |
|---|---|
| `248bbf457` | **!2861** merge: fix-case-precip-task-id-type（本次部署阻断修复） |
| `e9f3cef39` | docs: 沉淀 task_id 列类型不一致根因（wiki + lessons 448） |
| `078dd12dd` | fix(casework): 纠正 case_precipitation_task.task_id 列类型为 VARCHAR(36) |
| `de45d515b` | **!2858** fix(compliance): 纠正项目1117标书质量核查误判 |
| `623caf5e5`、`a62c99ac6` | fix(compliance): 保证金凭据页面视觉校验 / 标书质量核查误判 |
| `b7e9f8ebc` | **!2860** fix(scoreparse): 泛表头多级管道符维度解构 + 财务与信用分类扩充 |
| `2d75c7439` | **!2859** fix(casework): 应答片段摘要标题显示修复 + 小标题契约收口 |
| `e05fc19f2` | **!2857** fix(casework): 并发案例沉淀加固（V1222 来源） |
| `1e2100507` | **!2856** fix(casework): 存量 OTHER 标签案例穿透补偿分 |
| `48c18ae62` | **!2855** fix(scoreparse): 项目22供应商业绩阶梯计分最高分抽取 |
| `f922fe2bb` | **!2854** fix(casework): 案例推荐资质项错配与应急方案召回 |
| `c4c50acb7` | **!2853** feat(tender-reminder): 无配置标讯截止前 72 小时每日提醒（V1221 来源） |
| `7ac3054cb` | **!2852** fix(initiation): 立项通过后修改入围家数与不利项并同步 CRM |
| `181b5c731` | **!2851** fix(closure): 投标专员可填写保证金退回情况并提交结项 |
| `10af297ec` | **!2850** docs(release): 第 231 次测试环境部署报告 |

## 改动范围

- **数据库迁移**：3 个新增
  - `V1221__tender_reminder_log_nullable_setting.sql`（标讯提醒日志配置可空）
  - `V1222__create_case_precipitation_task.sql`（案例沉淀任务表）
  - `V1223__fix_case_precipitation_task_id_type.sql`（task_id 列类型纠正，本次部署阻断修复）
  - 回滚脚本 `U1221` / `U1222` / `U1223` 齐全
- **后端**：compliance / casework / scoreparse / tender-reminder / initiation / closure 六个模块的修复与加固
- **前端（16 文件）**：
  - 知识库案例详情：`views/Knowledge/views/components/caseDetailFormatter.js` 与 `.spec.js`、`CaseDetailDialog.spec.js`、`api/modules/knowledge.js`、`views/Knowledge/README.md`
  - 案例沉淀：`composables/useCasePrecipitationTask.js` 及其单测
  - 阶段页：`Project/stages/InitiationStage.vue`、`ClosureStage.vue`、`ResultConfirmStage.vue`、`components/AiRecommendDrawer.vue` 及对应 `.spec.js`、`useInitiationStageActions.js`
- **Sidecar 源码**：**零变更**（指纹 `24c0104f…`，schemaVersion 2.2，与已部署版本一致）
- **文档 / Wiki / 规格（27 文件）**：`docs/lessons/lessons-learned.md`（第 448 条）+ 第 231 次部署报告；`.wiki/pages/` 9 个页面 + `_index.md` + `log.md`（共 11 文件，其中页面 3 个新增）；`specs/075-077` 14 个文件

## Flyway 预检结果

```
部署前：flyway-repair-runner.sh validate -> ✅ 280 migrations checksums match
部署前 DB 版本：V1220
源码新增：V1221 / V1222 / V1223（3 pending）
部署后：1221 success=1、1222 success=1、1223 success=1（installed_on 2026-10-24 19:48:21）
case_precipitation_task.task_id 实际类型：varchar(36)（UNI 索引保留）
```

## 执行过程

1. 应用停机（`systemctl stop xiyu-bid-backend`）
2. 数据库备份 → `winbid-248bbf457-20261024194803.sql.gz`（15M）
3. `flyway-repair-runner.sh validate` → 280 迁移校验通过
4. 激活前端新产物 `/srv/www/xiyu-bid`（入口 `assets/index-BCurg_el.js`）
5. 原子替换后端 jar，写入 `/opt/xiyu-bid/deployed-release.json`
6. 启动服务，健康检查连续 3/3 通过（`remote-deploy.sh` 输出累计尝试计数 80，脚本原始输出）
7. 前端版本一致性校验通过
8. 回补上一版本 assets（`cp -rn`，当前 assets 277 个）

## Smoke 验证

| 项目 | 结果 |
|---|---|
| `GET /actuator/health` | HTTP 200 |
| `GET /actuator/health/readiness` | HTTP 200 |
| `POST /api/auth/login`（空体） | HTTP 400（预期参数校验） |
| `GET /api/projects`（未认证） | HTTP 401（预期鉴权） |
| `GET /api/integration/crm/health`（未认证） | HTTP 401（预期鉴权） |
| `GET /` | HTTP 200 |
| `GET /login` | HTTP 200 |
| 前端入口脚本 | `assets/index-BCurg_el.js`（与产物一致） |

## 已知配置

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：沿用历史决定，health 端点返回明细，仅测试环境。
- 本次未新增临时调试配置，无配置清理项。

## 回滚方案

1. 前端恢复上一版本：`/opt/xiyu-bid/releases/4ea606791/frontend` → `/srv/www/xiyu-bid`
2. 后端 jar 恢复上一版本并重启 `xiyu-bid-backend`
3. 数据库回滚（如需）：执行 `U1223` → `U1222` → `U1221`，或直接从 `winbid-248bbf457-20261024194803.sql.gz` 恢复
4. 注意：V1223 仅调整列类型，回滚不会丢数据；V1222 回滚会删除 `case_precipitation_task` 表（表内为后台任务记录）

## 结论

部署成功。版本 `4ea606791` → `248bbf457`，3 个迁移全部应用，健康检查与 Smoke 全部通过；部署阻断缺陷已在部署前通过 PR !2861 修复并纳入本次版本。
