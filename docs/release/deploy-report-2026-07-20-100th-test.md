# 第 100 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `4b0e9ea` |
| 上一版本 | `343ca660e`（第 99 次，2026-07-19 21:18） |
| 部署时间 | 2026-07-20 09:38 CST |
| 增量 | 37 commit（工作台待办模块角色化改造 + 工作台截止时间模块改造 + spec 039 OBS 直传归档 + CO-591 项目列表列宽 + lessons-learned 章节编号冲突门禁） |
| 新增迁移 | V1171 (spec 039 OBS 直传历史文档回填 archive_file) |
| 部署结果 | ✅ 成功（健康检查 79 次尝试通过，约 2 分 30 秒，未出现 Kafka readiness 延迟） |
| 回滚 | 未需要 |
| JVM 配置 | `-Xmx2g` 沿用 |

## 部署原因

本次部署合入五组业务功能与治理改造：

1. **工作台待办模块角色化改造**（PR !2115/!2134，CO-593 相关）：按角色返回项目待办，BE-1~4 + FE-1 完整链路改造，包含 JOIN 优化、审计注解、枚举类型安全、模块化整理
2. **工作台截止时间模块改造**（PR !2147，CO-593）：新增 `/api/workbench/deadline-items` 真实条目查询接口，替换前端 mock 数据，含竞态保护
3. **spec 039 OBS 直传文档同步归档到项目档案**（PR !2144）：将 OBS 直传上传的文档自动归档到项目档案，V1171 回填历史缺失数据
4. **CO-591 项目列表列宽调整**（PR !2146）：调整标书审核人和评标结果列宽
5. **lessons-learned 章节编号冲突门禁**（PR !2150）：新增 §9.10 门禁，防止并行归档撞号

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | /Users/user/xiyu/worktrees/trae |
| 分支 | agent/trae-init（锚点分支，部署不创建任务分支） |
| HEAD commit | `4b0e9ea56788998c23059eb5349bbbe954d46bd8` |
| origin/main | `4b0e9ea`（完全一致） |
| GitHub 镜像 | ⚠️ 落后 37 个 commit（部署前未同步，建议部署后执行 sync-to-github.sh） |
| git wrapper | ✅ 生效（scripts/git） |
| Flyway validate | ✅ 通过（233 migrations, all checksums match） |
| DB 已应用最新版本 | V1170（第 99 次部署） |
| 源码最新迁移版本 | V1171（待应用，本次部署将应用） |

## 增量 PR 列表

| PR | 类型 | 标题 |
|---|---|---|
| !2150 | feat(gate) | 新增 lessons-learned 章节编号冲突门禁（§9.10，防并行归档撞号） |
| !2149 | test | 补充导出下载/CO-582/CO-586 测试缺口（27 用例全绿） |
| !2148 | docs(lessons) | 第 73 节——review PR 必须看 commit vs parent 的实际 diff（PR !2146 误判教训） |
| !2147 | feat(workbench) | 工作台截止时间模块改造（真实条目接口 + 竞态保护）(CO-593) |
| !2146 | style(project-list) | 调整标书审核人和评标结果列宽 (CO-591) |
| !2145 | fix(scripts) | 移除 agent-start-task cleanup 中未绑定变量的死代码 |
| !2144 | spec 039 | OBS 直传文档同步归档到项目档案 |
| !2143 | docs(lessons) | 第 72 节——分支基线过期导致 PR diff 静默回退/删除他人文件 |
| !2134 | refactor(workbench) | PR #2115 设计修复（JOIN优化/审计/枚举类型安全/模块化） |
| !2115 | feat(workbench) | 工作台待办模块角色化改造（BE-1~4 + FE-1） |

## 改动范围

### 1. 工作台待办模块角色化改造（!2115 + !2134）
- **BE-1**：`/api/tasks/my` 新增 `projectStage` 过滤参数
- **BE-3**：`/api/projects/workbench-todos` 按角色返回项目待办
- **BE-4**：`/api/dashboard/resource-pending-approvals` 聚合资源待审批
- **FE-1**：工作台待办模块角色化前端改造
- **设计修复**：JOIN 优化（`findByAssigneeIdAndProjectStage` 从 IN 子查询改为 JOIN）、`@Auditable` 审计注解、枚举类型安全、workbenchApi 整合
- **测试**：补充 24 个 CO-593 截止时间模块测试用例

### 2. 工作台截止时间模块改造（!2147，CO-593）
- 新增 `/api/workbench/deadline-items` 真实条目查询接口
- 前端对接真实接口，替换 mock 数据
- 竞态保护机制

### 3. spec 039 OBS 直传文档同步归档（!2144）
- **V1171 迁移**：`backfill_archive_files_for_obs_direct_uploads` 回填历史 OBS 直传文档到 `archive_file` 表
- 归档逻辑上提到 `createProjectDocument` 末尾统一触发
- 提取 `ARCHIVE_FILE_SIZE_UNKNOWN` 常量替代 0L 字面量
- 修复归档 `file_path` 语义与 obs-direct 下载链路

### 4. CO-591 项目列表列宽（!2146）
- 调整标书审核人和评标结果列宽

### 5. lessons-learned 章节编号冲突门禁（!2150）
- 新增 §9.10 门禁，防止并行归档撞号
- 第 72/73 节教训沉淀

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（233 migrations） |
| Step 2: DB 已应用版本 vs 源码最新版本 | ✅ DB=V1170，源码=V1171，增量 1 个新迁移 |
| Step 3: remote-deploy.sh 内置 validate | ✅ 部署时自动通过 |

