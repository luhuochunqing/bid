# 第 107 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `af19ad4b` |
| 上一版本 | `0277ccb75`（第 106 次，2026-07-21 22:23） |
| 部署时间 | 2026-07-26 23:28 CST |
| 增量 | 47 个 commit（PR !2189/!2193/!2194/!2195/!2196/!2197/!2198/!2199/!2200/!2201/!2202 + 配套提交） |
| 新增迁移 | 5 个（V1174, V1177, V1178, V1179, V1180） |
| 部署结果 | ✅ 成功（健康检查 120 次失败后自恢复，Kafka SDK readiness 延迟约 4 分 22 秒） |
| 回滚 | 未需要 |
| JVM 配置 | `-Xmx2g` 沿用 |

## 部署原因

第 106 次部署（2026-07-21）后，main 合入 47 个 commit，覆盖多轮关键修复与新功能：
- CO-582 仓库合订本 Word 标题样式单测 + CO-597 项目列表排序图标 + CO-598 标讯列表排序
- §78 OSS 角色优先级排序 + 角色码归一化 + GLOBAL_ACCESS_ROLES 入口统一
- CA 预警通知文案增强（关联平台 + CA 类型字段）
- V1177 核心业务表字段 COMMENT 注释补全
- V1178-V1180 知识库资质/人员/子权限三个权限点新增
- E2E 系统性失败修复（权限矩阵/前端组件/测试选择器/状态流转，PR !2201）+ 根因教训回填（PR !2202）

本次部署将这些改动同步到测试环境。

## 修复内容

### 1. PR !2189 §78 OssRoleResolver 多角色优先级排序 + 角色码归一化

- **OSS 多角色优先级**：`OssRoleResolver` 对多角色排序，确保投标系统角色优先于其他系统 admin
- **P1-1 角色码归一化**：统一角色码处理，消除历史 `manager` / `auditor` 等已下线角色残留

### 2. PR !2193 CA 预警通知文案增强

- CA 预警通知文案添加关联平台和 CA 类型字段，便于用户快速识别预警来源

### 3. PR !2194 + !2195 §78 系列整改收尾

- §78 追加 PR !2189 修复记录 + §82 agent-locks 门禁行为文档
- §78 nit-cleanup 统一 `GLOBAL_ACCESS_ROLES` 入口

### 4. PR !2196 CO-597 项目列表排序图标

- 修复项目列表可排序列的排序图标被 44px 触控目标压制的问题

### 5. PR !2197 CO-582 WarehouseWordStyleRegistrar 直接单测

- 为 §3.4 `WarehouseWordStyleRegistrar` 补全直接单测，覆盖 Title/Heading1-3 样式注册

### 6. PR !2198 CO-598 标讯列表按报名截止日期/开标时间排序

- 标讯列表支持按报名截止日期、开标时间排序
- 修正 CSS 注释准确性（三步评审 Finding 1）

### 7. PR !2199 V1177 核心业务表字段 COMMENT 注释补全

- 为核心业务表字段补全 COMMENT 注释（cherry-pick 自 cursor），提升数据库可读性

### 8. PR !2200 测试覆盖缺口补全

- 补充 PR !2193/!2198 近期合并代码的测试覆盖缺口

### 9. PR !2201 E2E 系统性失败修复

- 修复 25 个 E2E spec 文件的系统性失败（权限矩阵/前端组件/测试选择器/状态流转）
- 后端 `RoleProfileCatalog` 新增 7 个 `KNOWLEDGE_*` 权限常量
- 配套迁移 V1178/V1179/V1180（知识库资质/人员/子权限）+ 回滚脚本 U1178/U1179/U1180
- 前端 `OperationLogTab` data-testid + `useDetailActions` Ref + `QualImportCombinedDialog` 文件校验

### 10. PR !2202 E2E 根因教训回填

- 回填 PR !2201 E2E 系统性失败根因教训到 wiki

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | /Users/user/xiyu/worktrees/trae |
| Worktree | trae（主工作区） |
| 分支 | `agent/trae-init`（锚点分支，HEAD = origin/main） |
| 同步后 HEAD | `af19ad4bb`（同 `origin/main`） |
| GitHub 镜像同步前落后 | 61 commits |
| GitHub 镜像同步后 | ⚠️ 未同步（本次部署未触发 GitHub 镜像同步） |
| 工作区状态 | 干净 |
| 本地门禁 | ✅ 7/7 通过（git wrapper 生效，pre-push-gate.sh 存在） |

