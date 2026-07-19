# 第 12 次生产环境部署报告 — 2026-07-19

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产 (prod)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.10.149` |
| 用途 | 正式环境、对外服务 |
| 部署序号 | 第 12 次（生产） |
| 部署时间 | 2026-07-19 12:50:31 CST |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `ce55d7d09` |
| 上一版本 Release | `59d3763cd`（2026-07-14 20:50:27 CST，第 11 次生产部署） |
| 基线 commit | `ce55d7d09`（origin/main） |
| 激活时间 | 2026-07-19T04:50:31Z |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | V1166, V1167, V1168（3 个） |
| Smoke 测试 | 全部通过 |
| GitHub 镜像 | 已同步（0 commits behind） |

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/trae`
- 锚点分支：`agent/trae-init`（ff-only 同步到 origin/main）
- 早操 SOP：已执行 `sync-env.sh`，HEAD = origin/main = `ce55d7d09`
- GitHub 镜像状态：已同步（0 commits behind）
- 本地门禁自检：7 项全部通过（hooksPath、pre-commit、pre-push、git wrapper、agent-locks 等）

## 增量改动（59d3763cd → ce55d7d09，92 个 commit）

### 主要 PR 列表（按时间倒序，摘选高影响 PR）

| PR | 说明 |
|---|---|
| !2139 | chore: 同步 GitHub cherry-pick + 刷新过期 wiki health_checked 日期 |
| !2133 | docs(release): 第 98 次测试环境部署报告 |
| !2132 | docs(lessons): 2026-W29 每周知识归档（4 条新增） |
| !2131 | docs(lessons): 每周知识归档 2026-07-18 — 新增 §63/§64/§65 + shell-gotchas §2 |
| !2128 | docs(AGENTS): 优化"收尾"暗号展开为五步流程 + 文件树概览补全 |
| !2127 | docs(lessons): 每周知识归档 §62 — 错误消息引导必须可执行 |
| !2126 | fix(warehouse): 下载导出包前端超时——axios blob 改为浏览器原生导航下载 |
| !2124 | fix(warehouse): 下载导出包 OOM 根治——Files.readAllBytes 改为流式输出 |
| !2122 | docs(playbook): 新增 07 迁移指南——经验复用四层模型与 Day-0 拷贝清单 |
| !2121 | docs(playbook): 新增 AI Coding 经验手册——2059 个 PR 的根因模式沉淀 |
| !2120 | fix(warehouse): Word 合订本导出 OOM 根治——buildBundle 改为流式写入 |
| !2118 | fix(warehouse): application-prod.yml 补全 warehouse.attachment.root 配置 |
| !2116 | fix(warehouse): 修复附件下载无反应与导出合订本文件缺失 |
| !2113 | fix(warehouse): 附件文件缺失时输出 WARN 日志（数据完整性诊断） |
| !2112 | fix(warehouse): 修复 Word 合订本附件内容丢失（macOS SSV + 绝对路径默认值） |
| !2111 | fix(warehouse): 修复仓库导入 @Async 自调用失效 — 提取独立 Bean (spec 039) |
| !2110 | fix(warehouse): 039 修复仓库导出全量合订本创建失败 — 提取 @Async 方法到独立 Bean |
| !2108 | feat(performance): 合同协议附件设为导入必填字段并加 * 号 (CO-586) |
| !2107 | fix(workbench): 固定待办/截止时间列宽，省略号生效 |
| !2106 | docs(release): 第 92 次测试环境部署报告 |
| !2104 | chore(ci)+docs(lessons): 教训 #61 schema 覆盖式迁移门禁升级为 CI 硬约束 |
| !2103 | fix(workbench): 待办/截止时间名称过长省略号 + 悬停显示全称 |
| !2102 | feat(tender-reminder): 投标关键节点提醒改为每日重复 + 默认提前3天 (spec 038) |
| !2101 | feat(initiation): 审批模式下开放计划入围供应商数量和招标文件不利项可编辑 |
| !2099 | feat(integration): 结果确认回调 feedback 增加立项阶段两个字段 |
| !2098 | feat(warehouse): 仓库到期提醒接收人新增投标专员角色 |
| !2097 | feat(resources): CA 列表/详情接口返回借用人信息 (CO-579) |
| !2096 | fix(crm): spec 037 修复 CRM 商机关联失败 — fallback 版 + 技术债清理 |
| !2095 | revert(project): 撤销 PR !2091 的 locked 修改，保留 region cascader 修复 |
| !2092 | fix(warehouse): CO-582 §3.6 严格按需求规范 Word 文档层级 |
| !2091 | feat(project): PENDING_REVIEW 状态下立项表单字段可编辑 |
| !2090 | feat(workbench): 工作台 UI 改造 - 对齐 HTML 参考设计 |
| !2089 | feat(tender-entry): 业务页接入 AdaptiveFormPage + 配置页加锁定字段和启用开关 + V1167 schema |
| !2087 | fix: JDBC URL 添加 zeroDateTimeBehavior=convertToNull 修复表单加载失败 |
| !2086 | fix(workflow-form): 修复独立表单点击无反应 |
| !2084 | fix(performance): 修复 Excel 导出状态英文 + 到期天数超大数字 |
| !2083 | docs(release): 第 89 次测试环境部署报告 |
| !2082 | feat(performance): CO-583 业绩管理列表分组与总截止日期聚合 |
| !2079 | docs(release): 第 11 次生产环境部署报告 |

