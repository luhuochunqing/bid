# 第 172 次部署报告（test 环境）

**日期**：2026-09-12
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`3988e4db8`
**部署时间**：14:12 - 14:29 CST
**状态**：✅ 成功

## 部署概览

- 增量多组 commit（自上次部署 `5563df17a`，第 171 次），**无新增 Flyway 迁移**。
- 主要变更：
  - **评分解析（scoreparse）证据管线收敛**（PR !2620 / !2621 / !2622 / !2623）：金标 fixture 回归门禁 + Sidecar 发布闭环；`fix(scoreparse)` 收敛 evidence pipeline 主链路、关闭 review workflow、新增真实语料 golden recorder；docs(wiki) 记录 sidecar schema drift。
  - **3 列表格抽取增强**（PR !2619）：`fix(scoreparse)` 增强 3 列表格内嵌分值提取能力并加固结构化通道防污染。
  - **资质匹配状态校准**（PR !2618）：`fix(compliance)` 修复标书质量核查资质匹配明细未匹配时总项误判通过缺陷，同步校准明细与总项一致性。
  - **超大 PDF OOM 根治**（PR !2616）：`fix(compliance)` PDF 分块流式提取根治 1373 大文件 OOM。
  - **Sidecar 图片证据修复**（PR !2613 / !2614 / !2615）：图片占位符统一 `alt=已附图片`、markdown 图片 token 替换、流式 SAX 补齐图片节点提取，彻底解决超大 docx 丢失身份证/营业执照图片证据。
- 本次**无新增 Flyway 迁移**（DB 最高版本维持 V1214）。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `3988e4db8`（origin/main，早操同步后本地 HEAD，零差异）       |
| 前次部署      | `5563df17a`（2026-09-10 第 171 次部署）                   |
| 增量 commit | `5563df17a..3988e4db8`                               |
| 新增迁移      | 无                                                    |
| 工作区状态     | 锚点分支 `agent/trae-init`，HEAD = origin/main = `3988e4db8`，工作区干净 |
| 早操三连      | HEAD 已 = origin/main（锚点分支 ff-only 同步）；wrapper 未激活（已知锚点环境行为） |

## 增量 commit 变更（`5563df17a..3988e4db8`）

