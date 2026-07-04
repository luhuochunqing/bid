# 第 45 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署日期 | 2026-07-04 18:24 CST |
| Release ID | `eb8ce51a5-api8080` |
| 上一版本 | `76c425667-api8080`（第 44 次，2026-07-04 17:00 部署） |
| 部署类型 | 增量部署（业务功能更新，无 DB 迁移） |
| 部署结果 | ✅ 成功 |
| 健康检查 | ✅ UP（consecutive 3/3，总尝试 79 次） |
| Readiness | ✅ UP（Kafka SDK 约 2 分 40 秒延迟，属已知行为） |
| 部署耗时 | 约 2 分钟（18:22 打包完成 → 18:23 服务重启 → 健康检查通过） |

## 基线信息

| 项目 | 值 |
|---|---|
| 工作区 | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae-init`（锚点分支） |
| HEAD commit | `eb8ce51a54dbdf5e05c2176989ab20b301b853e6` |
| 工作区状态 | 干净 |
| Git wrapper | ✅ 生效 |
| GitHub 镜像 | ✅ 同步完成（部署前落后 9 个 commit，部署后 0 落后） |

## 增量 PR 列表（11 个 commit）

| Commit | PR | 描述 |
|---|---|---|
| `60a0b8e50` | — | docs(release): 第 44 次生产部署报告 |
| `5637c2b0b` | !1677 | docs(release): 第 44 次生产部署报告 |
| `8cf88f3b3` | — | CO-484 v2: 标书审核多人化规则更新（上限3人/需含项目经理/辅助人员解禁） |
| `c167a6d26` | — | docs(spec): CO-498 spec.md — 复盘提交后解锁结项 tab |
| `1cd8401b7` | — | docs(plan): CO-498 plan.md — 最小侵入 controller 改动 + 4 个新测试 |
| `6ffd86c74` | — | docs(tasks): CO-498 tasks.md — TDD 17 任务 + 依赖图 |
| `566e3a85b` | — | fix(stage): CO-498 复盘阶段提交后解锁结项 tab（方案 A） |
| `4c2b11a1b` | — | fix(personnel): CO-469 第六轮，修复导出 zip 无法解压 + 0 条记录仍显示下载按钮 |
| `5cecd3776` | !1681 | fix(stage): CO-498 复盘阶段提交后解锁结项 tab（方案 A） |
| `f7306a5ad` | !1679 | fix(personnel): CO-469 第六轮，修复导出 zip 无法解压 + 0 条记录仍显示下载按钮 |
| `eb8ce51a5` | !1678 | CO-484 v2: 标书审核多人化规则更新（上限3人/需含项目经理/辅助人员解禁） |

## 改动范围

**核心业务变更**（3 个功能模块）：

### 1. CO-484 v2：标书审核多人化规则更新
- 审核人数上限调整为 3 人
- 审核组必须包含项目经理
- 辅助人员角色解禁（可参与审核）
- 涉及模块：标书审核流程、人员选择器

### 2. CO-498：复盘阶段提交后解锁结项 tab
- 方案 A：标志位法，堵住 timeline 异步 snapshot "回声拽回" 问题
- 复盘提交后结项 tab 正常解锁并跳转
- 涉及：`ProjectDetailMainColumn.vue`、`RetrospectiveStage.vue`

### 3. CO-469 第六轮：人员证书导出修复
- 修复导出 zip 无法解压问题（macOS `._*` 残留 + 压缩格式）
- 修复 0 条记录仍显示下载按钮的问题
- 涉及：人员证书模块导出功能

## Flyway 预检结果（3 步法）

| 步骤 | 结果 | 备注 |
|---|---|---|
| Step 1: validate | ✅ OK | 198 migrations, all checksums match |
| Step 2: DB 版本对比 | ✅ 一致 | DB V1134 = 源码 V1134（无新迁移） |
| Step 3: remote-deploy 内置 validate | ✅ 通过 | 部署时自动执行 |

**结论**：无新迁移需要应用，纯业务代码 + UI 修复部署。

## 部署步骤

### 1. 早操三连

```bash
source scripts/dev-env.sh
bash scripts/sync-env.sh .       # ✅ rebase origin/main 成功（落后 9 个 commit）
bash scripts/check-git-wrapper.sh # ✅ 7/7 通过
```

### 2. 本地打包（生产同源构建模式）

```bash
RELEASE_ID="eb8ce51a5-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh
```

- ✅ BUILD SUCCESS（后端 28.196 s，前端 9.06 s）
- ✅ jar 内 Flyway 迁移版本无重复
- ✅ 产物：`.release/xiyu-bid-release-eb8ce51a5-api8080.tar.gz`（145M）
- ✅ 前端入口：`assets/index-B5bw9ZFm.js`

### 3. 上传 + 部署

```bash
scp .release/xiyu-bid-release-eb8ce51a5-api8080.tar.gz scripts/release/remote-deploy.sh \
    jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 'RELEASE_ARCHIVE=... RELEASE_ID=eb8ce51a5-api8080 \
    SYSTEMCTL_SUDO=true ... bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- ✅ Flyway validate 通过（198 migrations）
