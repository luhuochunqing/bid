# 西域 CRM 客户接口 - 根据公司 id 查询客户负责人列表

> 接口编号：25259
> 接口名称：根据公司 id 列表查询客户负责人列表
> 接口路径：`POST /customerManager/getCustomerManagerListByCompanyId`
> Mock 地址：`https://yapi.ehsy.com/mock/509/customerManager/getCustomerManagerListByCompanyId`
> YApi 地址：<https://yapi.ehsy.com/project/509/interface/api/25259>
> BaseUrl：`https://cac-test.ehsy.com`（测试）/ 生产由 `CrmProperties` 配置
> 备注：接口场景为页面客户负责人列表展示；接口描述为根据公司 id 查询客户负责人列表（支持批量公司 id、批量工号）

---

## 业务场景

CO-302 标讯自动分配 — CRM 反查路径的**第二步**：

```
标讯招标主体名称
    │
    ▼
[25338] 模糊查询公司 → 取出公司 id（上一步）
    │
    ▼
[25259] 根据公司 id 查客户负责人（本接口）→ 取出 saleNo（工号）
    │
    ▼
写入标讯的"项目负责人"字段
```

详见 Linear CO-302 §5.3「CRM 反查的查询路径」。

---

## 请求参数

### Headers

| 参数名称     | 参数值            | 是否必须 | 示例 | 备注 |
| ------------ | ----------------- | -------- | ---- | ---- |
| Content-Type | application/json  | 是       |      |      |
| Authorization| `Bearer <token>`  | 是       |      | 通过 CRM 登录接口 23352 获取 |

### Body

| 名称        | 类型   | 是否必须 | 默认值 | 备注                                 |
| ----------- | ------ | -------- | ------ | ------------------------------------ |
| body        | object | 非必须   |        | 请求体                               |
| ├ companyIds| string | 非必须   |        | 公司 ID，**用英文逗号分隔**          |
| ├ saleNos   | string | 非必须   |        | 销售工号，用英文逗号分隔             |
| ├ saleTypes | string | 非必须   |        | 负责人类型，用英文逗号分隔           |
| ├ pageIndex | number | 非必须   |        | 页码                                 |
| └ pageSize  | number | 非必须   |        | 每页大小                             |

> 注意：`companyIds` / `saleNos` / `saleTypes` 是**逗号分隔的字符串**，不是数组。

### 请求示例

```json
{
  "body": {
    "companyIds": "81417644",
    "saleTypes": "16",
    "pageIndex": 1,
    "pageSize": 20
  }
}
```

---

## 返回数据

### 返回示例

```json
{
  "code": "0",
  "msg": "查询成功",
  "totalCount": 12,
  "pageSize": 3,
  "pageIndex": 2,
  "dataList": [
    {
      "id": 63,
      "companyId": 81417644,
      "saleNo": "01097",
      "saleType": 2,
      "saleTypeText": "对账开票专员",
      "isAchivementCount": 0,
      "isPower": 0,
      "receiveDeliverEmail": 1,
      "receiveInvoiceEmail": 1,
      "dockingMessagePush": 1,
      "receiveDeliverMsg": 1,
      "createAt": "2025-01-22 15:48:36",
      "updateAt": null,
      "createBy": "02436",
      "updateBy": null
    },
    {
      "id": 96,
      "companyId": 81417644,
      "saleNo": "01989",
      "saleType": 16,
      "saleTypeText": "百大项目负责人",
      "isAchivementCount": 0,
      "isPower": 0,
      "receiveDeliverEmail": 0,
      "receiveInvoiceEmail": 0,
      "dockingMessagePush": 0,
      "receiveDeliverMsg": 0,
      "createAt": null,
      "updateAt": null,
      "createBy": null,
      "updateBy": null
    }
  ],
  "empty": false,
  "notEmpty": true,
  "totalPages": 4
}
```

### 响应字段

| 名称                  | 类型      | 是否必须 | 默认值 | 备注                                                |
| --------------------- | --------- | -------- | ------ | --------------------------------------------------- |
| code                  | string    | 非必须   |        | "0" 为正常返回                                     |
| msg                   | string    | 非必须   |        | 操作信息                                            |
| totalCount            | string    | 非必须   |        | 记录总数                                            |
| pageSize              | string    | 非必须   |        | 分页大小                                            |
| pageIndex             | string    | 非必须   |        | 页码                                                |
| dataList              | object[]  | 非必须   |        | 数据列表                                            |
| ├ id                  | number    | 必须     |        | 客户负责人 id                                       |
| ├ companyId           | string    | 必须     |        | 公司 id                                             |
| ├ saleNo              | string    | 必须     |        | **负责人工号**（CO-302 反查要取的字段）             |
| ├ saleType            | number    | 必须     |        | 负责人类型                                          |
| ├ saleTypeText        | string    | 必须     |        | 负责人类型描述                                      |
| ├ isAchivementCount   | number    | 必须     |        | 是否算业绩（0：否，1：是）                          |
| ├ isPower             | number    | 必须     |        | 权限负责人标识（0：否，1：是）                      |
| ├ receiveDeliverEmail | number    | 必须     |        | 是否接收发货邮件（0：否，1：是）                    |
| ├ receiveInvoiceEmail | number    | 必须     |        | 是否接收发票邮件（0：否，1：是）                    |
| ├ dockingMessagePush  | number    | 必须     |        | 是否商城消息推送（0：否；1：是）                    |
| ├ receiveDeliverMsg   | number    | 必须     |        | 是否接收发货短信（0：否；1：是）                    |
| ├ createBy            | string    | 必须     |        | 创建人                                              |
| ├ updateBy            | string    | 必须     |        | 修改人（YApi 备注"创建时间"为笔误，实际为修改人）   |
| ├ createAt            | string    | 必须     |        | 创建时间（YApi 备注"修改人"为笔误，实际为创建时间） |
| └ updateAt            | string    | 必须     |        | 修改时间                                            |
| empty                 | boolean   | 非必须   |        | 是否为空                                            |
| notEmpty              | boolean   | 非必须   |        | 是否非空                                            |
| totalPages            | number    | 非必须   |        | 总页数                                              |

