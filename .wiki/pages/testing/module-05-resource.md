---
title: 资源管理 — 蓝图功能实现对照
space: engineering
category: testing
tags: [testing, 蓝图对照, 资源管理, resource]
sources:
  - .wiki/sources/testing/module-05-resource-test.md
  - .wiki/sources/testing/module-05-resource-test.md
backlinks:
  - _index
created: 2026-05-28
updated: 2026-05-27
health_checked: 2026-06-05
---
> 蓝图章节：§4.5 资源管理
> 对应飞书蓝图：https://my.feishu.cn/docx/FgLAdRmFho4QhwxncgAcfxKJn0d

## 覆盖度总览

| 蓝图功能 | 实现状态 | 测试方式 | 关键文件 |
|---------|---------|---------|---------|
| 保证金管理(登记/退还/核销) | ✅ 已完成 | API | `/api/knowledge/deposit/*`, `ExpenseController` |
| 保证金超期预警 | ✅ 已完成 | API | `ScanDepositReturnTrackingAppService` |
| 费用申请 | ✅ 已完成 | API + E2E | `POST /api/resources/expenses` |
| 费用审批流程 | ✅ 已完成 | API | `POST /{id}/approve` |
| 费用支付跟踪 | ✅ 已完成 | API | `POST /{id}/payments` |
| 费用台账(多维度统计) | ✅ 已完成 | API | `GET /ledger`, `GET /project/{id}/statistics` |
| 平台账号 CRUD | ✅ 已完成 | API | `AccountController` + `PlatformAccountController` |
| 平台账号领用审批 | ✅ 已完成 | API | `POST /{id}/borrow`, `POST /{id}/return` |
| 密码加密存储/审计 | ✅ 已完成 | API | `PasswordEncryptionUtil`, 审计日志 |
| CA 信息管理 | ✅ 已完成 | API + 手动 | CAManagement.vue 统一看板（统计卡片、表格、筛选、详情抽屉、CRUD） |
| CA 借用申请流程 | ✅ 已完成 | API | `BarCertificateController` 借还 |
| CA 自动到期提醒 | ✅ 已完成 | API | `ScanExpiringQualificationsAppService` |
| 合同借用流程 | ✅ 已完成 | API + 手动 | `ContractBorrowController` 全生命周期 |
| 权限矩阵 | ✅ 已完成 | E2E | `@PreAuthorize` 全注解 |

## 功能 1：保证金管理

### 蓝图要求
保证金登记/退还/核销、超期预警、费用台账。

### 实现说明
- 前端：`DepositBoard.vue`（路由 `/knowledge/deposit`）— 统计卡片 + 台账列表
- 后端：`DepositTrackingController`（`/api/knowledge/deposit/*`）
- 超期规则：超过预计退还日期未退还标记为红色
- 费用模块保证金跟踪：`ExpenseController` 中 `return-request`/`confirm-return`/`return-reminder`

### 测试方式
API 测试

