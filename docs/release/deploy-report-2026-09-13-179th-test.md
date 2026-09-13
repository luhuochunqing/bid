# 第 179 次部署报告（test 环境）

**日期**：2026-09-13  
**环境**：test（172.16.38.78 / winbid-01）  
**Release ID**：`693e7e0cc8f8-api8080`  
**状态**：✅ 成功

## 部署概览

本次从第 178 次版本 `4f0dd644e` 更新至 `693e7e0cc8f8`，无新增 Flyway 迁移。

主要变更：

- `fix: 复核完成后关闭待人工复核提示`（PR !2642，`d3ec3f42e`、`5486217f4`）。
- 合入第 178 次测试环境部署报告及相关 Wiki/经验记录（`1a92062a7`、`693e7e0cc`）。

## 基线信息

| 项目 | 值 |
| --- | --- |
| 当前分支 | `agent/trae-init` |
| 基线 commit | `693e7e0cc8f8`（与 `origin/main` 一致） |
| 前次部署 | `4f0dd644e`（第 178 次） |
| 增量范围 | `4f0dd644e..693e7e0cc8f8` |
| 工作区 | 部署前干净 |
| 发布入口 | `http://172.16.38.78:8080/` |
| 后端健康地址 | `http://127.0.0.1:18080/actuator/health` |

## 改动范围

- 前端：复核完成状态提示关闭逻辑及对应测试。
- 文档/Wiki：第 178 次部署报告、Wiki 日志和 scoreparse 规格记录。
- 数据库：无 migration-mysql 文件变更。

## Flyway 预检

- ✅ 服务器 validate：274 个迁移全部 checksum 匹配。
- ✅ 数据库最新成功迁移：V1214 `track opportunity event publish`。
- ✅ 本次无新增迁移，无需执行数据库变更。

## 部署步骤

1. 通过测试环境门禁，确认目标 `172.16.38.78`。
2. 在锚点分支同步并确认 `HEAD=origin/main=693e7e0cc8f8`，工作区干净。
3. 使用 `deploy-test.sh` 打包：同源 API、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`。
4. Spring 全量上下文/Flyway 容器测试通过；JAR 内迁移版本无重复。
5. 上传 release archive、remote-deploy 脚本和 Sidecar smoke 夹具，执行数据库备份、Flyway validate、原子激活和服务重启。
6. 从上一版本 `4f0dd644e` 保留旧前端 hash assets。

## 验证结果

| 检查项 | 结果 |
| --- | --- |
| 本地前端构建 | ✅ 通过 |
| Spring 全量上下文/Flyway 容器测试 | ✅ 通过 |
| 后端健康检查 | ✅ UP；连续 3/3，累计第 80 次探测 |
| Flyway validate | ✅ 274 个迁移 checksum 匹配 |
| readiness | ✅ HTTP 200 |
| 空登录 `/api/auth/login` | ✅ HTTP 400 |
| `/api/projects` | ✅ HTTP 403 |
| `/api/integration/crm/health` | ✅ HTTP 401 |
| 前端 `/` | ✅ HTTP 200 |
| 前端 `/login` | ✅ HTTP 200 |
| 前端入口 | ✅ `assets/index-DdB8fGC8.js` 与 release 一致 |
| OBS 直传 | ✅ `Detail` chunk `.upload(` 调用数=2，metadata `obsEnabled=true` |
| Sidecar release smoke | ✅ 5/5 通过；schema v2.0 |
| gzip/静态缓存 | ✅ gzip；`public, immutable`，max-age=31536000 |
| 前端旧资源保留 | ✅ 上一版本 assets 已保留 |

## 回滚信息

- **回滚状态**：ready，未触发。
- **后端/前端回滚锚点**：上一版本 release `4f0dd644e`。
- **数据库回滚**：本次无新增迁移；部署脚本已执行数据库备份，文件位于服务器备份目录，未记录敏感信息。
- **回滚责任人**：待定。

## 风险提示

- SSH 输出提示当前未使用 post-quantum key exchange；不影响本次部署结果，建议后续升级服务器 SSH 能力。
- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 为历史保留配置，本次未新增，已完成检查。
- 本次未执行带管理员密码的完整登录/UAT smoke；已使用无凭据路由与权限 smoke 替代验证。

## 部署确认清单

- [x] 环境门禁确认 test
- [x] 早操同步、Git wrapper 检查、基线确认
- [x] 服务器现状和增量确认
- [x] Flyway validate 与数据库版本确认
- [x] 本地构建、容器测试、JAR/OBS 产物校验
- [x] 数据库备份、上传、remote-deploy
- [x] 前端旧 hash assets 保留
- [x] 健康、readiness、API、前端、Sidecar smoke
- [x] 调试配置检查
- [x] 回滚锚点确认