| commit | 来源 | 描述 |
| ----- | --- | --- |
| `b62ec7c3e` | PR !2613 | fix(sidecar): 图片占位符统一使用 alt=已附图片，使合规策略能识别嵌入图片证据 |
| `4d1c0d74f` | PR !2614 | fix(sidecar): 将 markdown 图片 token 替换为【已附图片】纯文本，LLM 正确识别嵌入图片证据 |
| `fa8f4dd1e` | PR !2615 | 合并 !2614 |
| `f0bfba432` | PR !2615 | fix(sidecar): 流式 SAX 解析器补齐图片节点提取，彻底根治超大 docx 丢失身份证/营业执照图片证据 |
| `fa1de3278` | PR !2615 | 合并 !2615 |
| `96292fb46` | PR !2616 | fix(compliance): PDF 分块流式提取根治 1373 大文件 OOM |
| `b13a70e3f` | PR !2616 | docs: 知识沉淀 + wiki 回填（1373 PDF OOM 根治） |
| `8003dac50` | PR !2616 | 合并 !2616 |
| `255566eea` | PR !2617 | docs: wiki 回填 log 行（1373 PDF OOM 根治） |
| `5e38c3136` | PR !2617 | 合并 !2617 |
| `55814e6c9` | PR !2618 | fix(compliance): 修复资质匹配明细未匹配时总项误判通过缺陷 |
| `f99378f2c` | PR !2618 | docs(wiki): 补充资质匹配明细与总项状态一致性校准架构记录 |
| `b5c889d3a` | PR !2618 | 合并 !2618 |
| `a1b3b7c55` | PR !2619 | fix(scoreparse): 增强 3 列表格内嵌分值提取能力并加固结构化通道防污染 |
| `d1a6e2c8d` | PR !2619 | docs(wiki): 补充 3 列表格抽取与结构化通道防污染加固记录 |
| `88cf8c80e` | PR !2619 | 合并 !2619 |
| `8940f1768`/`6b4098957` | workflow | task: impl-01-scoreparse-gate / impl-05-sidecar-gate |
| `ef7036613` | PR !2620 | feat(scoreparse): 金标 fixture 回归门禁 + Sidecar 发布闭环 |
| `b6497f206`/`f7655c5c3` | PR !2621 | fix(scoreparse): 收敛 evidence pipeline 主链路 |
| `255ecdfe4`/`12761a3a5` | PR !2622 | fix(scoreparse): 关闭 review workflow for evidence pipeline |
| `24894bd0b`/`3988e4db8` | PR !2623 | test(scoreparse): 真实语料 golden recorder |

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` **初检失败**：`Detected applied migration not resolved locally: 1214`。根因是服务器 `/tmp/migration-mysql/` 扫描目录过时（缺 V1214，服务器 /tmp 最大 V1212），非 DB 状态问题（已知 skill 坑点 #10）。

**处置**：scp 同步本地 `V1214__track_opportunity_event_publish.sql` 到服务器 `/tmp/migration-mysql/`，重新 validate → **通过**（274 migrations all checksums match）。

**Step 2**：本次增量 `5563df17a..3988e4db8` **无新增迁移**；DB 已应用最高 V1214（2026-09-10 14:17:16 success=1），本地源码含 V1214，版本一致。

**Step 3**：remote-deploy.sh 内置 validate → 通过（日志 `VALIDATE OK - all checksums match`，274 migrations）。

**部署后迁移验证**：DB 最高版本仍为 **V1214**（success=1），无新增迁移，无需回滚。

## 部署步骤时间线（CST）

| 时间     | 步骤                                                                                          |
| -------- | --------------------------------------------------------------------------------------------- |
| 14:12 | 环境门禁（AskUserQuestion 确认 test 172.16.38.78）                                           |
| 14:18 | 早操（HEAD 已 = origin/main = `3988e4db8`，工作区干净）+ 基线确认                          |
| 14:18 | 服务器现状检查（前次 5563df17a UP，health 200，服务 active）                                  |
| 14:19 | 增量 commit 确认（无迁移变更）；Flyway preflight validate 初检失败（/tmp 缺 V1214）          |
| 14:20 | 同步 V1214 到 /tmp/migration-mysql → 修复 preflight → validate 通过（274 migrations）          |
| 14:22 | 本地打包成功：mvn BUILD SUCCESS + jar 迁移无重复 + sidecar（RELEASE_ID=3988e4db8 + VITE_API_BASE_URL= + VITE_OBS_ENABLED=true + COPYFILE_DISABLE=1） |
| 14:23 | 产物校验：release-metadata obsEnabled=true, apiBaseUrl="" 同源；Detail chunk .upload( 调用数=2 |
| 14:23 | 上传 archive（155MB）+ remote-deploy.sh → DB 备份 → Flyway validate → 前端激活 → 原子更新 jar → 重启服务 |
| 14:25 | 健康检查通过（80 次尝试，无 Kafka 延迟）；前端一致性验证 src="/assets/index-DAgz5x1Y.js" |
| 14:26 | 迁移验证：DB 最高版本 V1214，无新增迁移；部署记录更新为 3988e4db8                            |
| 14:28 | Smoke 测试全通过（health/readiness 200, login 400, projects 403, crm/health 401, 前端 / 与 /login 200） |
| 14:29 | 从旧 release(5563df17a) 保留旧 assets（防跨版本 404）；配置文件检查                          |

## 验证结果

| 检查项            | 结果          |
| --------------- | ----------- |
| 后端健康 (/actuator/health) | ✅ 200 UP |
| 后端就绪 (/actuator/health/readiness) | ✅ 200 UP |
| 登录接口 (/api/auth/login, 空密码) | ✅ 400（验证正常） |
| 项目列表 (/api/projects) | ✅ 403（需认证） |
| CRM 集成健康 (/api/integration/crm/health) | ✅ 401（需认证） |
| 前端首页 (/)          | ✅ 200      |
| 前端登录页 (/login)    | ✅ 200      |
| 前端入口 index        | ✅ index-DAgz5x1Y.js 与 release 一致 |
| OBS 直传启用           | ✅ obsEnabled=true（release-metadata 确认）, .upload( 调用数=2 |
| Flyway 迁移           | ✅ 最高 V1214（success=1），无新增迁移 |
| DB 备份              | ✅ winbid-3988e4db8-<timestamp>.sql.gz 已生成（remote-deploy 自动触发） |
| 临时调试配置清理         | ✅ SHOW_DETAILS=always（历史决定保留）；无 DEBUG/TRACE |

## 回滚信息

- **回滚锚点**：前一版本 `5563df17a`（/opt/xiyu-bid/releases/5563df17a-api8080/，jar 与前端资源均保留）
- **DB 备份**：本次部署自动执行 `mysqldump | gzip`，备份位于 `/opt/xiyu-bid/db-backups/`
- **回滚步骤**：`ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && sudo cp /opt/xiyu-bid/releases/5563df17a-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo cp -r /opt/xiyu-bid/releases/5563df17a-api8080/frontend/* /srv/www/xiyu-bid/ && sudo systemctl start xiyu-bid-backend'`
- **DB 回滚**：本次无新增迁移，**无需 DB 回滚**。

## 经验沉淀应用情况

- ✅ Flyway 预检 3 步法（validate + DB 版本对比核对 + remote-deploy 内置二次 validate）
- ✅ **服务器 /tmp/migration-mysql/ 过时处置**（skill 坑点 #10）：同步缺省 V 文件消除误报，而非盲目 repair。
- ✅ OBS 直传 VITE_OBS_ENABLED=true 双保险（打包显式传入 + 产物校验 + release-metadata 二次确认）
- ✅ Mac HTTP_PROXY 绕过（curl --noproxy '*'，本次无复发）
- ✅ 同源构建 VITE_API_BASE_URL=""（apiBaseUrl=""）
- ✅ 前端 hash 资源跨版本 404 防护（部署后从 5563df17a 保留旧 assets）
- ✅ Kafka readiness 延迟（本次无延迟，第 80 次尝试即通过）

## 风险提示

1. **SHOW_DETAILS=always 保留**：health 端点暴露详细组件状态，测试环境可接受（历史多次决定保留）。
2. **比例尺度变更覆盖范围广**：本次增量集中在 scoreparse/PDF OOM/sidecar 图片/资质匹配，均涉合规证据提取与分析链路。建议测试环境实测一次超大标书（含图片证据）+ 资质匹配核查，确认证据保留与总项判定符合预期。
3. **服务器 /tmp/migration-mysql/ 过时**：本次已同步 V1214 修复，但该目录仍可能落后于仓库最新。建议后续部署前一律先核对同步，避免每次误报。

## 部署确认清单

- [x] 环境门禁确认（test 172.16.38.78）
- [x] 早操三连 + 基线确认（HEAD = origin/main = 3988e4db8，工作区干净）
- [x] 服务器现状检查（5563df17a UP）+
- [x] Flyway 预检 3 步法（validate + V1214 版本核对 + remote-deploy 内置；/tmp 过时已同步修复）
- [x] 本地打包（OBS=true, 同源 API, COPYFILE_DISABLE=1）
- [x] 产物校验（jar 迁移无重复 + OBS 启用 + obsEnabled=true + index 一致性）
- [x] 上传 + 远程部署（SYSTEMCTL_SUDO=true, DB 备份）
- [x] 健康检查（UP）
- [x] 迁移应用验证（最高 V1214，无新增）
- [x] Smoke 测试（health/readiness/login/projects/crm/前端）
- [x] 保留上一版本 assets（防跨版本 404）
- [x] DB 备份 + 配置文件检查
- [x] 部署报告生成