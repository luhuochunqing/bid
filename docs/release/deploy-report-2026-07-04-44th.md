# 第 44 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署日期 | 2026-07-04 17:00 CST |
| Release ID | `76c425667-api8080` |
| 上一版本 | `860e7aea8-api8080`（第 43 次，2026-07-04 07:50 部署） |
| 部署类型 | 增量部署（纯 UI 修复，无 DB 迁移） |
| 部署结果 | ✅ 成功 |
| 健康检查 | ✅ UP（consecutive 3/3，总尝试 78 次） |
| Readiness | ✅ UP（无 Kafka SDK 延迟） |
| 部署耗时 | 约 2 分钟（16:56 打包完成 → 16:58 服务重启 → 健康检查通过） |

## 基线信息

| 项目 | 值 |
|---|---|
| 工作区 | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae/deploy-2026-07-04` |
| HEAD commit | `76c425667d9a3e3dc297e4bedc5b1301663f8acd` |
| 工作区状态 | 干净 |
| Git wrapper | ✅ 生效 |
| GitHub 镜像 | ✅ 同步完成（部署前落后 4 个 commit，部署后 0 落后） |

## 增量 PR 列表（4 个 commit）

| Commit | PR | 描述 |
|---|---|---|
| `52de08d4b` | — | fix(ui): CO-497 用标志位堵住 timeline 异步 snapshot 回声拽回 tab（方案 A） |
| `7b53c268b` | !1675 | docs(release): 第 43 次生产部署报告 |
| `76c425667` | !1676 | fix(ui): CO-497 方案 A 用标志位堵住 timeline 异步 snapshot 回声拽回 tab |
| `e441d3c56` | — | docs(release): 第 43 次生产部署报告（PR body） |

## 改动范围

**5 个文件改动**（4 个 UI 修复 + 1 个部署报告）：

- `docs/release/deploy-report-2026-07-04-43rd.md`（第 43 次部署报告）
- `src/components/project/detail/ProjectDetailMainColumn.spec.js`
- `src/components/project/detail/ProjectDetailMainColumn.vue`
- `src/views/Project/stages/RetrospectiveStage.spec.js`
- `src/views/Project/stages/RetrospectiveStage.vue`

**核心修复**：CO-497 复盘提交后跳转到结项阶段时，timeline 异步 snapshot 会"回声拽回"原 tab，导致用户看到的阶段与实际提交状态不一致。本次用标志位（方案 A）堵住异步 snapshot 回写，保证跳转后阶段稳定。

## Flyway 预检结果（3 步法）

| 步骤 | 结果 | 备注 |
|---|---|---|
| Step 1: validate | ✅ OK | 198 migrations, all checksums match |
| Step 2: DB 版本对比 | ✅ 一致 | DB V1134 = 源码 V1134（无新迁移） |
| Step 3: remote-deploy 内置 validate | ✅ 通过 | 部署时自动执行 |

**结论**：无新迁移需要应用，纯 UI 修复部署。

## 部署步骤

### 1. 早操三连

```bash
source scripts/dev-env.sh
bash scripts/sync-env.sh .       # ✅ rebase origin/main 成功
bash scripts/check-git-wrapper.sh # ✅ 7/7 通过
```

### 2. 本地打包（生产同源构建模式）

```bash
RELEASE_ID="76c425667-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh
```

- ✅ BUILD SUCCESS（59.106 s）
- ✅ jar 内 Flyway 迁移版本无重复
- ✅ 产物：`.release/xiyu-bid-release-76c425667-api8080.tar.gz`（138M）
- ✅ 前端入口：`assets/index-d1gyd3x5.js`

### 3. 上传 + 部署

```bash
scp .release/xiyu-bid-release-76c425667-api8080.tar.gz scripts/release/remote-deploy.sh \
    jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 'RELEASE_ARCHIVE=... RELEASE_ID=76c425667-api8080 \
    SYSTEMCTL_SUDO=true ... bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- ✅ Flyway validate 通过（198 migrations）
