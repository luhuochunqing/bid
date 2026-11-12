# 测试环境第 266 次部署报告

## 部署基本信息

- **部署时间**: 2026-11-12 07:09 CST（开始）→ 07:15:35 激活（`activatedAt=2026-11-11T23:15:35Z`）→ 07:19 健康检查连续 3/3 通过
- **部署环境**: 测试环境（winbid-01.test: `172.16.38.78:8080`）
- **发布版本 ID**: `6c9b7f851-api8080`
- **提交 Commit**: `6c9b7f851`
- **上一版本**: `d204051c5-api8080`（2026-11-08 18:54:18 CST 激活，对应第 265 次）
- **部署方式**: 手工标准链路 —— `scripts/release/package-release.sh`（同源构建 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）→ `scp` → `scripts/release/remote-deploy.sh`（`SYSTEMCTL_SUDO=true` + `DB_BACKUP_COMMAND` fail-closed 备份）
- **增量规模**: 33 个 commit / 14 个 PR，156 个文件变更（+8111 / -687）

---

## 变更核心内容

| PR | 类型 | 内容 |
|---|---|---|
| !3005 | fix(scoreparse) | 解除客观项过度防御短路，支持业绩材料提示、仓储多区域计算与证书跨天保持有效 |
| !3004 | fix(scoreparse) | 增加候选总分落库硬门禁并修复跨页续行证据收集 |
| !3003 | fix(scoreparse) | 防止满分汇总行重复计分 |
| !3002 | fix(scoreparse) | 阻止非计分零分条款进入正式评分项 |
| !3001 | fix(casework) | 结项写入案例子主题和质量分 |
| !3000 | fix(scoreparse) | 增强证书评分规则表格管道符剥离与三体系断句匹配 |
| !2999 | fix(scoreparse) | 修复扫描投标文件实际评分并区分预估状态 |
| !2998 | fix(casework) | 提升标书解析配额至 3GiB 并优化失败通知展示 |
| !2997 | fix(compliance) | 收敛偏离表识别门禁并优化 DOCX 页码定位提示 |
| !2996 | refactor(compliance) | 复用完整字符类并校准启发式语料期望 |
| !2995 | refactor(compliance) | 收口跨句证书推断并分层契约测试 |
| !2994 | fix(scripts) | 三技能审查门禁注入跨轮次历史与防死循环熔断机制 |
| !2993 | fix(compliance) | 解锁 OCR 转写要件与企业资质库放行一票否决通道 |
| !2992 | docs(release) | 第 265 次测试环境部署报告 |

- **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225（`score estimate freshness`）。
- **前端产物**: 入口 hash `assets/index--adUF0Cy.js`（上一版为 `index-ZLT6PNtE.js`，本次前端有 12 个文件变更）。
- **Sidecar**: 源码有变更（`app.py` / `converter.py` / OCR 扫描测试），SHA-256 由 `e5fc03f3…` 变为 `e9bcf69a…`，`remote-deploy.sh` 自动重建镜像并重启容器。

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 基线确认 | `agent/trae-init` 锚点分支，HEAD=`6c9b7f851`=`origin/main`，工作区干净（0 ahead / 0 behind） |
| 服务器连通性 | `ssh jetty@172.16.38.78` 正常，hostname=`winbid-01`，系统时间 2026-11-12 07:09 CST |
| 服务器部署前状态 | `deployed-release.json` releaseId=`d204051c5-api8080`，health=UP（db / aiProvider / diskSpace 均 UP） |
| 运行时 JDK | systemd `ExecStart=/opt/xiyu-tools/jdk-21/bin/java … -Xmx2g`（注意 `jetty` 用户 PATH 上的默认 `java` 仍是 1.8，不影响服务） |
| Flyway validate（服务器） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | V1225（2026-10-26 应用），与源码一致，无 pending |
| 增量迁移文件 | `git diff d204051c5..6c9b7f851 -- migration-mysql/ rollback/` 为空 |

