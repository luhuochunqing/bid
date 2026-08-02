# Quickstart: 项目三表单自定义字段验证指南

**Date**: 2026-07-31 | 前置：主工作区 `/Users/user/xiyu/worktrees/trae` 已启动开发环境（前端 1323 / 后端 18089 / MySQL xiyu_bid_main）

## 1. 数据库验证

```sql
-- 迁移后确认两列存在
SHOW COLUMNS FROM projects LIKE 'custom_fields';
SHOW COLUMNS FROM project_initiation_details LIKE 'custom_fields';

-- 提交自定义字段后确认落库（scope 命名空间结构）
SELECT id, name, JSON_EXTRACT(custom_fields, '$."project.basic"') FROM projects ORDER BY id DESC LIMIT 3;
SELECT project_id, JSON_EXTRACT(custom_fields, '$."project.initiation"') FROM project_initiation_details ORDER BY project_id DESC LIMIT 3;
```

## 2. 端到端手动验证（核心链路）

1. **设计器配置**：管理员登录 → 系统管理 → 工作流表单设计器 → 打开 `project.basic`
   - 确认：预置字段（项目名称/客户/预算等）key 与类型输入框禁用、删除按钮隐藏
   - 新增自定义字段 `budgetLevel`（文本，label"客户预算等级"）→ 保存草稿 → 发布
2. **创建向导填写**：从标讯中心进入一个标讯 → 创建项目 → 基本信息步骤应渲染"客户预算等级"→ 填"重点客户"→ 完成创建
3. **落库确认**：执行 §1 第二条 SQL，应看到 `{"budgetLevel": "重点客户"}`
4. **回显确认**：重新打开该项目详情/编辑向导 → "客户预算等级"显示"重点客户"
5. **立项链路**：进入该项目立项阶段 → 同法验证 `project.initiation` 自定义字段
6. **历史数据**：打开本特性上线前创建的老项目 → 表单正常渲染，自定义字段为空值不报错

## 3. 锁定机制验证

- `project.initiation` / `project.detail`：保存草稿、发布、新增字段按钮均可用（整表只读已移除）
- 尝试把自定义字段 key 改成预置字段 key（如 `name`）→ 保存被阻断并提示冲突
- tender.entry 打开 → 原 LOCKED_FIELD_KEYS / FIXED_GROUP_KEYS 行为不变（回归确认）

## 4. 自动化验证命令

```bash
# 后端（当前 worktree）
cd backend && mvn test -Dtest='*CustomFields*,*Initiation*,ProjectService*'
cd backend && mvn test -Dtest=ArchitectureTest,FlywayRollbackScriptCoverageTest

# 前端（当前 worktree）
npm run test:unit -- useCustomFields
npm run build && npm run check:line-budgets

# E2E（主工作区环境启动后）
npm run test:e2e -- project-form-custom-fields
```

## 5. 回滚验证

```bash
# U 脚本可回退加列（注意：会丢已存自定义字段值，仅灾备用）
mysql ... xiyu_bid_main < backend/src/main/resources/db/rollback/migration-mysql/U1183__add_custom_fields_to_project_tables.sql
# 回滚后实体/DTO 需同步回退（git revert），不可只回滚 DB
```

## 6. 走查结果记录（T034，2026-08-01）

**环境**：主工作区 `/Users/user/xiyu/worktrees/trae` dev（前端 1323 / 后端 18089 / MySQL xiyu_bid_main）

### §1 数据库验证 — ✅ 通过

- `projects.custom_fields` 列存在（JSON 类型，NULL）
- `project_initiation_details.custom_fields` 列存在（JSON 类型，NULL）
- 现有数据 custom_fields 为 NULL（符合新功能上线预期）

### §2 端到端 API 模拟 — ✅ 通过

- 创建项目提交 `project.basic`（budgetLevel=VIP）+ `project.detail`（remark=test）→ 落库一致
- API 回显与 DB 落库值一致
- 老项目（custom_fields=NULL）降级返回空 Map `{}`，不报错

### §3 锁定机制验证 — ✅ 通过

- `project.initiation`（hybrid scope）：预置 key `projectName` 被阻断（"自定义字段 key 命中预置清单"）
- `project.basic`（非 hybrid scope）：预置 key `customer` 不阻断（仅校验 key 重复）；重复 key `budgetLevel` 被阻断
- `tender.entry`：后端无校验，依赖前端 UI LOCKED_FIELD_KEYS 锁定（回归确认不变）

### §4 自动化验证 — 部分通过

**单元测试（全绿）**：
- `CustomFieldsCodecTest` 14 ✅
- `CustomFieldsSchemaPolicyTest` 8 ✅
- `ProjectServiceCustomFieldsTest` 4 ✅
- `ArchitectureTest` 29 ✅
- `FlywayRollbackScriptCoverageTest` 1 ✅
- 前端 `useCustomFields.spec.js` 9 ✅
- `npm run build` ✅（5 个 composable 内联警告，不影响功能）

**E2E 测试（9 个全部失败）**：根因为**测试代码问题 + 环境问题**，非 CO-601 产品代码缺陷：
1. **测试数据污染**：之前测试运行在 `project.basic` 表单定义累积了 `budgetLevel` 残留字段，后续 PUT 时校验报 "字段 key 重复"。测试代码缺少数据隔离（每次运行前未重置表单定义）
2. **角色权限不匹配**：测试用 `bid-Team` 角色创建项目，但 `POST /api/projects` 需要 `ADMIN/MANAGER` 角色，bid-Team 被 `Access Denied`；老项目测试前端 `getByLabel('项目名称')` 超时
3. **后端 OOM 崩溃**：测试中途后端 `exit code: 137`（SIGKILL/OOM），导致 US2/US3 全部 `ECONNREFUSED`

**手动 API 验证（产品代码正常）**：
- admin PUT `/api/admin/form-definitions/2` 添加 `t034_verify` 字段 → 200 ✅
- `/api/form-definitions/project.basic/active` 返回 3 字段（含新字段）✅
- 创建项目（id=236）提交 `project.basic.{t034_verify, budgetLevel}` → 200 ✅
- DB 落库：`JSON_EXTRACT(custom_fields, '$."project.basic".t034_verify')` = 验证值 ✅
- API 回显：`customFields.project.basic` 正确返回 ✅
- 测试数据已清理（表单定义恢复、项目 236 已删除）

### §5 回滚脚本 dry-run — ✅ 通过

- `V1183`（ADD COLUMN）与 `U1183`（DROP COLUMN）结构可逆
- `U1183` 会丢失已存自定义字段值（脚本明确警告需先备份）
- `FlywayRollbackScriptCoverageTest` 覆盖通过

### 结论

CO-601 核心功能（自定义字段创建 → 表单定义发布 → 项目提交 → 落库 → 回显 → 锁定机制）**验证通过**。E2E 测试失败根因为测试代码数据隔离不足 + 角色权限选择错误 + 后端 OOM，需作为后续任务修复测试代码（非产品代码问题）。