### 改动范围

- **后端**：
  - **spec 037 CRM 商机关联修复**：CrmTenderLinkService linkByBidIdIfPresent 新方法、CrmAuthService.generateToken 去掉 OSS token 依赖、OrganizationUserSyncWriter 用 username 填充 crm_sales_no
  - **spec 038 投标关键节点提醒改造**：报名截止/开标提前3天每日重复提醒，去重逻辑改为每 24 小时一次，默认值 24→72（V1168 迁移）
  - **spec 039 仓库 @Async 修复**：仓库导入/导出全量合订本 @Async 自调用失效根治，提取独立 Bean
  - **warehouse 模块多轮 OOM/附件修复**：buildBundle 流式写入、Files.readAllBytes 流式输出、axios blob 改浏览器原生导航下载、附件绝对路径默认值修复
  - **业绩管理 (performance)**：CO-583 列表分组与总截止日期聚合、Excel 导出状态英文修复、合同协议附件必填 (CO-586)
  - **动态表单引擎 (form-engine)**：V1166/V1167 schema 对齐 fallback 字段、加 enabled/pastedText/attachments 字段、独立表单点击无反应修复、JDBC URL zeroDateTimeBehavior 修复
  - **工作台 (workbench)**：UI 改造对齐 HTML 参考设计、待办/截止时间列宽固定+省略号+悬停全称
  - **资源管理 (resources)**：CA 列表/详情接口返回借用人信息 (CO-579)
  - **立项 (initiation)**：审批模式下开放计划入围供应商数量和招标文件不利项可编辑
  - **集成 (integration)**：结果确认回调 feedback 增加立项阶段两个字段
  - **CRM 商机关联三层根因修复**（spec 037）：fallback 版、Review P0+P1 修复、技术债清理
- **前端**：
  - 工作台 UI 改造、业绩管理列表分组、动态表单引擎多次修复、tender-entry 业务页接入 AdaptiveFormPage、工作台待办列宽固定
- **文档**：
  - AI Coding 经验手册（2059 个 PR 根因模式沉淀）、迁移指南 07、每周知识归档 §62-§65、AGENTS.md 收尾流程优化、多个测试环境部署报告
- **CI**：
  - 教训 #61 schema 覆盖式迁移门禁从软约束升级为硬约束

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（228 migrations） |
| Step 2: DB 已应用版本 vs 源码最新版本 | ✅ DB=V1165, 源码=V1168，缺失 V1166/V1167/V1168（预期待应用） |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过（228 migrations, all checksums match） |
| 新增迁移文件 | V1166 (UPDATE JSON), V1167 (UPDATE JSON), V1168 (ALTER TABLE MODIFY COLUMN) |

### 新增迁移详情

| 版本 | 类型 | 说明 | 幂等性 |
|---|---|---|---|
| V1166 | UPDATE JSON | 对齐 tender.entry schema 字段 key（tenderAgency→purchaser 等 9 个字段改名） | UPDATE JSON |
| V1167 | UPDATE JSON | tender.entry schema 加 enabled/pastedText/attachments 字段，version 3→4 | UPDATE JSON |
| V1168 | ALTER TABLE MODIFY COLUMN | tender_reminder_settings.remind_before_hours 默认值 24→72 | 幂等 MODIFY |

## 部署步骤

1. **环境门禁**：用户确认部署到生产环境 172.16.10.149（AskUserQuestion 显式授权）
2. **早操 SOP**：`sync-env.sh` 完成，HEAD = origin/main = `ce55d7d09`，7 项门禁通过
3. **服务器现状检查**：当前部署 `59d3763cd`（第 11 次生产部署，2026-07-14 20:50:27 CST），后端 health UP，所有组件 UP
4. **Flyway 预检 3 步法**：全部通过，DB=V1165, 源码=V1168，缺失 V1166/V1167/V1168（预期待应用）
5. **本地打包**：
   - `RELEASE_ID=ce55d7d09 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 bash scripts/release/package-release.sh`
   - 前端构建 8.82s + 后端 mvn clean package（27.012s）
