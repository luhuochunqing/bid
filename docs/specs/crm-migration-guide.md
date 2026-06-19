# CRM 标讯推送接口迁移指南

> 版本：v1.0（2026-06-19）
> 用途：指导 CRM 团队从旧接口迁移到新接口，解决评估表、总部所在地、项目类型等字段缺失问题

---

## 一、背景

当前 CRM 推送标讯使用的是旧接口 `/api/external/tenders`，存在以下问题：

| Issue | 问题描述 | 根因 |
|-------|---------|------|
| CO-267 | 评估表客户信息未带入 | 旧接口 DTO 缺少 `evaluation` 字段 |
| CO-275 | 总部所在地、项目类型字段缺失 | 旧接口 DTO 缺少 `region`、`projectType` 字段 |
| CO-275 | 来源平台显示值错误 | 新旧接口返回值不一致 |

**统一解决方案**：CRM 切换到新接口 `/api/integration/tenders/push`

---

## 二、新旧接口对比

| 对比项 | 旧接口 | 新接口 |
|--------|-------|--------|
| **路径** | `POST /api/external/tenders` | `POST /api/integration/tenders/push` |
| **Controller** | `TenderSyncController` | `TenderIntegrationController` |
| **DTO** | `TenderRequest` | `TenderPushRequest` |
| **幂等性** | ❌ 无 | ✅ 按 (sourceSystem, sourceId) 去重 |
| **认证方式** | X-API-Key | X-API-Key（相同） |

### 字段支持对比

| 字段 | 旧接口 | 新接口 | 说明 |
|------|--------|--------|------|
| `title` | ✅ | ✅ | 标讯标题 |
| `customerName` | ✅ | ✅ | 招标单位 |
| `region` | ❌ | ✅ | **总部所在地** |
| `projectType` | ❌ | ✅ | **项目类型** |
| `sourcePlatform` | ❌ | ✅ | **来源平台** |
| `evaluation` | ❌ | ✅ | **评估数据** |
| `contactInfo` | ❌ | ✅ | 联系人数组 |
| `attachments` | ❌ | ✅ | 附件列表 |
| `tenderInfo` | ❌ | ✅ | 标讯信息 |
| `projectManagerName` | ❌ | ✅ | 项目负责人 |
| `department` | ❌ | ✅ | 项目部门 |
| `creatorName` | ❌ | ✅ | 创建人 |
| `createDate` | ❌ | ✅ | 创建时间 |

---

## 三、新接口规范

### 3.1 请求方式

```
POST /api/integration/tenders/push
Content-Type: application/json
X-API-Key: <your-api-key>
```

### 3.2 请求字段

#### 基本信息

| 字段名 | 类型 | 必填 | 说明 | 示例值 |
|--------|------|------|------|--------|
| sourceSystem | String | **是** | 来源系统编码 | "CRM" |
| sourceId | String | **是** | 来源系统唯一 ID | "OPP-2026-00918" |
| title | String | **是** | 标讯标题（最长 500 字符） | "西域集团2026年度MRO采购" |
| customerName | String | 否 | 招标单位（最长 255 字符） | "西域智慧供应链（上海）股份公司" |
| publishDate | String | 否 | 发布日期，格式 yyyy-MM-dd | "2026-05-26" |
| dueDate | String | 否 | 投标截止时间，格式 yyyy-MM-dd HH:mm | "2026-06-15 10:00" |
| budgetAmount | BigDecimal | 否 | 预算金额（元） | 5000000.00 |

#### 扩展信息（新接口新增）

| 字段名 | 类型 | 必填 | 说明 | 示例值 |
|--------|------|------|------|--------|
| **region** | String | 否 | **总部所在地**（最长 100 字符） | "上海" |
| industry | String | 否 | 行业（最长 100 字符） | "制造业" |
| tenderAgency | String | 否 | 招标代理机构（最长 255 字符） | "XX招标代理有限公司" |
| bidOpeningTime | String | 否 | 开标时间，格式 yyyy-MM-dd HH:mm | "2026-06-16 09:30" |
| registrationDeadline | String | 否 | 报名截止时间，格式 yyyy-MM-dd HH:mm | "2026-06-10 17:00" |
| customerType | String | 否 | 客户类型 | "央企" |
| priority | String | 否 | 优先级 | "A 级" |
| **projectType** | String | 否 | **项目类型** | "工业品" |
| **sourcePlatform** | String | 否 | **来源平台**（最长 100 字符） | "中国政府采购网" |
| source | String | 否 | 来源（最长 200 字符） | "政府采购网" |
| tags | List\<String\> | 否 | 标签列表 | ["MRO", "信息化"] |
| tenderInfo | String | 否 | 标讯信息（最长 5000 字符） | "标讯详细信息..." |
| projectManagerName | String | 否 | 项目负责人姓名 | "张三" |
| department | String | 否 | 项目部门 | "技术部" |
| creatorName | String | 否 | 创建人姓名 | "李四" |
| createDate | String | 否 | 创建时间，格式 yyyy-MM-dd HH:mm | "2026-06-17 14:30" |

