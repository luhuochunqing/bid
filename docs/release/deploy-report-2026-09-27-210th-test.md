# 第 210 次部署报告（test）

- **部署日期**: 2026-09-27
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `0d8543c92`
- **部署类型**: 代码级部署（无新增 Flyway 迁移）
- **操作人**: Trae 开发宿主（基线 = origin/main `0d8543c92`）

## 部署概览

从上一已部署 release `e2502f5de`（209th，2026-09-26）增量部署至 `0d8543c92`，共 **9 个 commit**，无新增 Flyway 迁移（DB 最高版本保持 V1218）。

## 基线信息

- 基线: `origin/main` = `0d8543c92`（`!2771 fix(scoreparse): repair PDF score evidence binding for project 1382`）
- 工作区状态: 锚点分支 `agent/trae-init` ff-only 同步（HEAD = origin/main）
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-DpRxBIGA.js`
- sidecar: schemaVersion=2.2，SHA-256 `17aff7f089861e59f8ee67947793d08081ee2f07ea155f161201ebeecdaf8a0b`

## 主要改动（e2502f5de..0d8543c92）

- **fix(scoreparse)**: 修复项目 1382 PDF 评分证据绑定（!2771）
  - PDF 评分证据绑定到源行（91fc58bb9）
  - 连续 PDF 行 golden case 测试（93f9bd8fd）
- **docs**: CLAUDE.md 多 Agent 执行手册拆分迁移至 docs/agent-workflow.md（!2770）
- **docs(lessons)**: 第 407 条 bash 正则转义陷阱（!2769）
- **docs(release)**: 第 209 次部署报告 + 发布包自动清理（!2768）

> 本次核心为项目 1382 PDF 评分证据绑定修复（scoreparse 后端），无数据库结构变更、无新迁移。

## Flyway 预检结果

- **Step 1 validate**: **VALIDATE OK（278 migrations，all checksums match）**（服务器 flyway-repair-runner）
- **Step 2 DB 版本**: 已应用至 V1218（2026-09-25 16:08 最后应用），本次无新增迁移
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate **通过（278 migrations）**

## 部署步骤

1. 环境门禁确认（用户确认测试环境 172.16.38.78）
2. 早操同步（锚点分支 `agent/trae-init` 与 origin/main 一致，HEAD=origin/main）
3. 服务器现状探测（deployed-release=e2502f5de，health HTTP 200 + readiness 200，增量 9 commit，无迁移变更）
4. Flyway 预检 → validate OK（278 migrations）
5. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
6. 产物校验（obsEnabled=true、前端 index 入口 `index-DpRxBIGA.js`、jar 内迁移无重复版本、OBS Detail .upload=2、sidecar 打包）
7. 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：DB 备份 → Flyway validate → 前端激活 → 后端原子重启 → 健康检查
8. 前端资源保留（从上一版本 release e2502f5de `cp -rn` 旧 assets 到 /srv/www/xiyu-bid/assets/，防跨版本 404）

### ⚠️ 本部署特殊事件：VPN 隧道不稳定 + 分块上传

部署前探测发现本机 VPN（utun7）数据面异常：ping 网关 100% 丢包、SSH 握手被服务器关闭、HTTP 全 000，测试/生产服务器同时不可达（本机外网正常）。用户多次重连后恢复，但 **155M 归档单次 scp 传输至 57M 即断连（Broken pipe），且传输中断后隧道再次彻底失效**。

**处理策略（分块上传）**：
- 服务器无 rsync（无法断点续传）→ 本地 `split -b 25m` 分 7 块
- 逐块 scp（25M 小块在 VPN 下稳定，7 块全部一次成功）
- 服务器 `cat part_*` 合并 + `sha256sum` 校验与本地完全一致（`d188e2a3...`）

**防复发建议**：大文件跨不稳定 VPN 传输优先分块；上传前先清理上次中断的残缺归档（本次 57M 残缺文件已清理）。

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running)，健康检查 3/3 consecutive（第 80 次轮询） |
| 健康检查（经 Nginx 8080） | ✅ HTTP 200 UP |
| Readiness | ✅ HTTP 200 UP |
| Flyway validate | ✅ VALIDATE OK（278 migrations） |
| DB 最高版本 | ✅ V1218（success=1），无 pending |
| 前端一致性 | ✅ `assets/index-DpRxBIGA.js` 与 release 一致 |
| Login 路由 | ✅ HTTP 400（空密码参数校验，预期） |
| Projects 路由 | ✅ HTTP 401（需认证） |
| CRM 路由 | ✅ HTTP 401（需认证） |
| 前端 / | ✅ HTTP 200 |
| 前端 /login | ✅ HTTP 200 |
| 旧 assets 保留 | ✅ 已从 e2502f5de release 目录保留 |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ remote-deploy 自动备份（winbid-0d8543c92-*.sql.gz） |
| 归档完整性 | ✅ sha256 与本地一致（d188e2a3...） |
| 临时调试配置 | ⚠️ `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决策保留项，非本次新增） |

## 回滚

- **回滚锚点**: 上一 release `e2502f5de`（/opt/xiyu-bid/releases/e2502f5de 仍存在）
- **DB 备份**: remote-deploy 自动备份 `/opt/xiyu-bid/db-backups/winbid-0d8543c92-*.sql.gz`
- **schema 回滚**: 本次无新增迁移，无 schema 变更
- **回滚状态**: 就绪（未执行，验证通过无需回滚）

## 经验沉淀应用

- Flyway 预检 3 步法 ✅
- 同源构建（VITE_API_BASE_URL=） ✅
- OBS 直传显式启用 + 校验 obsEnabled ✅（第 84 次/第 8 次生产事故防复发）
- **大文件分块上传（VPN 不稳定场景）** ✅ 本次新增经验：25M 分块 + 逐块 scp + cat 合并 + sha256 校验

## 部署确认清单

- [x] 环境门禁（测试 172.16.38.78）
- [x] 早操同步 + 基线确认
- [x] Flyway 预检 3 步
- [x] 本地打包 + 产物校验
- [x] 分块上传 + 完整性校验
- [x] remote-deploy 自动部署
- [x] 健康检查 + Smoke 全通过
- [x] 前端资源保留
- [x] 部署报告
