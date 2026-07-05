# 第 46 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署日期 | 2026-07-04 22:58 CST |
| Release ID | `c9f36c23d-api8080` |
| 上一版本 | `a759eda25-api8080`（今日 19:59 CST 部署，报告缺失） |
| 部署类型 | 增量部署（业务功能更新 + UI 修复，无 DB 迁移） |
| 部署结果 | ✅ 成功 |
| 健康检查 | ✅ UP（consecutive 3/3，总尝试 80 次） |
| Readiness | ✅ UP（80 次尝试说明有 Kafka SDK 延迟，属已知行为） |
| 部署耗时 | 约 2 分钟（22:57 打包完成 → 22:58 服务重启 → 健康检查通过） |

## 基线信息

| 项目 | 值 |
|---|---|
| 工作区 | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae-init`（锚点分支） |
| HEAD commit | `c9f36c23ddd47d0c33271c68b65ccde30eae3729` |
| 工作区状态 | 干净 |
| origin/main 同步 | ✅ HEAD = origin/main（早操 sync-env.sh 已补 6 个 commit） |
| GitHub 镜像 | ✅ 同步完成（部署前落后 11 个 commit，部署后 0 落后） |

## 增量 PR 列表（11 个 commit，`a759eda25..c9f36c23d`）

| Commit | PR | 描述 |
|---|---|---|
| `9f026bdc2` | — | fix(project): CO-497 复盘提交后自动跳转结项阶段，删除进入结项按钮和可进入状态 |
| `254b06df4` | — | feat(tender): CO-502 标讯批量导入模板客户类型/优先级/项目类型改为必填 |
| `6c4d300a2` | — | fix: CO-496 切换文件视图时不加载数据的 bug |
| `84413228e` | !1689 | fix: CO-496 切换文件视图时不加载数据 |
| `8265c4183` | — | docs(lessons): CO-469 七轮修复全记录，沉淀系统性根因与防复发 |
| `24fd53b3e` | — | fix(personnel): 编辑/删除按钮强制同行排列 |
| `f37001f4f` | — | fix(archive): 档案详情抽屉文件表格紧凑化 + 按钮同行 |
| `05623a25a` | !1691 | fix(personnel): 编辑/删除按钮强制同行排列 |
| `76b1d8f76` | !1690 | docs(lessons): CO-469 七轮修复全记录，沉淀系统性根因与防复发 |
| `6d9f49bf7` | !1688 | feat(tender): CO-502 标讯批量导入模板客户类型/优先级/项目类型改为必填 |
| `c9f36c23d` | !1687 | fix(project): CO-497 复盘提交后自动跳转结项阶段 |

## 改动范围

**核心业务变更**（4 个功能模块）：

### 1. CO-497：复盘提交后自动跳转结项阶段（fix(project)）
- 删除"进入结项"按钮和可进入状态
- 复盘提交后自动跳转到结项阶段，简化流程
- 涉及模块：项目复盘阶段、结项阶段流转

### 2. CO-502：标讯批量导入模板字段必填（feat(tender)）
- 客户类型、优先级、项目类型改为必填
- 涉及模块：标讯批量导入模板

### 3. CO-496：切换文件视图时不加载数据 bug 修复（fix）
- 修复切换文件视图时错误加载数据的问题
- 涉及模块：文件视图切换逻辑

### 4. UI 修复：人员/档案模块按钮排列 + 紧凑化（fix(personnel/archive)）
- 人员证书编辑/删除按钮强制同行排列
- 档案详情抽屉文件表格紧凑化 + 按钮同行
- CO-469 七轮修复全记录文档沉淀（系统性根因与防复发）

## Flyway 预检结果（3 步法）

| 步骤 | 结果 | 备注 |
|---|---|---|
| Step 1: validate | ✅ OK | 198 migrations, all checksums match（execution time 0.088s） |
| Step 2: DB 版本对比 | ✅ 一致 | DB V1134（2026-07-04 15:51:45 应用）= 源码 V1134（无新迁移） |
| Step 3: remote-deploy 内置 validate | ✅ 通过 | 部署时自动执行，VALIDATE OK - all checksums match |

**结论**：无新迁移需要应用，纯业务代码 + UI 修复部署。

## 部署步骤

### 1. 早操三连

```bash
bash scripts/sync-env.sh .       # ✅ rebase origin/main 成功（落后 6 个 commit 已补上）
bash scripts/check-git-wrapper.sh # ✅ 7/7 通过
```

> 注：`source scripts/dev-env.sh` 因锚点分支守卫拦截跳过（部署场景非开发行为）。

### 2. 本地打包（生产同源构建模式）

```bash
RELEASE_ID="c9f36c23d-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh
```

- ✅ BUILD SUCCESS（后端 23.256 s，前端构建正常）
- ✅ jar 内 Flyway 迁移版本无重复（197 个 V*.sql）
- ✅ 产物：`.release/xiyu-bid-release-c9f36c23d-api8080.tar.gz`（138M）
- ✅ 前端入口：`assets/index-Cs7vAxb3.js`（同源构建，无 IP 字面量）

### 3. 上传 + 部署

```bash
scp .release/xiyu-bid-release-c9f36c23d-api8080.tar.gz scripts/release/remote-deploy.sh \
    jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 'RELEASE_ARCHIVE=... RELEASE_ID=c9f36c23d-api8080 \
    SYSTEMCTL_SUDO=true ... bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- ✅ Flyway validate 通过（198 migrations，0.092s）