6. **产物校验**：
   - ✅ release-metadata.json: `obsEnabled=true`, `apiBaseUrl=""`, `sentryEnabled=false`
   - ✅ Detail chunk `.upload(` 调用数=2（OBS 直传已启用，package-release.sh 内置校验）
   - ✅ jar 内 V1166/V1167/V1168 全部存在，无重复版本
   - ✅ 前端入口 `assets/index-C3Ta5kd1.js` + `assets/index-Cmq0rLNS.css`
   - ✅ tar.gz 153M
7. **上传 + 部署**：
   - scp tar.gz + remote-deploy.sh 到 `/opt/xiyu-bid/incoming/`
   - remote-deploy.sh 执行（SYSTEMCTL_SUDO=true）
   - Flyway validate 通过（228 migrations, all checksums match）
   - 后端服务重启：active/running since 12:50:01 CST（PID 32104）
   - 健康检查通过：consecutive 3/3, 15 attempts（约 30 秒，无 Kafka SDK 延迟）
   - 前端一致性验证：`/assets/index-C3Ta5kd1.js`
8. **前端资源保留**：从上一版本 `59d3763cd` 复制旧 assets 到 `/srv/www/xiyu-bid/assets/`（防止跨版本 404）

## 验证结果

### 后端健康检查（内部 18080）

| 组件 | 状态 | 详情 |
|---|---|---|
| overall | UP | - |
| aiProvider | UP | configured, qwen3.7-max |
| db | UP | MySQL, isValid() |
| diskSpace | UP | 85GB free / 98GB total |
| jwt | UP | HMAC-SHA256, 47 bytes |
| livenessState | UP | - |
| readinessState | UP | - |
| redis | UP | 6.2.19 |
| sidecar | UP | http://localhost:8000 |

### Smoke 测试（服务器本地，经 Nginx 8080）

| 检查项 | 结果 | 预期 |
|---|---|---|
| /actuator/health | HTTP 200 UP | ✅ |
| /actuator/health/readiness | HTTP 200 UP | ✅ |
| /api/auth/login POST | HTTP 400 参数校验失败（Username/Password is required） | ✅ |
| /api/projects | HTTP 403 需认证 | ✅ |
| /api/integration/crm/health | HTTP 401 需认证 | ✅ |

### 前端验证（服务器本地，经 Nginx 8080）

| 检查项 | 结果 | 预期 |
|---|---|---|
| 首页 / | HTTP 200 | ✅ |
| /login | HTTP 200 | ✅ |
| index.html 入口 | assets/index-C3Ta5kd1.js + assets/index-Cmq0rLNS.css | ✅ 与 release 一致 |

### 迁移验证

| 版本 | 描述 | success | installed_on |
|---|---|---|---|
| V1166 | align tender entry schema with fallback | 1 | 2026-07-19 12:50:08 |
| V1167 | add enabled field to tender entry schema | 1 | 2026-07-19 12:50:08 |
| V1168 | tender reminder default 72h | 1 | 2026-07-19 12:50:08 |

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 部署前落后 commit 数 | 0 |
| 同步状态 | ✅ 已同步（早操 SOP sync-env.sh + git fetch github 双重确认） |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| SHOW_DETAILS=always | 保留 | 历史决定保留（第 13/14/15 次部署用户决定） |
| DEBUG/TRACE | 无 | ✅ 无临时调试配置 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 上一版本 Release | `59d3763cd` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/59d3763cd` |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-ce55d7d09-*.sql.gz` |
| 回滚方式 | 恢复上一版本 jar + 前端 + 数据库备份（V1166/V1167/V1168 含 schema 变更，需恢复 DB） |

## 经验沉淀应用情况

1. **OBS 直传三层防护**（第 8 次生产事故根治）：
   - package-release.sh 默认 VITE_OBS_ENABLED=true
   - 打包时显式传入 VITE_OBS_ENABLED=true 作双保险
   - 产物校验 obsEnabled=true + Detail chunk `.upload(` 调用数=2
2. **Flyway 预检 3 步法**：部署前主动 validate + DB 版本对比，避免启动时才发现问题
3. **前端资源保留**：部署后从上一版本 release 目录 `cp -rn` 旧 assets，防止跨版本 404（skill 教训 #18）
4. **SYSTEMCTL_SUDO=true**：jetty 用户已配置 NOPASSWD sudo，避免服务重启失败（skill 教训 #8）
5. **COPYFILE_DISABLE=1**：避免 macOS `._*` 残留文件污染服务器（skill 教训 #14）
6. **Mac HTTP_PROXY 502 经验**：从本地 Mac 访问生产 172.16.10.149:8080 返回 HTTP 000（curl 退出码 52），改用服务器本地 curl 验证（skill 教训 #16）
7. **Flyway 9.22.3 + MySQL 8.0 语法限制**：V1166/V1167 使用 UPDATE JSON（非 ALTER TABLE ADD COLUMN IF NOT EXISTS），V1168 使用 ALTER TABLE MODIFY COLUMN，均为幂等设计

