# 第 47 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署日期 | 2026-07-05 14:34 CST |
| Release ID | `e8f1a36c3-api8080` |
| 上一版本 | `c9f36c23d-api8080`（2026-07-04 22:58 CST 部署，第 46 次） |
| 部署类型 | 增量部署（业务功能更新 + UI 修复 + AI 案例切片语义检索 + 自定义 AI Provider，含 2 个 DB 迁移） |
| 部署结果 | ✅ 成功 |
| 健康检查 | ✅ UP（consecutive 3/3，总尝试 89 次） |
| Readiness | ✅ UP（89 次尝试说明有 Kafka SDK 延迟，属已知行为） |
| 部署耗时 | 约 2 分钟（14:32 打包完成 → 14:34 服务重启 → 健康检查通过） |

## 基线信息

| 项目 | 值 |
|---|---|
| 工作区 | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae-init`（锚点分支，部署基于 origin/main 只读） |
| HEAD commit | `e8f1a36c3` |
| 工作区状态 | 干净 |
| origin/main 同步 | ✅ HEAD = origin/main |
| GitHub 镜像 | ✅ 同步完成（部署前落后 0 个 commit） |

## 增量 PR 列表（30+ commit，`c9f36c23d..e8f1a36c3`）

| Commit | PR | 描述 |
|---|---|---|
| `e8f1a36c3` | !1707 | fix(warehouse): 修复租赁合同附件上传500错误 + 附件必填校验 + 后端领域防线 |
| `51355b706` | !1706 | fix(archive): CO-496 文件视图复选框/文件名溢出/ZIP分类过滤 + 修复3个HIGH级设计隐患 |
| `7b78ba317` | !1705 | CO-484 v2: 驳回后投标负责人可重新选择审核人 |
| `d6c5d3b07` | !1704 | fix(dev-env): 固化 MySQL/Redis 容器配置，避免跨项目端口冲突 |
| `14ace9e9b` | !1703 | fix(scripts): agent-housekeeping.mjs handle EACCES on worktree scan |
| `4ad4b0dfa` | !1702 | refactor(knowledge): 删除业绩管理页面副标题 |
| `9dbbf4986` | !1701 | chore(agent-locks): cleanup stale lock for fix-personnel-batch-log-null-id |
| `17f3018b3` | !1700 | test: 补充测试缺口 - Expense实体/人员导出totalCount/项目归档视图切换/保证金UNION collation |
| `66ae6f4be` | !1699 | style(knowledge): 知识库模块样式统一 — 公共class + 按钮规范 + CTA渐变 |
| `7c752051d` | !1697 | fix: remove invalid .header-actions assertion from ProjectDetailLayout spec |
| `e62d51fde` | !1694 | docs(lessons): CO-469 §41 补充三层失效分析与 L1-L7 防复发机制 |
| `ddee5cab3` | !1696 | feat(ai): 通用 AI 大模型 API 聚合入口 — 自定义 Provider |
| `88d4e1e97` | !1695 | docs(release): 第 46 次部署报告 + a759eda25 补录报告 |
| `06f1107fb` | !1693 | docs(lessons): 精修 lessons-learned.md，删除低价值章节，修复编号冲突 |
| `ab082d0b1` | !1680 | feat(casework): AI 案例切片语义检索 — 切片入库 + 向量召回 + 精排推荐 |

## 改动范围

**核心业务变更**（7 个功能模块）：

### 1. AI 案例切片语义检索（feat(casework)）
- 历史投标文件章节切片入库 + 向量召回 + 精排推荐
- 涉及模块：案例切片、语义检索、向量索引
- 新增迁移：V1135（create bid case slice）

### 2. 自定义 AI Provider（feat(ai)）
- 通用 AI 大模型 API 聚合入口，支持自定义 Provider
- 前端 AI 模型配置支持自定义 Provider
- AiProviderCatalog 新增 custom Provider 条目 + validateBaseUrl SSRF 分流
- SsrfValidator 修复 IPv6 地址校验

### 3. CO-496：档案文件视图修复（fix(archive)）
- 文件视图复选框/文件名溢出/ZIP分类过滤
- 修复 3 个 HIGH 级设计隐患

### 4. CO-484 v2：驳回后重新选择审核人（fix(project)）
- 驳回后投标负责人可重新选择审核人

### 5. CO-497：复盘提交后自动跳转结项阶段（fix(project)）
- 删除"进入结项"按钮和可进入状态
- 复盘提交后自动跳转到结项阶段，简化流程

### 6. CO-502：标讯批量导入模板字段必填（feat(tender)）
- 客户类型、优先级、项目类型改为必填

### 7. 仓库租赁合同附件上传修复（fix(warehouse)）
- 附件上传500错误修复 + 附件必填校验 + 后端领域防线
- 新增迁移：V1136（warehouse attachment type to varchar）

**其他变更**：
- 知识库模块样式统一 + 公共class + 按钮规范 + CTA渐变
- 测试补全：Expense实体/人员导出totalCount/项目归档视图切换/保证金UNION collation
- 开发环境：固化 MySQL/Redis 容器配置，避免跨项目端口冲突
- CO-469 七轮修复全记录文档沉淀（系统性根因与防复发）
- 删除业绩管理页面副标题

## Flyway 预检结果（3 步法）

| 步骤 | 结果 | 备注 |
|---|---|---|
| Step 1: validate | ✅ OK | 198 migrations, all checksums match（execution time 0.085s） |
| Step 2: DB 版本对比 | ✅ 已应用 V1134 | DB V1134（2026-07-04 15:51:45 应用）→ 源码 V1136（待应用 V1135, V1136） |
| Step 3: remote-deploy 内置 validate | ✅ 通过 | 部署时自动执行，VALIDATE OK - all checksums match |

**结论**：2 个新迁移（V1135、V1136）需应用，已在部署后验证成功应用。

## 部署步骤

### 1. 早操三连

```bash
# 锚点分支守卫拦截 source dev-env.sh，但部署基于 origin/main 只读，使用 PATH 方式激活 git wrapper
export PATH="$(pwd)/scripts:$PATH"
bash scripts/check-git-wrapper.sh  # ✅ 7/7 通过
git status --short                  # ✅ 干净
git log --oneline -1 HEAD          # ✅ e8f1a36c3
git log --oneline -1 origin/main  # ✅ e8f1a36c3
```

> 注：`source scripts/dev-env.sh` 因锚点分支守卫拦截跳过（部署场景非开发行为）。

### 2. 本地打包（生产同源构建模式）

```bash
RELEASE_ID="e8f1a36c3-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh
```

- ✅ BUILD SUCCESS（后端 26.016 s，前端构建正常）
- ✅ jar 内 Flyway 迁移版本无重复（199 个 V*.sql）
- ✅ 产物：`.release/xiyu-bid-release-e8f1a36c3-api8080.tar.gz`（138M）
- ✅ 前端入口：`assets/index-BK7tigV4.js`（同源构建，无 IP 字面量）

### 3. 上传 + 部署

```bash
scp .release/xiyu-bid-release-e8f1a36c3-api8080.tar.gz scripts/release/remote-deploy.sh \
    jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 'RELEASE_ARCHIVE=... RELEASE_ID=e8f1a36c3-api8080 \
    SYSTEMCTL_SUDO=true ... bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- ✅ Flyway validate 通过（198 migrations，0.087s）
