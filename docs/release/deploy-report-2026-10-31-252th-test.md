# 测试环境第 252 次部署报告

## 部署基本信息
- **部署时间**: 2026-10-31 09:01:02 CST（激活）→ 09:03:44 CST（校验完成）
- **部署环境**: 测试环境（winbid-01.test: `172.16.38.78:8080`）
- **发布版本 ID**: `2a0388e51-api8080`
- **提交 Commit**: `2a0388e51`（!2940）
- **上一版本**: `0b3854b45-api8080`（2026-10-29 22:15 激活）
- **部署脚本**: `ENV=test XYYU_SYNC_ENV_ACTIVE=1 XIYU_DEV_CONFIRMED=1 bash scripts/release/deploy-test.sh`
- **增量规模**: 65 个 commit

---

## 变更核心内容
1. **合规评分项判定重构（!2940 / !2939 系列）**
   - 分离资格条件、证书适用性与材料事实判定；空资质明细按材料事实裁决；保留证书条件与编号化资质范围。
   - 案例来源回填改为生产安全路径（只读预演 + 审计产物），禁止直接写库。
2. **案例工作台（CO-652，!2938）**
   - 资质证书评分项"一票否决"方案案例，直连企业资质库原件推荐；补充企业资质端口异常降级与正则预编译优化。
3. **标讯模块（!2936）**
   - 修复批量导入校验失败计数漏回写与前端错误明细误隐藏。
4. **案例来源标题丢失根治（!2935）**
   - 持久化 AI 前置来源、跨 AI 匹配保留 provenance、按源偏移定位。
5. **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225（`score estimate freshness`）。

---

## 部署前预检
- **Flyway validate**: `VALIDATE OK - all checksums match`（285 个迁移）
- **DB 已应用版本**: V1225，与源码最新版本一致，无 pending 迁移
- **服务器健康**: `/actuator/health` UP，`/actuator/health/readiness` UP

## 部署执行
- 打包：`mvn clean -DskipTests package` + 前端同源构建（`VITE_API_BASE_URL=""`）
- 固化项校验：`obsEnabled=true`、`apiBaseUrl=""`、`sentryEnabled=false`；Detail chunk `.upload(` 调用数 = 2（OBS 直传生效）
- 上传 + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`），激活前自动 validate 并备份数据库：
  `db-backups/winbid-2a0388e51-api8080-20261031090035.sql.gz`
- 前端入口一致性：`index.html` → `assets/index-xSKpZ2Mv.js`，与 release 目录一致
- 旧版本 assets 保留：从 `0b3854b45-api8080/frontend/assets` 复制 193 个文件（`cp -rn`，不覆盖新版本）
- `._*` 元数据残留：0 个

## 部署后验证
| 检查项 | 结果 |
|---|---|
| `/actuator/health`（经 Nginx 8080） | HTTP 200 |
| `/actuator/health/readiness` | HTTP 200 |
| `POST /api/auth/login`（空体） | HTTP 400（参数校验，符合预期） |
| `GET /api/projects`（未认证） | HTTP 401 |
| `GET /api/integration/crm/health`（未认证） | HTTP 401 |
| 前端 `/`、`/login` | HTTP 200 / 200 |
| Sidecar 冒烟（`smoke-sidecar.sh`） | 5/5 通过（指纹 2.2、fixture 回放、v1/v2 探针） |
| 服务状态 | `xiyu-bid-backend` active |
| DB 迁移 | V1225 success=1（无新增） |
| 健康检查等待 | 连续 3/3 通过（共 80 次尝试，无 Kafka readiness 阻塞） |

## 临时配置检查
- `/etc/xiyu-bid/backend.env` 存在 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留项，非本次新增），无 `DEBUG` / `TRACE` 临时配置。

## 回滚信息
- 上一版本目录：`/opt/xiyu-bid/releases/0b3854b45-api8080`
- 数据库备份：`/opt/xiyu-bid/db-backups/winbid-2a0388e51-api8080-20261031090035.sql.gz`
- 本次无 schema 变更，回滚只需切回上一版本 jar + 前端目录，无需恢复数据库

## 风险提示
- 本次以合规评分判定与案例 provenance 逻辑为主，未见 schema 破坏性变更；重点回归点为评分标准复核/采纳、案例来源展示、标讯批量导入错误明细。
- 测试环境仍保留健康详情暴露（`SHOW_DETAILS=always`），生产环境不得照搬该配置。

## 结论
- **验收结果**: ✅ 通过（PASS）
- **部署状态**: 已成功交付测试环境 `http://172.16.38.78:8080/`
