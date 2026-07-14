# 客户商机洞察数据流规格

> 来源：2026-07-11 测试环境客户商机中心数据为 0 vs tenders 表 359 条 CRM 同步商机
> 本文档记录客户商机洞察功能的数据流、刷新机制和已知限制

---

## 1. 数据流概览

```
tenders 表（359 条 CRM 同步商机）
  ↓
snapshotMapper（标题提取公司名）
  ↓
groupByPurchaserHash（按采购人 hash 分组）
  ↓
CustomerOpportunityRefreshPolicy（评分策略）
  ↓
customer_predictions 表（206 条洞察数据）
  ↓
GET /api/customer-opportunities/insights（前端展示）
```

## 2. 关键组件

| 组件 | 职责 | 位置 |
|---|---|---|
| `tenders` 表 | 原始标讯数据，含 CRM 同步的商机 | MySQL |
| `snapshotMapper` | 从标讯标题提取采购公司名 | 后端 |
| `groupByPurchaserHash` | 按采购人 hash 分组聚合 | 后端 |
| `CustomerOpportunityRefreshPolicy` | 评分策略，计算商机优先级 | 后端 |
| `customer_predictions` 表 | 洞察结果表，前端读取此表 | MySQL |
| `POST /api/customer-opportunities/refresh` | 手动刷新接口 | 后端 |
| `GET /api/customer-opportunities/insights` | 前端查询接口 | 后端 |

## 3. 刷新机制

### 3.1 手动刷新（当前唯一方式）

```bash
# 手动触发刷新
curl -X POST http://127.0.0.1:18089/api/customer-opportunities/refresh \
  -H "Authorization: Bearer <token>"
```

- **权限要求**：ADMIN 或管理员角色（`refreshInsights()` 方法注解 `@PreAuthorize`）
- **注意**：MANAGER 角色已不存在，应使用当前有效角色（如 `admin`）
- **刷新频率**：无自动定时任务，必须手动调用

### 3.2 数据量差异说明

tenders 表 359 条 → customer_predictions 表 206 条的差异原因：

- `PurchaserExtractionPolicy` 无法从标讯标题中提取公司名的记录会被过滤
- 标题格式不符合提取规则的标讯不参与洞察聚合

## 4. CRM 商机查询接口行为

### 4.1 POST /customer-chance/page-list

CRM 接口严格按 token 中的 `saleNo` 过滤，返回**当前用户有权限的全部商机**。

| 参数 | 行为 |
|---|---|
| `projectLeaderNo` | **不生效**，不影响返回结果范围 |
| `groupName` | 仅在当前用户商机范围内进行二次过滤 |
| token 中的 `saleNo` | **唯一过滤条件**，决定返回数据范围 |

### 4.2 前端 fallback 逻辑

当 `groupName` 匹配不到结果时，前端会 fallback 到 `selectAll=true` 查询当前用户全部商机，导致不同招标主体场景下展示数量可能变化。

### 4.3 CrmChanceService 查询规则

`CrmChanceService` 查询 CRM 商机时必须按当前用户工号过滤，`projectLeaderNo` 取值优先级为：

```
crm_sales_no > employee_number > username
```

## 5. 已知限制

1. **无自动刷新**：`customer_predictions` 表不会自动更新，必须手动调用 `POST /api/customer-opportunities/refresh`
2. **标题提取依赖**：无法提取公司名的标讯不参与洞察聚合
3. **CRM 接口按 token 过滤**：`projectLeaderNo` 参数不生效，展示的是当前用户全部商机
4. **SSO 用户限制**：SSO 登录用户无法获取 OSS token，CRM 相关功能可能受限

## 6. 建议改进

1. **定时刷新任务**：添加定时任务自动刷新 `customer_predictions` 表（如每天凌晨）
2. **部署时初始化**：部署后自动调用刷新接口，避免首次访问为空
3. **前端空状态提示**：当 `customer_predictions` 为空时提示用户"请先刷新"
4. **角色权限修正**：`refreshInsights()` 的 `@PreAuthorize` 应使用当前有效角色，不应引用已废弃的 MANAGER 角色

## 7. 相关文档

- `backend/src/main/java/com/xiyu/bid/customer/application/CustomerOpportunityRefreshService.java` — 刷新服务
- `backend/src/main/java/com/xiyu/bid/customer/application/CustomerOpportunityRefreshPolicy.java` — 评分策略
- `backend/src/main/java/com/xiyu/bid/crm/application/CrmChanceService.java` — CRM 商机查询
- `docs/lessons/crm-integration-lessons.md` — CRM 集成经验
