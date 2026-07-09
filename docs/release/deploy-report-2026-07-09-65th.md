# 第 65 次部署报告 — 测试环境

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | test |
| 主机 | winbid-01 / 172.16.38.78 |
| Release ID | `0d56c8108-api8080` |
| 部署时间 | 2026-07-09 17:37:13 CST |
| 部署人 | trae agent |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae/deploy-test-2026-07-09` |
| HEAD commit | `0d56c8108` |
| origin/main | `0d56c8108`（同步） |
| 上次部署 releaseId | `22638f08a-api8080-obs`（2026-07-09 07:20 UTC） |
| 增量 commit 数 | 20 |
| 增量 PR 数 | 10（!1937 - !1946） |

## PR 列表

| PR | 标题 | 类型 |
|---|---|---|
| !1937 | fix(project): 项目详情页项目类型显示为英文 | bugfix |
| !1938 | fix: 删除按钮防重复点击守卫，修复 DELETE /documents/{id} 404 | bugfix |
| !1939 | fix(oss): resolve silent cache overwrite on menu permission sync failure | bugfix |
| !1940 | feat(CO-567): 平台账户密码字段改为非必填 | feature |
| !1941 | fix(crm): obs-direct URL 转换为 OBS 预签名 URL（自定义域名 widbid-obs.ehsy.com） | bugfix |
| !1942 | fix(xiyu-1e): 修复文档删除并发冲突 + 任务交付物静默跳过可观测性 | bugfix |
| !1943 | fix(task): 任务保存防重复点击守卫，避免重复创建任务 | bugfix |
| !1944 | chore(env): 同步 XIYU_OBS_DOWNLOAD_CUSTOM_DOMAIN 到 .env.example | chore |
| !1945 | fix(oss): prioritize person-to-role mappings during real-time login to prevent whitelist users from being rejected | bugfix |
| !1946 | fix(CO-565): 终态项目下隐藏任务提交审核按钮+异常降级避免Sentry噪声 | bugfix |

## 改动范围

- **新增迁移**：V1160__platform_account_password_nullable.sql（CO-567，ALTER TABLE platform_accounts MODIFY COLUMN password VARCHAR(255) NULL）
- **回滚脚本**：U1160__platform_account_password_nullable.sql（已存在）
- **前端**：防重复点击守卫、项目类型中文显示、终态项目隐藏提交审核按钮
- **后端**：OSS 权限同步修复、文档删除并发冲突修复、CRM obs-direct URL 转换、平台账户密码非必填

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - all checksums match（222 migrations） |
| Step 2: DB 已应用版本 | V1159（最新），V1160 待应用 |
| Step 3: remote-deploy 内置 validate | ✅ VALIDATE OK - all checksums match |

## 部署步骤

1. ✅ 环境门禁确认（test / 172.16.38.78）
2. ✅ 早操三连（dev-env.sh + sync-env.sh + check-git-wrapper.sh）
3. ✅ 基线确认（HEAD = origin/main = 0d56c8108）
4. ✅ 服务器现状核查（health UP，readiness UP）
5. ✅ Flyway 预检 3 步法全部通过
6. ✅ 本地打包（RELEASE_ID=0d56c8108-api8080，VITE_API_BASE_URL= 同源构建）
7. ✅ 产物校验（jar 内 222 个迁移文件无重复，V1160 存在，前端入口 index-DRqS4euz.js）
8. ✅ 上传 + 部署（scp + remote-deploy.sh SYSTEMCTL_SUDO=true）
9. ✅ 健康检查通过（80 次尝试，3/3 连续成功）
10. ✅ 前端一致性验证（index-DRqS4euz.js 匹配）

## 验证结果

### 后端健康检查

| 组件 | 状态 |
|---|---|
| overall | UP |
| aiProvider | UP（qwen3.7-max） |
| db | UP（MySQL） |
| diskSpace | UP（31G free） |
| jwt | UP（STRONG） |
| livenessState | UP |
| readinessState | UP |
| redis | UP（6.2.19） |
| sidecar | UP |

### 迁移应用验证

| version | description | success | installed_on |
|---|---|---|---|
| 1160 | platform account password nullable | 1 | 2026-07-09 17:37:20 |

### Smoke 测试

| 接口 | 预期 | 实际 | 结果 |
|---|---|---|---|
| GET /actuator/health | 200 UP | 200 UP | ✅ |
| GET /actuator/health/readiness | 200 UP | 200 UP | ✅ |
| POST /api/auth/login (空 body) | 400 | 400 | ✅ |
| GET /api/projects (无认证) | 403 | 403 | ✅ |
| GET /api/integration/crm/health | 401 | 401 | ✅ |

### 前端验证

| 检查项 | 结果 |
|---|---|
| 首页 HTTP | 200 ✅ |
| /login HTTP | 200 ✅ |
| 前端入口 | assets/index-DRqS4euz.js（与 release 一致）✅ |

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 同步前落后 | 21 commit |
| 同步后状态 | ✅ 完全一致（Gitee main = GitHub main = 0d56c8108） |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always | 保留 | 用户历史决定（第 13-15 次） |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚 releaseId | `22638f08a-api8080-obs` |
| 回滚 releaseDir | `/opt/xiyu-bid/releases/22638f08a-api8080-obs` |
| 回滚迁移 | U1160__platform_account_password_nullable.sql |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-0d56c8108-api8080-*.sql.gz` |
| 回滚命令 | `ssh jetty@172.16.38.78 'cd /opt/xiyu-bid/releases/22638f08a-api8080-obs && bash rollback.sh'`（如存在）或手动恢复 jar + 前端 |

## 经验沉淀应用情况

| 经验 | 本次应用 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ 全部执行 |
| #3 生产前端同源构建（baseURL=""） | ✅ VITE_API_BASE_URL= 显式设空 |
| #8 systemctl sudo 权限 | ✅ SYSTEMCTL_SUDO=true |
| #12 rollback 脚本命名规范 | ✅ U1160 已存在 |
| #16 Mac HTTP_PROXY 502 | ✅ curl --noproxy '*' |
| #17 SentryAppender | ✅ 无 logback.xml 手动声明 |

## 风险提示

- V1160 是 ALTER COLUMN 允许 NULL（向后兼容，低风险）
- 本次部署无破坏性 schema 变更
- readiness 首次检查即 UP（无 Kafka SDK 延迟）

## 部署确认清单

- [x] 环境门禁确认
- [x] 早操三连完成
- [x] 基线对齐（HEAD = origin/main）
- [x] Flyway 预检通过
- [x] 本地打包成功
- [x] 产物校验通过
- [x] 上传 + 部署成功
- [x] 健康检查通过
- [x] 迁移应用验证通过
- [x] Smoke 测试通过
- [x] 前端一致性验证通过
- [x] GitHub 镜像同步
- [x] 配置清理检查完成
- [x] 部署报告生成
