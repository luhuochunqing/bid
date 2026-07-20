# 第 13 次生产环境部署报告 — 2026-07-20

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产 (prod)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.10.149` |
| 用途 | 正式环境、对外服务 |
| 部署序号 | 第 13 次（生产） |
| 部署时间 | 2026-07-20 23:21:56 CST |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `ad9f2378c-prod` |
| 上一版本 Release | `ce55d7d09`（2026-07-19 12:50:01 CST，第 12 次生产部署） |
| 基线 commit | `ad9f2378c`（origin/main） |
| 激活时间 | 2026-07-20T15:21:56Z |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | V1169, V1170, V1171, V1172, V1173（5 个） |
| Smoke 测试 | 全部通过 |
| GitHub 镜像 | 已同步（0 commits behind） |

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/trae`
- 锚点分支：`agent/trae-init`（ff-only 同步到 origin/main）
- 早操 SOP：已执行 `sync-env.sh`，HEAD = origin/main = `ad9f2378c`
- GitHub 镜像状态（部署前）：落后 6 commits，部署后已同步
- 本地门禁自检：7 项全部通过（hooksPath、pre-commit、pre-push、git wrapper、agent-locks 等）
- pre-push 门禁：15 道全绿

## 增量改动（ce55d7d09 → ad9f2378c，约 90+ 个 commit）

### 主要 PR 列表（按时间倒序，摘选高影响 PR）

| PR | 说明 |
|---|---|
| !2173 | fix(workbench): CO-597 项目负责人只看自己项目 + CO-598 任务待办日期 + 滚动条贴边框 |
| !2170 | fix(workbench-calendar): 修复翻月弹回 + 标讯事件下钻到标讯详情（CO-594 bugfix） |
| !2171 | fix(workbench): CO-596 项目待办 status/stage 字段混淆 + 4 卡片滚动条优化 |
| !2169 | fix(workbench): 开标模块跳转改为标讯详情（CO-593 follow-up） |
| !2168 | fix(warehouse-word): 修复 Word 合订本标题未应用 pStyle 导致导航窗格为空（CO-582 §3.4） |
| !2167 | docs(lessons): §77 V1173 迁移 SQL 列名错误教训（MySQL 保留字 size 陷阱） |
| !2166 | docs(release): 第 103 次测试环境部署报告 |
| !2165 | fix(migration): V1173 修复 pd.size → pd.file_size 列名错误 |
| !2163 | fix(workbench-calendar): 投标日历红绿点生效 + 聚合 Tender 开标/报名截止事件（CO-594） |
| !2162 | fix(task-detail): 修复任务详情文本框滚动条锁死问题 |
| !2161 | fix(archive-detail): 项目档案详情 4 项修复（列宽/大小0B/操作人/统计归一化） |
| !2160 | docs(release): 第 102 次测试环境部署报告 |
| !2159 | fix(workbench): 修复工作台待办 3 个测试环境 Bug |
| !2158 | fix(integration): 支持企微工作台应用主页固定 state 入口 |
| !2156 | fix(revenue): 修复 !564 回归 - 客户营收字段映射错乱（CO-595） |
| !2155 | docs(release): 第 101 次测试环境部署报告 |
| !2153 | feat(integration): v3.10 标讯接口新增项目负责人工号字段 |
| !2152 | docs(obs): 补全 OBS CORS 配置清单 + 新增 lessons §74（winbid-test 下载 preflight 失败） |
| !2151 | docs(release): 第 99/100 次测试环境部署报告 |
| !2150 | feat(gate): 新增 lessons-learned 章节编号冲突门禁（§9.10，防并行归档撞号） |
| !2149 | test(warehouse,performance): 补充导出下载/CO-582/CO-586 测试缺口（27 用例全绿） |
| !2147 | feat(workbench): 工作台截止时间模块改造（真实条目接口 + 竞态保护）（CO-593） |
| !2146 | style(project-list): 调整标书审核人和评标结果列宽（CO-591） |
| !2144 | feat(archive): 项目档案文档分类统一为 6 个中文选项（CO-592） |
| !2143 | docs(lessons): 第 72 节——分支基线过期导致 PR diff 静默回退/删除他人文件 |
| !2142 | feat(project-list): 投标项目列表增加四列及导出支持（CO-591） |
| !2141 | feat(project-result): 结果确认阶段新增合同信息模块（CO-590） |
| !2140 | docs(release): 第 12 次生产环境部署报告 |
| !2138 | feat(project-result): 结果确认阶段新增合同信息模块（CO-590） |
| !2136 | feat(integration): 企微 SSO 单点登录走 base-oss 换 token 完成自动登录 |
| !2134 | refactor(workbench): PR #2115 设计修复（JOIN优化/审计/枚举类型安全/模块化） |
| !2115 | feat(workbench): 工作台待办模块角色化改造（BE-1~4 + FE-1） |

