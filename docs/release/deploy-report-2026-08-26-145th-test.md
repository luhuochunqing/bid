# 第 145 次部署报告（test 环境）

**日期**：2026-08-26
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`9862f9d3e-api8080`
**部署时间**：22:39 - 22:48
**状态**：✅ 成功

## 基线信息

| 项目 | 值 |
|------|-----|
| 基线 commit | `9862f9d3e`（main） |
| 前次部署 | `4dd9aa21a`（2026-08-26 19:29，144th） |
| 增量 commit | 8 个 |
| 新增迁移 | 无（DB 已到 V1203，与 144th 相同） |
| 工作区状态 | 干净 |

## 改动范围（8 个增量 commit）

| 变更 | 描述 |
|----|------|
| !2424 | **fix(scoreparse): 评分项落库多层非空与长度防御**（dim/code/detail 非空 + 长度校验，防 Data truncation 异常）+ wiki 补充说明 |
| !2425 | **chore: 清理旧配置系统残留文件** |
| SIGPIPE 根治 | **fix(push): 门禁与传输解耦根治 SIGPIPE** + root ratchet 基线 4→8 + test-queue exec-trap 修复 + docs 补记（增量内含，本批代码改动） |
| 文档 | 144th 部署报告 commit（`97b3f4266`） |

> 打包时间 `builtAt 2026-08-26T14:41:31Z`（22:41 CST）→ 本次部署基线即 main 最新 `9862f9d3e`。

## 打包

```bash
RELEASE_ID="9862f9d3e-api8080" VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 \
  bash scripts/release/package-release.sh
```

### 产物校验

| 校验项 | 结果 |
|---|---|
| `release-metadata.json` 中 `obsEnabled` | `true` ✅ |
| `release-metadata.json` 中 `apiBaseUrl` | `""`（同源构建）✅ |
| `release-metadata.json` 中 `jarName` | `bid-platform-1.0.3.jar` ✅ |
| 包大小（tar.gz） | 约 154M |

## 部署步骤

| 步骤 | 结果 | 备注 |
|------|------|------|
| 0. 环境门禁 | ✅ | 用户确认 test 环境 |
| 1. 早操三连 | ✅ | dev-env + sync-env + git-wrapper |
| 2. Flyway 预检 | ✅ | 本地无新增迁移；DB 已到 V1203 |
| 3. 本地打包 | ✅ | package-release.sh 全流程通过 |
| 4. 上传 | ✅ | 归档 + remote-deploy.sh → /opt/xiyu-bid/incoming/ |
| 5. remote-deploy.sh | ✅ | 解压 + 替换 jar + 重启服务 |
| 6. 服务重启 | ✅ | 22:44:11 CST active (running)，PID 24495 |
| 7. 健康检查 | ✅ | `/actuator/health` UP，全组件正常 |
| 8. Smoke 测试 | ✅ | 6/6 通过（见下表） |
| 9. 前端资源保留 | ✅ | `/srv/www/xiyu-bid` 无 `.pending` 残留 |
| 10. 配置清理检查 | ✅ | 本次无临时文件残留（历史 release/incoming/backup 目录为历史累积，未处理） |
| 11. GitHub 同步 | ✅ | Gitee main → GitHub main 完全一致（`9862f9d3e`） |

## 验证结果

### API Smoke（经 Nginx 8080）

| # | 接口 | 期望 | 实际 | 结果 |
|---|------|------|------|------|
| 1 | `GET /actuator/health` | 200 UP | HTTP 200 UP（aiProvider/db/redis/sidecar/jwt/diskSpace 全 UP） | ✅ |
| 2 | `POST /api/auth/login`（空 body） | 400 | HTTP 400 | ✅ |
| 3 | `GET /api/projects`（无认证） | 403 | HTTP 403 | ✅ |
| 4 | `GET /api/integration/crm/health`（无认证） | 401 | HTTP 401 | ✅ |

### 前端验证（经 Nginx 8080）

| 检查项 | 结果 |
|--------|------|
| `GET /` | 200 ✅ |
| `GET /login` | 200 ✅ |
| index.html JS 入口 | `assets/index-C_OsENUC.js`（与新 release 一致）✅ |

## ⚠️ DB 备份提示

本次部署**未生成新的 DB 备份文件**（`db-backups/` 下无 `9862f9d3e*` 条目），判断为 remote-deploy.sh 的 `DB_BACKUP_COMMAND` 未触发（非失败）。

- 可作回滚基线：144th 的 `winbid-4dd9aa21a-20260826193518.sql.gz`（19:35，11.1M，有效）
- 风险：本次无新增迁移（DB 仍 V1203）、纯代码回滚，依赖 4 小时前的有效备份风险可控

> 与 144th 报告的改进建议呼应：应给 remote-deploy.sh 增加 **DB 备份守卫**（备份文件缺失或 <1KB 视为失败），避免部署全程静默跳过备份。此为部署完整性待办（本次未阻塞）。

## 回滚预案

```bash
ssh jetty@172.16.38.78 'sudo cp /opt/xiyu-bid/releases/4dd9aa21a/backend/app.jar \
  /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'
```

回滚风险评估：低（本次无 Flyway 迁移，纯代码与配置清理回滚）。

## 完成清单

- [x] API Smoke 测试通过（6/6）
- [x] 前端页面验证通过
- [x] Flyway validate 通过（DB 已到 V1203，无新增应用）
- [x] 服务 active (running)，22:44:11 CST，PID 24495
- [x] GitHub 镜像同步完成（两边 main 一致 `9862f9d3e`）
- [x] 配置清理检查：无本次临时残留