#### 联系人

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| contactInfo | List\<ContactDTO\> | 否 | 联系人数组（最多 2 个） |

**ContactDTO 结构**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 否 | 联系人姓名 |
| phone | String | 否 | 手机号 |
| tel | String | 否 | 座机号 |
| mail | String | 否 | 邮箱 |

#### 附件

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| attachments | List\<AttachmentRef\> | 否 | 附件列表（最多 10 个） |

**AttachmentRef 结构**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| fileName | String | 是 | 附件名称 |
| fileUrl | String | 是 | 附件下载 URL |

#### 评估数据（CO-267 核心字段）

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| **evaluation** | Object | 否 | **项目评估数据** |

**evaluation 结构**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| evaluationBasic | Object | 否 | 评估基础信息 |
| evaluationCustomerInfos | List\<Object\> | 否 | 客户信息数组 |
| evaluationRecommendation | Object | 否 | 投标建议 |

**evaluationBasic 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| plannedShortlistedCount | Integer | 计划入围供应商数量 |
| mroOfficeFlowAmount | BigDecimal | 电商 MRO+办公流水金额（万） |
| unfavorableItems | String | 招标文件不利项 |
| riskAssessment | String | 风险预判 |
| contingencyPlan | String | 兜底方案 |
| processKnowledge | String | 是否了解评标全流程 |
| supportNotes | String | 需要的支持备注 |
| projectPlanGap | String | 项目计划 GAP |
| customerRevenue | BigDecimal | 客户营收（万） |

**evaluationCustomerInfos 字段**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| NAME | String | 否 | 姓名 |
| **CONTACT** | String | 否 | **联系方式**（自动映射为 CONTACT_INFO） |
| POSITION | String | 否 | 职位 |
| XIYU_CONTACT | String | 否 | 西域项目负责人 |
| CONTACT_METHOD | String | **是** | 触达方式 |
| **EVALUATION_BASIS** | String | 否 | **倾向性评估依据**（自动映射为 INFO_TENDENCY_BASIS） |
| INFO_CLEAR_WINNER_BID | Boolean | 否 | 是否给出明确中标信息 |
| INFO_WIN_RATE_IMPACT | String | 否 | 对中标影响率 |
| CONTACTED | String | 否 | 是否触达 |
| GUIDED_BID | String | 否 | 是否引导标书 |
| CAN_GET_KEY_INFO | String | 否 | 是否可获取关键信息 |
| CAN_REMOVE_ADVERSE | String | 否 | 是否可删除不利项 |
| CAN_SYNC_EVAL | String | 否 | 是否可同步评标信息 |
| TENDENCY | String | 否 | 对我司的倾向性 |

> **字段映射说明**：CRM 推送时使用 `CONTACT` 和 `EVALUATION_BASIS`，系统会自动映射为前端使用的 `CONTACT_INFO` 和 `INFO_TENDENCY_BASIS`。

**evaluationRecommendation 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| shouldBid | Boolean | 是否建议投标 |
| reason | String | 建议理由 |

#### 其他

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| contentDesc | String | 否 | 招标需求描述（最长 5000 字符） |
| crmId | String | 否 | CRM 商机 ID（传入后自动关联商机） |
| forceUpdate | Boolean | 否 | 是否强制覆盖更新（默认 false） |

---

## 四、请求示例

### 4.1 基础推送（含总部所在地和项目类型）

```json
{
  "sourceSystem": "CRM",
  "sourceId": "OPP-2026-00918",
  "title": "西域集团2026年度MRO物料采购招标项目",
  "customerName": "西域智慧供应链（上海）股份公司",
  "publishDate": "2026-05-26",
  "dueDate": "2026-06-15 10:00",
  "budgetAmount": 5000000.00,
  "region": "上海",
  "projectType": "工业品",
  "sourcePlatform": "中国政府采购网",
  "customerType": "央企",
  "priority": "A 级",
  "contactInfo": [
    {
      "name": "张经理",
      "phone": "13800138000",
      "tel": "021-12345678",
      "mail": "zhang@example.com"
    }
  ]
}
```

### 4.2 完整推送（含评估数据）