### 测试示例
```bash
# 登录
TOKEN=$(curl -s -X POST 'http://127.0.0.1:18081/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"XiyuAdmin2026!"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 保证金汇总
curl -s 'http://127.0.0.1:18081/api/knowledge/deposit/summary' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 台账列表
curl -s 'http://127.0.0.1:18081/api/knowledge/deposit/list' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 标记退还
curl -s -X POST 'http://127.0.0.1:18081/api/knowledge/deposit/return/1' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## 功能 2：费用管理

### 蓝图要求
费用申请、审批流程、支付跟踪。

### 实现说明
费用管理在 2 个后端模块并行实现（`fees` 模块 + `resources` 模块）：
- 前端：`src/views/Resource/Expense.vue`
- 主入口：`ExpenseController`（`/api/resources/expenses`）
  - 创建 → `POST /`
  - 审批 → `POST /{id}/approve`
  - 支付 → `POST /{id}/payments`
- 旧模块：`FeeController`（`/api/fees`）

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 创建费用申请
curl -s -X POST 'http://127.0.0.1:18081/api/resources/expenses' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"projectId":1,"expenseType":"保证金","amount":50000.00,"description":"xxx项目投标保证金","department":"投标部","applicantName":"张三"}'

# 审批通过(需 ADMIN/MANAGER)
curl -s -X POST 'http://127.0.0.1:18081/api/resources/expenses/1/approve' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"approved":true,"approverName":"系统管理员","comment":"审批通过"}'

# 登记支付
curl -s -X POST 'http://127.0.0.1:18081/api/resources/expenses/1/payments' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"amount":50000.00,"paymentMethod":"转账","paidBy":"张三","paidAt":"2026-05-20"}'

# 查询费用列表
curl -s 'http://127.0.0.1:18081/api/resources/expenses?page=0&size=10' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## 功能 3：费用台账

### 蓝图要求
按项目/费用类型的多维度汇总统计。

### 实现说明
- 前端台账表：`ExpenseLedgerTable.vue` + `ExpenseSummaryCards.vue`
- 后端：
  - `GET /api/resources/expenses/ledger` — 多维度台账（projectId, department, expenseType, dateRange）
  - `GET /api/resources/expenses/project/{id}/statistics` — 项目维度分类汇总
  - `GET /api/resources/expenses/project/{id}/total` — 项目总额

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 台账查询（多维度）
curl -s 'http://127.0.0.1:18081/api/resources/expenses/ledger?projectId=1&startDate=2026-01-01&endDate=2026-12-31' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 项目费用统计
curl -s 'http://127.0.0.1:18081/api/resources/expenses/project/1/statistics' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## 功能 4：平台账号管理

### 蓝图要求
平台台账（名称/URL/账号/联系人），新增/编辑/删除，账号领用审批流程。

### 实现说明
两个后端模块并行：
1. `AccountController`（`/api/resources/accounts` — 主入口，支持 type/industry 筛选）
2. `PlatformAccountController`（`/api/platform/accounts` — 带密码加密的借阅管理）
- 密码加密：`PasswordEncryptionUtil` — 加密存储 + 人员查看审计
- 借用流程：申请→保管员审批→线下交付→归还登记→改密码

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 创建账户
curl -s -X POST 'http://127.0.0.1:18081/api/resources/accounts' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"国采平台","type":"BID_PLATFORM","url":"https://www.ccgp.gov.cn","username":"xiyu_bid","contactPerson":"张三","contactPhone":"13800138000"}'

# 借用
curl -s -X POST 'http://127.0.0.1:18081/api/platform/accounts/1/borrow' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"purpose":"购买标书","projectId":"P001","dueHours":48}'

# 归还
curl -s -X POST 'http://127.0.0.1:18081/api/platform/accounts/1/return' \
  -H "Authorization: Bearer $TOKEN"
```

## 功能 5：CA 信息管理

### 蓝图要求
CA 类型(实体/电子)/印章/密码/有效期/保管人登记，CA 借用申请流程，自动到期提醒，审计日志。

### 实现说明
CA/证书管理功能分散在多个模块：
- 资质证书模块：`QualificationController` 管理 CERTIFICATION 类型证书
- BAR 证书模块：`BarCertificateController` 数字印章/CA 管理
- 平台模块：`PlatformAccountController` 密码加密管理
- 过期扫描：`ScanExpiringQualificationsAppService`
- **注意**：缺乏蓝图中描述的独立"CA 信息管理"统一仪表板

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 创建 CA 类资质证书
curl -s -X POST 'http://127.0.0.1:18081/api/knowledge/qualifications' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"CA数字证书-国信CA","certificateNo":"CA-2026-001","issuer":"国信CA中心","holderName":"西域公司","expiryDate":"2027-01-15","category":"CERTIFICATION"}'

# 借阅 CA 证书
curl -s -X POST 'http://127.0.0.1:18081/api/knowledge/qualifications/1/borrow' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"borrower":"李四","department":"投标部","purpose":"投标CA签章"}'

# 扫描 30 天内过期
curl -s -X POST 'http://127.0.0.1:18081/api/knowledge/qualifications/scan-expiring?thresholdDays=30' \
  -H "Authorization: Bearer $TOKEN"
```

## 功能 6：合同借用流程

### 蓝图要求
合同借用申请→审批→归还全生命周期管理。

### 实现说明
- 前端：`ContractBorrow.vue`
- 后端：`ContractBorrowController`（完整生命周期：创建/审批/驳回/归还/取消）
- 领域模型：`ContractBorrowApplication` + `ContractBorrowLifecyclePolicy` + 乐观锁

### 测试方式
API 测试

### 测试示例
```bash
TOKEN=...

# 创建借用申请
curl -s -X POST 'http://127.0.0.1:18081/api/contract-borrows' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"contractNo":"HT-2026-001","contractName":"xxx项目合同","borrowerName":"张三","borrowerDept":"投标部","purpose":"投标资格审查","expectedReturnDate":"2026-06-01"}'

# 审批通过
curl -s -X POST 'http://127.0.0.1:18081/api/contract-borrows/1/approve' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"comment":"审批通过"}'

# 归还
curl -s -X POST 'http://127.0.0.1:18081/api/contract-borrows/1/return' \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"comment":"合同已归还"}'

# 生命周期事件
curl -s 'http://127.0.0.1:18081/api/contract-borrows/1/events' \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

---

## 相关文件
- E2E: `e2e/commercial-main-flow.spec.js`
- 前端: `src/views/Resource/`
- 后端: `backend/src/main/java/com/xiyu/bid/fees/`, `platform/`, `contractborrow/`, `resources/`
