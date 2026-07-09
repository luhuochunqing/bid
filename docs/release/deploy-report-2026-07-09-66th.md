# 第 66 次部署报告 — 测试环境

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | test |
| 主机 | winbid-01 / 172.16.38.78 |
| Release ID | `b1304462f-api8080` |
| 部署时间 | 2026-07-09 18:15:05 CST |
| 部署人 | trae agent |
| 特殊说明 | 第 65 次部署回滚后的修复验证部署 |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae/deploy-test-2026-07-09` |
| HEAD commit | `b1304462f` |
| origin/main | `b1304462f`（同步） |
| 上次部署 releaseId | `22638f08a-api8080-obs`（第 64 次，回滚后保留） |
| 增量 commit 数 | 13 |
| 增量 PR 数 | 3（!1947-!1952） |

## PR 列表

| PR | 标题 | 类型 |
|---|---|---|
| !1947 | feat(CO-566): CA信息管理 — 关联平台改为文本 + CA密码改为非必填 | feature |
| !1948 | fix(permission): 补全 OSS 菜单映射缺失的 retrospective.submit 权限键（CO-560） | bugfix |
| !1949 | fix(oss): merge catalog menu permissions for whitelisted OSS users to prevent 429 infinite redirect | bugfix |
| !1950 | fix(integration): CRM 推标创建前校验商机号占用，避免唯一索引 500 | bugfix |
| !1951 | fix(router): handle Vite dynamically imported module 404 after deployment | bugfix |
| !1952 | fix(obs): 下载 ObsClient 启用 path-style，修复自定义域名 SSL 证书不匹配 | bugfix |

## 改动范围

- **新增迁移**：V1161__ca_related_platforms_text.sql（CO-566，ca_certificates 新增 related_platforms 文本列 + 回填 + 删除遗留 platform_ids 列）
- **OSS 登录修复**：PR !1949 合并白名单用户 catalog 菜单权限（修复第 65 次的 06234 登录被拒问题）
- **前端**：Vite 动态导入 404 处理（PR !1951，避免部署后浏览器缓存导致白屏）
- **权限**：补全 retrospective.submit 等权限键映射
- **OBS**：ObsClient path-style 启用，修复自定义域名 SSL

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - all checksums match（223 migrations） |
| Step 2: DB 已应用版本 | V1160（最新），V1161 待应用 |
| Step 3: remote-deploy 内置 validate | ✅ VALIDATE OK - all checksums match |

## 部署步骤

1. ✅ 环境门禁确认（test / 172.16.38.78）
2. ✅ 同步最新代码（rebase origin/main，HEAD = b1304462f）
3. ✅ Flyway 预检 3 步法全部通过
4. ✅ 本地打包（RELEASE_ID=b1304462f-api8080，VITE_API_BASE_URL= 同源构建）
5. ✅ 产物校验（jar 内 223 个迁移文件无重复，V1161 存在，前端入口 index-DOHcYYOZ.js）
6. ✅ 上传 + 部署（scp + remote-deploy.sh SYSTEMCTL_SUDO=true）
7. ⚠️ 健康检查首次失败（Kafka SDK readiness 延迟，已知行为经验 #2）
8. ✅ 等待后健康检查通过（readiness UP）

## 验证结果

### 后端健康检查

| 组件 | 状态 |
|---|---|
| overall | UP |
| aiProvider | UP |
| db | UP |
| diskSpace | UP |
| jwt | UP |
| livenessState | UP |
| readinessState | UP |
| redis | UP |
| sidecar | UP |

### 迁移应用验证

| version | description | success | installed_on |
|---|---|---|---|
| 1160 | platform account password nullable | 1 | 2026-07-09 17:37:20 |
| 1161 | ca related platforms text | 1 | 2026-07-09 18:15:12 |

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
| 前端入口 | assets/index-DOHcYYOZ.js（与 release 一致）✅ |

### 06234 登录修复验证

| 项目 | 修复前（第 65 次） | 修复后（本次） |
|---|---|---|
| roleCode | `bid-SystemAdmin` ❌ | `admin` ✅ |
| 登录结果 | 403 拒绝（role not allowed） | 成功 |
| 权限构建 | 失败（缓存被清除） | 43 个 authority 正常构建 |
| 业务操作 | 无法登录 | 18:16:24 成功执行 CRM 推标 webhook |

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 同步前落后 | 15 commit |
| 同步后状态 | ✅ 完全一致（Gitee main = GitHub main = b1304462f） |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚 releaseId | `22638f08a-api8080-obs`（第 64 次） |
| 回滚 releaseDir | `/opt/xiyu-bid/releases/22638f08a-api8080-obs` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-b1304462f-api8080-*.sql.gz` |

## 经验沉淀应用情况

| 经验 | 本次应用 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ 全部执行 |
| #2 Kafka SDK readiness 延迟 | ✅ 容忍并等待恢复 |
| #3 生产前端同源构建（baseURL=""） | ✅ VITE_API_BASE_URL= 显式设空 |
| #8 systemctl sudo 权限 | ✅ SYSTEMCTL_SUDO=true |
| #16 Mac HTTP_PROXY 502 | ✅ curl --noproxy '*' |

## 风险提示

- V1161 含 DROP COLUMN `platform_ids`（破坏性，但 V1073 起已废弃，无代码读写）
- V1161 rollback 脚本 U1161 缺失（需补全）
- 健康检查首次失败是 Kafka SDK 延迟（已知行为，非实际故障）

## 部署确认清单

- [x] 环境门禁确认
- [x] 同步最新代码
- [x] Flyway 预检通过
- [x] 本地打包成功
- [x] 产物校验通过
- [x] 上传 + 部署成功
- [x] 健康检查通过（容忍 Kafka 延迟）
- [x] 迁移应用验证通过（V1161）
- [x] Smoke 测试通过
- [x] 前端一致性验证通过
- [x] 06234 登录修复验证通过
- [x] GitHub 镜像同步
- [x] 部署报告生成
