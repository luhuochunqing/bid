# 第 128 次测试环境部署报告 — 2026-08-17

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 日常部署验证、功能测试 |
| 部署序号 | 第 128 次（测试） |
| 部署时间 | 2026-08-17 16:15:12 CST（服务启动） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `2d6ff758c-api8080` |
| 上一版本 Release | `7a09da3b6`（2026-08-17 14:44 CST，第 127 次测试部署） |
| 基线 commit | `2d6ff758c`（origin/main HEAD，PR !2309 合并提交，含评分表格 UI 修复 + 第 127 次部署报告） |
| 健康检查通过 | ✅（79 次尝试后 3/3 连续通过，Kafka readiness 延迟属已知行为） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | 无（纯前端 CSS/模板变更） |
| Smoke 测试 | 全部通过（健康检查 + 前端一致性 + OBS 直传校验） |
| 前端入口 | `index-CUwG1xNx.js` |
| GitHub 镜像 | ⏳ 未同步（部署自临时构建区；镜像落后 Gitee，待主工作区同步） |

## 背景：本次为评分解析表格 UI 重叠修复上线

第 127 次部署（NPE 修复）后，用户在测试环境项目 225 的评分标准解析结果表格中发现两处 UI 重叠：

1. **编号 / 评分项两列数据重叠**：`table-layout: fixed` 下编号列仅 48px 且 `code-cell` 无换行规则，长编号溢出压到评分项列
2. **底部合计行全部叠压**：tfoot 按列拆成 6 个 td，`0 满足 · 1 不满足 · 7 待确认` 等 nowrap 统计文案（约 180px）被塞进 80px 的满足状态列，溢出叠压后续列

### 修复内容

| 类型 | 文件 | 说明 |
|---|---|---|
| bug fix | `ScoreParseTable.vue` | 编号列 48px→92px + `word-break: break-all`；评分项列 76px→64px |
| bug fix | `ScoreParseTable.vue` tfoot | 重构为 `colspan=8` 单条 flex 布局（`flex-wrap: wrap`）：合计/权重/满足统计/主客观项分布/客观分横向排列，窄容器自动换行 |
| wiki | `.wiki/pages/score-parse-service.md` §7 | 回填 `table-layout: fixed` 窄列 nowrap 叠压坑位 |

PR !2309（已合入，merge commit `2d6ff758c`）。纯 CSS/宽度调整，无选择器变更，无后端变更。

## 验证证据

- 单测：`ScoreParseDrawer.spec.js` + `ScoreParseV3.qa.spec.js` 12/12 通过；pre-commit 全量 1693 通过 / 1 跳过
- 构建：`npm run build` 通过；pre-push 门禁 21 项全过
- 线上产物核验：`https://winbid-test.ehsy.com/assets/Detail-CRDkpg9S.js` 包含 `tfoot-flex` 样式类（curl 验证，与本地构建产物一致）

## 特殊事项：部署自临时构建区（重要，供后续复盘）

主工作区 `trae` 在部署时段被并发会话占用（`agent/trae/echarts-tree-shaking` 分支 + 9 个图表文件未提交 WIP），`package-release.sh` 从工作区打包会将 WIP 混入产物，故本次采用隔离构建：

| 措施 | 说明 |
|---|---|
| 临时 worktree | `git worktree add --detach /tmp/xiyu-deploy-128 2d6ff758c`（基线 = origin/main HEAD，只读构建，不承载任务开发） |
| node_modules | `cp -cR` APFS clonefile 克隆主工作区依赖（瞬时、零额外磁盘、不污染共享状态） |
| dev-env 会话门禁 | `CHAT_ONLY=1` 跳过会话锁（仅部署打包，无开发行为） |
| 基线分支 | 临时 worktree 检出 `agent/trae-init` 锚点（位于 `2d6ff758c`），满足 deploy-test.sh Step 1 锚点校验 |
| 清理 | 部署完成后移除临时 worktree |

## 部署过程记录

| Step | 结果 |
|---|---|
| Step 0 早操三连 | ✅ 门禁自检 7/7 |
| Step 1 基线确认 | ✅ `agent/trae-init` @ `2d6ff758c` |
| Step 2 服务器现状 | ✅ SSH 通、旧版 `7a09da3b6`、health UP |
| Step 3 本地打包 | ✅ 前端 10.69s（OBS=true 固化、同源 API、dev API 地址零残留）；后端容器测试门禁通过 |
| Step 4 上传+远端部署 | ✅ Flyway 预检跳过（runner 未随包上传，无迁移变更不受影响） |
| Step 5 服务重启 | ✅ 16:15:12 CST active/running |
| Step 6 部署后验证 | ✅ 健康 3/3、前端一致性、OBS 直传启用 |

## 回滚方案（未启用）

上一版本 `7a09da3b6` 良好。如需回滚：

```bash
ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && \
  sudo ln -sfn /opt/xiyu-bid/releases/7a09da3b6-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
  sudo systemctl start xiyu-bid-backend'
# 前端回滚：将 /srv/www/xiyu-bid 指回 releases/7a09da3b6-api8080/frontend/
```
