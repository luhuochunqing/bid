# 第 135 次测试环境部署报告 — 2026-08-19

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 数据分析 collation 修复 + 竞品是否中标读 prc.notes + 案例沉淀 OBS 读取上线 |
| 部署序号 | 第 135 次（测试） |
| 部署时间 | 2026-08-19 22:57 CST（backend 激活） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `c326ba01d` |
| 上一版本 Release | `3525de3d5-api8080`（2026-08-19 20:44 激活） |
| 基线 commit | `c326ba01d`（origin/main 最新） |
| 健康检查通过 | ✅（UP） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | 无 |

## 变更内容（自 7707d8506..c326ba01d）

### 1. 数据分析 collation 修复 + 筛选选项（PR !2347，gemini）

**修复**：`fix(analytics): resolve MySQL collation 1267 error and remove silent catch in filter options`（fbf39df46）
- 修复 MySQL collation 1267 错误（JOIN 时 collation 冲突）
- 移除筛选选项里的静默 catch，暴露真实错误
- 配套文件：`FilterOptionsQueryService`、`TrendAnalysisDimensionQueryService`、`TrendDrillDownService`、`RegionDimensionExtractor`（新增）
- 配套测试：`RegionDimensionExtractorTest`（新增）

### 2. 竞品「是否中标」改读 prc.notes 枚举（PR !2346，zcode）

**修复**：`fix(analytics): 竞品「是否中标」改读 prc.notes 枚举，删除项目级状态推导`（a0348dca1）
- 竞品是否中标字段不再依赖项目级状态推导，改读 `prc.notes` 枚举
- 配套知识沉淀 + wiki 回填（385e43340，lessons §127）
- 配套测试：`CompetitorAnalysisJpqlRegressionTest`（新增）

### 3. 案例沉淀标书正文 OBS 读取 + 评分项 WARN 日志（PR !2345，trae）

- `56a4216b5`：案例沉淀标书正文读取支持 OBS obs-direct 引用
- `f45762be8`：AI criteriaId 未匹配 score_item 时输出 WARN 日志，避免 0 入库静默丢片段

## 验证结果

| 检查项 | 结果 |
|---|---|
| Flyway 预检（flyway-repair-runner.sh validate） | ✅ VALIDATE OK（258 migrations checksum 匹配） |
| DB 已应用版本 | ✅ V1197 = 源码最新 V1197（无新增迁移） |
| 后端 health（127.0.0.1:18080） | ✅ UP（consecutive 3/3，80 次尝试） |
| readiness | ✅ HTTP 200 |
| jar 内 Flyway 迁移版本 | ✅ 无重复 |
| OBS 直传 | ✅ 已启用（Detail chunk `.upload(` 数=2） |
| 前端一致性 | ✅ `src="/assets/index-8AyZ-yJr.js"` 与 release 一致 |
| 部署记录 | ✅ `/opt/xiyu-bid/deployed-release.json` = `c326ba01d` |
| 上一版本 assets 保留 | ✅ 已从 `3525de3d5-api8080` cp -rn 保留旧 hash 资源 |

### API Smoke（经 Nginx 8080 代理）

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ HTTP 200 |
| `/actuator/health/readiness` | ✅ HTTP 200 |
| `POST /api/auth/login`（空 body） | ✅ HTTP 400（参数校验：Username/Password required） |
| `/api/projects`（未认证） | ✅ HTTP 403 |
| `/api/integration/crm/health`（未认证） | ✅ HTTP 401 |
| 前端根路径 `/` | ✅ HTTP 200 |
| 前端 `/login` 路由 | ✅ HTTP 200 |
| 前端入口 JS | ✅ `assets/index-8AyZ-yJr.js` 与 release 一致 |

## 部署步骤

1. 环境门禁确认：**测试环境 172.16.38.78**（用户确认）
2. 早操三连：dev-env + sync-env + check-git-wrapper ✅（锚点分支 `agent/trae-init` 正确拒绝开发，切到 main 后 ff-only 同步）
3. 基线确认：`agent/trae-init` 分支 ff-only 同步到 `origin/main`（`c326ba01d`），工作区干净
4. 服务器现状：上一版本 `3525de3d5-api8080`，健康 UP
5. Flyway 预检：validate OK + DB V1197 = 源码 V1197（无新增迁移）
6. 本地打包：`RELEASE_ID=c326ba01d VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 bash scripts/release/package-release.sh`
   - mvn clean package BUILD SUCCESS；jar 内迁移无重复；OBS 启用
7. 产物校验：release-metadata.json（obsEnabled=true、apiBaseUrl=""）+ 前端入口 `index-8AyZ-yJr.js`
8. 上传 scp + remote-deploy.sh（SYSTEMCTL_SUDO=true，含 DB backup）
9. Flyway validate（remote-deploy 内置）✅ → 后端重启 → 健康检查 UP（80 次尝试）
10. 前端资源保留：从上一版本 `3525de3d5-api8080/frontend/assets/` cp -rn 到 `/srv/www/xiyu-bid/`
11. API Smoke + 前端验证（全部通过）
12. 配置清理检查：`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 为历史保留决策（第 13-15 次用户确认保留），非临时调试配置

## GitHub 镜像状态

- ⚠️ Gitee main 领先 GitHub 350 commits（历史镜像滞后，非本次部署引入）
- **待办**：本次未执行 `sync-to-github.sh`，避免扩大部署边界；后续由主工作区按需对齐

## 回滚方案（未启用）

```bash
ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && \
  sudo ln -sfn /opt/xiyu-bid/releases/3525de3d5-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
  sudo systemctl start xiyu-bid-backend'
```

## 经验沉淀应用

- OBS 直传显式传入 `VITE_OBS_ENABLED=true` 双保险 + 产物校验（obsEnabled=true + .upload( 数=2）
- 前端 hash 资源跨版本 404 防护：部署后从上一版本 release 目录 cp -rn 保留旧 assets
- 无新增迁移，Flyway 预检通过即可放行，无需等待迁移应用

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）
- [x] 早操三连 + 基线确认（c326ba01d = origin/main）
- [x] Flyway 预检（validate OK + DB/源码版本一致）
- [x] 本地打包 + 产物校验（OBS 直传 + 前端入口 + jar 迁移去重）
- [x] backend 部署 + 健康检查（UP）
- [x] Flyway 迁移验证（无新增迁移）
- [x] API Smoke + 前端验证（全部通过）
- [x] 前端资源保留（上一版本 assets）
- [x] 部署报告生成（本文件）