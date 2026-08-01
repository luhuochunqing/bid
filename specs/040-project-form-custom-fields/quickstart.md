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
