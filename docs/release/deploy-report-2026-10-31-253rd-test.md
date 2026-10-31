# 测试环境第 253 次部署报告

## 部署基本信息
- **部署时间**: 2026-10-31 16:01:31 CST（激活）→ 16:04:11 CST（校验完成）
- **部署环境**: 测试环境（winbid-01.test: `172.16.38.78:8080`）
- **发布版本 ID**: `75b689638-api8080`
- **提交 Commit**: `75b689638`（!2942）
- **上一版本**: `2a0388e51-api8080`（2026-10-31 09:01 CST 激活）
- **部署脚本**: `ENV=test XYYU_SYNC_ENV_ACTIVE=1 XIYU_DEV_CONFIRMED=1 bash scripts/release/deploy-test.sh`
- **增量规模**: 6 个 commit

---

## 变更核心内容
1. **资格证书与偏离证据判定校准（!2942 / !2941 系列）**
   - 新增 `DeviationEvidencePolicy`，把偏离表证据判定从 `DeviationTableExtractor` 中拆分出来，抽取器只负责取表。
   - 新增 `QualificationPlaceholderPolicy`（占位资质判定）与 `QualificationClauseRolePolicy`（条款角色判定），收敛资格条件语义边界。
   - 调整 `CertificateRequirementPolicy`、`QualificationMatchPolicy`、`QualificationDetailReconciler`、`ResponsivenessReviewPolicy`，分离资格条件、证书适用性与材料事实三层判定。
   - 新增 `RuleCitationContext`（规则引用上下文），配套 `SemanticEvidenceBoundary` 收窄。
2. **回归测试补强**
   - 新增 `R6SemanticBoundaryTest`、`R7EvidenceRoleTest`、`R8EvidenceBindingTest`、`R9CertificateBindingTest`、`R10IsoBindingTest`、`QualityEvidenceContextRegressionTest`、`CertificatePenaltyContextTest`、`BidQualityCheckAsyncExecutorTest`。
3. **文档与规格**
   - 新增 `specs/20261032-quality-evidence-semantics/`（plan + tasks），`.wiki/pages/quality-evidence-boundary.md` 回填，`docs/lessons/lessons-learned.md` 追加。
4. **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225（`score estimate freshness`）。
5. **前端**: 本次无前端源码改动，构建产物 hash 与上一版本一致（`assets/index-xSKpZ2Mv.js`）。

---

## 部署前预检
- **Flyway validate**: `VALIDATE OK - all checksums match`（285 个迁移）
- **DB 已应用版本**: V1225（`2026-10-26 20:01:47` 应用），与源码最新版本一致，无 pending 迁移
- **服务器健康**: `/actuator/health` UP

## 部署执行
- 打包：`mvn clean -DskipTests package` + 前端同源构建（`VITE_API_BASE_URL=""`）
- 固化项校验：`obsEnabled=true`、`apiBaseUrl=""`、`sentryEnabled=false`；Detail chunk `.upload(` 调用数 = 2（OBS 直传生效）
- 上传 + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`），激活前自动 validate 并备份数据库：
  `db-backups/winbid-75b689638-api8080-20261031160121.sql.gz`（15.7 MB）
- 前端目录属主：检测到 5 个非 jetty 属主条目，脚本自动 `sudo chown -R` 修复后激活
- 前端入口一致性：`index.html` → `assets/index-xSKpZ2Mv.js`，与 release 目录一致
- 旧版本 assets 保留：`cp -rn 2a0388e51-api8080/frontend/assets → /srv/www/xiyu-bid/assets`，新旧资产 193/193 完全重合（无前端改动，保留为幂等 no-op），属主统一为 jetty
- `._*` 元数据残留：0 个

## 部署后验证
> 以下数值取证于 2026-10-31 16:04–16:07 CST（部署脚本输出 + 部署后独立 curl/SQL 复核）。

| 检查项 | 结果 |
|---|---|
| `/actuator/health`（经 Nginx 8080） | HTTP 200 |
| `/actuator/health/readiness` | HTTP 200 |
| `POST /api/auth/login`（空体） | HTTP 400（参数校验，符合预期） |
| `GET /api/projects`（未认证） | HTTP 401 |
| `GET /api/integration/crm/health`（未认证） | HTTP 401 |
| 前端 `/`、`/login` | HTTP 200 / 200 |
| 前端入口 hash | `assets/index-xSKpZ2Mv.js`（与 release 一致） |
| Sidecar 冒烟（`smoke-sidecar.sh`） | 5/5 通过（指纹 2.2、fixture 回放、v1/v2 探针） |
| 服务状态 | `xiyu-bid-backend` active（PID 32703） |
| 后端启动后 ERROR 日志 | 无（`journalctl -p err` 无条目） |
| DB 迁移 | V1225 success=1（无新增） |
| 健康检查等待 | 连续 3/3 通过（共 80 次尝试，无 Kafka readiness 阻塞） |
| 旧 assets 保留 | 公共目录 193 个，与新版本一致 |

## 临时配置检查
- `/etc/xiyu-bid/backend.env` 存在 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留项，非本次新增），无 `DEBUG` / `TRACE` 临时配置。

## 回滚信息
- 上一版本目录：`/opt/xiyu-bid/releases/2a0388e51-api8080`
- 数据库备份：`/opt/xiyu-bid/db-backups/winbid-75b689638-api8080-20261031160121.sql.gz`
- 本次无 schema 变更、无前端产物变更，回滚只需切回上一版本 jar（前端目录可直接复用当前 hash）

## 风险提示
- 本次为合规资格/偏离证据判定重构，影响评分标准解析与复核链路，重点回归点为评分标准解析结果、资格条件与证件绑定的展示、偏离表证据判定。
- 测试环境仍保留健康详情暴露（`SHOW_DETAILS=always`），生产环境不得照搬该配置。
- 部署变量注意：sync-env 门禁豁免变量为 `XYYU_SYNC_ENV_ACTIVE`（双 Y），误写成 `XIYU_SYNC_ENV_ACTIVE` 会被锚点分支门禁拦截。

## 结论
- **部署级验收**: ✅ 通过（健康检查、接口路由、前端资源一致性与 Sidecar 冒烟均正常）
- **未覆盖项**: 本次未执行浏览器端业务功能回归（增量以合规判定后端逻辑为主），建议由业务方在测试环境按回归清单验证评分标准解析/复核与偏离证据判定。
- **部署状态**: 已成功交付测试环境 `http://172.16.38.78:8080/`
