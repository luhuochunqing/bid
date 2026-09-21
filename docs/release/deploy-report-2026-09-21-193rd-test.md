# 第 193 次部署报告（test）

- **部署日期**: 2026-09-21
- **部署环境**: 测试环境（test）winbid-01 `172.16.38.78`
- **Release ID**: `bafa312d6`
- **部署类型**: 代码级部署（无 Flyway 迁移变更）
- **操作人**: Trae 开发宿主（agent/trae-init 锚点分支，与 main ff 同步）

## 部署概览

从上一已部署 release `86815c9de`（192nd，2026-09-20）增量部署至 `bafa312d6`，共 **154 个文件变更**，无新增 Flyway 迁移（DB 停留在 V1215）。

## 基线信息

- 分支: `agent/trae-init`（锚点分支，HEAD == origin/main ==`bafa312d6`）
- 工作区状态: 干净
- jar: `bid-platform-1.0.3.jar`
- 前端入口: `assets/index-RFPRH5PV.js`

## 主要改动（86815c9de..bafa312d6）

- **feat(compliance)**: 视觉印章完整性检测（signature-seal-vision）
- **fix(frontend)**: PDF 扫描件识别提示
- **feat(casework)**: 「原文所在标题」章节号契约 + 摘要打平文本按行还原
- **fix(frontend)**: 原生下载先做 1 字节 Range 预检，透传后端业务错误
- **fix(scoreparse)**: 明确业绩评分依据并保守计分 + 多模态主召回与异常审核
- **fix(compliance)**: AI 标书质量核查解析失败误报修复（spec 066，超时预算×规模 + 失败五分类）
- **perf(sidecar)**: PDF 分块 10→100 页并补转换可观测日志
- **fix(scripts)**: agent-finish-task 三层架构收尾修复

## Flyway 预检结果

- **Step 1 validate**: 275 个迁移全部通过，checksum 匹配（VALIDATE OK）
- **Step 2 DB 版本**: 已应用到 V1215（2026-09-16）
- **Step 3 内置校验**: remote-deploy.sh 激活前自动 validate 通过
- **结论**: 本次无迁移变更，DB 状态健康

## 部署步骤

1. 早操三连（dev-env + sync-env + check-git-wrapper）
2. 基线确认（agent/trae-init == origin/main == bafa312d6，工作区干净）
3. 服务器现状探测（deployed-release=86815c9de，health UP）
4. Flyway 预检 3 步法通过
5. 本地打包（`VITE_API_BASE_URL=` 同源 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）
6. 产物校验（obsEnabled=true、前端 index 入口、jar 内 274 个迁移无重复、OBS Detail .upload=2）
7. scp 上传 + remote-deploy.sh（SYSTEMCTL_SUDO=true）
8. 前端资源保留（从上一 release 86815c9de cp -rn 旧 assets）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 service | ✅ active(running)，PID 11433 |
| 健康检查（经 Nginx） | ✅ HTTP 200 UP（80 次探测连续通过） |
| Readiness | ✅ HTTP 200 UP |
| Flyway validate | ✅ VALIDATE OK（275 migrations） |
| 前端一致性 | ✅ `assets/index-RFPRH5PV.js` 与 release 一致 |
| Login 路由 | ✅ HTTP 400（空密码参数校验，预期） |
| Projects 路由 | ✅ HTTP 401（需认证） |
| CRM 路由 | ✅ HTTP 401（需认证） |
| 前端 / | ✅ HTTP 200 |
| 前端 /login | ✅ HTTP 200 |
| OBS 直传 | ✅ obsEnabled=true，.upload 调用=2 |
| 历史 assets 保留 | ✅ 已从 86815c9de 保留 |

## 回滚

- **回滚锚点**: 上一 release `86815c9de`（/opt/xiyu-bid/releases/86815c9de）
- **DB 备份**: 已由 remote-deploy.sh 自动执行（本次无迁移，备份主要为保险）
- **回滚状态**: 就绪（未执行，行为正常无需回滚）

## 经验沉淀应用

- 同源构建（VITE_API_BASE_URL=） ✅
- OBS 直传显式启用 + 校验 obsEnabled ✅（第 84 次/第 8 次生产事故防复发）
- COPYFILE_DISABLE=1 防 macOS `._*` 残留 ✅
- --noproxy 绕过 Mac HTTP_PROXY 502 ✅
- SYSTEMCTL_SUDO=true ✅（第 15 次事故防复发）
- 前端资源保留防跨版本 404 ✅（Lesson 17）
- 无 sentry-logback SentryAppender 手动声明 ✅（第 34 次事故已修复）

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：历史既有配置（第 13/14/15 次决定保留），非本次引入，保留。
- 未发现 DEBUG/TRACE 临时配置。

## 风险提示

- 无新增迁移，DB schema 风险低。
- 视觉印章检测、多模态召回等新能力建议在测试环境重点回归对应页面。

## 部署确认清单

- [x] 环境门禁（用户确认测试环境 172.16.38.78）
- [x] 早操三连通过
- [x] 基线 = origin/main
- [x] Flyway 预检通过
- [x] 打包 + 产物校验通过
- [x] 部署成功（health + smoke + 前端一致性）
- [x] 部署报告生成