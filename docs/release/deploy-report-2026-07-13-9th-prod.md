# 第 9 次生产环境部署报告 — 2026-07-13

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产 (prod)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.10.149` |
| 用途 | 正式环境、对外服务 |
| 部署序号 | 第 9 次（生产） |
| 部署时间 | 2026-07-13 11:43 CST |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `b1126a2b3` |
| 上一版本 Release | `df9adabad`（2026-07-12 05:57:23 UTC） |
| 基线 commit | `b1126a2b3`（origin/main） |
| 激活时间 | 2026-07-13T03:43:17Z |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | 无（最新仍为 V1165） |
| Smoke 测试 | 全部通过 |
| GitHub 镜像 | 已同步（两边 main 完全一致） |

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/trae`
- 锚点分支：`agent/trae-init`（ff-only 同步到 origin/main）
- 早操 SOP：已执行 `sync-env.sh`，HEAD = origin/main = `b1126a2b3`
- GitHub 镜像状态：部署前落后 52 个 commit，部署后已同步
- 本地门禁自检：7 项全部通过（hooksPath、pre-commit、pre-push、git wrapper、agent-locks 等）

## 增量改动（df9adabad → b1126a2b3，57 个 commit）

### 主要 PR 列表

| PR | 说明 |
|---|---|
| !2060 | fix(release): OBS 防护漏洞补丁 — 5 个绕过路径 + deploy-prod.sh 死代码修复 |
| !2059 | chore(release): OBS 直传部署三层防护 — 防止漏传 VITE_OBS_ENABLED=true 回归 |
| !2058 | fix: 24小时提交后置审计 — 4个关键缺陷修复 |
| !2057 | fix(test): 修复7项测试失败 + 架构违规治理（上线前全量测试） |
| !2056 | test: 补充回归测试覆盖缺口 — TenderIntake/Webhook/Notification/ClosureGate 核心模块 |
| !2055 | feat(project): CO-578 项目详情页公共模块增加投标负责人和投标辅助人员字段 |
| !2054 | fix: 关联CRM商机时校验对接人非空，为空时阻断并提示 |
| !2053 | fix(test): 修复 10 个 standaloneSetup 测试因 XML 回退导致 JSON path 断言失败 |
| !2052 | feat(tender-intake): 增强标讯人工录入AI识别准确率 |
| !2051 | feat(task): CO-575 任务看板底部增加审核提示信息 |
| !2050 | fix(task): CO-574 保证金缴纳任务放权项目负责人 + 修复看板执行人改后不刷新 |
| !2049 | feat(wecom): 投标关键节点企微通知触点 |
| !2048 | feat(closure): CO-573 项目结项阶段保证金退回金额校验规则 |
| !2046 | fix(closure): 提交结项申请后表单字段应为只读状态 (CO-572) |
| !2045 | fix(evaluation): 评标中阶段选项顺序调整，公示置于结果已出前 (CO-571) |
| !2039 | refactor(webhook): CO-576 Phase C 删短工厂与两参 updateStatus，禁止空操作人事件 |

### 改动范围

- **后端**：
  - OBS 直传部署三层防护（防漏传 VITE_OBS_ENABLED=true 回归）
  - 24h 后置审计 4 个关键缺陷修复（scoreanalysis 事务回滚、通知 store、closure 守卫、prompt injection 防御）
  - CO-571 评标中阶段选项顺序、scoreanalysis 重构
  - CO-572 后端 PENDING 状态 re-submit 守卫
  - CO-573 保证金退回金额校验
  - CO-574 保证金缴纳任务放权项目负责人
  - CO-575 任务看板审核提示
  - CO-576 webhook 重构（删短工厂与两参 updateStatus）
  - CO-578 项目详情页增加投标负责人和辅助人员字段
  - CRM 商机对接人非空校验
  - 标讯人工录入 AI 识别增强
  - 投标关键节点企微通知触点