---

## 部署执行

- **本地打包**：`scripts/release/package-release.sh`
  - 前端 Vite 构建 + 产物校验「不含 dev API 地址」通过
  - 容器测试门禁 `FlywayMysqlContainerTest` 通过（Spring 全量上下文装配正常）
  - `mvn clean -DskipTests package` BUILD SUCCESS（35.2s，3449 个主源码文件）
  - Sidecar 打包完成，SHA-256 `e9bcf69ac9aea71b94ac34f1a58aedca057761a1b49197bf782c91f5b86dd782`
- **产物校验**：
  - `release-metadata.json`：`apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false`、`sidecarSchemaVersion=2.2`
  - jar 内 Flyway 迁移文件 284 个，无重复版本
  - OBS 直传：Detail chunk `.upload(` 调用数 = 2
  - 归档 156 MB（163,318,052 bytes），上传后 MD5 与服务端一致（`9a97409418909d96bf5f6b527b813892`）
- **远程部署**（`remote-deploy.sh`）：
  1. 部署前数据库备份 → `/opt/xiyu-bid/db-backups/winbid-6c9b7f851-api8080-20261112071420.sql.gz`（16 MB）
  2. Flyway validate 通过 → 原子切换 jar → `systemctl restart xiyu-bid-backend`
  3. Sidecar hash 变更 → 重建 `document-converter-sidecar:latest`（镜像创建时间 07:15:26）→ `xiyu-sidecar` 容器重启
  4. 前端目录切换，入口一致性校验通过（`/assets/index--adUF0Cy.js`）
  5. 健康检查连续 3/3 通过（共 79 次尝试，无 Kafka readiness 长延迟）