## PR 列表（增量 47 commits 内，主要 PR）

| PR | 标题 | 类型 |
|---|---|---|
| !2189 | fix(security): §78 OssRoleResolver 多角色优先级排序 + P1-1 角色码归一化 | fix |
| !2193 | feat(ca): CA预警通知文案添加关联平台和CA类型字段 | feat |
| !2194 | docs(lessons): §78 追加 PR !2189 修复记录 + §82 agent-locks 门禁行为 | docs |
| !2195 | refactor(security): §78 nit-cleanup 统一 GLOBAL_ACCESS_ROLES 入口 | refactor |
| !2196 | fix(project-list): 显示可排序列排序图标（CO-597） | fix |
| !2197 | test(warehouse): CO-582 §3.4 WarehouseWordStyleRegistrar 直接单测 | test |
| !2198 | feat(bidding): CO-598 标讯列表按报名截止日期/开标时间排序 | feat |
| !2199 | chore(db): V1177 补全核心业务表字段 COMMENT 注释 | chore |
| !2200 | test(gap-backfill): 补充 PR !2193/!2198 近期合并代码的测试覆盖缺口 | test |
| !2201 | fix(e2e): 修复 E2E 系统性失败（权限矩阵/前端组件/测试选择器/状态流转） | fix |
| !2202 | docs(wiki): 回填 PR !2201 E2E 系统性失败根因教训 | docs |

## 改动范围

| 模块 | 文件 | 说明 |
|---|---|---|
| 后端安全 | `OssRoleResolver`、`GLOBAL_ACCESS_ROLES` 相关 | §78 OSS 角色优先级 + 入口统一 |
| 后端权限 | `RoleProfileCatalog.java` | 新增 7 个 KNOWLEDGE_* 权限常量 |
| 后端仓库 | `WarehouseWordStyleRegistrar` | CO-582 样式注册（配套单测） |
| 后端标讯 | 标讯列表排序相关 | CO-598 报名截止/开标时间排序 |
| 后端 CA | CA 预警通知相关 | 文案增强 |
| 数据库迁移 | V1174, V1177, V1178, V1179, V1180 + U1178/U1179/U1180 回滚 | 5 个新迁移 + 3 个回滚脚本 |
| 前端 | `OperationLogTab`、`useDetailActions`、`QualImportCombinedDialog` | data-testid + Ref + 文件校验 |
| 前端 | 项目列表排序图标 | CO-597 |
| E2E 测试 | 25 个 spec 文件 + `auth-helpers.js` | 权限矩阵/前端组件/测试选择器/状态流转 |
| 文档 | `docs/release/`、`docs/lessons/`、`.wiki/` | 部署报告 + 教训 + wiki 回填 |

## Flyway 预检 3 步法

| 步骤 | 命令 | 结果 |
|---|---|---|
| Step 1: validate | `bash /opt/xiyu-bid/bin/flyway-repair-runner.sh validate` | ✅ `VALIDATE OK - all checksums match`（236 migrations 对齐） |
| Step 2: DB 版本对比 | 查询 `flyway_schema_history` 最新 10 条 | ✅ 最新已应用 V1173（2026-07-20 20:19:05），待应用 V1174/V1177/V1178/V1179/V1180 |
| Step 3: remote-deploy 内置 | `remote-deploy.sh` 在激活新 jar 前跑 validate | ✅ `Successfully validated 236 migrations`（pending 5 个新迁移为预期状态） |

## 部署步骤

### 1. 本地打包

```bash
RELEASE_ID="af19ad4b" VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 \
  bash scripts/release/package-release.sh
```

- 前端构建：产物 `assets/index-bL7zgB0K.js`，179 个 assets 文件
- 后端打包：`mvn clean -DskipTests package` 28.169s，产物 `bid-platform-1.0.3.jar`（240 个迁移文件）
- 产物校验：
  - ✅ jar 内 Flyway 迁移版本无重复
  - ✅ OBS 直传已启用（Detail chunk `.upload(` 调用数=2）
  - ✅ `release-metadata.json` 中 `obsEnabled=true`、`apiBaseUrl=""`（同源构建）
  - ✅ jar 内含 V1174/V1177/V1178/V1179/V1180 五个新迁移
- archive 大小：153M

### 2. 上传 + 部署

