# 第 134 次测试环境部署报告 — 2026-08-19

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 案例沉淀评分项数据源改读 score_item + 数据分析根因修复上线 |
| 部署序号 | 第 134 次（测试） |
| 部署时间 | 2026-08-19 20:44 CST（backend 激活） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `3525de3d5-api8080` |
| 上一版本 Release | `ae7e7a3f6-api8080`（2026-08-19 16:13 激活） |
| 基线 commit | `3525de3d5`（agent/trae/fix-case-precipitation-score-item，已合入 main） |
| 健康检查通过 | ✅（UP） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | 无 |

## 变更内容（自 ae7e7a3f6..3525de3d5，共 10 commit）

### 1. 案例沉淀评分项数据源改读 score_item（PR !2342，trae）

**修复**：`fix(casework): 案例沉淀评分项数据源改读 score_item`（741f94c98）
- 案例沉淀的评分项数据不再从旧数据源读取，改为读取 `score_item`
- 配套知识沉淀 + wiki 回填（e4ec642a9）

### 2. 数据分析模块根因修复（PR !2343，gemini）

**修复**：`fix(analytics): fix department fallback, region zero counts, customer type enum mapping, and competitor isWon field`（d1cbe8400）
- 部门 fallback 修复
- 区域零计数修复
- 客户类型枚举映射修复
- 竞品 isWon 字段修复
- 配套 wiki 更新（03dc4c240）

### 3. 文档修正（f5ccb8ee8）

- 修正案例沉淀教训章节 PR 编号笔误（!2343 → !2342）

## 验证结果

| 检查项 | 结果 |
|---|---|
| Flyway 预检（flyway-repair-runner.sh validate） | ✅ VALIDATE OK（258 migrations checksum 匹配） |
| DB 已应用版本 | ✅ V1197 = 源码最新 V1197（无新增迁移） |
| 容器测试门禁（FlywayMysqlContainerTest） | ✅ 通过（Spring 全量上下文装配正常） |
| 后端 health（127.0.0.1:18080） | ✅ UP（consecutive 3/3，79 次尝试） |
| readiness | ✅ HTTP 200 |
| jar 内 Flyway 迁移版本 | ✅ 无重复 |
| OBS 直传 | ✅ 已启用（Detail chunk `.upload(` 数=2） |
| 前端一致性 | ✅ `src="/assets/index-BnvO5xM1.js"` 与 release 一致 |
| 部署记录 | ✅ `/opt/xiyu-bid/deployed-release.json` = `3525de3d5-api8080` |
| 上一版本 assets 保留 | ✅ 已从 `ae7e7a3f6-api8080` cp -rn 保留旧 hash 资源 |

### API Smoke（经 Nginx 8080 代理）

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ HTTP 200 |
| `/actuator/health/readiness` | ✅ HTTP 200 |
| `POST /api/auth/login`（空 body） | ✅ HTTP 400（参数校验：Password/Username required） |
| `/api/projects`（未认证） | ✅ HTTP 403 |
| `/api/integration/crm/health`（未认证） | ✅ HTTP 401 |
| 前端根路径 `/` | ✅ HTTP 200 |
| 前端 `/login` 路由 | ✅ HTTP 200 |
| 前端入口 JS | ✅ `assets/index-BnvO5xM1.js` 与 release 一致 |

## 部署步骤

1. 环境门禁确认：**测试环境 172.16.38.78**（用户确认）
2. 早操三连：dev-env + sync-env + check-git-wrapper ✅
3. 基线确认：分支 `agent/trae/fix-case-precipitation-score-item`，HEAD `3525de3d5` = origin/main，工作区干净
4. 服务器现状：上一版本 `ae7e7a3f6-api8080`，健康 UP
5. Flyway 预检：validate OK + DB V1197 = 源码 V1197
6. 本地打包：`RELEASE_ID=3525de3d5-api8080 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 bash scripts/release/package-release.sh`
   - 容器测试门禁通过（FlywayMysqlContainerTest）
   - mvn clean package BUILD SUCCESS；jar 内迁移无重复；OBS 启用
7. 产物校验：release-metadata.json（obsEnabled=true、apiBaseUrl=""）+ 前端入口存在
8. 上传 scp + remote-deploy.sh（SYSTEMCTL_SUDO=true，含 DB backup）
9. Flyway validate（remote-deploy 内置）✅ → 后端重启 → 健康检查 UP（79 次尝试）
10. 前端资源保留：从上一版本 `ae7e7a3f6-api8080/frontend/assets/` cp -rn 到 `/srv/www/xiyu-bid/`
11. API Smoke + 前端验证（全部通过）
12. 配置清理检查：`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 为历史保留决策（第 13-15 次用户确认保留），非临时调试配置

## GitHub 镜像状态

- ⚠️ GitHub main 领先 Gitee 1 个 commit：`a0578860a chore(locks): prune stale expired locks`（GitHub Actions bot 自动清理过期锁文件 `.agent-locks/performance-bundle-export.yml`）
- 该 commit 仅删除锁文件，无业务影响；Gitee main 上该锁文件仍存在
- Gitee 领先 GitHub 340 commits（历史镜像滞后）
- **待办**：如需彻底对齐，先 `scripts/sync-from-github.sh a0578860a` 把该 commit cherry-pick 回 Gitee，再 `scripts/sync-to-github.sh` 镜像同步（本次未执行，避免扩大部署边界）

## 回滚方案（未启用）

```bash
ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && \
  sudo ln -sfn /opt/xiyu-bid/releases/ae7e7a3f6-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
  sudo systemctl start xiyu-bid-backend'
```

## 经验沉淀应用

- 打包脚本容器测试门禁正常执行（FlywayMysqlContainerTest 验证 Spring 装配）
- OBS 直传显式传入 `VITE_OBS_ENABLED=true` 双保险 + 产物校验（obsEnabled=true + .upload( 数=2）
- 前端 hash 资源跨版本 404 防护：部署后从上一版本 release 目录 cp -rn 保留旧 assets

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）
- [x] 早操三连 + 基线确认（3525de3d5 = origin/main）
- [x] Flyway 预检（validate OK + DB/源码版本一致）
- [x] 本地打包 + 产物校验（OBS 直传 + 前端入口 + jar 迁移去重）
- [x] backend 部署 + 健康检查（UP）
- [x] Flyway 迁移验证（无新增迁移）
- [x] API Smoke + 前端验证（全部通过）
- [x] 前端资源保留（上一版本 assets）
- [x] 部署报告生成（本文件）