- **旧版本 assets 保留**：`remote-deploy.sh` 内置策略（保留当前公开 assets + 最近 2 个历史版本：`d204051c5-api8080`、`1a7a534d1-api8080`），`/srv/www/xiyu-bid/assets` 共 364 个文件（上一版 278 个），`Detail-*` chunk 10 个（新旧并存）

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `deployed-release.json` | releaseId=`6c9b7f851-api8080`，activatedAt=`2026-11-11T23:15:35Z`，`obsEnabled=true` |
| `/actuator/health`（经 Nginx 8080） | HTTP 200 UP |
| `/actuator/health/readiness`（经 Nginx 8080） | HTTP 200 |
| `/api/auth/login`（空 body） | HTTP 400（参数校验，路由正常） |
| `/api/projects`（未认证） | HTTP 401（鉴权生效） |
| `/api/integration/crm/health`（未认证） | HTTP 401（鉴权生效） |
| 前端 `/`、`/login` | HTTP 200、HTTP 200 |
| 前端入口一致性 | `assets/index--adUF0Cy.js` 与 release 一致 |
| 旧 hash 资产保留 | assets 目录 364 个文件，`Detail-*` 10 个 |
| 数据库迁移 | 最新 `success=1` 仍为 V1225，无新迁移 |
| Sidecar 容器 | `xiyu-sidecar` Up，镜像 `document-converter-sidecar:latest`（07:15:26 构建），`/health` → 200 `{"status":"up"}` |
| Sidecar 发布冒烟 | 5/5 通过（schema 指纹 2.2 / 1371 fixture 回放 tables=14 cells=8215 / v1+v2 版本探针） |
| 后端服务状态 | `systemctl is-active xiyu-bid-backend` = active；近 10 分钟 err 级日志 0 条（144 行正常日志） |
| macOS `._*` 残留 | `find /srv/www/xiyu-bid -name "._*"` 无结果 |
| 临时调试配置 | 仅历史遗留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`，本次未新增 |

---

## 已知项与风险提示

1. `/etc/xiyu-bid/backend.env` 中仍存在历史遗留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历次部署决定保留），本次未新增调试配置。
2. 本次走的是 skill 标准手工链路（package → scp → remote-deploy）；`scripts/release/deploy-test.sh` 在锚点分支 `agent/trae-init` 上仍会被 session-gate 规则 1 拦截，需显式传 `XYYU_SYNC_ENV_ACTIVE=1`（与第 264/265 次报告建议一致，仍未在脚本内部落地）。
3. 本次改动集中在标书评分解析（scoreparse）、偏离表/证书合规判定（compliance）、案例结项（casework）与标书解析配额，业务验证重点：跨页评分细则续行收集是否丢项、满分汇总行是否重复计分、非计分零分条款是否被误纳、扫描版投标文件 OCR 评分是否落在「实际评分」而非「预估」。
4. Sidecar 本次有代码变更（OCR 扫描能力），已重建容器并冒烟通过，但业务侧仍建议用真实扫描件跑一次端到端解析。
5. 旧标签页跨版本 404 风险由「保留最近 2 个版本资产」覆盖，用户侧仍建议刷新页面获取新入口。

---

## 回滚信息

- **上一版本产物**: `/opt/xiyu-bid/releases/d204051c5-api8080/`（jar + frontend 均保留）
- **上一版 Sidecar 镜像**: 需从 `d204051c5-api8080/sidecar/sidecar.tar.gz` 重新 `docker load` + 重建容器（本次镜像已被覆盖为 `:latest`）
- **数据库备份**: `/opt/xiyu-bid/db-backups/winbid-6c9b7f851-api8080-20261112071420.sql.gz`（16 MB，部署前快照）
- **回滚步骤**:
  1. `cp /opt/xiyu-bid/releases/d204051c5-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar`
  2. `sudo systemctl restart xiyu-bid-backend`
  3. 恢复 `d204051c5-api8080/frontend` 到 `/srv/www/xiyu-bid`
  4. 如需回滚 Sidecar：从 `d204051c5-api8080/sidecar/sidecar.tar.gz` 重新构建/加载镜像并重启 `xiyu-sidecar`
  5. 健康检查 + Smoke
- **回滚状态**: 未触发

---

## 部署确认清单

- [x] 环境门禁确认（测试 172.16.38.78）
- [x] 基线确认（HEAD = origin/main = 6c9b7f851，工作区干净）
- [x] Flyway 三步预检通过，无 pending 迁移
- [x] 打包产物校验（同源构建 / OBS 直传 / 迁移无重复 / MD5 一致）
- [x] DB 部署前备份完成（16 MB）
- [x] 后端健康检查 + 前端入口一致性校验通过
- [x] 冒烟测试（health / readiness / 三个接口路由 / 前端页面）全部符合预期
- [x] Sidecar 镜像重建 + 容器运行 + 发布冒烟 5/5 通过
- [x] 旧版本 assets 保留生效
- [x] 无新增临时调试配置，无 macOS `._*` 残留，无 err 级日志

---

## 升级验收报告（业务验证）

> 验证时间：2026-11-12 07:20–08:20 CST；环境：测试 `172.16.38.78:8080`（release `6c9b7f851-api8080`）。
> 验证方式：ego-browser 真实点击（非接口断言）；素材来自生产只读下载的真实扫描版投标文件。

### 验证素材

- 生产环境（172.16.10.149，只读）投标文件均存于 OBS 桶 `ehsy-widbid`，本地盘无副本；经后端公开附件下载端点取回。
- 选用 `王老吉投标文件V1（20260715）.pdf`（29 MB / 224 页，电子页与扫描证书页混合），上传至 project 1392 作为打分与质量核查素材。
- 验证结束后已清理测试环境该文件与 OBS 对象（下载端点复核 404），本地临时文件同步删除。

### 验收矩阵

| PR | 验证点 | 结论 | 证据 |
|---|---|---|---|
| !3005 | 客观项过度防御短路解除 / 仓储约束 tooltip | 通过 | 评分表第 6 项 tooltip：「未抽取到可执行的仓库匹配约束（面积），禁止按在用仓存在性给满分，待人工确认预计得分」 |
| !3004 | 候选总分落库门禁 + 跨页续行证据收集 | 通过 | 阶段 1 共 19 项、权重合计 100；阶段 2 DB `items=19 / uniq=19 / 总分=5.00`；续行条目「2。投标人能够提供项目解决方案…」「2。投标人平台进货…」原文完整未截断 |
| !3003 | 满分汇总行不重复计分 | 弱通过 | 19 项无重复、权重 100（客观 52 + 主观 48） |
| !3002 | 非计分零分条款不进入正式评分项 | 待业务确认 | 权重 0 的追责条款（第 18/19 条）仍在生效评分项列表中，需业务口径判定 |
| !3001 | 结项写入案例子主题与质量分 | 部分验证 | project 1390 结项审核通过（toast「项目结项审核通过」，DB `CLOSED/WON`），但该项目无评分项/标书，未沉淀案例；project 1377 已提交复盘进入结项，admin 无「提交结项」权限（仅 `bid-Team`/`bid-projectLeader` 可提交），案例 `sub_topic/quality_score` 运行时写入未验证 |
| !3000 | 证书规则管道符剥离/三体系断句 | 无 UI 可点 | 代码与单测层收敛 |
| !2999 | 扫描投标文件实际评分 + 预估三态区分 | 通过 | 预估表渲染 `需人工评估/待专家评审/待确认` 三态与 `title=estBasis`；project 1382 实际表真实分值 7/3/0（✓满足/✗不满足） |
| !2998 | 标书解析配额 3 GiB + 失败通知展示 | 通过 | 通知中心失败通知展示「失败原因：前置条件不满足：缺少评分项」；本次 29 MB 真实标书解析成功 |
| !2997 | 偏离表门禁 + DOCX 页码定位提示 | 通过 | 质量核查 25 项（7 通过 / 2 不通过 / 16 待确认）；「投标函格式规范」详情显示 `问题定位 第 21 页` + 「（排版页码可能因阅读器缩放或文档转换略有差异，请结合下方原文摘录核对）」+ 原文摘录 + 建议操作；`compliance_check_results` 已落库 |
| !2996 | 完整字符类复用/语料校准 | 无 UI 可点 | 重构 + 契约测试 |
| !2995 | 跨句证书推断收口 | 无 UI 可点 | 重构 + 契约测试 |
| !2994 | 三技能门禁跨轮次历史/熔断 | 无 UI 可点 | 脚本门禁能力 |
| !2993 | OCR 转写要件 + 企业资质库一票否决放行 | 通过 | 评分项 1（ICP 备案）评分依据：「投标文件第 60 页后包含工信部 ICP 备案管理系统官网实时查询截图，其对应的 OCR 文本包含官方网址 beian.miit.gov.cn」；标书引用展示该扫描页 OCR 转写文本 |

### 端到端运行数据

- 打分任务 `score_parse_task id=366`：`COMPLETED`，耗时 7 分 44 秒（224 页扫描/电子混合标书，含 OCR 通道）。
- 质量核查：25 项合规矩阵，耗时约 4 分钟，结果落 `compliance_check_results`。
- 项目状态变化：`1390` → `CLOSED/WON`（结项审核通过）；`1377` 复盘已提交、结项进行中（`stage=RETROSPECTIVE`）。

### 遗留事项

1. **!3001 补验**：需测试环境 `bid-Team` / `bid-projectLeader` 账号对 1377 执行「提交结项」并完成审核，再核对 `knowledge_case.sub_topic` / `quality_score` 是否按结项数据写入。
2. **!3002 口径**：权重 0 的追责类条款是否应进入正式评分项，需业务确认。
3. **验证素材清理**：测试环境 project 1392 投标文件、project 1377 复盘报告附件及其 OBS 对象已删除（OBS 下载端点复核 404），本地临时文件已清理。