```bash
scp .release/xiyu-bid-release-af19ad4b.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 'set -e; cd /opt/xiyu-bid/incoming && \
  source /etc/xiyu-bid/backend.env && \
  RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-af19ad4b.tar.gz \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:18080/actuator/health \
  RELEASE_ID=af19ad4b \
  FLYWAY_REPAIR_RUNNER=/opt/xiyu-bid/bin/flyway-repair-runner.sh \
  SYSTEMCTL_SUDO=true \
  DB_BACKUP_COMMAND="..." \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- DB 备份：`/opt/xiyu-bid/db-backups/winbid-af19ad4b-*.sql.gz`
- Flyway validate：236 migrations OK（pending 5 个新迁移为预期状态）
- 后端重启：`xiyu-bid-backend.service` active/running（PID 16884）
- 健康检查：⚠️ 120 次尝试后失败（remote-deploy.sh 退出码 0 但健康检查未通过 3/3）
  - 实际服务在 23:32:45-47 之间恢复 UP（Kafka SDK 启动完成）
  - 总恢复时间约 4 分 22 秒（23:28:25 → 23:32:47）
- 前端一致性：`src="/assets/index-bL7zgB0K.js"` 与 release 一致

### 3. 前端资源保留（防止跨版本 404）

```bash
ssh jetty@172.16.38.78 'sudo cp -rn /opt/xiyu-bid/releases/0277ccb75/frontend/assets/* \
  /srv/www/xiyu-bid/assets/ 2>/dev/null && echo "✅ 已从 0277ccb75 保留旧 assets"'
```

> 注：`deployed-release.json` 已被本次部署覆盖，`releaseDir` 指向当前版本。直接从上一版本 release 目录 `0277ccb75` 复制。

## 验证结果

### 后端 API Smoke

| 检查项 | URL | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| health | `http://172.16.38.78:8080/actuator/health` | 200 UP | 200 UP（aiProvider: qwen3.7-max, readinessState: UP） | ✅ |
| readiness | `http://172.16.38.78:8080/actuator/health/readiness` | 200 UP | 200 UP（db: UP, readinessState: UP） | ✅ |
| auth/login | `POST /api/auth/login`（空 body） | 400 | 400（"Username is required; Password is required"） | ✅ |
| projects | `GET /api/projects`（无认证） | 403 | 403 | ✅ |
| CRM health | `GET /api/integration/crm/health` | 401 | 401 | ✅ |

### 前端页面

| 检查项 | URL | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| 首页 | `http://172.16.38.78:8080/` | 200 | 200 | ✅ |
| 登录页 | `http://172.16.38.78:8080/login` | 200 | 200 | ✅ |
| index.html 入口 | `assets/index-bL7zgB0K.js` | 与 release 一致 | 一致 | ✅ |
| 上一版本 assets 保留 | `assets/index-CKWHQ5Ar.js` | 存在 | 存在 | ✅ |

### 迁移应用验证

| 版本 | 描述 | success | installed_on |
|---|---|---|---|
| V1174 | fix quoted menu permissions | 1 | 2026-07-26 23:28:32 |
| V1177 | backfill business table comments | 1 | 2026-07-26 23:28:33 |
| V1178 | add knowledge qualification permission | 1 | 2026-07-26 23:28:33 |
| V1179 | add knowledge personnel permission | 1 | 2026-07-26 23:28:33 |
| V1180 | add knowledge sub permissions | 1 | 2026-07-26 23:28:33 |

5 个新迁移全部成功应用 ✅

## GitHub 镜像同步