- ✅ DB 备份完成（`/opt/xiyu-bid/db-backups/winbid-eb8ce51a5-*.sql.gz`）
- ✅ 后端服务重启（PID 24092，2026-07-04 18:23:58 CST）
- ✅ 健康检查通过（consecutive 3/3，总尝试 79 次）
- ✅ 前端一致性验证通过（`index-B5bw9ZFm.js` 与 release 一致）

## 验证结果

### 健康检查

| 端点 | 状态 | 备注 |
|---|---|---|
| `/actuator/health` | ✅ UP | 全组件 UP（db/redis/jwt/sidecar/aiProvider） |
| `/actuator/health/readiness` | ✅ UP | readinessState UP（Kafka SDK 约 2 分 40 秒延迟，属已知行为） |

### Smoke 测试（admin 密码未知，用 400/403/401 验证接口路由）

| 接口 | HTTP | 预期 | 结果 |
|---|---|---|---|
| `POST /api/auth/login`（空 body） | 400 | 验证错误 | ✅ |
| `GET /api/projects`（无认证） | 403 | 需认证 | ✅ |
| `GET /api/integration/crm/health` | 401 | 需认证 | ✅ |
| `GET /`（前端首页） | 200 | OK | ✅ |
| `GET /login`（前端登录页） | 200 | OK | ✅ |

**登录 Smoke 跳过说明**：admin 密码未授予，完整登录 smoke 无法完成，使用 400/403/401 替代验证策略（自第 8 次起固化）。

## GitHub 镜像同步

| 项目 | 状态 |
|---|---|
| 部署前落后 commit 数 | 9 |
| 同步命令 | `bash scripts/sync-to-github.sh` |
| 同步后 Gitee main | `eb8ce51a54dbdf5e05c2176989ab20b301b853e6` |
| 同步后 GitHub main | `eb8ce51a54dbdf5e05c2176989ab20b301b853e6` |
| 状态 | ✅ 完全一致（0 落后） |

## 配置清理检查

| 配置项 | 状态 | 备注 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 用户连续多次决定保留，运维监控需要 |
| `SHOW_DETAILS` / `DEBUG` / `TRACE` 其他项 | 无 | 无其他临时调试配置 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一可用 release | `76c425667-api8080`（第 44 次，2026-07-04 17:00 部署） |
| 上一 release 目录 | `/opt/xiyu-bid/releases/76c425667-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-eb8ce51a5-*.sql.gz`（本次部署前生成） |
| 回滚命令 | `bash /opt/xiyu-bid/bin/rollback-to-release.sh 76c425667-api8080` |
| 回滚需求 | 不需要（本次部署无 P0 问题） |

**回滚姿态**：就绪（rollback 立即可执行），但当前不需要回滚。

## 经验沉淀应用情况

| 经验 | 本次应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部通过（无新迁移） |
| GitHub 镜像同步 | ✅ 部署后立即同步 |
| Smoke admin 密码限制 | ✅ 用 400/403/401 替代验证 |
| Mac HTTP_PROXY 502 | ✅ curl 加 `--noproxy '*'` 绕过 |
| systemctl sudo | ✅ `SYSTEMCTL_SUDO=true` 默认启用 |
| 配置清理检查 | ✅ SHOW_DETAILS=always 按用户决定保留 |
| readiness Kafka 延迟 | ✅ 约 2 分 40 秒，属已知行为，未误判为故障 |
| SentryAppender crash-loop | ✅ 第 35 次已修复，本次未复发 |

## 风险提示

1. **CO-498 方案 A 为症状级修复**：标志位法堵住异步 snapshot 回写，根因（timeline 异步 snapshot 与跳转的时序竞争）仍存在。若后续复盘阶段有其他异步路径触发类似问题，需考虑方案 B（重构 timeline snapshot 调度）。
2. **CO-484 多人审核需业务验证**：审核规则涉及多人协作流程，建议 UAT 验证 3 人上限、项目经理必含、辅助人员解禁等场景。
3. **`SHOW_DETAILS=always` 仍在生产生效**：暴露健康详情（DB/Redis/JWT 等组件信息）。如需收紧安全，可改为 `never` 并重启后端。
4. **GitHub 镜像同步依赖手工触发**：部署前发现 GitHub 落后 9 个 commit，未自动同步。如需 CI 自动化同步，需在 Gitee CI 中加入 post-merge 同步步骤。

## 部署确认清单

- [x] 早操三连通过（sync-env + check-git-wrapper）
- [x] 工作区干净，HEAD = origin/main
- [x] Flyway 预检 3 步法通过
- [x] 本地打包成功（jar 内无重复迁移版本）
- [x] 上传 + 部署成功（remote-deploy.sh 退出码 0）
- [x] 健康检查通过（health UP, readiness UP）
- [x] Smoke 测试通过（400/403/401 + 前端 200）
- [x] 前端一致性验证通过（index.html 入口与 release 一致）
- [x] GitHub 镜像同步完成（0 落后）
- [x] 配置清理检查完成（仅 SHOW_DETAILS=always 保留）
- [x] 部署报告生成

## 部署历史延续

本次部署为第 45 次。前一次（第 44 次，`76c425667-api8080`）于今日早些时候完成，部署内容为 CO-497 UI 修复。本次部署在此基础上叠加 3 个业务模块更新：CO-484 标书审核多人化、CO-498 复盘结项解锁、CO-469 人员证书导出修复。

---

**部署执行人**：Trae Agent（主工作区）
**报告生成时间**：2026-07-04 18:25 CST