- ✅ DB 备份完成（`/opt/xiyu-bid/db-backups/winbid-c9f36c23d-api8080-20260704225825.sql.gz`，3.2M）
- ✅ 后端服务重启（PID 9107，2026-07-04 22:58:32 CST）
- ✅ 健康检查通过（consecutive 3/3，总尝试 80 次）
- ✅ 前端一致性验证通过（`index-Cs7vAxb3.js` 与 release 一致）

## 验证结果

### 健康检查

| 端点 | 状态 | 备注 |
|---|---|---|
| `/actuator/health` | ✅ UP | 全组件 UP（aiProvider doubao / db / diskSpace / jwt / livenessState / ping / readinessState / redis / sidecar） |
| `/actuator/health/readiness` | ✅ UP | readinessState UP（80 次尝试说明有 Kafka SDK 延迟，属已知行为） |

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

**前端入口校验**：`assets/index-Cs7vAxb3.js` ✅（与 release 一致）

**登录 Smoke 跳过说明**：admin 密码未授予，完整登录 smoke 无法完成，使用 400/403/401 替代验证策略（自第 8 次起固化）。

## GitHub 镜像同步

| 项目 | 状态 |
|---|---|
| 部署前落后 commit 数 | 11 |
| 同步命令 | `bash scripts/sync-to-github.sh` |
| 同步后 Gitee main | `c9f36c23ddd47d0c33271c68b65ccde30eae3729` |
| 同步后 GitHub main | `c9f36c23ddd47d0c33271c68b65ccde30eae3729` |
| 状态 | ✅ 完全一致（0 落后） |

## 配置清理检查

| 配置项 | 状态 | 备注 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 用户连续多次决定保留（第 13/14/15 次决定），运维监控需要 |
| `SHOW_DETAILS` / `DEBUG` / `TRACE` 其他项 | 无 | 无其他临时调试配置 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一可用 release | `a759eda25-api8080`（今日 19:59 CST 部署） |
| 上一 release 目录 | `/opt/xiyu-bid/releases/a759eda25-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-c9f36c23d-api8080-20260704225825.sql.gz`（本次部署前生成，3.2M） |
| 回滚命令 | `bash /opt/xiyu-bid/bin/rollback-to-release.sh a759eda25-api8080` |
| 回滚需求 | 不需要（本次部署无 P0 问题） |