```json
{
  "sourceSystem": "CRM",
  "sourceId": "OPP-2026-00918",
  "title": "西域集团2026年度MRO物料采购招标项目",
  "customerName": "西域智慧供应链（上海）股份公司",
  "publishDate": "2026-05-26",
  "dueDate": "2026-06-15 10:00",
  "budgetAmount": 5000000.00,
  "region": "上海",
  "projectType": "工业品",
  "sourcePlatform": "中国政府采购网",
  "customerType": "央企",
  "priority": "A 级",
  "contactInfo": [
    {
      "name": "张经理",
      "phone": "13800138000",
      "tel": "021-12345678",
      "mail": "zhang@example.com"
    }
  ],
  "crmId": "OPP-2026-00918",
  "evaluation": {
    "evaluationBasic": {
      "plannedShortlistedCount": 5,
      "mroOfficeFlowAmount": 1200.00,
      "unfavorableItems": "付款周期较长",
      "riskAssessment": "中等风险",
      "contingencyPlan": "已准备备选方案",
      "processKnowledge": "熟悉全流程",
      "supportNotes": "需要法务支持",
      "projectPlanGap": "交付周期偏紧",
      "customerRevenue": 5000.00
    },
    "evaluationCustomerInfos": [
      {
        "NAME": "张三",
        "CONTACT": "13800138000",
        "POSITION": "总经理",
        "XIYU_CONTACT": "李四",
        "CONTACT_METHOD": "电话",
        "EVALUATION_BASIS": "长期合作",
        "INFO_CLEAR_WINNER_BID": false,
        "INFO_WIN_RATE_IMPACT": "高",
        "CONTACTED": "是",
        "GUIDED_BID": "是",
        "CAN_GET_KEY_INFO": "是",
        "CAN_REMOVE_ADVERSE": "否",
        "CAN_SYNC_EVAL": "否",
        "TENDENCY": "支持"
      }
    ],
    "evaluationRecommendation": {
      "shouldBid": true,
      "reason": "项目匹配度高，利润可观"
    }
  }
}
```

---

## 五、响应说明

### 5.1 成功响应（HTTP 201）

```json
{
  "success": true,
  "code": 201,
  "msg": "标讯推送接收成功",
  "data": {
    "tenderId": 100,
    "status": "CREATED",
    "message": "标讯创建成功"
  }
}
```

### 5.2 重复推送（HTTP 409）

```json
{
  "success": false,
  "code": 409,
  "msg": "标讯 sourceId=OPP-2026-00918 已存在，如需覆盖请传入 forceUpdate=true",
  "data": {
    "tenderId": 50,
    "status": "DUPLICATE"
  }
}
```

### 5.3 覆盖更新（HTTP 200）

```json
{
  "success": true,
  "code": 200,
  "msg": "标讯推送接收成功（已覆盖更新）",
  "data": {
    "tenderId": 50,
    "status": "UPDATED",
    "message": "标讯已覆盖更新"
  }
}
```

### 5.4 status 取值

| 值 | 说明 | HTTP 状态码 |
|----|------|------------|
| CREATED | 新建成功 | 201 |
| UPDATED | 覆盖更新 | 200 |
| DUPLICATE | 幂等命中未更新 | 409 |

---

## 六、迁移步骤

### 6.1 CRM 侧修改

1. **修改接口地址**
   - 旧：`POST /api/external/tenders`
   - 新：`POST /api/integration/tenders/push`

2. **修改请求体**
   - 添加 `sourceSystem` 和 `sourceId` 字段（幂等键）
   - 添加 `region`（总部所在地）
   - 添加 `projectType`（项目类型）
   - 添加 `sourcePlatform`（来源平台）
   - 添加 `evaluation`（评估数据，如需要）

3. **处理响应**
   - 新接口返回 `status` 字段（CREATED/UPDATED/DUPLICATE）
   - 重复推送时返回 HTTP 409，需判断是否需要 `forceUpdate`

### 6.2 测试验证

```bash
# 测试环境
curl -X POST "http://<host>:<port>/api/integration/tenders/push" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <your-api-key>" \
  -d '{
    "sourceSystem": "CRM",
    "sourceId": "TEST-001",
    "title": "测试标讯",
    "region": "上海",
    "projectType": "工业品",
    "sourcePlatform": "中国政府采购网"
  }'
```

---

## 七、常见问题

### Q1: 旧接口还能用吗？

A: 旧接口 `/api/external/tenders` 仍可使用，但不支持 `evaluation`、`region`、`projectType` 等新字段。建议尽快迁移到新接口。

### Q2: sourceSystem 和 sourceId 怎么填？

A: 
- `sourceSystem`：固定填 `"CRM"`
- `sourceId`：填 CRM 商机的唯一编号（如商机 code）

### Q3: 推送失败怎么办？

A: 
- HTTP 400：检查请求体格式
- HTTP 401：检查 API Key
- HTTP 409：标讯已存在，可传 `forceUpdate: true` 覆盖
- HTTP 429：请求过于频繁，稍后重试

### Q4: 评估数据字段名和前端不一致？

A: 系统会自动映射：
- `CONTACT` → `CONTACT_INFO`
- `EVALUATION_BASIS` → `INFO_TENDENCY_BASIS`

---

## 八、联系方式

对接过程中如有问题，请联系本平台技术团队。

---

**文档版本记录**

| 版本 | 日期 | 变更说明 |
|------|------|---------|
| v1.0 | 2026-06-19 | 初版发布 |