> ⚠️ **YApi 字段备注错乱提示**：原始 YApi 文档中 `updateBy` 备注"创建时间"、`createAt` 备注"修改人"，明显与字段名语义不符。本表已按字段名语义修正，实现时以返回示例为准。
>
> ⚠️ **字段名不一致提示**：响应示例中为 `dockingMessagePush`，但 YApi 字段清单中对应字段名为 `deliverMessageSwitch`（备注"是否商城消息推送"）。二者语义相同，反序列化时建议保留 `dockingMessagePush` 作为主字段名（与实际响应一致），`deliverMessageSwitch` 作为兼容别名。

### 业务成功条件

`code == "0" && dataList.length > 0`

> 注意：本接口 `code` 是 **string 类型** `"0"`，与 25338 的 integer 类型 `0` 不同，反序列化时需要兼容处理。

---

## CO-302 反查路径中的使用方式

### 1. 输入

将上一步 25338 返回的 `dataList[0].id`（公司 id）作为 `body.companyIds` 传入。

```json
{
  "body": {
    "companyIds": "81417644",
    "pageIndex": 1,
    "pageSize": 20
  }
}
```

### 2. 负责人类型筛选（关键）

返回的 `dataList` 可能包含**多个不同 `saleType` 的负责人**（对账开票专员、百大项目负责人、销售负责人等）。CO-302 issue 5.3 要求"取该客户负责人中的某个角色为标讯的项目负责人"，因此需要按 `saleType` 筛选。

**业务已确认筛选规则**（2026-07-03）：

| saleType | saleTypeText   | 说明                                       |
| -------- | -------------- | ------------------------------------------ |
| 19       | 集团项目经理   | 标讯自动分配项目责任人**唯一**取值角色    |

> ✅ **业务已确认**：标讯自动分配项目责任人时，仅取 `saleType=19`（集团项目经理）。未命中则保持空（不再 fallback 到其他角色），等待人工分配。
>
> 配置项：`app.crm.customer.group-project-manager-sale-type`（默认 19，可调整）。
> 实现见 `CrmCustomerManagerLookupService.findGroupProjectManager`。

### 3. 降级策略

- 返回空列表 → 视为"未查到"，不阻塞流程
- 返回多个相同 `saleType=19` 的负责人 → 取第一条有效（saleNo 非空）的集团项目经理
- 返回的 dataList 无 `saleType=19` → 返回 empty，标讯保持"待分配"，**不再 fallback 到其他角色**
- 接口异常/超时 → 降级为 noMatch，不阻塞主流程

### 4. 取 saleNo

将筛选后负责人的 `saleNo`（工号）作为标讯的"项目负责人工号"写入。

> ⚠️ **注意**：本接口只返回 `saleNo`（工号），不返回负责人姓名。如需姓名，需要用工号调 OSS 员工接口 23358 反查，或在本地 `User` 表中按工号查找。

---

## CO-302 反查路径完整链路（两步）

```
1. 标讯 purchaserName (招标主体)
   │
   ▼ [25338] POST /company/getCompanyNameByLikeName
   │   body.name = purchaserName
   │
   ▼ 取 dataList[0].id (精确匹配优先)
   │
2. 公司 id
   │
   ▼ [25259] POST /customerManager/getCustomerManagerListByCompanyId
   │   body.companyIds = id
   │   body.saleTypes = 16 (建议，待业务确认)
   │
   ▼ 按 saleType 筛选 + 取 saleNo (工号)
   │
3. 项目负责人 saleNo (工号)
   │
   ▼ 写入标讯 + 状态变 "跟踪中"
```

> ⚠️ **现状提示**：CO-302 当前实现（`CrmCustomerLeaderQueryService.findLeaderByGroupName`）走的是商机接口 `/customer-chance/page-list`，**未走上述两步路径**。如需对齐 issue 5.3 描述，需要：
> 1. 新增 `CrmCompanySearchService.searchByName(name)` 调用接口 25338
> 2. 新增 `CrmCustomerLeaderLookupService.findByCompanyId(companyId)` 调用本接口 25259
> 3. 在 `TenderAutoAssignmentService.tryAutoAssignFromCrm` 中替换为两步链路
> 4. 注意类型差异：25338 返回 `code` 为 integer，25259 返回 `code` 为 string

---

## 相关接口

| 顺序 | 接口编号 | 接口名称             | 接口路径                                                       | 状态      |
| ---- | -------- | -------------------- | -------------------------------------------------------------- | --------- |
| 1    | 25338    | 模糊查询公司         | `POST /company/getCompanyNameByLikeName`                       | ✅ 已就绪 |
| 2    | 25259    | 按 companyId 查负责人| `POST /customerManager/getCustomerManagerListByCompanyId`      | ✅ 本文档 |

---

## 关联文档

- [CRM 对接规范（索引页）](../../.wiki/pages/integration-oa-crm.md) §1.3 接口清单 + §1.3.1 CO-302 反查路径
- [接口 25338 - 模糊查询公司](xiyu-crm-customer-api-25338-getCompanyNameByLikeName.md) — CO-302 反查路径第一步
- [西域CRM商机对接接口](../integration/西域CRM商机对接接口.md) — 商机接口 `/customer-chance/page-list`（当前 CO-302 实现所走路径）
- Linear CO-302 §5.3 CRM 反查的查询路径
