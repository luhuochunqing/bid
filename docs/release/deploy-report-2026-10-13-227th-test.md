# 第 227 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-13 22:41 (UTC+8) |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `836f9b279-api8080` |
| 部署分支 | `agent/trae-init`（锚点分支，HEAD = origin/main） |
| HEAD Commit | `836f9b279` |
| 旧版本 | `ef704cfe6-api8080`（第 226 次，同日早间部署） |
| 构建包 | `.release/xiyu-bid-release-836f9b279-api8080.tar.gz`（155M） |

## PR 列表（增量 2 个 commit，自 ef704cfe6..836f9b279）

| Commit | 说明 |
|---|---|
| `c87c05efd` | fix(scoreparse): restore full rules from row quotes without outer bars |
| `836f9b279` | **!2829** fix(scoreparse): 修复缺外侧分隔符的行引文完整规则恢复 |

## 改动范围

- **后端**：scoreparse 评分规则恢复修复（缺外侧分隔符的行引文场景），18 文件（+323/-19）
- **前端**：无独立变更（入口 hash `index-BpcZvMz6.js` 与 226 次一致，属预期——本次改动全在后端 scoreparse 逻辑）
- **数据库迁移**：无新迁移（DB 与源码同步在 V1220）
- **Sidecar**：源码零变更（`git diff ef704cfe6..HEAD -- document-converter-sidecar/` 为空）

## Flyway 预检结果

| 检查项 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK（all checksums match） |
| Step 2: DB 已应用版本 | 最新 V1220（2026-09-27），与源码一致 |
| Step 3: remote-deploy 内置 validate | ✅ 通过 |

## 部署步骤

1. **环境门禁**：用户声明 ENV=test 并确认部署 `836f9b279` ✅
2. **早操三连**：dev-env + sync-env（7/7 门禁就绪）+ check-git-wrapper ✅
3. **基线确认**：HEAD = origin/main = `836f9b279`，工作区仅 226 次报告未跟踪文件 ✅
4. **服务器现状**：旧版 ef704cfe6 运行中，health UP ✅
5. **Flyway 预检 3 步法** ✅（见上表）
6. **本地打包**：`RELEASE_ID=836f9b279-api8080 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` ✅
7. **产物校验** ✅：obsEnabled=true、apiBaseUrl=""（同源）、OBS .upload( 调用数=2、入口资源正常
8. **sidecar sha 预更新**：新 sha `8c57b43e` 提前写入服务器 `.deployed_sha256`，跳过无意义重建 ✅
9. **上传 + 部署**：scp + remote-deploy.sh（SYSTEMCTL_SUDO=true）✅ 一次成功
10. **前端资源保留** ✅：从上一版本 `ef704cfe6-api8080` 目录 `cp -rn` 旧 assets（本次 226 版 release 目录尚在，保留成功）
11. **健康检查** ✅：79 次尝试后 3/3 连续通过（Kafka readiness 延迟，已知行为）
12. **迁移验证**：无新迁移，跳过
13. **Smoke 测试** ✅（8/8，见下节）
14. **配置清理检查**：仅 `SHOW_DETAILS=always`（用户决定保留）

## 验证结果

### 健康检查

```
✅ Health check passed (consecutive 3/3, total attempts: 79, service: active/running)
Active: active (running) since 二 2026-10-13 22:41:15 CST
```

### Smoke 测试（经 Nginx 8080 代理到后端 18080）

| 检查项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| /actuator/health | 200 | 200 | ✅ |
| /actuator/health/readiness | 200 | 200 | ✅ |
| POST /api/auth/login (空参) | 400 | 400 | ✅ |
| /api/projects (未认证) | 401 | 401 | ✅ |
| /api/integration/crm/health (未认证) | 401 | 401 | ✅ |
| 前端 / | 200 | 200 | ✅ |
| 前端 /login | 200 | 200 | ✅ |
| 前端入口资源 | 与 release 一致 | assets/index-BpcZvMz6.js | ✅ |

### 部署记录确认

```json
"releaseId": "836f9b279-api8080",
"activatedAt": "2026-10-13T14:41:15Z"
```

## 本次流程改进

**sidecar sha 预更新（226 次教训的落地应用）**：

- 226 次部署时 sidecar sha 漂移触发 Docker 镜像重建，遇镜像源 502 导致首次部署失败
- 本次在打包后、部署前，主动将新 sha（`8c57b43e`）写入服务器 `/opt/xiyu-bid/sidecar/.deployed_sha256`，跳过无意义重建
- 结果：部署一次成功，无中断
- **建议**：将该步骤固化进 `remote-deploy.sh`（改用 `git diff --name-only <old>..<new> -- document-converter-sidecar/` 判断源码是否真变）或 `package-release.sh`（sidecar tar 排除时间戳），彻底消除 sha 漂移误判

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一版本 Release | `ef704cfe6-api8080`（第 226 次） |
| 上一版本 Release 目录 | `/opt/xiyu-bid/releases/ef704cfe6-api8080`（本次部署后仍存在） |
| DB 备份 | `winbid-836f9b279-20261013224105.sql.gz`（部署前自动备份） |
| 回滚方式 | jar 备份原子恢复 + systemd restart（本次无 schema 变更，DB 无需回滚） |
| 回滚 owner | 运维 + 当班开发 |

## 风险提示

1. **Kafka readiness 延迟**：79 次尝试（约 2 分 38 秒）后 UP，属已知行为；超过 4 分钟需排查 Kafka broker
2. **前端入口 hash 未变**（index-BpcZvMz6.js）：本次改动纯后端，浏览器缓存的旧页面与新后端 API 兼容（同接口契约），无风险
3. **releases 目录**：226/227 两个版本目录当前并存，remote-deploy.sh 自动轮换清理

## 部署确认清单

- [x] 环境门禁（用户确认 ENV=test，部署 836f9b279）
- [x] 早操三连通过
- [x] 服务器现状确认（旧版 health UP）
- [x] Flyway 预检 3 步法通过
- [x] 本地打包成功（BUILD SUCCESS）
- [x] 产物校验通过（obsEnabled、同源、OBS 直传、入口资源）
- [x] sidecar sha 预更新（规避 Docker 502 复发）
- [x] DB 备份完成
- [x] remote-deploy.sh 一次成功
- [x] 健康检查通过（3/3 连续）
- [x] 前端旧 assets 保留（跨版本 404 防护）
- [x] Smoke 测试 8 项全部通过
- [x] 前端入口资源与 release 一致
- [x] 临时调试配置检查（仅保留 SHOW_DETAILS=always）
- [x] 部署记录确认（deployed-release.json 已更新）
- [x] 部署报告生成

**部署状态：✅ 成功（一次通过，零中断）**