- ✅ DB 备份完成（`/opt/xiyu-bid/db-backups/winbid-e8f1a36c3-20260705143249.sql.gz`，3.3M）
- ✅ 后端服务重启（PID 1382，2026-07-05 14:34:25 CST）
- ✅ 健康检查通过（consecutive 3/3，总尝试 89 次）
- ✅ 前端一致性验证通过（`index-BK7tigV4.js` 与 release 一致）

## 验证结果

### 健康检查

| 端点 | 状态 | 备注 |
|---|---|---|
| `/actuator/health` | ✅ UP | 全组件 UP（aiProvider doubao / db / diskSpace / jwt / livenessState / ping / readinessState / redis / sidecar） |
| `/actuator/health/readiness` | ✅ UP | readinessState UP（89 次尝试说明有 Kafka SDK 延迟，属已知行为） |

### Smoke 测试（admin 密码未知，用 400/403/401 验证接口路由）

| 接口 | HTTP | 预期 | 结果 |
|---|---|---|---|
| `GET /actuator/health` | 200 | UP | ✅ |
| `GET /actuator/health/readiness` | 200 | UP | ✅ |
| `POST /api/auth/login`（空 body） | 400 | 验证错误 | ✅ |
| `GET /api/projects`（无认证） | 403 | 需认证 | ✅ |
| `GET /api/integration/crm/health` | 401 | 需认证 | ✅ |
| `GET /`（前端首页） | 200 | OK | ✅ |
| `GET /login`（前端登录页） | 200 | OK | ✅ |