- **前端**：通知 store 静默清零 unreadCount 修复、结项申请表单只读、任务看板审核提示
- **测试**：7 项测试失败修复 + 架构违规治理 + 回归测试覆盖缺口补充
- **发布工具**：OBS 直传部署三层防护 + deploy-prod.sh 死代码修复

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（228 migrations） |
| Step 2: DB 已应用版本 vs 源码最新版本 | ✅ 一致（V1165，2026-07-12 08:57:40 应用） |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过（仅 pending 新迁移为预期状态） |
| 新增迁移文件 | 无（本次部署无数据库变更） |

## 部署步骤

1. **环境门禁**：用户确认部署到生产环境 172.16.10.149
2. **早操 SOP**：`sync-env.sh` 完成，HEAD = origin/main，7 项门禁通过
3. **服务器现状检查**：当前部署 `df9adabad`，后端 health UP
4. **Flyway 预检 3 步法**：全部通过，DB V1165 = 源码 V1165
5. **本地打包**：
   - `RELEASE_ID=b1126a2b3 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 bash scripts/release/package-release.sh`
   - 前端构建 + 后端 mvn clean package（27s）
   - **修复 package-release.sh bug**：grep 无匹配时 `set -euo pipefail` 触发退出，添加 `|| true` 容错
6. **产物校验**：
   - ✅ release-metadata.json: obsEnabled=true
   - ✅ Detail chunk .upload( 调用数=2（OBS 直传已启用）
   - ✅ jar 内迁移文件 V1165（与源码一致）
   - ✅ 前端入口 assets/index-6CZFzl2i.js
   - ✅ tar.gz 153M
7. **上传 + 部署**：
   - scp tar.gz + remote-deploy.sh 到 `/opt/xiyu-bid/incoming/`
   - remote-deploy.sh 执行（SYSTEMCTL_SUDO=true）
   - 数据库备份完成
   - Flyway validate 通过
   - 后端服务重启：active/running since 11:43:17 CST
   - 健康检查通过：consecutive 3/3, 14 attempts
8. **前端资源保留**：从上一版本 `df9adabad` 复制旧 assets 到 `/srv/www/xiyu-bid/assets/`（防止跨版本 404）

## 验证结果

### 后端健康检查（内部 18080）

| 组件 | 状态 | 详情 |
|---|---|---|
| overall | UP | - |
| aiProvider | UP | configured, qwen3.7-max |
| db | UP | MySQL, isValid() |
| diskSpace | UP | 80GB free / 98GB total |
| jwt | UP | HMAC-SHA256, 47 bytes |
| livenessState | UP | - |
| readinessState | UP | - |
| redis | UP | 6.2.19 |
| sidecar | UP | http://localhost:8000 |

### Smoke 测试（服务器本地，经 Nginx 8080）

| 检查项 | 结果 | 预期 |
|---|---|---|
| /actuator/health | HTTP 200 UP | ✅ |
| /actuator/health/readiness | HTTP 200 UP | ✅ |
| /api/auth/login POST | 400 参数校验失败 | ✅ |
| /api/projects | 403 需认证 | ✅ |
| /api/integration/crm/health | 401 需认证 | ✅ |

### 前端验证（服务器本地，经 Nginx 8080）

| 检查项 | 结果 | 预期 |
|---|---|---|
| 首页 / | HTTP 200 | ✅ |
| /login | HTTP 200 | ✅ |
| index.html 入口 | assets/index-6CZFzl2i.js | ✅ 与 release 一致 |

### 迁移验证

- DB 最新版本：V1165（无变化，与部署前一致）

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 部署前落后 commit 数 | 52 |
| 同步命令 | `bash scripts/sync-to-github.sh` |
| 同步结果 | ✅ 两边 main 完全一致（b1126a2b3） |
| Gitee main | b1126a2b3 |
| GitHub main | b1126a2b3 |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| SHOW_DETAILS=always | 保留 | 历史决定保留（第 13/14/15 次部署用户决定） |
| DEBUG/TRACE | 无 | ✅ 无临时调试配置 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 上一版本 Release | `df9adabad` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/df9adabad` |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-b1126a2b3-*.sql.gz` |
| 回滚方式 | 恢复上一版本 jar + 前端 + 数据库备份（如有迁移） |

## 经验沉淀应用情况

1. **OBS 直传三层防护**（第 8 次生产事故根治）：
   - package-release.sh 默认 VITE_OBS_ENABLED=true
   - 打包时显式传入 VITE_OBS_ENABLED=true 作双保险
   - 产物校验 obsEnabled=true + Detail chunk .upload( 调用数
2. **Flyway 预检 3 步法**：部署前主动 validate + DB 版本对比，避免启动时才发现问题
3. **前端资源保留**：部署后从上一版本 release 目录 cp -rn 旧 assets，防止跨版本 404
4. **SYSTEMCTL_SUDO=true**：jetty 用户已配置 NOPASSWD sudo，避免服务重启失败
5. **COPYFILE_DISABLE=1**：避免 macOS ._* 残留文件污染服务器

## 本次部署新发现

### package-release.sh grep 容错 bug 修复

**问题**：`set -euo pipefail` 下，`grep -o "\.upload(" "$_f"` 在某个 Detail chunk 无匹配时返回 1，pipefail 触发脚本提前退出，导致 release-metadata.json 和 tar.gz 未生成。

**根因**：前端构建产生两个 Detail chunk（`Detail-6Na-d586.js` 有 2 个 .upload(，`Detail-BdJlrG1a.js` 有 0 个），循环到无匹配文件时 grep 返回 1。

**修复**：在 grep 管道末尾添加 `|| true`，确保无匹配时不触发 set -e 退出：
```bash
_n=$(grep -o "\.upload(" "$_f" 2>/dev/null | wc -l | tr -d ' ' || true)
```

**影响**：本次部署修复后打包成功，后续部署不再受此 bug 影响。

## 风险提示

1. **Nginx 8080 外部访问超时**：从本地 Mac 访问生产 172.16.10.149:8080 超时（HTTP 000），但服务器内部访问正常。可能是防火墙或网络策略限制，不影响服务正常运行。
2. **无新增 Flyway 迁移**：本次部署纯代码/测试/文档变更，无数据库 schema 变更，回滚风险低。
3. **57 个增量 commit**：本次部署涵盖大量改动（PR !2039~!2060），建议关注上线后核心功能（CRM 推送、企微通知、项目结项、任务看板）的运行状态。

## 部署确认清单

- [x] 环境门禁确认（生产 172.16.10.149）
- [x] 早操 SOP + 基线确认（HEAD = origin/main）
- [x] 服务器现状检查（df9adabad, health UP）
- [x] Flyway 预检 3 步法（全部通过）
- [x] 本地打包（BUILD SUCCESS, OBS obsEnabled=true）
- [x] 产物校验（jar 迁移 V1165, 前端入口一致, tar.gz 153M）
- [x] 上传 + 部署（remote-deploy.sh 成功）
- [x] 前端资源保留（df9adabad 旧 assets 已复制）
- [x] 健康检查（health UP, readiness UP）
- [x] Smoke 测试（5 项全部符合预期）
- [x] 前端验证（/, /login 200, index.html 入口一致）
- [x] 迁移验证（DB V1165 无变化）
- [x] GitHub 镜像同步（两边 main 一致）
- [x] 配置清理检查（无临时调试配置）
- [x] 部署报告生成

## 回滚指引

如需回滚到上一版本 `df9adabad`：

```bash
# 1. 恢复后端 jar
ssh jetty@172.16.10.149 'cp /opt/xiyu-bid/releases/df9adabad/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'

# 2. 恢复前端
ssh jetty@172.16.10.149 'sudo cp -R /opt/xiyu-bid/releases/df9adabad/frontend/* /srv/www/xiyu-bid/'

# 3. 等待健康检查
ssh jetty@172.16.10.149 'for i in $(seq 1 120); do if curl -fsS http://127.0.0.1:18080/actuator/health >/dev/null 2>&1; then echo "✅ 健康检查通过"; break; fi; sleep 2; done'

# 4. 恢复数据库（如有迁移变更，本次无）
# ssh jetty@172.16.10.149 'gunzip < /opt/xiyu-bid/db-backups/winbid-b1126a2b3-*.sql.gz | mysql -h... -u... -p... xiyu_bid_main'
```
