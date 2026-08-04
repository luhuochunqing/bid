# 第 116 次测试环境部署报告

- **环境**：测试（test）
- **日期**：2026-08-04
- **Release ID**：`c8cec7a16`
- **上一版本**：`41f342a8c`（第 115 次）

## 部署概览

| 项目 | 值 |
|------|-----|
| 目标服务器 | `172.16.38.78`（winbid-01） |
| 部署前健康状态 | UP |
| 增量 commit 数 | 6 |
| 新 Flyway 迁移 | 无 |
| 打包方式 | 同源构建（`VITE_API_BASE_URL=`） |
| OBS 直传 | 已启用 |
| 部署结果 | 成功 |

## 基线信息

| 项目 | 值 |
|------|-----|
| 源分支 | `agent/trae-init`（与 origin/main 一致） |
| HEAD commit | `c8cec7a16` |
| 工作区状态 | 干净 |

## PR 列表

| PR | 简述 |
|----|------|
| !2251 | docs(lessons): 新增 lessons #99 + #100 — PR 找回方法 + auto-stash 有害 revert |
| !2253 | docs(wiki): §5.4 jobNumber 三字段同源 + lessons #98 回填 |
| !2252 | fix(organization): 组织管理页修复（部门来源/部门显示/工号列） |

## 改动范围

- **文档**：lessons-learned.md、wiki 页面更新
- **前端**：组织管理页修复（`OrganizationManagement.vue`，部门名称映射、工号列展示）
- **后端**：无业务代码变更
- **数据库迁移**：无

## Flyway 预检

- 部署前 validate：OK（244 migrations，all checksums match）
- DB 最新版本：V1183（2026-08-02）
- 本次无新迁移

## 部署步骤

1. 早操同步 → 基线确认
2. 服务器现状检查（`deployed-release.json`、健康检查）
3. Flyway validate 预检通过
4. 本地打包（`RELEASE_ID=c8cec7a16`，同源构建 + OBS 直传）
5. 产物校验：jar 内迁移无重复、OBS 直传启用
6. 上传 + `remote-deploy.sh` 部署
7. 后端重启（systemd），健康检查通过（79 次尝试，约 2.5 分钟）

## 验证结果

| 检查项 | 结果 |
|--------|------|
| `/actuator/health` | 200 UP |
| `/actuator/health/readiness` | 200 UP |
| `/` | 200 |
| `/login` | 200 |
| `POST /api/auth/login` | 400（空密码验证拒绝） |
| `/api/projects` | 403（未认证拒绝） |
| `/api/integration/crm/health` | 401（未认证拒绝） |
| 前端资源一致性 | 通过（`index-FMwnTTg8.js`） |

## GitHub 同步

- 同步前：GitHub 落后 6 个 commit
- 同步后：Gitee main 与 GitHub main 完全一致（`c8cec7a16`）

## 回滚信息

- 上一版本 Release ID：`41f342a8c`
- 回滚方式：`remote-deploy.sh` 重新部署上一版本 release 包
- 回滚风险：无（本次无数据库迁移，直接回滚前端 + 后端 jar 即可）

## 风险提示

- 无

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操同步 + 基线确认
- [x] 服务器健康检查
- [x] Flyway 预检
- [x] 本地打包 + 产物校验
- [x] 上传 + 部署
- [x] 健康检查等待
- [x] Smoke 验证（7 项全部通过）
- [x] GitHub 镜像同步
- [x] 配置清理检查
- [x] 部署报告生成