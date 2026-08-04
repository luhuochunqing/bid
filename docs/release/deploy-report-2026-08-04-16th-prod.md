# 第 16 次生产环境部署报告 — 2026-08-04

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产 (prod)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.10.149` |
| 用途 | 正式环境、对外服务 |
| 部署序号 | 第 16 次（生产） |
| 部署时间 | 2026-08-04 23:21:17 CST |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `a3ecaa47b-prod` |
| 上一版本 Release | `6cf4a07d1-prod`（2026-07-30 22:09:25 CST，第 15 次生产部署） |
| 基线 commit | `a3ecaa47b`（origin/main） |
| 激活时间 | 2026-08-04T23:21:17Z |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 增量 commit | 121 个（5 天跨度） |
| 新增 Flyway 迁移 | 3 个（V1182, V1183, V1184） |
| Smoke 测试 | 7 项全部通过 |
| GitHub 镜像 | ✅ 已同步（两边 main 完全一致） |
| 前端资源保留 | ✅ 已从 `6cf4a07d1-prod` 复制旧 assets 防 404 |

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/trae`
- 锚点分支：`agent/trae-init`
- 工作区状态：干净，HEAD = origin/main = `a3ecaa47b01d3393125e35177d3b1677d7340df4`
- 早操三连：sync-env.sh ff-only 同步通过，本地门禁 7/7 通过
- 部署前 GitHub 镜像落后 Gitee 2 个 commit（部署后已同步）

## 增量改动（6cf4a07d1 → a3ecaa47b，121 个 commit）

### 关键 PR 列表

| PR | 描述 | 关联 |
|---|---|---|
| !2250 | feat(performance): 业绩合订本 Word 导出功能 | CO-602（含 V1184 迁移） |
| !2252 | fix(organization): 组织管理页修复（部门来源/部门显示/工号列） | — |
| !2254 | fix(performance): P2 #13-#23 修复 + useAsyncTask 轮询修复 | CO-602 |
| !2256 | fix(performance): 补全 @Auditable 审计注解 | CO-602 P2 #14 |
| !2257 | fix(project): 项目页面部门显示始终用实时反查覆盖历史快照 | CO-602 调岗场景 |
| !2258 | fix(audit): 修复导出端点 @Auditable 被白名单静默丢弃 | CO-602 |
| !2260 | wiki: CO-602 audit-whitelist-pitfalls 页 + 回填索引 | CO-602 |
| !2263 | fix(organization): 清理部门来源下拉的 ehsy 历史遗留选项 | — |
| !2265 | perf(organization): 部门树渲染性能优化 - 全局去重+默认只展开根节点 | CO-605（22795→800 节点） |
| !2266 | fix(workbench): 修复 workbench-characterization 测试 vue-router mock 缺失 | — |
| !2268 | docs(release): 第 118 次测试环境部署报告 | — |

### 改动范围聚合

| 目录 | 主要内容 |
|---|---|
| backend/performance | 业绩合订本 Word 导出（含新表 V1184） |
| backend/project | 项目页面部门实时反查修复（CO-602 调岗场景） |
| backend/audit | @Auditable 注解补全 + 白名单修复 |
| backend/knowledge | 项目三表单自定义字段（CO-601，含 V1183） |
| backend/form-engine | 移除废弃 knowledge.case 表单定义（含 V1182） |
| src/views/Organization | 组织管理页部门来源清理 + 部门树渲染性能优化 |
| src/views/Performance | 业绩合订本导出 UI |

### 新增 Flyway 迁移（3 个）

| 版本 | 描述 | 类型 | 风险 | 回滚脚本 |
|---|---|---|---|---|
| V1182 | remove_unused_form_definitions | DELETE 废弃表单定义（knowledge.case） | 🟡 数据删除（已回滚脚本幂等） | U1182 ✅ |
| V1183 | add_custom_fields_to_project_tables | ALTER TABLE ADD COLUMN JSON（projects/project_initiation_details） | 🟢 加列非破坏 | U1183 ✅ |
| V1184 | create_performance_export_task | CREATE TABLE（业绩导出任务表） | 🟢 新表 | U1184 ✅ |

- **无破坏性 DROP TABLE / DROP COLUMN 结构变更**
- 3 个迁移均有对应 rollback 脚本（U1182/U1183/U1184）
- V1182 为 DELETE 操作但幂等（U1182 修复为 INSERT 前 DELETE 避免重复）

## Flyway 预检 3 步法

### Step 1: Flyway validate（部署前）

```
VALIDATE OK - all checksums match
Successfully validated 242 migrations (execution time 00:00.099s)
```

