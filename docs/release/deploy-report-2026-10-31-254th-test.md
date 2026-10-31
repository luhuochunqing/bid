# 测试环境第 254 次部署报告

## 部署基本信息
- **部署时间**: 2026-10-31 21:46:45 CST（激活）→ 21:49 前后健康检查通过（约 2.5 分钟，Kafka readiness 延迟属已知行为）
- **部署环境**: 测试环境（winbid-01.test: `172.16.38.78:8080`）
- **发布版本 ID**: `09ccb7a84`
- **提交 Commit**: `09ccb7a84`（!2944）
- **上一版本**: `75b689638-api8080`（2026-10-31 16:01 CST 激活）
- **部署方式**: 按 `xiyu-server-deploy` SOP 手工执行 `package-release.sh` + `remote-deploy.sh`（固化 `VITE_API_BASE_URL=""`、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`）
- **增量规模**: 4 个 commit（含 !2943 文档报告 + !2944 合规修复）

---

## 变更核心内容
1. **响应性检查误判修复（!2944）**
   - `ResponsivenessReviewPolicy`、`ResponsivenessRuleCitation`、`SemanticRulesLane` 调整，排除“招标规则引用”被误判为负偏离。
   - 回归测试：`Report147ResponsivenessRegressionTest`。
2. **规格与知识沉淀**
   - 新增 `specs/20261031-responsive-evidence/`（spec + plan + tasks + 基线失败样本 + 豁免说明）。
   - `.wiki/pages/bid-quality-check-evidence-conflicts.md` 与 `_index`、`.wiki/log.md` 回填；`docs/lessons/lessons-learned.md` 追加；`compliance/README.md` 同步。
3. **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225（`score estimate freshness`）。
4. **前端**: 本次无前端源码改动，构建产物 hash 与上一版本完全一致（`assets/index-xSKpZ2Mv.js`，193 个 assets）。

---

## 部署前预检
- **Flyway validate（服务器）**: `VALIDATE OK - all checksums match`（285 个迁移）
- **DB 已应用版本**: V1225（`2026-10-26 20:01:47`），与源码最新版本一致，无 pending 迁移
- **服务器健康（部署前）**: `/actuator/health` UP，`xiyu-bid-backend` active（PID 32703，已运行 5h41m）

## 部署执行
- 本地打包：前端同源构建 + `FlywayMysqlContainerTest` 门禁 + `mvn clean -DskipTests package`（BUILD SUCCESS，37.7s）
- 产物校验：jar 内 284 个 `V*.sql` 无重复；`apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false`；Detail chunk `.upload(` 调用数 = 2
- 上传：`scp` 归档 + `remote-deploy.sh` + `smoke-sidecar.sh` + 1371 fixture；归档 MD5 一致（`57631f0c14dc67b5495e8e4eecaa4880`）
- 远程部署：数据库备份 `db-backups/winbid-09ccb7a84-20261031214635.sql.gz`（15 MB）→ Sidecar 内容指纹一致（`e5fc03f3…`）跳过容器重建 → AI Provider 守卫通过 → Flyway validate 通过 → 原子切换 jar → 重启服务
- 旧版本 assets 保留：`cp -rn 75b689638-api8080/frontend/assets → /srv/www/xiyu-bid/assets`（新旧 hash 完全一致，幂等 no-op）
- `._*` 元数据残留：0 个

## 部署后验证
> 以下数值取证于 2026-10-31 21:46–21:52 CST（远程部署脚本输出 + 部署后独立 ssh/curl/SQL 复核）。

| 检查项 | 结果 |
|---|---|
| `/actuator/health`（服务器本地） | UP（db / redis / sidecar / aiProvider 均 UP） |
| `/actuator/health`（经 Nginx 8080） | HTTP 200 |
| `/actuator/health/readiness` | HTTP 200 |
| `POST /api/auth/login`（空体） | HTTP 400（参数校验，符合预期） |
| `GET /api/projects`（未认证） | HTTP 401 |
| `GET /api/integration/crm/health`（未认证） | HTTP 401 |
| 前端 `/`、`/login` | HTTP 200 / 200 |
| 前端入口 hash | `assets/index-xSKpZ2Mv.js`（与 release 目录一致） |
| Sidecar 冒烟（`smoke-sidecar.sh`） | 5/5 通过（fixture v1 回放 tables=14 cells=8215、v1/v2 探针） |
| 服务状态 | `xiyu-bid-backend` active（PID 21966） |
| 后端启动后 ERROR 日志 | 无（`journalctl -p err` 无条目） |
| DB 迁移 | V1225 success=1（无新增） |
| 健康检查等待 | 连续 3/3 通过（脚本 79 次尝试，约 2.5 分钟） |
| 公共 assets / release assets | 193 / 193（一致） |

## 临时配置检查
- `/etc/xiyu-bid/backend.env` 存在 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留项，非本次新增），无 `DEBUG` / `TRACE` 临时配置。

## 回滚信息
- 上一版本目录：`/opt/xiyu-bid/releases/75b689638-api8080`
- 数据库备份：`/opt/xiyu-bid/db-backups/winbid-09ccb7a84-20261031214635.sql.gz`
- 本次无 schema 变更、无前端产物变更，回滚只需切回上一版本 jar（前端目录可直接复用当前 hash）

## 风险提示
- 本次为合规响应性判定修复（招标规则引用排除），影响评分标准响应性复核链路，重点回归点为合规响应性检查结论是否不再误报负偏离。
- 测试环境仍保留健康详情暴露（`SHOW_DETAILS=always`），生产环境不得照搬该配置。

## 结论
- **部署级验收**: ✅ 通过（健康检查、接口路由、前端资源一致性、Sidecar 冒烟均正常）
- **未覆盖项**: 未执行浏览器端业务功能回归（本次为后端合规判定修复），建议由业务方在测试环境按回归清单验证响应性检查结论。
- **部署状态**: 已成功交付测试环境 `http://172.16.38.78:8080/`
