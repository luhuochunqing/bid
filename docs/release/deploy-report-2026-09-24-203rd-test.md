# 第 203 次部署报告（test）

- **部署日期**: 2026-09-24
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `469b9eb3a`
- **部署类型**: 代码级部署（无新增 Flyway 迁移）
- **操作人**: Trae 开发宿主（基线 = origin/main `469b9eb3a`）

## 部署概览

从上一已部署 release `c5a1b6eed`（202nd，2026-09-24）增量部署至 `469b9eb3a`，共 **8 个 commit**，无新增 Flyway 迁移（DB 保持 V1217，与上一部署一致）。

## 基线信息

- 基线: `origin/main` = `469b9eb3a`（本地锚点 ff-only 同步至最新）
- 工作区状态: 干净
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-CQRpRhrT.js`（与上一部署一致，本次前端无改动）

## 主要改动（c5a1b6eed..469b9eb3a）

- **fix(scoreparse)**: preserve text scoring evidence across docx boundaries 跨 docx 边界保留文本评分证据（!2751，含 commit `469b9eb3a` / `cee20dd10`）
  - `TextPrimaryEvidenceBinder` 文本主证据绑定逻辑增强（+64 行）
  - `MultimodalFactParentCodeNormalizer` / `MultimodalScoreFactValidator` / `OriginalScoringContext` 边界校验强化
  - `TextPrimaryEvidenceBinderTest` / `MultimodalFactParentCodeNormalizerTest` / `MultimodalScoreFactValidatorTest` / `OriginalScoreEvidencePolicyTest` 配套单测
  - golden case（`case-docx-run-boundary-score`）docx 边界分组场景
- **fix(scoreparse)**: reject blank normalized evidence quotes 拒绝空白归一化证据引号
- **fix(scoreparse)**: reuse normalized text evidence mapping 复用归一化文本证据映射
- **fix(scoreparse)**: map text evidence to matched source offset 文本证据映射到匹配源偏移
- **test(scoreparse)**: add docx boundary golden case docx 边界黄金用例
- **docs**: wiki 回填（`scoreparse-multimodal-primary` 页面）
- **docs**: 第 202 次部署报告（`deploy-report-2026-09-24-202nd-test.md`）

> 本次核心为评分解析（scoreparse）跨 docx 边界文本评分证据保留修复：分段文档评分时主证据绑定、证据引号校验、源偏移映射加固。均配套单元测试 + golden case。无数据库结构变更。

## Flyway 预检结果

- **Step 1 validate**: `flyway-repair-runner.sh` → **VALIDATE OK（277 migrations，checksum 全部匹配）**
- **Step 2 DB 版本**: 已应用至 V1217（add score child aggregation），与源码 validate 一致（本次无新增迁移）
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate 通过（277 migrations）
- **结论**: 本次无新增迁移，DB 与源码版本一致，无 pending

## 部署步骤

1. 环境门禁确认（用户确认测试环境 172.16.38.78）
2. 早操同步（fetch origin/main + 本地锚点 ff-only 同步至 `469b9eb3a`）
3. 服务器现状探测（deployed-release=c5a1b6eed，health/readiness 全 UP，obsEnabled=true）
4. Flyway 预检 3 步法（validate OK → DB 版本一致 → 内置校验）
5. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
6. 产物校验（obsEnabled=true、前端 index 入口、jar 内迁移无重复、OBS Detail .upload=2、sidecar 打包）
7. scp 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - remote-deploy 自动执行：健康检查 → Flyway validate → 前端激活 → 后端原子重启（含 sudo chown 前端目录）
8. 前端资源保留（从上一版本 release `c5a1b6eed` cp -rn 旧 assets 到 /srv/www/xiyu-bid/assets/）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running)，PID 6196，健康检查 3/3 consecutive（第 79 次轮询） |
| 健康检查（经 Nginx 8080） | ✅ HTTP 200 UP |
| Readiness | ✅ HTTP 200 UP |
| Flyway validate | ✅ VALIDATE OK（277 migrations） |
| 前端一致性 | ✅ `assets/index-CQRpRhrT.js` 与 release 一致 |
| Login 路由 | ✅ HTTP 400（空密码参数校验，预期） |
| Projects 路由 | ✅ HTTP 401（需认证） |
| CRM 路由 | ✅ HTTP 401（需认证） |
| 前端 / | ✅ HTTP 200 |
| 前端 /login | ✅ HTTP 200 |
| 旧 assets 保留 | ✅ 已执行（从 c5a1b6eed cp -rn 幂等） |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| DB 备份 | ✅ 已自动执行 |

## 回滚

- **回滚锚点**: 上一 release `c5a1b6eed`（/opt/xiyu-bid/releases/c5a1b6eed 仍存在，后端 jar 可重新构建部署）
- **DB 备份**: 已由 remote-deploy.sh 自动执行
- **schema 回滚**: 本次无新增迁移，无需回滚脚本
- **回滚状态**: 就绪（未执行，行为正常无需回滚）

## 经验沉淀应用

- 同源构建（VITE_API_BASE_URL=） ✅
- OBS 直传显式启用 + 校验 obsEnabled ✅（第 84 次/第 8 次生产事故防复发）
- COPYFILE_DISABLE=1 防 macOS `._*` 残留 ✅
- --noproxy 绕过 Mac HTTP_PROXY 502 ✅
- SYSTEMCTL_SUDO=true ✅（第 15 次事故防复发）
- 前端 hash 资源跨版本 404 防护 ✅（部署后从上一版本 cp -rn 旧 assets）

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既有配置（第 13/14/15 次决定保留），非本次引入，保留。
- 未发现 DEBUG/TRACE 临时配置。

## 风险提示

- 本次无数据库结构变更，schema 风险低。
- 核心业务改动为评分解析（scoreparse）跨 docx 边界证据保留，建议回归：
  - 分段文档（跨 docx 边界）评分解析场景，文本评分证据引用与源偏移是否一致
  - 归一化证据引号为空 / 边界分组场景