### Step 2: DB 已应用版本对比

| 项 | 值 |
|---|---|
| 部署前 DB 最新版本 | 1181（cleanup audit logs project id, 2026-07-30 22:09:32） |
| 源码最新版本 | 1184 |
| 待应用 | V1182, V1183, V1184 |

### Step 3: remote-deploy 内置 validate（部署中）

```
VALIDATE OK - all checksums match
✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
```

## 部署步骤

### 1. 本地打包

```bash
RELEASE_ID="a3ecaa47b-prod" VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 \
  bash scripts/release/package-release.sh
```

- 打包时间：约 33 秒（mvn clean -DskipTests package）
- 产物大小：161M
- 同源构建：`apiBaseUrl=""`（生产前端+后端同入口经 Nginx 反代）
- OBS 直传：`obsEnabled=true` ✅（Detail chunk .upload( 调用数=2）

### 2. 产物校验

| 校验项 | 结果 |
|---|---|
| release-metadata.json obsEnabled | `true` ✅ |
| jar 内 V*.sql 无重复版本 | ✅（244 files） |
| 前端 index.html 入口 | `assets/index-Nwjpxa6o.js` + `assets/index-Cmq0rLNS.css` ✅ |
| 前端无 dev API 地址 | ✅（baseURL 同源） |

### 3. 上传 + 部署

```bash
scp .release/xiyu-bid-release-a3ecaa47b-prod.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.10.149:/opt/xiyu-bid/incoming/

ssh jetty@172.16.10.149 'set -e; cd /opt/xiyu-bid/incoming && \
  source /etc/xiyu-bid/backend.env && \
  RELEASE_ARCHIVE=... RELEASE_ID=a3ecaa47b-prod \
  SYSTEMCTL_SUDO=true \
  DB_BACKUP_COMMAND="..." \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- DB 备份：`/opt/xiyu-bid/db-backups/winbid-a3ecaa47b-prod-<timestamp>.sql.gz` ✅
- Flyway validate：✅ 通过
- 后端服务重启：`active (running) since Tue 2026-08-04 23:21:17 CST`
- 健康检查：✅ 15 次尝试，3 次连续成功
- 前端一致性：✅ `assets/index-Nwjpxa6o.js` 与 release 一致

### 4. 前端资源保留（防跨版本 404）

```bash
sudo cp -rn /opt/xiyu-bid/releases/6cf4a07d1-prod/frontend/assets/* \
  /srv/www/xiyu-bid/assets/ 2>/dev/null
```

- ✅ 已保留上一版本 `6cf4a07d1-prod` 的 assets
- 旧标签页 `<link rel="preload">` 指向的旧 hash 资源可继续访问 24h

## 验证结果

### 健康检查（部署后）

| 项 | 结果 |
|---|---|
| status | UP |
| livenessState | UP |
| readinessState | UP |
| db | UP（MySQL） |
| redis | UP（version 6.2.19） |
| sidecar | UP（http://localhost:8000） |
| aiProvider | UP（qwen3.7-max） |
| jwt | UP（HMAC-SHA256, 47 bytes） |

### Smoke 测试（经 Nginx 8080 代理）

| 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | 200 | ✅ |
| `/actuator/health/readiness` | 200 UP | 200 | ✅ |
| `/api/auth/login` POST 空 body | 400 | 400 | ✅ |
| `/api/projects` | 403 | 403 | ✅ |
| `/api/integration/crm/health` | 401 | 401 | ✅ |
| `/` 前端首页 | 200 | 200 | ✅ |
| `/login` 前端登录页 | 200 | 200 | ✅ |

- 前端入口：`assets/index-Nwjpxa6o.js`（与 release 一致）✅

### 迁移应用验证

| 版本 | 描述 | success | installed_on |
|---|---|---|---|
| 1182 | remove unused form definitions | 1 | 2026-08-04 23:21:24 |
| 1183 | add custom fields to project tables | 1 | 2026-08-04 23:21:24 |
| 1184 | create performance export task | 1 | 2026-08-04 23:21:24 |

3 个新迁移全部成功应用 ✅

## GitHub 镜像同步

| 项 | 值 |
|---|---|
| 部署前落后 | 2 commits |
| 同步命令 | `bash scripts/sync-to-github.sh` |
| 同步结果 | ✅ 两边 main 完全一致（HEAD: a3ecaa47b） |

## 回滚信息

| 项 | 值 |
|---|---|
| 回滚状态 | ✅ Ready（未需要） |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/6cf4a07d1-prod/` |
| 上一版本 jar | `/opt/xiyu-bid/releases/6cf4a07d1-prod/backend/app.jar` |
| 上一版本前端 | `/opt/xiyu-bid/releases/6cf4a07d1-prod/frontend/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-a3ecaa47b-prod-<timestamp>.sql.gz` |
| 回滚脚本 | U1182, U1183, U1184（均位于 `db/rollback/migration-mysql/`） |

### 回滚步骤（如需）

1. 停止后端服务：`sudo systemctl stop xiyu-bid-backend`
2. 恢复上一版本 jar：`cp /opt/xiyu-bid/releases/6cf4a07d1-prod/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar`
3. 恢复上一版本前端：`sudo cp -r /opt/xiyu-bid/releases/6cf4a07d1-prod/frontend/* /srv/www/xiyu-bid/`
4. 逆向执行 U1184 → U1183 → U1182（按版本号倒序）
5. 启动后端：`sudo systemctl start xiyu-bid-backend`
6. 健康检查：`curl http://127.0.0.1:18080/actuator/health`

## 经验沉淀应用情况

| 经验条目 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行（validate + DB 版本对比 + remote-deploy 内置） |
| OBS 直传双保险 | ✅ 打包显式 `VITE_OBS_ENABLED=true` + 产物校验 `obsEnabled=true` |
| 同源构建 baseURL="" | ✅ 显式 `VITE_API_BASE_URL=` |
| COPYFILE_DISABLE=1 | ✅ 防 macOS `._*` 残留 |
| SYSTEMCTL_SUDO=true | ✅ 防 `Interactive authentication required` |
| 前端资源保留防 404 | ✅ 从上一版本 release 目录 `cp -rn` 旧 assets |
| Smoke 测试 400/403/401 | ✅ admin 密码未知用路由验证替代 |
| Mac HTTP_PROXY 502 | ✅ 服务器内部 curl 绕过 |
| 临时配置清理检查 | ⚠️ 发现 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决定保留，第 13/14/15 次用户决定） |

## 风险提示

| 风险 | 等级 | 说明 |
|---|---|---|
| V1182 删除废弃表单定义 | 🟡 低 | DELETE 操作但幂等，U1182 已修复为 INSERT 前 DELETE |
| V1183 加列非破坏 | 🟢 极低 | ALTER TABLE ADD COLUMN JSON，不影响现有数据 |
| V1184 新建表 | 🟢 极低 | CREATE TABLE，首次使用待验证 |
| 业绩合订本导出首次使用 | 🟡 中 | V1184 新表首次写入，建议端到端验证 |
| 项目三表单自定义字段 | 🟡 中 | V1183 新字段首次使用，建议验证表单设计器 |
| 部门实时反查覆盖快照 | 🟡 中 | CO-602 调岗场景修复，需验证 10323/06442 |

## 部署确认清单

- [x] 环境门禁通过（用户确认部署到生产 172.16.10.149）
- [x] 早操三连完成（sync-env + check-git-wrapper）
- [x] 基线确认（HEAD = origin/main = a3ecaa47b）
- [x] 服务器现状检查（健康 UP，systemd 运行 11h）
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（161M，obsEnabled=true）
- [x] 产物校验通过（jar 无重复 V*.sql，前端入口一致）
- [x] 上传 + 部署成功（健康检查 15 次/3 次连续通过）
- [x] 前端资源保留（从 6cf4a07d1-prod 复制旧 assets）
- [x] 迁移应用验证（V1182/V1183/V1184 全部 success=1）
- [x] Smoke 测试 7 项全部通过
- [x] GitHub 镜像同步（两边 main 完全一致）
- [x] 配置清理检查（SHOW_DETAILS=always 历史保留）
- [x] 回滚就绪（旧 release + DB 备份 + U1182/U1183/U1184）

## 待用户验证事项

> 部署已完成，以下事项建议用户在生产环境实际验证：

1. **CO-602 调岗场景部门显示**（PR !2257 修复）
   - 工号 10323（周子靖）访问 `/project/29`，部门应显示"央企BD部"而非"客户开发部"
   - 工号 06442（刘向博）项目页面部门应显示当前实时部门
2. **业绩合订本 Word 导出**（PR !2250，V1184 新表首次使用）
   - 端到端验证导出流程，确认 `performance_export_task` 表正常写入
3. **项目三表单自定义字段**（CO-601，V1183 新字段）
   - 验证表单设计器预置字段锁定 + 自定义字段扩展功能
4. **组织管理页部门树渲染性能**（PR !2265 优化）
   - 验证页面加载速度（节点数从 22795 降至 800）
5. **组织管理页部门来源下拉**（PR !2263）
   - 确认 ehsy 历史选项已清理，仅保留 OSS 来源
