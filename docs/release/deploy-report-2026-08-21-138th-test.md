# 第 138 次测试环境部署报告 — 2026-08-21

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 049 标书质量核查实质化 + 案例沉淀流式化/摘录化 + npm alias 清理 + 异步任务表守卫 |
| 部署序号 | 第 138 次（测试） |
| 部署时间 | 2026-08-21 08:17 CST（backend 激活） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `849dfb281` |
| 上一版本 Release | `9d50247ff-api8080`（2026-08-20 09:56Z 激活） |
| 基线 commit | `849dfb281`（本次打包的 release 源码） |
| 健康检查通过 | ✅（UP，第 79 次探测通过） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | **无**（增量 commit 不含 `migration-mysql/` 改动） |

## 变更内容（37 个 commit）

### 049 标书质量核查实质化（spec 049，US1-US5 真实实现）
- **US1** 真实投标文件取流 + trigger 4xx 前置校验（T017-T024）
- **US2** 真实招标文件 + LLM 结构化提取（T025-T030）
- **US3** 四知识库真实查询 + KnowledgeMatch 结构化证据（T031-T038）
- **US4** Evidence 真实页码/原文定位（T039-T044）
- **US5** LLM 混合裁决 lane——真实 AI 裁决 + ChatOutcome 值返回（T045-T050）
- 两处确定性误判修复：负偏离否定语义 + veto 归一化匹配
- 删除旧同步质量核查链（第零步双链路合并）

### 案例沉淀流式化 + 摘录化（PR !2362 系列）
- OBS 标书流式加载二态分派 + 事务只包落库
- CaseAiMatcher 逐评分项摘录化——LLM 只收 12K 相关段落，防超大标书上下文超限
- ScoreDocExcerptExtractor 新增 dim/detail 重载 + 单测
- 剥离摘录内嵌 base64 图片，防 LLM 网关 convert_request_failed 500
- 过滤 Word 自动目录锚点段，防 TOC 占用摘录预算
- 解析 LLM 网关 OpenAI choices 包装，修复 8 评分项全部误判无证据
- 唯一键从评分项标题改为评分项 ID，修复同标题多评分项整批回滚

### 工程清理
- 删除 33 条 npm script 别名（PR !2363）
- 拦截新建模块专属异步导入/导出任务表（PR !2364）

## 验证结果

### Flyway
| 检查项 | 结果 |
|---|---|
| validate（flyway-repair-runner） | ✅ VALIDATE OK（260 迁移 checksum 全匹配） |
| DB 已应用最新版本 | ✅ V1199（知识案例评分项 ID 去重） |
| 本地源码最新版本 | ✅ V1199（一致，无新增迁移） |
| 失败迁移记录 | ✅ 无（`success=0` 为空） |

### 部署后 Smoke（经 Nginx 8080）
| 检查项 | 结果 |
|---|---|
| 后端 health | ✅ UP |
| readiness | ✅ 200 |
| deployed-release.json | ✅ `849dfb281` |
| OBS 直传启用 | ✅ `.upload(` 调用数=2 |
| 前端入口与 release 一致 | ✅ `src="/assets/index-DzlouZX_.js` |
| `/actuator/health` | ✅ 200 |
| `/actuator/health/readiness` | ✅ 200 |
| `POST /api/auth/login`（空密码） | ✅ 400（路由正常） |
| `GET /api/projects`（未认证） | ✅ 403（权限守卫） |
| `GET /api/integration/crm/health`（未认证） | ✅ 401 |
| 前端根路径 `/` | ✅ 200 |
| 前端 `/login` | ✅ 200 |
| DB 备份 | ✅ `winbid-849dfb281-20260821081722.sql.gz`（11MB） |

## 部署步骤

1. 环境门禁确认：测试环境 172.16.38.78（用户通过 AskUserQuestion 确认）
2. 早操三连：dev-env + sync-env + check-git-wrapper ✅
3. 基线确认：HEAD=849dfb281（= origin/main），服务器现状（部署 9d50247ff-api8080，健康 UP）
4. Flyway 预检 3 步：validate OK + DB 版本 V1199 + 增量迁移变更=空
5. 本地打包：`RELEASE_ID=849dfb281 VITE_API_BASE_URL= VITE_OBS_ENABLED=true`（同源构建 + OBS 直传，容器测试门禁通过）
6. 产物校验：obsEnabled=true + jar 内迁移无重复（至 V1199）
7. scp 上传 archive（154M）+ remote-deploy.sh（SYSTEMCTL_SUDO=true）
8. remote-deploy：Flyway validate → 停服 → 换 jar → 重启 → 健康检查（79 次探测通过）→ 前端一致性校验
9. 保留上一版本 assets（防止跨版本 404）
10. 部署后验证：Flyway 无 failed + Smoke 全绿
11. GitHub 镜像同步（本次已同步，两边 main 一致）

## GitHub 镜像状态

- ✅ 已同步：`sync-to-github.sh` 执行成功，Gitee main = GitHub main = `849dfb281`，完全一致。

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：**保留**（技能明确记录"3 次决定保留"项，非临时 DEBUG/TRACE）。
- 无 `DEBUG` / `TRACE` 临时配置残留。

## 回滚方案（未启用）

```bash
ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && \
  sudo ln -sfn /opt/xiyu-bid/releases/9d50247ff-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
  sudo systemctl start xiyu-bid-backend'
```

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）
- [x] 早操三连 + 基线确认（HEAD = origin/main = 849dfb281）
- [x] Flyway 预检 3 步（validate + DB 对比 + 增量迁移=空）
- [x] 本地打包 + 产物校验（同源 API + OBS + 前端入口）
- [x] backend 部署 + 健康检查（UP，79 次探测）
- [x] 保留上一版本 assets（防止跨版本 404）
- [x] API Smoke + 前端验证（全部通过）
- [x] OBS 直传启用校验（.upload( 数=2）
- [x] 配置清理检查（SHOW_DETAILS=always 保留）
- [x] GitHub 镜像同步（两边 main 一致）

## 待办

- [ ] 复测 049 标书质量核查：登录测试环境 → 进入标书质量核查 → 验证 LLM 混合裁决 + Evidence 页码定位
- [ ] 复测案例沉淀：超大标书流式化 + 逐评分项摘录（项目 1350 相关）
- [ ] 本报告提交 PR 合入 main
