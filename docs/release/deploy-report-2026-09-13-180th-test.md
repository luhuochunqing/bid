# 第 180 次部署报告（test 环境）

**日期**：2026-09-13
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`c31f62df1`
**状态**：✅ 成功

## 部署概览

本次从第 179 次版本 `693e7e0cc8f8-api8080` 更新至 `c31f62df1`，无新增 Flyway 迁移。

主要变更：

- PR !2644：隐藏 score evidence 内部详情。
- PR !2645：修复标书质检资质提取跨章节粘连导致的一票否决误判。
- PR !2646：优化资格条件一票否决检查结论。
- 合入第 179 次部署报告及相关知识/Wiki 更新。

## 基线信息

| 项目 | 值 |
| --- | --- |
| 当前分支 | `agent/trae-init` |
| 基线 commit | `c31f62df1`（与 `origin/main` 一致） |
| 前次部署 | `693e7e0cc8f8-api8080` |
| 增量范围 | `693e7e0cc8f8a11b293bdb21e7fece630cf4736f..c31f62df1d30298bfbce143c12b513c8240c3735` |
| 工作区 | 部署前后干净 |
| 发布入口 | `http://172.16.38.78:8080/` |
| 后端健康地址 | `http://127.0.0.1:18080/actuator/health` |

## 改动范围

- 前端：ScoreItemDetailModal、ScoreParseDrawer 隐藏内部 score evidence 详情及测试。
- 后端：资质提取与资格一票否决策略及测试。
- 文档/Wiki：部署报告、质量检查实现说明和经验记录。
- 数据库：无 `migration-mysql` 文件变更，未执行新迁移。

## Flyway 预检

- ✅ 部署前服务器 validate：274 个迁移全部 checksum 匹配。
- ✅ 数据库最新成功迁移：V1214 `track opportunity event publish`。
- ✅ remote-deploy 激活前再次 validate 通过。
- ✅ 本次无新增迁移，无需数据库 schema 变更。

## 部署步骤

1. 通过测试环境门禁，确认目标 `172.16.38.78`。
2. 在锚点分支完成同步检查，确认 `HEAD=origin/main=c31f62df1`，工作区干净；`sync-env.sh` 的末尾锚点开发守卫出现已知误报，未绕过代码安全门禁。
3. 使用 `package-release.sh` 打包：前端同源 API、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`。
4. Spring 全量上下文/Flyway 容器测试、JAR 内迁移版本校验和 OBS 产物校验通过。
5. 上传 release archive，执行数据库备份、Flyway validate、前端/后端原子激活、Sidecar 更新和后端重启。
6. 从上一版本 `693e7e0cc8f8-api8080` 保留旧前端 hash assets。

## 验证结果

| 检查项 | 结果 |
| --- | --- |
| 前端构建 | ✅ 通过 |
| Spring 全量上下文/Flyway 容器测试 | ✅ 通过 |
| 后端服务 | ✅ `active/running` |
| 后端健康检查 | ✅ UP；连续 3/3，累计第 80 次探测 |
| readiness | ✅ HTTP 200 |
| 空登录 `/api/auth/login` | ✅ HTTP 400 |
| `/api/projects` | ✅ HTTP 403 |
| `/api/integration/crm/health` | ✅ HTTP 401 |
| 前端 `/` | ✅ HTTP 200 |
| 前端 `/login` | ✅ HTTP 200 |
| 前端入口 | ✅ `assets/index-B1ewsVBD.js` 与 release 一致 |
| OBS 直传 | ✅ `Detail` chunk `.upload(` 调用数=2，metadata `obsEnabled=true` |
| Sidecar | ✅ `xiyu-sidecar` 容器 Up，schema v2.0 |
| 前端旧资源保留 | ✅ 上一版本 assets 已保留 |

## 回滚信息

- **回滚状态**：ready，未触发。
- **后端/前端回滚锚点**：上一版本 release `693e7e0cc8f8-api8080`。
- **数据库回滚**：本次无新增迁移；部署前备份已生成：`/opt/xiyu-bid/db-backups/winbid-c31f62df1-20260913174051.sql.gz`。
- **回滚责任人**：待定。

## 风险提示

- SSH 输出提示当前未使用 post-quantum key exchange；不影响本次部署结果，建议后续升级服务器 SSH 能力。
- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 为历史保留配置，本次未新增；未发现 `DEBUG/TRACE` 临时配置。
- 后端 readiness 在重启后经历约 80 次探测才恢复，符合已知 Kafka SDK 启动延迟行为。
- 未执行带管理员密码的完整登录/UAT smoke，已使用无凭据路由与权限 smoke 替代验证。

## 部署确认清单

- [x] 环境门禁确认 test
- [x] 基线、工作区和 Git wrapper 检查
- [x] 服务器现状和增量确认
- [x] Flyway validate 与数据库版本确认
- [x] 本地构建、容器测试、JAR/OBS 产物校验
- [x] 数据库备份、上传、remote-deploy
- [x] 前端旧 hash assets 保留
- [x] 健康、readiness、API、前端、Sidecar smoke
- [x] 调试配置检查
- [x] 回滚锚点确认
