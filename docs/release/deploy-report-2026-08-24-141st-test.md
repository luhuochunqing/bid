# 第 141 次测试环境部署报告 — 2026-08-24

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | PDF 评分标准解析准确率治理 + 标书质量核查异步化 + Docx 媒体流秒级解析 + 后端测试基线修复 |
| 部署序号 | 第 141 次（测试） |
| 部署时间 | 2026-08-24 11:55 CST（backend 激活） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `020f50d0b` |
| 上一版本 Release | `a1f09384e`（2026-08-21 16:32 CST 激活） |
| 基线 commit | `020f50d0b`（= origin/main，本次打包源码） |
| 健康检查 | ✅ 首次失败后重跑，remote-deploy 内置通过（80 次尝试后连续 3/3 UP） |
| 部署结果 | ✅ 成功（含一次前端目录权限故障修复） |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | **无**（增量 commit 不含 `migration-mysql/` 改动，本地最新 V1199 与 DB 一致） |

## ⚠️ 特殊事件：前端目录权限故障（非 sudo `rm -rf` 失败，已修复）

### 时间线（CST）

| 时间 | 事件 |
|---|---|
| ~11:54 | 首次执行 remote-deploy.sh，前端激活阶段 `rm -rf /srv/www/xiyu-bid` **大量 `Permission denied`**，`set -e` 立即中止。旧版本（`a1f09384e`）仍在运行，未覆盖 jar、未重启 |
| 11:54 | 定位根因：顶层 `/srv/www/xiyu-bid` 属 `jetty:jetty`，但 `assets/` 子目录属 **`nginx:nginx`**（上次部署遗留）。remote-deploy.sh 的 `rm -rf` 非 sudo，进入 assets 内删除 nginx 所有文件时无写权限 |
| 11:54 | 处置：`sudo chown -R jetty:jetty /srv/www/xiyu-bid`（jetty 有 `NOPASSWD: ALL`），目录 755、assets 755，nginx 仍可读静态文件 |
| 11:55 | 重跑 remote-deploy.sh，部署完整成功 |

### 根因分析

- 灰度结论与清单「反复出现的问题 #9 前端 hash 资源跨版本 404」同源，落在技能「教训 13 前端目录权限」覆盖范围：
  - `/srv/www/xiyu-bid` 顶层属主正确，但 `assets/` 子目录属主在历史部署中被置为 `nginx:nginx`，形成**父目录可写、子目录不可写**的非预期状态。
  - remote-deploy.sh 前端切换（`rm -rf "$FRONTEND_PUBLIC_DIR"`）走 jetty 身份非 sudo，遇 nginx 属主文件即失败。
- 历史上多次部署成功，说明 `assets/` 通常应为 jetty 属主；本次失败的触发点在于 assets 属主漂移，与 Manifest 中任意一次部署即可能造成的子目录属主不一致耦合。

### 处置与防复发

1. 本次：`sudo chown -R jetty:jetty /srv/www/xiyu-bid` 后重跑成功。
2. 建议：部署前将「校验 `/srv/www/xiyu-bid` 全层级属主为 jetty」纳入预检，或在 remote-deploy.sh 前端激活前对 `FRONTEND_PUBLIC_DIR` 先执行一次 `sudo chown -R`，避免同类故障复发。

## 变更内容（45 个 commit）

### PDF 评分标准解析准确率治理（PR !2389 / scoreparse）
- `f313b37d2` fix(scoreparse): PDF 评分标准解析准确率系统性治理（Sidecar 表格换行保真 + 跨维度防碰撞 + 聚焦切片召回）
- `c77e4920f` docs(wiki): 评分标准解析 PDF 表格保真与去重防碰撞机制
- `a9fb1966a` fix(scoreparse): e2e profile 补 OpenAiScoreAnalyzer 桩修复启动崩溃

### 标书质量核查 / Docx 秒级解析（PR !2378-!2381）
- `457f5b05b` fix(compliance): 标书质量核查触发接口毫秒级纯异步化与进度独立事务改造
- `6adfe0ae6` feat(compliance): Docx 媒体流前置瘦身与超大标书秒级解析优化
- `d29a3d64b` fix(compliance): Java 端文档降级安全守卫（消除 Files.readString 导致的 DirectBuffer 堆外 OOM）
- `4520f5b25` fix(sidecar): 绕过 MarkItDown pre_process_docx 全量解包（根治 383MB 大标书 1GB 容器内 OOM）
- `d768474fe` docs: 标书质量核查异步触发与事务可见性 wiki 回填