**回滚姿态**：就绪（rollback 立即可执行），但当前不需要回滚。

## 经验沉淀应用情况

| 经验 | 本次应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部通过（无新迁移） |
| GitHub 镜像同步 | ✅ 部署后立即同步（落后 11 → 0） |
| Smoke admin 密码限制 | ✅ 用 400/403/401 替代验证 |
| Mac HTTP_PROXY 502 | ✅ Smoke 通过 SSH 内部访问，绕过 Mac 代理 |
| systemctl sudo | ✅ `SYSTEMCTL_SUDO=true` 默认启用（PR !1324 后固化） |
| 配置清理检查 | ✅ 仅 SHOW_DETAILS=always 按用户决定保留 |
| readiness Kafka 延迟 | ✅ 80 次尝试通过，属已知行为，未误判为故障 |
| SentryAppender crash-loop | ✅ 第 35 次已修复，本次未复发 |
| 同源构建 baseURL="" | ✅ VITE_API_BASE_URL= 显式设空，前端入口与 release 一致 |

## 风险提示

1. **今日部署频繁（8 次）**：今日已有 a759eda25、eb8ce51a5、76c425667、860e7aea8、3dbec41cd、d12e4c36e、4b3d6ec9a 等多次部署，加上本次 c9f36c23d 共 8 次。频繁部署增加了变更窗口风险，建议后续合并小修小补为单次发布。
2. **a759eda25 报告缺失**：第 45 次报告（eb8ce51a5）之后的 a759eda25 部署未生成报告，违反"每次部署必须生成报告"纪律（skill 第 7 条经验）。本次报告中已记录其作为上一版本，但建议补录。
3. **CO-497 流程简化需 UAT 验证**：删除"进入结项"按钮和可进入状态后，复盘提交到结项的自动跳转需 UAT 验证不同角色（投标管理员/项目负责人/投标组长）的流程一致性。
4. **CO-502 模板字段必填影响存量数据**：客户类型/优先级/项目类型改为必填后，存量标讯数据若有空值可能在批量导入模板回显时报错，建议监控。
5. **`SHOW_DETAILS=always` 仍在生产生效**：暴露健康详情（DB/Redis/JWT/AI Provider 等组件信息）。如需收紧安全，可改为 `never` 并重启后端。

## 部署确认清单

- [x] 早操三连通过（sync-env + check-git-wrapper）
- [x] 工作区干净，HEAD = origin/main
- [x] Flyway 预检 3 步法通过（validate + DB 版本对比 + remote-deploy 内置）
- [x] 本地打包成功（jar 内 197 个 V*.sql 无重复迁移版本）
- [x] 产物校验通过（前端入口 index-Cs7vAxb3.js 与 release 一致）
- [x] 上传 + 部署成功（remote-deploy.sh 退出码 0）
- [x] 健康检查通过（health UP, readiness UP，3/3 连续）
- [x] Smoke 测试通过（400/403/401 + 前端 200）
- [x] 前端一致性验证通过（index.html 入口与 release 一致）
- [x] GitHub 镜像同步完成（0 落后）
- [x] 配置清理检查完成（仅 SHOW_DETAILS=always 保留）
- [x] 部署报告生成

## 部署历史延续

本次部署为第 46 次。前一次有报告的部署为第 45 次（`eb8ce51a5-api8080`，2026-07-04 18:24 CST）。期间 `a759eda25-api8080`（今日 19:59 CST）已上线但报告缺失，本次报告将其记录为上一版本以保持可追溯性。

本次增量（11 个 commit）覆盖 4 个业务模块：CO-497 复盘自动跳转结项、CO-502 标讯导入模板必填、CO-496 文件视图切换 bug、人员/档案 UI 紧凑化 + CO-469 七轮修复文档沉淀。

---

**部署执行人**：Trae Agent（主工作区）
**报告生成时间**：2026-07-04 23:05 CST