### 改动范围

- 前端变更：46 个文件
- 后端变更：124 个文件
- 新增 Flyway 迁移：5 个（V1169-V1173）

## Flyway 预检结果（3 步法）

| 步骤 | 结果 | 备注 |
|---|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - all checksums match（231 migrations） | DB 当前状态健康 |
| Step 2: DB 已应用版本对比 | ✅ 已应用到 V1168，待新增 V1169-V1173（5 个） | `success=1` 过滤后查询 |
| Step 3: remote-deploy.sh 内置 validate | ✅ 部署时自动执行，失败则停止 rollout | 旧 jar 仍在运行 |

### 新增迁移详情

| 版本 | 描述 | 状态 | 安装时间 |
|---|---|---|---|
| V1169 | add contract info to project result | ✅ success=1 | 2026-07-20 23:22:03 |
| V1170 | unify archive file category | ✅ success=1 | 2026-07-20 23:22:03 |
| V1171 | backfill archive files for obs direct uploads | ✅ success=1 | 2026-07-20 23:22:03 |
| V1172 | align customer revenue column comment | ✅ success=1 | 2026-07-20 23:22:03 |
| V1173 | backfill archive file size from project documents | ✅ success=1 | 2026-07-20 23:22:03 |

> ⚠️ **V1173 历史教训**：V1173 最初使用 MySQL 保留字 `pd.size` 作为列名导致部署失败（第 103 次测试环境部署事故）。已修复为 `pd.file_size`。详见 `docs/lessons/lessons-learned.md` §77。

## 部署步骤

### Step 0: 环境门禁（硬门禁）

- 用户声明 ENV=prod
- AI 展示目标环境信息（IP=172.16.10.149，主机名=winbid-01，用途=正式环境）
- AskUserQuestion 工具确认 → 用户选择"确认：生产环境 172.16.10.149"
- 设置 `TARGET_HOST=172.16.10.149`

### Step 1: 早操三连

```bash
source scripts/dev-env.sh
bash scripts/sync-env.sh .
bash scripts/check-git-wrapper.sh
```

- 分支 `agent/trae-init`，HEAD = origin/main = `ad9f2378c`（up-to-date）
- 工作区干净（仅 1 个未追踪文件 `e2e/playwright-chrome.config.js`，不影响部署）
- 本地门禁 7 项全绿
- git wrapper 生效

### Step 2: 确认基线

- HEAD = `ad9f2378c`（!2173 workbench CO-597 + CO-598 修复）
- HEAD 完全等于 origin/main（0 ahead, 0 behind）
- GitHub 镜像落后 6 commits（部署后同步）

### Step 3: 服务器现状

- 当前 release：`ce55d7d09`（2026-07-19 04:50:01 UTC 激活）
- 后端服务 active (running)，PID 32104，内存 1.5G
- 健康状态：UP（db/redis/jwt/sidecar/aiProvider 全 UP）

### Step 4: Flyway 预检 3 步

- ✅ Step 1: VALIDATE OK - all checksums match（231 migrations）
- ✅ Step 2: DB 已应用到 V1168，待新增 V1169-V1173
- Step 3: remote-deploy.sh 内置（部署时执行）

### Step 5: 本地打包

```bash
RELEASE_ID="ad9f2378c-prod" \
VITE_API_BASE_URL= \
VITE_OBS_ENABLED=true \
COPYFILE_DISABLE=1 \
bash scripts/release/package-release.sh
```

- ✅ 前端同源构建（不含 dev API 地址）
- ✅ jar 内 Flyway 迁移版本无重复
- ✅ OBS 直传已启用（Detail chunk `.upload(` 调用数=2）
- ✅ BUILD SUCCESS（26.887s）