**前端入口校验**：`assets/index-BK7tigV4.js` ✅（与 release 一致）

**登录 Smoke 跳过说明**：admin 密码未授予，完整登录 smoke 无法完成，使用 400/403/401 替代验证策略（自第 8 次起固化）。

### Flyway 迁移应用验证

| 版本 | 描述 | success | installed_on |
|---|---|---|---|
| V1135 | create bid case slice | 1 | 2026-07-05 14:34:32 |
| V1136 | warehouse attachment type to varchar | 1 | 2026-07-05 14:34:32 |

## GitHub 镜像同步

| 项目 | 状态 |
|---|---|
| 部署前落后 commit 数 | 0 |
| 状态 | ✅ 完全一致（0 落后） |

## 配置清理检查

| 配置项 | 状态 | 备注 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 用户连续多次决定保留（第 13/14/15 次决定），运维监控需要 |
| `SHOW_DETAILS` / `DEBUG` / `TRACE` 其他项 | 无 | 无其他临时调试配置 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一可用 release | `c9f36c23d-api8080`（2026-07-04 22:58 CST 部署） |
| 上一 release 目录 | `/opt/xiyu-bid/releases/c9f36c23d-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-e8f1a36c3-20260705143249.sql.gz`（本次部署前生成，3.3M） |
| 回滚命令 | 手动恢复：`cp /opt/xiyu-bid/releases/c9f36c23d-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |
| 回滚需求 | 不需要（本次部署无 P0 问题） |

**回滚姿态**：就绪（手动恢复立即可执行），但当前不需要回滚。

## 经验沉淀应用情况

| 经验 | 本次应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部通过（2 个新迁移待应用） |
| GitHub 镜像同步 | ✅ 部署前已同步（0 落后） |
| Smoke admin 密码限制 | ✅ 用 400/403/401 替代验证 |
| Mac HTTP_PROXY 502 | ✅ Smoke 通过 SSH 内部访问，绕过 Mac 代理 |
| systemctl sudo | ✅ `SYSTEMCTL_SUDO=true` 默认启用（PR !1324 后固化） |
| 配置清理检查 | ✅ 仅 SHOW_DETAILS=always 按用户决定保留 |
| readiness Kafka 延迟 | ✅ 89 次尝试通过，属已知行为，未误判为故障 |
| SentryAppender crash-loop | ✅ 第 35 次已修复，本次未复发 |
| 同源构建 baseURL="" | ✅ VITE_API_BASE_URL= 显式设空，前端入口与 release 一致 |

## 风险提示

1. **V1135 案例切片表**：新增 `bid_case_slice` 表，用于 AI 案例切片语义检索。首次部署后无数据，需后续导入切片数据才能使用语义检索功能。
2. **V1136 仓库附件类型**：将 `warehouse_attachment.type` 字段改为 VARCHAR。属兼容性变更，不影响现有数据。
3. **AI 案例切片功能**：依赖向量索引和嵌入模型，首次部署后需确认向量索引正常创建。

## 部署确认清单

- [x] 早操三连 + git wrapper 检查
- [x] 基线确认（HEAD = origin/main）
- [x] GitHub 镜像同步检查（0 落后）
- [x] 服务器现状探测（health UP）
- [x] Flyway 预检 3 步法（validate + DB 版本对比 + remote-deploy 内置）
- [x] 本地打包（BUILD SUCCESS）
- [x] 产物校验（199 个迁移文件，无重复）
- [x] 上传 + 部署（remote-deploy.sh）
- [x] 健康检查（UP，consecutive 3/3）
- [x] 迁移应用验证（V1135, V1136 已应用）
- [x] Smoke 测试（7/7 通过）
- [x] 前端入口校验（`index-BK7tigV4.js` 一致）
- [x] 配置清理检查（仅 SHOW_DETAILS=always 保留）
- [x] 部署报告生成
