# 第 114 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | test（测试环境） |
| Release ID | `557d3091d` |
| 部署时间 | 2026-08-02 09:52:45 CST |
| 服务器 | winbid-01（172.16.38.78） |
| 部署类型 | 增量部署（CO-601 自定义字段 + 评分草稿 + 通知修复 + 知识库清理） |
| 健康状态 | ✅ UP |
| 回滚状态 | 不需要 |

## 基线信息

| 项目 | 值 |
|---|---|
| Worktree | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae-init`（锚点分支） |
| HEAD commit | `557d3091d` |
| 上一版本 commit | `eeba9690a`（第 113 次部署） |
| GitHub 镜像同步 | ⚠️ 落后 2 commit（VPN 干扰 SSH，待稍后同步） |

## PR 列表

| PR # | 标题 | 类型 |
|---|---|---|
| !2242 | chore(locks): prune stale expired locks | maintenance |
| !2241 | docs: 知识沉淀 + wiki 回填 - T034 E2E 失败根因模式 | docs |
| !2240 | docs: 收尾流程调整-知识沉淀提前到合并PR前 + lesson #95 | docs |
| !2238 | perf(project): getProjectStatistics 改用 DB 聚合替代 findAll+内存计数 | perf |
| !2237 | feat(score-draft): 打通 AI 评分标准分析到评分草稿的数据流 | feature |
| !2236 | chore(knowledge): 清理 V999 双 Tab 时期遗留的孤儿组件和死 API 方法 | refactor |
| !2235 | CO-601 项目三表单已有字段锁定 + 自定义字段扩展 | feature |
| !2234 | fix(notification): Warehouse/TenderReminder 通知接通企微外发并透传 body | bug fix |
| !2233 | docs(wiki): Flyway 陷阱集新增 §11 INSERT IGNORE + NULL 唯一键不幂等 | docs |
| !2231 | fix(notification): 企微CA通知文案丢失body+点击跳转工作台 | bug fix |
| !2229 | feat(workflow-forms): 简化工作流表单配置 + 删除废弃 knowledge.case | refactor |
| !2228 | docs(lessons): 第 90 条 - purchaserName 复合 bug 六次修复才成功的真正根因 | docs |

## 改动范围

### 主要功能

1. **CO-601 项目三表单自定义字段**（!2235）
   - 项目立项/详情/结项三表单支持自定义字段扩展
   - 设计器预置字段锁定 + key 冲突校验
   - CustomFieldsCodec JSON 编解码 + useCustomFields composable
   - 新增 V1182（清理无用表单定义）+ V1183（项目表加 custom_fields JSON 列）

2. **AI 评分标准分析到评分草稿数据流**（!2237）
   - 4 维度（资质/技术/商务/风险）正则兜底提取
   - 权重总和校验 + 归一化到 100 分
   - 评分项正则兜底提取器 + 别名词表

3. **通知企微外发修复**（!2234, !2231）
   - Warehouse/TenderReminder 通知接通企微外发并透传 body
   - 企微 CA 通知文案丢失 body + 点击跳转工作台修复

4. **性能优化**（!2238）
   - getProjectStatistics 改用 DB 聚合替代 findAll+内存计数

5. **知识库清理**（!2236, !2229）
   - 清理 V999 双 Tab 时期遗留的孤儿组件和死 API 方法
   - 简化工作流表单配置 + 删除废弃 knowledge.case

### 新增 Flyway 迁移

| 版本 | 文件 | 说明 |
|---|---|---|
| V1182 | `V1182__remove_unused_form_definitions.sql` | 清理无用表单定义 |
| V1183 | `V1183__add_custom_fields_to_project_tables.sql` | 项目表加 custom_fields JSON 列 |

## Flyway 预检结果

| 步骤 | 结果 | 详情 |
|---|---|---|
| Step 1: validate | ✅ 通过 | 242 migrations validated, all checksums match |
| Step 2: DB 版本对比 | ✅ 正常 | DB 最新 V1181（07-29），待应用 V1182+V1183 |
| Step 3: remote-deploy 内置 | ✅ 通过 | 部署时自动 validate 通过 |

## 部署步骤

| 步骤 | 结果 | 详情 |
|---|---|---|
| 环境门禁 | ✅ | 用户确认测试环境 172.16.38.78 |
| 早操三连 | ✅ | sync-env + check-git-wrapper（session 开头已执行） |
| 基线确认 | ✅ | HEAD=557d3091d，工作区干净 |
| 服务器现状 | ✅ | 上一版本 eeba9690a（07-30），health UP |
| Flyway 预检 | ✅ | 3 步法全部通过 |
| 本地打包 | ✅ | VITE_OBS_ENABLED=true + VITE_API_BASE_URL= + COPYFILE_DISABLE=1 |
| 产物校验 | ✅ | obsEnabled=true, jar 内 V1182+V1183 无重复 |
| 上传 + 部署 | ✅ | scp + remote-deploy.sh（SYSTEMCTL_SUDO=true） |
| 前端资源保留 | ℹ️ | 上一版本 release 目录不含 frontend，无需保留 |
| 健康检查 | ✅ | consecutive 3/3, 80 attempts |
| 迁移应用验证 | ✅ | V1182 + V1183 均已应用（08-02 09:52:53） |

## 验证结果

### 后端健康检查

| 检查项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | 200 UP | ✅ |
| `/actuator/health/readiness` | 200 UP | 200 UP | ✅ |

### API Smoke 测试

| 检查项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `POST /api/auth/login`（空 body） | 400 | 400 | ✅ |
| `GET /api/projects`（无认证） | 403 | 403 | ✅ |
| `GET /api/integration/crm/health`（无认证） | 401 | 401 | ✅ |

### 前端验证

| 检查项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `GET /`（经 Nginx 8080） | 200 | 200 | ✅ |
| `GET /login`（经 Nginx 8080） | 200 | 200 | ✅ |
| 前端入口 asset | 与 release 一致 | `assets/index-B6Kl5ykP.js` | ✅ |

### Flyway 迁移应用验证

```sql
SELECT version, description, success, installed_on
FROM flyway_schema_history WHERE version IN ('1182', '1183') ORDER BY version;

