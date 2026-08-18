# Data Model: 历史投标项目信息批量导入

**Feature**: 041-historical-project-import
**Date**: 2026-08-06

## 实体关系

```
Excel 采集表（19 列）
    │
    ▼
HistoricalProjectRow（内部 record，纯核心）
    │
    ├──→ Project（projects 表）
    ├──→ Tender（tenders 表，占位标讯）
    ├──→ ProjectInitiationDetails（project_initiation_details 表）
    ├──→ ProjectResult（project_result 表，仅终态创建）
    └──→ ProjectLeadAssignment（project_lead_assignment 表）
            │
            └──→ User（users 表，按工号关联）
```

## 字段映射表

### 采集表列 → 数据库表.列

| 列 | 采集表字段 | 必填 | 目标表.列 | 类型 | 转换规则 |
|---|---|---|---|---|---|
| A | 项目名称 | ✅ | projects.name | VARCHAR(500) NOT NULL | 直接 |
| B | 招标主体 | ✅ | projects.customer + tenders.purchaser_name | VARCHAR(255) | 直接 |
| C | 来源平台 | ✅ | projects.platform + tenders.source_platform | VARCHAR(255) | 直接 |
| D | 项目状态 | ✅ | projects.status | ENUM NOT NULL | 中文→枚举（见状态映射） |
| E | 总部所在地 | ✅ | projects.region + tenders.region | VARCHAR(100) | 直接 |
| F | 客户类型 | ✅ | projects.customer_type + tenders.customer_type | VARCHAR(100) | 直接 |
| G | 项目类型 | ✅ | projects.industry + tenders.industry | VARCHAR(100) | 直接 |
| H | 优先级 | ✅ | tenders.priority | VARCHAR(10) | S/A/B/C 直接 |
| I | 客户营收(亿) | ❌ | project_initiation_details.annual_revenue | DECIMAL(20,2) | ×100000000 转元 |
| J | 计划入围供应商数 | ❌ | project_initiation_details.expected_bidders | INT | 直接 |
| K | 开标时间 | ✅ | projects.deadline + tenders.bid_opening_time | DATE / DATETIME | 多格式解析（见 R-003） |
| L | 投标月份 | ❌ | project_initiation_details.bid_month | VARCHAR(16) | yyyy-MM，可从开标时间推导 |
| M | 联系人1姓名 | ❌ | tenders.contact_name | VARCHAR(100) | 直接 |
| N | 联系人1电话 | ❌ | tenders.contact_phone | VARCHAR(50) | 直接 |
| O | 项目负责人 | ✅ | project_lead_assignment.primary_lead_user_id + tenders.project_manager_name | BIGINT + VARCHAR | 解析"姓名（工号）"→users.id（见用户解析） |
| P | 投标负责人 | ✅ | projects.manager_id + tenders.bidding_person_name | BIGINT NOT NULL + VARCHAR | 同上 |
| Q | 项目服务周期(年) | ✅ | project_result.service_period_years | DECIMAL(5,1) | 直接 |
| R | 项目服务周期截止时间 | ✅ | projects.end_date + project_result.service_period_end_date | DATETIME / DATE | 直接 |
| S | 项目阶段 | ✅ | projects.stage | VARCHAR(32) NOT NULL | 历史项目固定 CLOSED |

## 状态映射

### 项目状态（projects.status）

| 采集表中文名 | 枚举值 | 说明 |
|---|---|---|
| 已中标 | WON | 终态 |
| 未中标 | LOST | 终态 |
| 已流标 | FAILED | 终态 |
| 已放弃 | ABANDONED | 终态 |

其他值（投标中/评标中/待立项/已立项）→ 失败，原因"状态不在允许范围（仅支持已中标/未中标/已流标/已放弃）"

### 项目阶段（projects.stage）

| 采集表值 | 枚举值 |
|---|---|
| 项目结项 | CLOSED |

历史项目导入时 stage 固定为 `CLOSED`（因为所有历史项目都是终态，终态要求 stage=CLOSED）。

### 项目结果类型（project_result.result_type）

| 采集表状态 | result_type |
|---|---|
| 已中标 | WON |
| 未中标 | LOST |
| 已流标 | FAILED |
| 已放弃 | ABANDONED |

## 用户解析规则

### 输入格式

"姓名（工号）"，如"李四（123456）"或"王五（012345）"

支持的全角/半角括号：
- `（）` 全角括号
- `()` 半角括号

### 解析步骤

