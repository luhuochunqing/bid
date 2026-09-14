# 第 182 次部署报告（test 环境）

**日期**：2026-09-14
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`8605e0bd6`
**状态**：✅ 成功

## 部署概览

本次从第 181 次版本 `888cb83c4` 更新至 `8605e0bd6`，无新增 Flyway 迁移。

主要变更：

- PR !2653：电子标 CA 证书质检项纠偏——以知识库台账为主导并解除标书贴图约束（含合规质检策略调整、Wiki 与教训沉淀）。
- 合入第 181 次部署报告。

## 基线信息

| 项目 | 值 |
| --- | --- |
| 当前分支 | `agent/trae-init`（锚点，ff-only 同步） |
| 基线 commit | `8605e0bd6`（与 `origin/main` 一致） |
| 前次部署 | `888cb83c4` |
| 增量范围 | `888cb83c4..8605e0bd6`（5 个 commit，含 2 个部署报告文档 commit） |
| 工作区 | 部署前后干净 |
| 发布入口 | `http://172.16.38.78:8080/` |
| 后端健康地址 | `http://127.0.0.1:18080/actuator/health` |

## 改动范围

- 后端：compliance 模块 CA 证书质检策略调整（`AdjudicationGuidelines`、`BidDocumentQualityEngine`、`CaCertificateCheckPolicy`），纠偏质检项以知识库台账为主导并解除标书贴图约束；相关测试更新。
- 文档/Wiki：CA 证书质检项纠偏台账核验规范、lessons-learned 沉淀。
- 数据库：无 `migration-mysql` 文件变更，未执行新迁移。
- 前端：本次无 `src/` 前端业务源码变更。

## Flyway 预检

- ✅ 部署前服务器 validate：274 个迁移全部 checksum 匹配（VALIDATE OK）。
- ✅ 数据库最新成功迁移：V1214 `track opportunity event publish`（服务已应用到源码一致版本，无待应用迁移）。
- ✅ remote-deploy 激活前再次 validate 通过。
- ✅ 本次无新增迁移，无需数据库 schema 变更。

## 部署步骤

1. 通过测试环境门禁，确认目标 `172.16.38.78`。
2. 早操：基线确认 `origin/main=agent/trae-init=8605e0bd6`，工作区干净。
3. 使用 `package-release.sh` 打包：`RELEASE_ID=8605e0bd6`、前端同源 API（`VITE_API_BASE_URL=`）、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`。
4. 打包校验通过：Spring repackage BUILD SUCCESS、JAR 内迁移版本无重复、OBS 直传启用（`.upload(` 调用数=2、`obsEnabled=true`）、前端入口 `index-BTozBZ0v.js`。
5. 上传 `xiyu-bid-release-8605e0bd6.tar.gz`（155MB）与 `remote-deploy.sh`，执行 `SYSTEMCTL_SUDO=true` 远端部署：DB 备份 → Flyway validate → 前端/后端原子激活 → 后端重启。
6. 前端资源保留：本次无 `src/` 前端源码变更，前端 chunks hash 不变，跨版本 404 风险极低，跳过旧资源保留。

## 验证结果

| 检查项 | 结果 |
| --- | --- |
| 前端构建 | ✅ 通过 |
| Spring repackage（BUILD SUCCESS） | ✅ 通过 |
| 后端服务 | ✅ `active`（`active/running`） |
| 后端健康检查 | ✅ UP；连续 3/3，累计第 79 次探测 |
| readiness 经 Nginx | ✅ HTTP 200 |
| 空登录 `/api/auth/login` | ✅ HTTP 400（参数校验） |
| `/api/projects` | ✅ HTTP 403 |
| `/api/integration/crm/health` | ✅ HTTP 401 |
| 前端 `/` | ✅ HTTP 200 |
| 前端入口 | ✅ `assets/index-BTozBZ0v.js` 与 release 一致 |
| OBS 直传 | ✅ `Detail` chunk `.upload(` 调用数=2，metadata `obsEnabled=true` |
| Sidecar | ✅ 打包 schema v2.0 |

## 回滚信息

- **回滚状态**：ready，未触发。
- **后端/前端回滚锚点**：上一版本 release `888cb83c4`。
- **数据库回滚**：本次无新增迁移；部署前备份已生成：`/opt/xiyu-bid/db-backups/winbid-8605e0bd6-*.sql.gz`。
- **回滚责任人**：待定。

## 风险提示

- SSH 输出提示当前未使用 post-quantum key exchange；不影响本次部署结果，建议后续升级服务器 SSH 能力。
- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 为历史保留配置，本次未新增；未发现 `DEBUG/TRACE` 临时配置。
- 未执行带管理员密码的完整登录/UAT smoke，已使用无凭据路由与权限 smoke 替代验证。

## 部署确认清单

- [x] 环境门禁确认 test
- [x] 基线、工作区和 Git wrapper 检查
- [x] 服务器现状和增量确认
- [x] Flyway validate 与数据库版本确认
- [x] 本地构建、JAR/OBS 产物校验
- [x] 数据库备份、上传、remote-deploy
- [x] 健康、readiness、API、前端 smoke
- [x] 调试配置检查
- [x] 回滚锚点确认