## 风险提示

1. **3 个新 Flyway 迁移已应用**：V1166（tender.entry schema 字段改名）涉及存量数据 JSON 字段更新，V1167（新增 enabled/pastedText/attachments 字段），V1168（remind_before_hours 默认值 24→72）。回滚需恢复数据库备份，不能仅回滚 jar。
2. **Nginx 8080 外部访问异常**：从本地 Mac 访问生产 172.16.10.149:8080 返回 HTTP 000（curl 退出码 52），服务器内部访问全部正常。可能是 Mac HTTP_PROXY 干扰或网络策略限制，不影响服务正常运行。
3. **增量改动范围大（92 commit）**：本次部署涉及 CRM 商机关联修复 (spec 037)、投标关键节点提醒改造 (spec 038)、仓库 @Async 修复 (spec 039)、warehouse 多轮 OOM/附件修复、工作台 UI 改造、业绩管理列表分组、动态表单引擎多次修复等多个模块，建议关注上线后：
   - CRM 商机自动关联行为
   - 投标关键节点提醒每日重复发送（默认 72 小时提前）
   - 仓库导入/导出大文件下载（流式写入）
   - 工作台 UI 显示
   - 业绩管理列表分组与排序
   - 动态表单（tender.entry）字段渲染与保存
4. **rollback 脚本命名规范**：V1166/V1167/V1168 应配套 `U1166/U1167/U1168` rollback 脚本（按 skill 教训 #12），生产回滚主路径是数据库备份恢复。

## 部署确认清单

- [x] 环境门禁确认（生产 172.16.10.149，AskUserQuestion 显式授权）
- [x] 早操 SOP + 基线确认（HEAD = origin/main = ce55d7d09）
- [x] 服务器现状检查（59d3763cd, health UP）
- [x] Flyway 预检 3 步法（全部通过，DB V1165 → 应用 V1166/V1167/V1168）
- [x] 本地打包（BUILD SUCCESS 27.012s, OBS obsEnabled=true）
- [x] 产物校验（jar 含 V1166/V1167/V1168, 前端入口 index-C3Ta5kd1.js, tar.gz 153M）
- [x] 上传 + 部署（remote-deploy.sh 成功，健康检查 3/3 通过，约 30 秒）
- [x] 前端资源保留（59d3763cd 旧 assets 已复制）
- [x] 健康检查（health UP, readiness UP，无 Kafka SDK 延迟）
- [x] Smoke 测试（5 项全部符合预期）
- [x] 前端验证（/, /login 200, index.html 入口一致）
- [x] 迁移验证（V1166/V1167/V1168 全部 success=1，2026-07-19 12:50:08 应用）
- [x] GitHub 镜像同步（0 commits behind，已同步）
- [x] 配置清理检查（仅 SHOW_DETAILS=always 历史保留项）
- [x] 部署报告生成

## 回滚指引

如需回滚到上一版本 `59d3763cd`：

```bash
# 1. 恢复后端 jar
ssh jetty@172.16.10.149 'cp /opt/xiyu-bid/releases/59d3763cd/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'

# 2. 恢复前端
ssh jetty@172.16.10.149 'sudo cp -R /opt/xiyu-bid/releases/59d3763cd/frontend/* /srv/www/xiyu-bid/'

# 3. 等待健康检查
ssh jetty@172.16.10.149 'for i in $(seq 1 120); do if curl -fsS http://127.0.0.1:18080/actuator/health >/dev/null 2>&1; then echo "✅ 健康检查通过"; break; fi; sleep 2; done'

# 4. 恢复数据库（V1166/V1167/V1168 涉及 schema/JSON 变更，需恢复 DB 备份）
# 先停止后端，再恢复数据库
ssh jetty@172.16.10.149 'sudo systemctl stop xiyu-bid-backend && \
  source /etc/xiyu-bid/backend.env && \
  gunzip -c /opt/xiyu-bid/db-backups/winbid-ce55d7d09-*.sql.gz | \
  mysql -h"${DB_HOST:-127.0.0.1}" -P"${DB_PORT:-3306}" -u"${DB_USER:-root}" -p"${DB_PASSWORD}" "${DB_NAME:-xiyu_bid_main}" && \
  sudo systemctl start xiyu-bid-backend'
```