1. 正则匹配：`^(.+?)[（(](.+?)[）)]$`
2. 提取姓名和工号
3. 按工号查 `users.employee_number` → `users.id`
4. 工号查不到 → 失败，原因"投标负责人工号 XXX 未找到对应用户"

### 批量查询

所有行的工号一次性查询（`SELECT id, employee_number FROM users WHERE employee_number IN (...)`），构建 `Map<String, Long> 工号→用户ID`。遵循 Constitution V §批量优先。

## 数据库写入顺序（单行）

```
1. 查询 external_id = "HISTORICAL_{MD5(项目名称+招标主体)}" 是否已存在
   └── 存在 → 跳过，记入"已存在跳过"清单
2. 解析"投标负责人"和"项目负责人"工号 → users.id
   └── 工号不存在 → 失败，记入失败清单
3. 创建占位 Tender
   ├── external_id = "HISTORICAL_{MD5(项目名称+招标主体)}"
   ├── title = 项目名称
   ├── purchaser_name = 招标主体
   ├── source_platform = 来源平台
   ├── region = 总部所在地
   ├── customer_type = 客户类型
   ├── industry = 项目类型
   ├── priority = 优先级
   ├── bid_opening_time = 开标时间
   ├── contact_name = 联系人姓名
   ├── contact_phone = 联系人电话
   ├── project_manager_name = 项目负责人姓名
   ├── bidding_person_name = 投标负责人姓名
   ├── status = ABANDONED（占位标讯，标记为已放弃）
   └── source = "历史项目导入"
4. 创建 Project
   ├── name = 项目名称
   ├── tender_id = step3 创建的 tender.id
   ├── manager_id = 投标负责人 users.id
   ├── status = 状态枚举（WON/LOST/FAILED/ABANDONED）
   ├── stage = "CLOSED"
   ├── customer = 招标主体
   ├── platform = 来源平台
   ├── region = 总部所在地
   ├── customer_type = 客户类型
   ├── industry = 项目类型
   ├── deadline = 开标时间（LocalDate）
   ├── end_date = 服务周期截止时间
   └── source_module = "历史项目导入"
5. 创建 ProjectInitiationDetails
   ├── project_id = step4 创建的 project.id
   ├── expected_bidders = 计划入围供应商数
   ├── annual_revenue = 客户营收 ×100000000
   ├── bid_month = 投标月份（或从开标时间推导 yyyy-MM）
   ├── customer_type = 客户类型
   ├── bid_open_time = 开标时间
   ├── locked = true（历史数据锁定）
   └── owner_unit = 招标主体
6. 创建 ProjectResult（仅终态状态）
   ├── project_id = step4 创建的 project.id
   ├── result_type = 状态枚举（WON/LOST/FAILED/ABANDONED）
   ├── service_period_years = 服务周期(年)
   ├── service_period_end_date = 服务周期截止时间
   ├── registered_at = 当前时间
   └── summary = "历史项目导入"
7. 创建 ProjectLeadAssignment
   ├── project_id = step4 创建的 project.id
   ├── primary_lead_user_id = 项目负责人 users.id
   ├── assigned_at = 当前时间
   └── assigned_by = 当前操作用户 ID
8. 触发档案归档（archiveIfEnabled）
```

## 新增字段/表

本功能**不新增数据库表或列**，完全复用现有表结构：

- `projects`：复用现有列（V73 + V1044 + V1183）
- `tenders`：复用现有列（V73 + V75 + V107 + V118）
- `project_initiation_details`：复用现有列（V108 + V112 + V1031）
- `project_result`：复用现有列（V108 + V113 + CO-590 新增的 service_period_years/service_period_end_date）
- `project_lead_assignment`：复用现有列（V110）
- `users`：只读查询（employee_number → id）

## 权限迁移

新增 Flyway 迁移脚本：为 admin 角色添加 `project:import` 权限键。

```sql
-- V{next}__add_project_import_permission.sql
INSERT INTO role_profile_permissions (role_code, permission_key)
VALUES ('admin', 'project:import')
ON DUPLICATE KEY UPDATE permission_key = permission_key;
```

（具体迁移脚本在 tasks 阶段生成，用 `scripts/new-migration.sh` 创建）

## 占位标讯过滤

标讯列表查询接口需要过滤 `external_id LIKE 'HISTORICAL_%'` 的占位标讯。

方案：在 `TenderRepository` 的列表查询方法中追加 `AND external_id NOT LIKE 'HISTORICAL_%'` 条件。项目详情页按 `tender_id` 精确查询不受影响。