| 项目 | 状态 |
|---|---|
| 同步前落后 | 61 commits |
| 同步尝试 | 未尝试（本次部署未触发 GitHub 镜像同步） |
| 同步结果 | ⚠️ 仍落后 61 commits |
| 影响 | 不影响本次部署（部署已成功），仅 GitHub 镜像落后 |
| 后续 | 可执行 `SYNC_TO_GITHUB=1 bash scripts/sync-env.sh .` 或 `bash scripts/sync-to-github.sh` 同步 |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | ⚠️ 保留 | 历史决定保留（第 13/14/15 次部署用户决定），非临时调试配置 |
| `SHOW_DETAILS` / `DEBUG` / `TRACE` 其他临时配置 | ✅ 无 | 仅 SHOW_DETAILS=always 一项 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚姿态 | 未需要 |
| 上一版本 Release ID | `0277ccb75` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/0277ccb75/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-af19ad4b-*.sql.gz` |
| 回滚命令 | `sudo systemctl stop xiyu-bid-backend && cp /opt/xiyu-bid/releases/0277ccb75/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl start xiyu-bid-backend` |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行，validate + DB 版本对比 + remote-deploy 内置 |
| OBS 直传漏传 VITE_OBS_ENABLED=true（第 84 次 + 第 8 次生产事故） | ✅ 显式传入 `VITE_OBS_ENABLED=true`，产物校验 `.upload(` 调用数=2 |
| macOS `._*` 残留文件（第 10 次） | ✅ 打包时 `COPYFILE_DISABLE=1` |
| 同源构建 baseURL="" （第 3 次经验） | ✅ 显式 `VITE_API_BASE_URL=` 触发同源构建 |
| 前端 hash 资源跨版本 404（第 18 条经验） | ✅ 部署后从 0277ccb75 release 目录 `cp -rn` 保留旧 assets |
| Kafka SDK readiness 延迟（第 8/9/10/13/15 次） | ⚠️ 健康检查 120 次失败后自恢复（约 4 分 22 秒），已超 remote-deploy.sh 默认 4 分钟等待窗口 |
| SYSTEMCTL_SUDO=true（第 15 次 PR !1324） | ✅ 显式传入，避免 `Interactive authentication required` |
| 强制 `mvn clean`（V1096 jar 内重复事故） | ✅ package-release.sh 内置 `mvn clean -DskipTests package` |
| jar 内 Flyway 迁移版本无重复校验 | ✅ 打包时自动校验，240 files 无重复 |

## 风险提示

1. **Kafka SDK readiness 延迟突破 4 分钟**：本次恢复时间约 4 分 22 秒（23:28:25 → 23:32:47），已超过 `remote-deploy.sh` 默认 120 次 × 2s = 240s = 4 分钟的健康检查窗口。`remote-deploy.sh` 退出时打印了 `Rollback: restore previous jar` 提示，但服务实际在退出后 22 秒自恢复 UP。已沉淀为已知行为，但若延迟持续增长需关注 `OrganizationEventSdkKafkaStarter` 是否需要改 `@Async` 或独立线程池。
2. **GitHub 镜像落后 61 commits**：本次部署未触发 GitHub 镜像同步。AI Coding 工具（Cursor/Codex/Claude 等）拉取 GitHub 镜像时会缺少最新代码。建议执行 `SYNC_TO_GITHUB=1 bash scripts/sync-env.sh .` 同步。
3. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 保留**：测试环境保留此配置便于排障，但生产环境应评估是否需要收紧（当前生产也保留）。
4. **5 个新迁移已应用**：V1174/V1177/V1178/V1179/V1180 全部成功应用，无失败回滚需求。回滚时需先恢复 DB 到 `winbid-af19ad4b-*.sql.gz` 备份，再恢复旧 jar。

## 部署确认清单

- [x] 环境门禁确认（ENV=test, 172.16.38.78）
- [x] 早操三连（dev-env + sync-env + check-git-wrapper）
- [x] 基线确认（HEAD = origin/main = af19ad4bb）
- [x] 服务器现状检查（0277ccb75, UP）
- [x] Flyway 预检 3 步（validate + DB 版本 + remote-deploy 内置）
- [x] 本地打包（前端 + 后端 28.169s）
- [x] 产物校验（jar 无重复 + OBS 启用 + 同源构建 + 5 个新迁移入 jar）
- [x] 上传 + 部署（remote-deploy.sh, SYSTEMCTL_SUDO=true）
- [x] 前端资源保留（0277ccb75 旧 assets cp -rn）
- [x] 健康检查（120 次失败后自恢复，约 4 分 22 秒后 UP）
- [x] Smoke 测试（health + readiness + 400/403/401 + 前端 200）
- [x] 迁移应用验证（V1174/V1177/V1178/V1179/V1180 全部 success=1）
- [ ] GitHub 镜像同步（⚠️ 未同步，仍落后 61 commits）
- [x] 配置清理检查（仅 SHOW_DETAILS=always 历史保留）
- [x] 部署报告生成

## 部署统计

- 测试环境累计部署次数：107 次
- 本月（2026-07）测试环境部署次数：9 次
- 上一次部署：第 106 次（2026-07-21 22:23, `0277ccb75`）