## 部署步骤

| 步骤 | 命令 / 结果 |
|---|---|
| 本地打包 | `RELEASE_ID=4b0e9ea VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 bash scripts/release/package-release.sh` |
| 产物校验 | ✅ jar 内 233 个迁移文件无重复；V1171 存在；OBS obsEnabled=true；Detail chunk .upload( 调用数=2 |
| 上传 | `scp .release/xiyu-bid-release-4b0e9ea.tar.gz scripts/release/remote-deploy.sh jetty@172.16.38.78:/opt/xiyu-bid/incoming/` |
| 部署 | `remote-deploy.sh`（SYSTEMCTL_SUDO=true，含 DB 备份） |
| Flyway validate | ✅ 233 migrations, all checksums match |
| 后端重启 | ✅ active (running) since 2026-07-20 09:38:16 CST |
| 健康检查 | ✅ 79 次尝试通过（consecutive 3/3），约 2 分 30 秒 |
| 前端一致性 | ✅ `/assets/index-XcC1Psz3.js`（与 release 一致） |
| 前端资源保留 | ✅ 从上一版本 `343ca660e` release 目录 cp -rn 旧 assets 到 `/srv/www/xiyu-bid/assets/` |

## 验证结果

### Smoke 测试（经 Nginx 8080 代理）

| 检查项 | 状态码 | 预期 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 | UP | ✅ |
| `/actuator/health/readiness` | 200 | UP | ✅（未出现 Kafka readiness 延迟） |
| `POST /api/auth/login`（空体） | 400 | 验证错误 | ✅ |
| `/api/projects` | 403 | 需认证 | ✅ |
| `/api/integration/crm/health` | 401 | 需认证 | ✅ |
| 前端 `/` | 200 | OK | ✅ |
| 前端 `/login` | 200 | OK | ✅ |
| 前端 index.html 入口 | `assets/index-XcC1Psz3.js` | 与 release 一致 | ✅ |

### Flyway 迁移应用验证

```sql
SELECT version, description, success, installed_on FROM flyway_schema_history WHERE version='1171';
```

| version | description | success | installed_on |
|---|---|---|---|
| 1171 | backfill archive files for obs direct uploads | 1 | 2026-07-20 09:38:23 |

## 配置清理检查

| 检查项 | 结果 |
|---|---|
| `SHOW_DETAILS/DEBUG/TRACE` 临时配置 | `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留，第 13-15 次决定保留） |
| 其他临时调试配置 | ✅ 无 |

## GitHub 镜像同步

| 项目 | 状态 |
|---|---|
| 部署前 github/main..origin/main | 37 个 commit 落后 |
| 部署后同步 | ⚠️ 待执行（建议执行 `bash scripts/sync-to-github.sh`） |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚命令（后端） | `ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && sudo cp /opt/xiyu-bid/releases/343ca660e/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl start xiyu-bid-backend'` |
| 回滚命令（前端） | `ssh jetty@172.16.38.78 'sudo cp -r /opt/xiyu-bid/releases/343ca660e/frontend/* /srv/www/xiyu-bid/'` |
| 回滚命令（数据库） | ⚠️ V1171 为幂等 INSERT...SELECT，回滚仅需 DELETE 新增记录或使用 U1171 rollback 脚本 |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-4b0e9ea-*.sql.gz` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/343ca660e/` |
| 回滚 posture | ready（未需要） |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行 |
| OBS 直传显式传入 VITE_OBS_ENABLED=true | ✅ 双保险（脚本默认 + 显式传入） |
| COPYFILE_DISABLE=1 防 macOS `._*` 残留 | ✅ |
| SYSTEMCTL_SUDO=true | ✅ |
| 前端 hash 资源跨版本 404 防护 | ✅ 从上一版本 cp -rn 保留旧 assets |
| Mac HTTP_PROXY 502 绕过 | ✅ curl --noproxy '*' |
| 健康检查容忍 Kafka 延迟 | ✅ 79 次尝试（未触发延迟） |
| 幂等迁移设计 | ✅ V1171 LEFT JOIN + WHERE af.id IS NULL |

## 风险提示

1. **GitHub 镜像落后 37 个 commit**：建议部署后执行 `bash scripts/sync-to-github.sh` 同步镜像
2. **V1171 数据回填**：历史 OBS 直传文档已回填到 archive_file 表，建议业务侧验证项目档案视图是否正确显示这些文档
3. **工作台待办模块角色化**：BE-1~4 + FE-1 完整链路改造，建议各角色（管理员、投标组长、投标专员、投标项目负责人、行政人员）登录验证待办列表

## 部署确认清单

- [x] 环境门禁确认（ENV=test, TARGET_HOST=172.16.38.78）
- [x] 早操三连 + 基线确认
- [x] 服务器现状查询（deployed-release.json + health）
- [x] 增量 commit 和 Flyway 迁移文件变更检查
- [x] Flyway 预检 3 步法
- [x] 本地打包（VITE_OBS_ENABLED=true + COPYFILE_DISABLE=1）
- [x] 产物校验（jar 内迁移文件 + OBS obsEnabled=true）
- [x] 上传 + 部署（SYSTEMCTL_SUDO=true）
- [x] 前端资源保留（cp -rn 上一版本 assets）
- [x] 健康检查（UP，79 次尝试）
- [x] 迁移应用验证（V1171 success=1）
- [x] Smoke 测试（7 项全绿）
- [x] 配置清理检查（仅历史保留项）
- [ ] GitHub 镜像同步（待执行）
- [x] 部署报告生成