### Step 6: 产物校验

- ✅ `release-metadata.json`: `obsEnabled=true`，`apiBaseUrl=""`（同源）
- ✅ jar 内 235 个 V*.sql，无重复版本
- ✅ 前端入口 `assets/index-1mpOlVZj.js`
- ✅ V1168-V1173 全部打包进 jar

### Step 7: 上传 + 部署

```bash
scp .release/xiyu-bid-release-ad9f2378c-prod.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.10.149:/opt/xiyu-bid/incoming/

ssh jetty@172.16.10.149 '... SYSTEMCTL_SUDO=true ... bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- ✅ DB 备份完成
- ✅ Flyway validate 通过（VALIDATE OK - all checksums match，231 migrations）
- ✅ Backend 服务停止 + 更新 jar + 重启（PID 16160）
- ✅ 健康检查连续 3/3 通过（15 次尝试，比预期 4 分钟快）
- ✅ 前端一致性校验通过（`assets/index-1mpOlVZj.js`）

### Step 8: 前端资源保留（防跨版本 404）

- 已知缺陷：`deployed-release.json` 已被新部署覆盖，PREV 变量取值失效
- 手动从上一版本 `ce55d7d09` 复制 assets：
  ```bash
  sudo cp -rn /opt/xiyu-bid/releases/ce55d7d09/frontend/assets/* /srv/www/xiyu-bid/assets/
  ```
- 文件数：179 → 265（多保留 86 个旧 hash 化资源）
- 旧标签页（用户浏览器未刷新）的 `<link rel="preload">` 仍能找到旧 CSS/JS，避免 Nginx 404 Sentry 噪声

### Step 9-11: 健康检查 + 迁移验证 + Smoke 测试

**详细健康检查**：

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ UP（aiProvider/db/diskSpace/jwt/livenessState/ping/readinessState/redis/sidecar 全 UP） |
| `/actuator/health/readiness` | ✅ HTTP 200 |
| `/actuator/health/liveness` | ✅ HTTP 200 |

**迁移应用验证**：

V1169-V1173 全部 success=1，installed_on: 2026-07-20 23:22:03

**Smoke 测试（经 Nginx :8080 代理到后端 :18080）**：

| # | 接口 | HTTP Code | 期望 | 结果 |
|---|---|---|---|---|
| 1 | `GET /actuator/health` | 200 | 200 UP | ✅ |
| 2 | `GET /actuator/health/readiness` | 200 | 200 UP | ✅ |
| 3 | `POST /api/auth/login` (空 body) | 400 | 400 空 body 验证错误 | ✅ |
| 4 | `GET /api/projects` (no auth) | 403 | 403 需认证 | ✅ |
| 5 | `GET /api/integration/crm/health` | 401 | 401 需认证 | ✅ |
| 6 | `GET /` (index.html) | 200 | 200 | ✅ |
| 7 | `GET /login` | 200 | 200 | ✅ |
| 8 | 前端入口 chunk | `assets/index-1mpOlVZj.js` | 与 release 一致 | ✅ |

> Mac 本地 curl 因 HTTP_PROXY 走代理导致 502（已知问题，第 19/23 次发现），改为通过服务器内部 curl 验证。

### Step 12: GitHub 镜像同步

- 部署前：GitHub 落后 Gitee 6 commits
- 执行 `bash scripts/sync-to-github.sh`
- 部署后：Gitee main = GitHub main = `ad9f2378c`（完全一致）

### Step 13: 配置清理检查

- 发现 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 配置
- 这是用户之前已经决定保留的配置（第 13、14、15 次决定保留），用于监控
- 无需清理

## 经验沉淀应用情况

本次部署遵循 18 条部署经验：

| 经验 | 应用 |
|---|---|
| 1. Flyway 预检 3 步法 | ✅ Step 1+2+3 全部执行 |
| 2. Readiness 延迟恢复（Kafka SDK） | ✅ 容忍 4 分钟延迟（本次 15 次尝试即通过，无延迟） |
| 3. 生产前端同源构建（baseURL=""） | ✅ `VITE_API_BASE_URL=` 显式设空 |
| 4. Smoke 测试限制（Admin 密码未知） | ✅ 用 400/403/401 替代验证 |
| 5. GitHub 镜像同步 | ✅ 部署后立即同步 |
| 6. 临时调试配置清理 | ✅ 检查 SHOW_DETAILS/DEBUG/TRACE（保留已决定项） |
| 7. 幂等迁移设计 | ✅ V1169-V1173 已在测试环境验证 |
| 8. systemctl sudo 权限 | ✅ `SYSTEMCTL_SUDO=true` |
| 9. git.properties commit id | N/A（未涉及） |
| 10. 破坏性 schema 变更 | N/A（无 DROP COLUMN） |
| 11. 服务器 /tmp/migration-mysql/ 过时 | N/A（不影响 validate） |
| 12. rollback 脚本命名规范 | N/A（无破坏性变更） |
| 13. 前端目录权限 | ✅ 使用 `sudo cp -rn` |
| 14. macOS `._*` 残留文件 | ✅ `COPYFILE_DISABLE=1` 预防 |
| 15. Flyway 防护体系 | ✅ 全部门禁通过 |
| 16. Mac HTTP_PROXY 502 | ✅ 通过服务器内部 curl 绕过 |
| 17. SentryAppender crash-loop | N/A（未引入 sentry-logback） |
| 18. 前端 hash 资源跨版本 404 | ✅ 已从上一版本复制 assets 保留 24h |

## 风险提示

1. **V1173 历史**：V1173 最初使用 MySQL 保留字 `pd.size` 导致第 103 次测试环境部署失败，已修复为 `pd.file_size`。本次生产部署为修复后版本。
2. **企微 SSO 首次上生产**：本次包含企微 SSO 单点登录首次生产部署（!2136），建议观察用户登录路径。
3. **工作台改造范围大**：工作台待办模块角色化改造（!2115）、投标日历红绿点（CO-594）、项目列表四列（CO-591）等多项 UI 改动首次上生产，建议关注用户反馈。
4. **OBS 直传启用**：本次显式启用 `VITE_OBS_ENABLED=true`，大文件直传逻辑首次完整上生产。

## 回滚信息

- **回滚状态**：未需要
- **回滚准备**：已就绪
- **上一版本**：`ce55d7d09`（releaseDir: `/opt/xiyu-bid/releases/ce55d7d09`）
- **DB 备份**：`/opt/xiyu-bid/db-backups/winbid-ad9f2378c-prod-*.sql.gz`
- **回滚命令**（如需）：
  ```bash
  ssh jetty@172.16.10.149 'sudo systemctl stop xiyu-bid-backend && \
    cp /opt/xiyu-bid/releases/ce55d7d09/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
    sudo systemctl start xiyu-bid-backend'
  ```

## 部署确认清单

- [x] 环境门禁通过（用户确认 prod）
- [x] 早操三连通过（sync-env + check-git-wrapper）
- [x] 基线确认（HEAD = origin/main）
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（同源 + OBS 启用 + 无重复迁移）
- [x] 产物校验全绿
- [x] DB 备份完成
- [x] 部署成功（健康检查通过）
- [x] 前端资源保留（防跨版本 404）
- [x] 迁移应用验证（V1169-V1173 全部 success=1）
- [x] Smoke 测试全绿（8/8）
- [x] GitHub 镜像同步
- [x] 配置清理检查（无新增临时配置）
- [x] 部署报告生成

## 部署摘要

本次第 13 次生产环境部署成功上线 `ad9f2378c-prod`，包含约 90+ 个增量 commit，涵盖：

1. **企微 SSO 单点登录**首次上生产（!2136）
2. **工作台改造**：待办模块角色化（!2115）、投标日历红绿点（CO-594）、CO-597/598/599 系列修复
3. **项目列表四列增强**（CO-591）+ **结果确认合同信息模块**（CO-590）
4. **项目档案详情修复**（CO-582 §3.4 Word 合订本 pStyle）
5. **客户营收字段修复**（CO-595 回归）
6. **V1173 迁移修复**（pd.size → pd.file_size）
7. **OBS 直传首次完整上生产**（VITE_OBS_ENABLED=true 显式启用）

所有验证全绿，无需回滚。GitHub 镜像已同步。建议关注企微 SSO 登录路径与工作台 UI 改动的用户反馈。
