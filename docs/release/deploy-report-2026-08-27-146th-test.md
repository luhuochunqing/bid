# 第 146 次部署报告（test 环境）

**日期**：2026-08-27
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`5eaa167b0-api8080`
**部署时间**：10:38 - 10:47
**状态**：✅ 成功

## 基线信息

| 项目 | 值 |
|------|-----|
| 基线 commit | `5eaa167b0`（main） |
| 前次部署 | `9862f9d3e`（2026-08-26 22:44，145th） |
| 增量 commit | 12 个 |
| 新增迁移 | 无（DB 已到 V1203，与 145th 相同） |
| 工作区状态 | 干净 |

## 改动范围（12 个增量 commit）

| 变更 | 描述 |
|----|------|
| !2428 | **fix(data-analysis): M1/M4 竞品下拉 label/value 格式修复**（DTO 属性正确映射，PR #2419 后续修复） |
| !2427 | **fix(scoreparse): 过滤须知前附表误入评分候选池**（候选提取与全局归并 prompt 补 contains 锚定硬性规则 + 注释收敛单一出处 + 混合表用例） |
| docs | lessons §167 前附表知识沉淀 + wiki 回填 + ai-capabilities health_checked 更新 |
| 文档 | 145th 部署报告合入（!2426） |

> 本次为纯前端/解析逻辑修复，无 schema 变更。

## 打包

```bash
RELEASE_ID="5eaa167b0-api8080" VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 \
  bash scripts/release/package-release.sh
```

### 产物校验

| 校验项 | 结果 |
|---|---|
| jar 内 Flyway 迁移版本无重复 | ✅（263 个 V*.sql） |
| OBS 直传启用（Detail chunk `.upload(` 调用数） | ✅（=2） |
| `release-metadata.json` 中 `obsEnabled` | `true` ✅ |
| `release-metadata.json` 中 `apiBaseUrl` | `""`（同源构建）✅ |
| 包大小（tar.gz） | 约 154M |

## 部署步骤

| 步骤 | 结果 | 备注 |
|------|------|------|
| 0. 环境门禁 | ✅ | 用户确认 test 环境 |
| 1. 早操三连 | ✅ | 本地门禁就绪（发现 GitHub 落后 12 个 commit，后续补同步） |
| 2. Flyway 预检 | ✅ | 服务器 validate OK（264 migrations checksum 一致）；DB 已应用至 V1203 全 success；本次无新增迁移 |
| 3. 本地打包 | ✅ | package-release.sh 全流程通过（Maven clean 构建无脏 target） |
| 4. 上传 | ✅ | 归档 + remote-deploy.sh → /opt/xiyu-bid/incoming/ |
| 5. remote-deploy.sh | ✅ | Flyway validate 预检通过 → DB 备份 → 替换 jar → 重启服务 |
| 6. 服务重启 | ✅ | 10:44:00 CST active (running)，PID 2983 |
| 7. 健康检查 | ✅ | UP（第 79 次探测通过，约 2 分 38 秒，属 Kafka SDK 启动延迟已知行为范围内） |
| 8. Smoke 测试 | ✅ | 8/8 通过（见下表） |
| 9. 前端旧 assets 保留 | ✅ | 从上一 release 目录 `cp -rn` 补回 190 个旧 hash 文件（总数 272），防旧标签页 preload 404 |
| 10. 配置清理检查 | ✅ | 仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决策保留项），无 DEBUG/TRACE 残留 |
| 11. GitHub 同步 | ✅ | Gitee main → GitHub main 完全一致（`5eaa167b0`），落后 12 清零 |

## 验证结果

### API Smoke（经 Nginx 8080）

| # | 接口 | 期望 | 实际 | 结果 |
|---|------|------|------|------|
| 1 | `GET /actuator/health` | 200 UP | HTTP 200（aiProvider/db/redis/sidecar/jwt/diskSpace/liveness/readiness 全 UP） | ✅ |
| 2 | `GET /actuator/health/readiness` | 200 UP | HTTP 200 | ✅ |
| 3 | `POST /api/auth/login`（空 body） | 400 | HTTP 400 | ✅ |
| 4 | `GET /api/projects`（无认证） | 403 | HTTP 403 | ✅ |
| 5 | `GET /api/integration/crm/health`（无认证） | 401 | HTTP 401 | ✅ |

### 前端验证（经 Nginx 8080）

| 检查项 | 结果 |
|--------|------|
| `GET /` | 200 ✅ |
| `GET /login` | 200 ✅ |
| index.html JS 入口 | `assets/index-B9ULEIzG.js`（与新 release 一致）✅ |
| `/assets/*` 强缓存 | `Cache-Control: max-age=31536000, public, immutable` ✅ |

## DB 备份与回滚锚点

本次 `DB_BACKUP_COMMAND` 正常触发，备份已生成：

- `winbid-5eaa167b0-20260827104351.sql.gz`（10:43，12M）——本次部署前备份
- 前次基线备份 `winbid-4dd9aa21a-20260826193518.sql.gz` 仍在

> 145th 报告提出的"DB 备份守卫"改进待办仍未实施，但本次验证备份命令链路本身工作正常。

## 回滚预案

```bash
ssh jetty@172.16.38.78 'sudo cp /opt/xiyu-bid/releases/9862f9d3e-api8080/backend/app.jar \
  /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'
```

回滚风险评估：低（本次无 Flyway 迁移，纯代码回滚；回滚目录 `/opt/xiyu-bid/releases/9862f9d3e-api8080` 完整保留）。

## 完成清单

- [x] API Smoke 测试通过（8/8）
- [x] 前端页面验证通过 + hash 资源强缓存生效
- [x] Flyway validate 通过（DB V1203，无新增迁移）
- [x] 服务 active (running)，10:44:00 CST，PID 2983
- [x] DB 备份已生成（winbid-5eaa167b0-20260827104351.sql.gz）
- [x] 上一版本前端 assets 已保留 24h（190 个文件）
- [x] GitHub 镜像同步完成（两边 main 一致 `5eaa167b0`）
- [x] 配置清理检查：无临时调试残留