### brand-auth / project（PR !2385 回滚）
- `21b7ed416` revert(brandauth+project): 回滚 PR !2385 生产代码变更
- `e13a4f0fd` fix(e2e+brandauth): 角色码归一化 + brand-auth spec 对齐
- `e535604d7` fix(e2e+project): 补 ProjectMapper 维护声明
- `89498313f` test(brandauth): 补 AGENT 整体区间推导回归用例

### 后端测试基线修复（PR !2382-!2384）
- `509667628` chore(wiki): 刷新 data-analysis-revamp health_checked 日期
- `fd1bf6b63` test(mysql-integration): 修复三处存量漂移
- `09fe4dfd6` test(casework): 对齐 spec039 下载收口契约
- `bc5f5ca83` test(support): 全局 mock 认证用户种子机制（H2-only 守卫）
- 等 7 个 test 相关 commit

### E2E 对齐（PR !2386-!2388）
- `3e4e95bb4` fix(e2e): 立项主链路 spec 对齐当前路由与阶段门禁
- `4467a7bea` fix(e2e+seed): FormDefinitionE2eSeeder tender.entry 补 pastedText + 标讯簇修复
- `3856be658` fix(e2e): 下拉选择后等待面板收起
- `001a9fe31` fix(e2e): 27 个 spec 硬编码端口改 env 驱动 baseURL
- 等 E2E 对齐 commit

### 文档 / 报告
- `297d85bb4` docs(wiki): 回填后端测试基线修复页
- 等 docs 相关 commit

## 验证结果

### Flyway

| 检查项 | 结果 |
|---|---|
| validate（remote-deploy 内置） | ✅ 通过（260 迁移 checksum 全匹配） |
| 本地源码最新版本 | V1199（与 DB 一致，本次无新增迁移） |
| DB 已应用版本 V1199 | ✅ success=1（2026-08-20 17:56 先行应用，非本次新增） |

### 部署后 Smoke（经 Nginx 8080）

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ 200 UP |
| `/actuator/readiness` | ✅ 200 UP |
| `/`（前端首页） | ✅ 200（入口 `assets/index-CGylnuIL.js` 与 jar 内一致性校验通过） |
| `assets/index-CGylnuIL.js`（静态资源） | ✅ 200（nginx 读取正常，属主变更后无误） |
| 前端首页入口引用 | ✅ `assets/index-CGylnuIL.js` |
| POST `/api/auth/login`（空体） | ✅ 400（路由存在） |
| GET `/api/projects`（无 token） | ✅ 403（需认证） |
| GET `/api/integration/crm/health`（无 token） | ✅ 401（需认证） |
| nginx -t | ✅ syntax ok / test successful |
| `.pending` 残留 | ✅ 无 |

### 前端旧资源保留（跨版本 404 防护）

- 从上一版本 release 目录 `cp -rn /opt/xiyu-bid/releases/a1f09384e/frontend/assets/. /srv/www/xiyu-bid/assets/`
- 当前 `assets/` 共 **178** 个文件（保留上一版本旧 hash 资源，防旧标签页 404 / Sentry 噪声）

## 其他收尾

| 项目 | 结果 |
|---|---|
| GitHub 镜像同步 | ✅ `sync-to-github.sh` 完成，Gitee main = GitHub main = `020f50d0b`（同步前落后 45 commits） |
| 配置清理检查 | ✅ 仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（测试环境标准运维配置，非调试残留） |

## 经验教训

1. **前端目录全层级属主需一致**：`/srv/www/xiyu-bid` 顶层与 `assets/` 子目录属主漂移（nginx:nginx）会导致 remote-deploy.sh 非 sudo 的 `rm -rf` 失败。建议部署预检校验前端目录全层级属主，或在 remote-deploy 前端激活前增加 `sudo chown -R jetty` 前置。
2. **`set -e` 前滚中止是安全默认**：本次首次部署失败时旧版本（`a1f09384e`）仍在运行、jar 未覆盖、健康不中断——优雅停留在旧版本，无任何 P0 影响，回滚姿态天然就绪。
3. **PC 代理/内网影响连通性**：本次从无 172.16 路由（未连 VPN）到 SSH 握手被服务端关闭，均属环境连通性阻塞，非部署逻辑问题；连上 VPN 后直连正常。

## 关联

- 上一份报告：`docs/release/deploy-report-2026-08-21-140th-test.md`
- 打包源码基线：`020f50d0b`（origin/main）