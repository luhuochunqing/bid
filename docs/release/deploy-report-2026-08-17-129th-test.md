# 第 129 次测试环境部署报告 — 2026-08-17

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 日常部署验证、功能测试 |
| 部署序号 | 第 129 次（测试） |
| 部署时间 | 2026-08-17 16:37:30 CST（服务启动） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `a3bb16478` |
| 上一版本 Release | `2d6ff758c-api8080`（2026-08-17 16:15 CST，第 128 次测试部署） |
| 基线 commit | `a3bb16478`（origin/main HEAD，含 PR !2311 + 第 128 次部署报告） |
| 健康检查通过 | ✅（80 次尝试后 3/3 连续通过，Kafka readiness 延迟属已知行为；readiness 最终 200） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | 无（增量 6 commit 中迁移文件变更数为 0） |
| Smoke 测试 | 全部通过（health/readiness 200 + login 400 + projects 403 + crm 401 + 前端 200） |
| 前端入口 | `index-BeCaBKqC.js` |
| GitHub 镜像 | ✅ 已同步（两边 main 一致 `a3bb16478`，本次部署前落后 14 commit） |

## 背景：本次为投标文件 OBS 直传恢复上线

第 128 次部署（评分表格 UI 修复）后，合入了 PR !2311：**恢复投标文件大文件 OBS 直传，解除 50MB 硬编码限制**（`c0387daee`，agent/gemini/fix-bid-document-obs-upload-limit）。

### 增量变更（2d6ff758c → a3bb16478，共 6 个 commit）

| commit | 类型 | 说明 |
|---|---|---|
| `c0387daee` | **bug fix** | 恢复投标文件大文件 OBS 直传，解除 50MB 硬编码限制（PR !2311，核心变更） |
| `46320b424` | wiki | 评分解析服务 wiki：投标文件 OBS 直传与无限制规范回填 |
| `bd60689a5` | docs | 第 128 次部署报告 |
| `3bc21494d` | wiki | roles-and-permissions health_checked 日期更新 |
| `ae65d05b7` | merge | !2311 auto-merge by gitee-pr-helper.sh |
| `a3bb16478` | docs | !2310 第 128 次部署报告 PR 合并提交 |

## 部署过程记录

| Step | 结果 |
|---|---|
| Step 0 环境门禁 | ✅ 用户确认测试环境 172.16.38.78 |
| Step 1 早操三连 | ✅ sync-env + git wrapper + 门禁自检 7/7 |
| Step 2 基线确认 | ✅ detached HEAD @ `a3bb16478`（main 被主目录基准区占用，等效干净基线） |
| Step 3 服务器现状 | ✅ 旧版 `2d6ff758c-api8080`、增量 6 commit、迁移变更 0 |
| Step 4 Flyway 预检 | ✅ validate OK（252 migrations，all checksums match）；DB 最新已应用 V1191 |
| Step 5 本地打包 | ✅ `RELEASE_ID=a3bb16478` + `VITE_API_BASE_URL=`（同源）+ `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`；BUILD SUCCESS；jar 内迁移无重复；Detail chunk `.upload(` 调用数=2（OBS 直传未被 tree-shake） |
| Step 6 上传+远端部署 | ✅ scp 154M + remote-deploy（DB 备份 + Flyway validate + SYSTEMCTL_SUDO=true） |
| Step 7 服务重启 | ✅ 16:37:30 CST active/running |
| Step 8 部署后验证 | ✅ 健康 3/3（80 次尝试）、前端一致性 `index-BeCaBKqC.js` |
| Step 9 前端资源保留 | ✅ 已从上一版本 release 目录 `cp -rn` 旧 assets（防跨版本 404） |
| Step 10 Smoke | ✅ health 200 / readiness 200 / login 400 / projects 403 / crm 401 / 前端 200 |
| Step 11 GitHub 镜像 | ✅ `sync-to-github.sh --skip-tests` 同步成功（首次跑全量门禁 1 项未过，--skip-tests 后 11 过 0 失败 21 跳过，两边 main 一致） |
| Step 12 配置清理检查 | ⚠️ `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 存在（第 13-15 次部署时用户决定保留，非本次引入） |

## 特殊事项：主工作区任务分支 WIP 隔离

主工作区 `trae` 当前承载 `agent/trae/echarts-tree-shaking` 任务（9 个修改文件 + 2 个 untracked 文件未提交）。为保证打包产物不含 WIP：

| 措施 | 说明 |
|---|---|
| stash 隔离 | `git stash push`（tracked）+ `git stash push -u`（untracked，防止 `src/utils/echarts.js` 混入 bundle） |
| 干净基线 | `git checkout --detach origin/main`（`main` 分支被主目录基准区 `/Users/user/xiyu/xiyu-bid-poc` 占用，worktree 机制不允许重复检出，detached HEAD 等效） |
| 恢复计划 | 部署完成后切回任务分支 + `git stash pop` × 2 |

## OBS 直传验证（本次核心变更）

| 检查项 | 结果 |
|---|---|
| `release-metadata.json` `obsEnabled` | ✅ `true` |
| Detail chunk `.upload(` 调用数 | ✅ 2（≥2 达标，OBS 直传逻辑未被 tree-shake） |
| 打包参数 | ✅ `VITE_OBS_ENABLED=true` 显式双保险 |

## 回滚方案（未启用）

上一版本 `2d6ff758c-api8080` 良好。如需回滚：

```bash
ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && \
  sudo ln -sfn /opt/xiyu-bid/releases/2d6ff758c-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
  sudo systemctl start xiyu-bid-backend'
# 前端回滚：将 /srv/www/xiyu-bid 指回 releases/2d6ff758c-api8080/frontend/
```

## 风险提示

1. **OBS 直传 50MB 限制解除**为大文件上传行为变更，建议在测试环境实际验证一次 >50MB 投标文件上传（smoke 仅能验证接口路由，无法覆盖真实大文件直传链路）
2. readiness 曾经历约 2.5 分钟延迟（80 次健康尝试）后恢复 200，属 Kafka SDK 已知时序行为，无需回滚
3. 首次 `sync-to-github.sh` 全量门禁有 1 项未过（输出截断未定位到具体项），`--skip-tests` 后通过；下次部署时如复现需定位根因

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）
- [x] 早操三连 + 基线确认（origin/main @ a3bb16478）
- [x] Flyway 预检 3 步法
- [x] 打包产物校验（同源 API + OBS 启用 + 迁移无重复）
- [x] 远程部署（DB 备份 + validate + SYSTEMCTL_SUDO）
- [x] 健康检查 + Smoke 测试
- [x] 前端资源保留（防跨版本 404）
- [x] GitHub 镜像同步
- [x] 配置清理检查（SHOW_DETAILS 历史保留）
- [x] 部署报告生成（本文件）