- ✅ DB 备份完成（`/opt/xiyu-bid/db-backups/winbid-76c425667-*.sql.gz`）
- ✅ 后端服务重启（PID 17878，2026-07-04 16:58:40 CST）
- ✅ 健康检查通过（consecutive 3/3，总尝试 78 次）
- ✅ 前端一致性验证通过（`index-d1gyd3x5.js` 与 release 一致）

## 验证结果

### 健康检查

| 端点 | 状态 | 备注 |
|---|---|---|
| `/actuator/health` | ✅ UP | 全组件 UP（db/redis/jwt/sidecar/aiProvider） |
| `/actuator/health/readiness` | ✅ UP | readinessState UP（无 Kafka SDK 延迟） |

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
| 部署前落后 commit 数 | 4 |
| 同步命令 | `bash scripts/sync-to-github.sh` |
| 同步后 Gitee main | `76c425667d9a3e3dc297e4bedc5b1301663f8acd` |
| 同步后 GitHub main | `76c425667d9a3e3dc297e4bedc5b1301663f8acd` |
| 状态 | ✅ 完全一致（0 落后） |

## 配置清理检查

| 配置项 | 状态 | 备注 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 用户连续 3 次决定保留（第 13/14/15 次），运维监控需要 |
| `SHOW_DETAILS` / `DEBUG` / `TRACE` 其他项 | 无 | 无其他临时调试配置 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一可用 release | `860e7aea8-api8080`（第 43 次，2026-07-04 07:50 部署） |
| 上一 release 目录 | `/opt/xiyu-bid/releases/860e7aea8-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-76c425667-*.sql.gz`（本次部署前生成） |
| 回滚命令 | `bash /opt/xiyu-bid/bin/rollback-to-release.sh 860e7aea8-api8080` |
| 回滚需求 | 不需要（本次部署无 P0 问题） |

**回滚姿态**：就绪（rollback 立即可执行），但当前不需要回滚。

## 经验沉淀应用情况

| 经验 | 本次应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部通过（无新迁移） |
| GitHub 镜像同步 | ✅ 部署后立即同步 |
| Smoke admin 密码限制 | ✅ 用 400/403/401 替代验证 |
| Mac HTTP_PROXY 502 | ✅ 通过 SSH 内部访问绕过 |
| systemctl sudo | ✅ `SYSTEMCTL_SUDO=true` 默认启用 |
| 配置清理检查 | ✅ SHOW_DETAILS=always 按用户决定保留 |
| readiness Kafka 延迟 | ✅ 本次未出现（启动后立即 UP） |
| SentryAppender crash-loop | ✅ 第 35 次已修复，本次未复发 |

## 风险提示

1. **CO-497 修复方案 A 是症状级修复**：用标志位堵住异步 snapshot 回写，根因（timeline 异步 snapshot 与跳转的时序竞争）仍存在。若后续复盘阶段有其他异步路径触发类似问题，需考虑方案 B（重构 timeline snapshot 调度）。
2. **GitHub 镜像同步依赖手工触发**：本次部署前发现 GitHub 落后 4 个 commit，未自动同步。如需 CI 自动化同步，需在 Gitee CI 中加入 post-merge 同步步骤。
3. **`SHOW_DETAILS=always` 仍在生产生效**：暴露健康详情（DB/Redis/JWT 等组件信息）。如需收紧安全，可改为 `never` 并重启后端。

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

本次部署为第 44 次。前一次（第 43 次，`860e7aea8-api8080`）于今日早些时候完成，部署内容为 `!1674 fix(tender): 删除标讯前检查关联项目，防止项目 tender_id 悬空`。本次部署在此基础上叠加 CO-497 UI 修复。

---

**部署执行人**：Trae Agent（主工作区）
**报告生成时间**：2026-07-04 17:05 CST