version  description                                  success  installed_on
1182     remove unused form definitions               1        2026-08-02 09:52:53
1183     add custom fields to project tables          1        2026-08-02 09:52:53
```

## GitHub 镜像同步

| 项目 | 状态 |
|---|---|
| GitHub main 落后 Gitee | 2 commit |
| 同步尝试 | ❌ 失败（VPN 代理干扰 SSH 连接，198.18.0.28 连接被关闭） |
| 待办 | VPN 稳定后执行 `bash scripts/sync-to-github.sh` |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 历史决定保留（第 13-15 次均确认） |
| `DEBUG` / `TRACE` 临时配置 | 无 | 干净 |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ 部署前执行 validate + DB 版本对比 |
| OBS 直传双保险 | ✅ VITE_OBS_ENABLED=true 显式传入 + 产物校验 obsEnabled=true |
| 同源构建 | ✅ VITE_API_BASE_URL= 显式设空 |
| macOS ._* 残留 | ✅ COPYFILE_DISABLE=1 |
| SYSTEMCTL_SUDO=true | ✅ 避免第 15 次 Interactive authentication 事故 |
| 前端 hash 资源跨版本 404 | ℹ️ 检查了上一版本 release 目录（不含 frontend） |

## 风险提示

1. **GitHub 镜像落后 2 commit**：VPN 代理干扰 SSH，需 VPN 稳定后同步
2. **V1183 破坏性 schema 变更**：新增 `custom_fields` JSON 列，回滚需从 DB 备份恢复
3. **Kafka SDK readiness 延迟**：已知行为，本次未出现（health 首次即通过）

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚方式 | 回退到上一版本 jar + 前端 |
| 上一版本 Release ID | `eeba9690a` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/eeba9690a` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-557d3091d-*.sql.gz` |
| 回滚命令 | `ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && cp /opt/xiyu-bid/releases/eeba9690a/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl start xiyu-bid-backend'` |
| ⚠️ DB 回滚 | V1182+V1183 为 DDL 变更，回滚需从 DB 备份恢复整表 |

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连 + 基线干净
- [x] Flyway 预检 3 步法通过
- [x] 打包参数正确（OBS + 同源 + COPYFILE_DISABLE）
- [x] 产物校验通过（obsEnabled + 迁移文件无重复）
- [x] 部署成功（remote-deploy.sh 退出码 0）
- [x] 健康检查通过（UP + readiness 200）
- [x] 迁移应用验证（V1182 + V1183 success=1）
- [x] Smoke 测试全部符合预期
- [x] 前端入口一致
- [x] 配置清理检查（无临时调试配置）
- [ ] GitHub 镜像同步（待 VPN 稳定）
- [x] 部署报告生成
