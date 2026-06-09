---
title: 默认模块
language_tabs:
  - shell: Shell
  - http: HTTP
  - javascript: JavaScript
  - ruby: Ruby
  - python: Python
  - php: PHP
  - java: Java
  - go: Go
toc_footers: []
includes: []
search: true
code_clipboard: true
highlight_theme: darkula
headingLevel: 2
generator: "@tarslib/widdershins v4.0.30"

---

# 默认模块

基于Gin + Vue + Element UI的前后端分离权限管理系统的接口文档


Base URLs:

# Authentication

# 通用

## POST 生成JWT Token

POST /common/inner/generateToken

> Body 请求参数

```json
{
  "nickName": "string",
  "salesNo": "string"
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|Authorization|header|string| 是 |none|
|body|body|[GenerateTokenDTO](#schemageneratetokendto)| 否 |none|

> 返回示例

> 200 Response

```json
{
  "code": "",
  "msg": "",
  "data": ""
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|[SingleResponseString](#schemasingleresponsestring)|

# 客户商机接口

## POST 商机列表

POST /customer-chance/page-list

商机列表

> Body 请求参数

```json
{
  "pageIndex": 0,
  "pageSize": 0,
  "body": {
    "groupName": [
      "string"
    ],
    "name": "string",
    "code": "string",
    "projectStatus": [
      0
    ],
    "projectRisk": [
      0
    ],
    "cooperationStatus": 0,
    "evaluationStartTime": "string",
    "evaluationEndTime": "string",
    "projectLeaderName": [
      "string"
    ],
    "projectLeaderNo": "string",
    "updateStartAt": "string",
    "updateEndAt": "string",
    "selectAll": true,
    "selectList": [
      0
    ],
    "notSelectList": [
      0
    ],
    "timeSort": 0
  }
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|Authorization|header|string| 是 |none|
|body|body|[PageRequestCustomerChanceDTO](#schemapagerequestcustomerchancedto)| 否 |none|

> 返回示例

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "transferVisible": false
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "transferVisible": false
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "transferVisible": false
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "transferVisible": false
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "transferVisible": false
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "transferVisible": false
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "backupPlanText": "",
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "activeRecordCreateBy": "",
      "transferVisible": false
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "projectStatusText": "",
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "projectRiskText": "",
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "backupPlanText": "",
      "managerUnderstandProcess": "",
      "projectGap": "",
      "gapFile": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "activeRecordCreateBy": "",
      "transferVisible": false
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "projectStatusText": "",
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "projectRiskText": "",
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "backupPlanText": "",
      "managerUnderstandProcess": "",
      "projectGap": "",
      "gapFile": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "activeRecordCreateBy": "",
      "transferVisible": false
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "projectStatusText": "",
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "projectRiskText": "",
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "backupPlanText": "",
      "managerUnderstandProcess": "",
      "projectGap": "",
      "gapFile": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "activeRecordCreateBy": "",
      "transferVisible": false
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "projectStatusText": "",
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "projectRiskText": "",
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "backupPlanText": "",
      "managerUnderstandProcess": "",
      "projectGap": "",
      "gapFile": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "activeRecordCreateBy": "",
      "transferVisible": false,
      "bidRemainTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "projectStatusText": "",
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "projectRiskText": "",
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "backupPlanText": "",
      "managerUnderstandProcess": "",
      "projectGap": "",
      "gapFile": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "activeRecordCreateBy": "",
      "transferVisible": false,
      "bidRemainTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "projectStatusText": "",
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "projectRiskText": "",
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "backupPlanText": "",
      "managerUnderstandProcess": "",
      "projectGap": "",
      "gapFile": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "activeRecordCreateBy": "",
      "transferVisible": false,
      "bidRemainTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "projectStatusText": "",
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "projectRiskText": "",
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "backupPlanText": "",
      "managerUnderstandProcess": "",
      "projectGap": "",
      "gapFile": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "activeRecordCreateBy": "",
      "transferVisible": false,
      "bidRemainTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "projectStatusText": "",
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "projectRiskText": "",
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "backupPlanText": "",
      "managerUnderstandProcess": "",
      "projectGap": "",
      "gapFile": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "activeRecordCreateBy": "",
      "transferVisible": false,
      "bidRemainTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "projectStatusText": "",
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "projectRiskText": "",
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "backupPlanText": "",
      "managerUnderstandProcess": "",
      "projectGap": "",
      "gapFile": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "activeRecordCreateBy": "",
      "transferVisible": false,
      "bidRemainTime": ""
    }
  ]
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|[PageResponseCustomerChanceVO](#schemapageresponsecustomerchancevo)|

## POST 标讯回传接口

POST /customer-chance/bidInfoSync

标讯回传接口

> Body 请求参数

```json
{
  "bidInfoList": [
    {
      "name": "string",
      "code": "string",
      "status": 0,
      "statusEditor": "string",
      "statusEditTime": "string",
      "feedback": "string"
    }
  ]
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|Authorization|header|string| 是 |none|
|body|body|[BidInfoSyncDTO](#schemabidinfosyncdto)| 否 |none|

> 返回示例

```json
{
  "code": "",
  "msg": "",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "groupId": 0,
      "tenderSubject": "",
      "tenderSubjectId": 0,
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "projectStatusText": "",
      "cooperationStatus": 0,
      "winningVendor": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "projectRiskText": "",
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "backupPlanText": "",
      "managerUnderstandProcess": "",
      "projectGap": "",
      "gapFile": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": "",
      "activeRecordCreateBy": "",
      "transferVisible": false,
      "bidRemainTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": {}
}
```

```json
{
  "code": "",
  "msg": "",
  "data": {}
}
```

```json
{
  "code": "",
  "msg": "",
  "data": {}
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|[SingleResponse](#schemasingleresponse)|

# 客户商机对接人接口

## POST 对接列表

POST /contact-person-info/page-list

对接列表

> Body 请求参数

```json
{
  "ccId": 0
}
```

### 请求参数

|名称|位置|类型|必选|说明|
|---|---|---|---|---|
|Authorization|header|string| 是 |none|
|body|body|[ContactPersonListDTO](#schemacontactpersonlistdto)| 否 |none|

> 返回示例

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "code": "",
      "name": "",
      "groupName": "",
      "tenderSubject": "",
      "projectLeaderName": "",
      "projectLeaderNo": "",
      "secondDeptLeaderName": "",
      "secondDeptLeaderNo": "",
      "projectStatus": 0,
      "cooperationStatus": 0,
      "winningVendor": "",
      "winningReason": "",
      "bidFailureReason": "",
      "missReason": "",
      "feedBack": "",
      "projectRisk": "",
      "activeId": 0,
      "evaluationTime": "",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0.0,
      "customerRevenue": 0.0,
      "bidDocumentDisadvantage": "",
      "riskPrediction": "",
      "backupPlan": false,
      "managerUnderstandProcess": "",
      "projectGap": "",
      "remark": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": "",
      "activeRecord": "",
      "activeRecordTime": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "name": "",
      "phone": "",
      "email": "",
      "ehsyProjectManager": "",
      "contacted": false,
      "contactMethod": "",
      "preferenceLevel": "",
      "preferenceBasis": "",
      "seniorMeeting": false,
      "guidedBidDocument": false,
      "getKeyInfo": false,
      "deleteDisadvantage": false,
      "syncInfo": false,
      "guaranteeWin": false,
      "impactRate": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "name": "",
      "phone": "",
      "ehsyProjectManager": "",
      "contacted": false,
      "contactMethod": "",
      "preferenceLevel": "",
      "preferenceBasis": "",
      "seniorMeeting": false,
      "guidedBidDocument": false,
      "getKeyInfo": false,
      "deleteDisadvantage": false,
      "syncInfo": false,
      "guaranteeWin": false,
      "impactRate": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": ""
    }
  ]
}
```

```json
{
  "code": "",
  "msg": "",
  "data": [
    {
      "id": 0,
      "name": "",
      "phone": "",
      "ehsyProjectManager": "",
      "contacted": false,
      "contactMethod": "",
      "preferenceLevel": "",
      "preferenceBasis": "",
      "seniorMeeting": false,
      "guidedBidDocument": false,
      "getKeyInfo": false,
      "deleteDisadvantage": false,
      "syncInfo": false,
      "guaranteeWin": false,
      "impactRate": "",
      "createBy": "",
      "createByName": "",
      "updateBy": "",
      "updateByName": "",
      "createAt": "",
      "updateAt": ""
    }
  ]
}
```

### 返回结果

|状态码|状态码含义|说明|数据模型|
|---|---|---|---|
|200|[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)|none|[SingleResponseListContactPersonInfoVO](#schemasingleresponselistcontactpersoninfovo)|

# 数据模型

<h2 id="tocS_"></h2>

<a id="schema"></a>
<a id="schema_"></a>
<a id="tocS"></a>
<a id="tocs"></a>

```json
{}

```

### 属性

*None*

<h2 id="tocS_CustomerChanceDTO">CustomerChanceDTO</h2>

<a id="schemacustomerchancedto"></a>
<a id="schema_CustomerChanceDTO"></a>
<a id="tocScustomerchancedto"></a>
<a id="tocscustomerchancedto"></a>

```json
{
  "groupName": [
    "string"
  ],
  "name": "string",
  "code": "string",
  "projectStatus": [
    0
  ],
  "projectRisk": [
    0
  ],
  "cooperationStatus": 0,
  "evaluationStartTime": "string",
  "evaluationEndTime": "string",
  "projectLeaderName": [
    "string"
  ],
  "projectLeaderNo": "string",
  "updateStartAt": "string",
  "updateEndAt": "string",
  "selectAll": true,
  "selectList": [
    0
  ],
  "notSelectList": [
    0
  ],
  "timeSort": 0
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|groupName|[string]|false|none||集团名称|
|name|string|false|none||商机名称|
|code|string|false|none||商机编号|
|projectStatus|[integer]|false|none||项目状态|
|projectRisk|[integer]|false|none||项目风险|
|cooperationStatus|integer|false|none||客户合作状态|
|evaluationStartTime|string|false|none||评标时间|
|evaluationEndTime|string|false|none||评标时间|
|projectLeaderName|[string]|false|none||项目负责人|
|projectLeaderNo|string|false|none||项目负责人工号|
|updateStartAt|string|false|none||更新时间|
|updateEndAt|string|false|none||更新时间|
|selectAll|boolean|false|none||全选|
|selectList|[integer]|false|none||正选|
|notSelectList|[integer]|false|none||反选|
|timeSort|integer|false|none||评标时间排序 1或null-正序2-倒序|

<h2 id="tocS_SingleResponseString">SingleResponseString</h2>

<a id="schemasingleresponsestring"></a>
<a id="schema_SingleResponseString"></a>
<a id="tocSsingleresponsestring"></a>
<a id="tocssingleresponsestring"></a>

```json
{
  "code": "string",
  "msg": "string",
  "data": "string"
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|code|string|true|none||响应码|
|msg|string|false|none||响应消息|
|data|string|false|none||返回数据|

<h2 id="tocS_CustomerChanceVO">CustomerChanceVO</h2>

<a id="schemacustomerchancevo"></a>
<a id="schema_CustomerChanceVO"></a>
<a id="tocScustomerchancevo"></a>
<a id="tocscustomerchancevo"></a>

```json
{
  "id": 0,
  "code": "string",
  "name": "string",
  "groupName": "string",
  "groupId": 0,
  "tenderSubject": "string",
  "tenderSubjectId": 0,
  "projectLeaderName": "string",
  "projectLeaderNo": "string",
  "secondDeptLeaderName": "string",
  "secondDeptLeaderNo": "string",
  "projectStatus": 0,
  "projectStatusText": "string",
  "cooperationStatus": 0,
  "winningVendor": "string",
  "bidFailureReason": "string",
  "missReason": "string",
  "feedBack": "string",
  "projectRisk": "string",
  "projectRiskText": "string",
  "evaluationTime": "string",
  "planSupplierCount": 0,
  "ecommerceMroAmount": 0,
  "customerRevenue": 0,
  "bidDocumentDisadvantage": "string",
  "riskPrediction": "string",
  "backupPlan": true,
  "backupPlanText": "string",
  "managerUnderstandProcess": "string",
  "projectGap": "string",
  "gapFile": "string",
  "remark": "string",
  "createBy": "string",
  "createByName": "string",
  "updateBy": "string",
  "updateByName": "string",
  "createAt": "string",
  "updateAt": "string",
  "activeRecord": "string",
  "activeRecordTime": "string",
  "activeRecordCreateBy": "string",
  "transferVisible": true,
  "bidRemainTime": "string"
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|id|integer(int64)|false|none||主键id|
|code|string|false|none||商机编号|
|name|string|false|none||商机名称|
|groupName|string|false|none||集团名称|
|groupId|integer|false|none||集团名称|
|tenderSubject|string|false|none||招标主体|
|tenderSubjectId|integer|false|none||招标主体id|
|projectLeaderName|string|false|none||项目负责人|
|projectLeaderNo|string|false|none||项目负责人工号|
|secondDeptLeaderName|string|false|none||项目负责人|
|secondDeptLeaderNo|string|false|none||项目负责人工号|
|projectStatus|integer|false|none||项目状态|
|projectStatusText|string|false|none||项目状态|
|cooperationStatus|integer|false|none||客户合作状态|
|winningVendor|string|false|none||中标友商及折扣-json|
|bidFailureReason|string|false|none||流标原因|
|missReason|string|false|none||丢标原因|
|feedBack|string|false|none||结果反馈|
|projectRisk|string|false|none||项目风险|
|projectRiskText|string|false|none||项目风险|
|evaluationTime|string|false|none||评标时间|
|planSupplierCount|integer(int64)|false|none||计划入围供应商数量|
|ecommerceMroAmount|number|false|none||电商MRO+办公流水金额(万)|
|customerRevenue|number|false|none||客户营收(万)|
|bidDocumentDisadvantage|string|false|none||招标文件不利项|
|riskPrediction|string|false|none||风险预判|
|backupPlan|boolean|false|none||是否有兜底方案|
|backupPlanText|string|false|none||是否有兜底方案|
|managerUnderstandProcess|string|false|none||项目经理是否了解评标全流程|
|projectGap|string|false|none||项目GAP|
|gapFile|string|false|none||项目GAP附件|
|remark|string|false|none||需要的支持|
|createBy|string|false|none||创建人|
|createByName|string|false|none||创建人姓名|
|updateBy|string|false|none||更新人姓名|
|updateByName|string|false|none||更新人姓名|
|createAt|string|false|none||创建时间|
|updateAt|string|false|none||更新时间|
|activeRecord|string|false|none||跟进记录|
|activeRecordTime|string|false|none||跟进时间|
|activeRecordCreateBy|string|false|none||跟进创建人|
|transferVisible|boolean|false|none||转移权限|
|bidRemainTime|string|false|none||投标剩余时间|

<h2 id="tocS_GenerateTokenDTO">GenerateTokenDTO</h2>

<a id="schemageneratetokendto"></a>
<a id="schema_GenerateTokenDTO"></a>
<a id="tocSgeneratetokendto"></a>
<a id="tocsgeneratetokendto"></a>

```json
{
  "nickName": "string",
  "salesNo": "string"
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|nickName|string|true|none||用户昵称|
|salesNo|string|true|none||销售编号|

<h2 id="tocS_ContactPersonInfoVO">ContactPersonInfoVO</h2>

<a id="schemacontactpersoninfovo"></a>
<a id="schema_ContactPersonInfoVO"></a>
<a id="tocScontactpersoninfovo"></a>
<a id="tocscontactpersoninfovo"></a>

```json
{
  "id": 0,
  "name": "string",
  "phone": "string",
  "ehsyProjectManager": "string",
  "contacted": true,
  "contactMethod": "string",
  "preferenceLevel": "string",
  "preferenceBasis": "string",
  "seniorMeeting": true,
  "guidedBidDocument": true,
  "getKeyInfo": true,
  "deleteDisadvantage": true,
  "syncInfo": true,
  "guaranteeWin": true,
  "impactRate": "string",
  "createBy": "string",
  "createByName": "string",
  "updateBy": "string",
  "updateByName": "string",
  "createAt": "string",
  "updateAt": "string"
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|id|integer(int64)|false|none||主键id|
|name|string|false|none||客户对接人姓名|
|phone|string|false|none||联系方式-手机号|
|ehsyProjectManager|string|false|none||西域项目负责人|
|contacted|boolean|false|none||是否触达 - 0否 1是|
|contactMethod|string|false|none||触达方式|
|preferenceLevel|string|false|none||对我司的倾向性|
|preferenceBasis|string|false|none||倾向性评估依据|
|seniorMeeting|boolean|false|none||是否有高层接触|
|guidedBidDocument|boolean|false|none||是否向此人引导标书|
|getKeyInfo|boolean|false|none||是否可以通过此人获取标书关键信息|
|deleteDisadvantage|boolean|false|none||是否可以通过此人将标书中对我司不利项删除|
|syncInfo|boolean|false|none||是否可以在评标期间实时同步评标信息|
|guaranteeWin|boolean|false|none||是否给出明确我司可以中标的信息|
|impactRate|string|false|none||对中标影响率|
|createBy|string|false|none||创建人|
|createByName|string|false|none||创建人姓名|
|updateBy|string|false|none||更新人姓名|
|updateByName|string|false|none||更新人姓名|
|createAt|string|false|none||创建时间|
|updateAt|string|false|none||更新时间|

<h2 id="tocS_SingleResponseListContactPersonInfoVO">SingleResponseListContactPersonInfoVO</h2>

<a id="schemasingleresponselistcontactpersoninfovo"></a>
<a id="schema_SingleResponseListContactPersonInfoVO"></a>
<a id="tocSsingleresponselistcontactpersoninfovo"></a>
<a id="tocssingleresponselistcontactpersoninfovo"></a>

```json
{
  "code": "string",
  "msg": "string",
  "data": [
    {
      "id": 0,
      "name": "string",
      "phone": "string",
      "ehsyProjectManager": "string",
      "contacted": true,
      "contactMethod": "string",
      "preferenceLevel": "string",
      "preferenceBasis": "string",
      "seniorMeeting": true,
      "guidedBidDocument": true,
      "getKeyInfo": true,
      "deleteDisadvantage": true,
      "syncInfo": true,
      "guaranteeWin": true,
      "impactRate": "string",
      "createBy": "string",
      "createByName": "string",
      "updateBy": "string",
      "updateByName": "string",
      "createAt": "string",
      "updateAt": "string"
    }
  ]
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|code|string|true|none||响应码|
|msg|string|false|none||响应消息|
|data|[[ContactPersonInfoVO](#schemacontactpersoninfovo)]|false|none||返回数据|

<h2 id="tocS_PageResponseCustomerChanceVO">PageResponseCustomerChanceVO</h2>

<a id="schemapageresponsecustomerchancevo"></a>
<a id="schema_PageResponseCustomerChanceVO"></a>
<a id="tocSpageresponsecustomerchancevo"></a>
<a id="tocspageresponsecustomerchancevo"></a>

```json
{
  "code": "string",
  "msg": "string",
  "totalCount": 0,
  "pageSize": 0,
  "pageIndex": 0,
  "dataList": [
    {
      "id": 0,
      "code": "string",
      "name": "string",
      "groupName": "string",
      "groupId": 0,
      "tenderSubject": "string",
      "tenderSubjectId": 0,
      "projectLeaderName": "string",
      "projectLeaderNo": "string",
      "secondDeptLeaderName": "string",
      "secondDeptLeaderNo": "string",
      "projectStatus": 0,
      "projectStatusText": "string",
      "cooperationStatus": 0,
      "winningVendor": "string",
      "bidFailureReason": "string",
      "missReason": "string",
      "feedBack": "string",
      "projectRisk": "string",
      "projectRiskText": "string",
      "evaluationTime": "string",
      "planSupplierCount": 0,
      "ecommerceMroAmount": 0,
      "customerRevenue": 0,
      "bidDocumentDisadvantage": "string",
      "riskPrediction": "string",
      "backupPlan": true,
      "backupPlanText": "string",
      "managerUnderstandProcess": "string",
      "projectGap": "string",
      "gapFile": "string",
      "remark": "string",
      "createBy": "string",
      "createByName": "string",
      "updateBy": "string",
      "updateByName": "string",
      "createAt": "string",
      "updateAt": "string",
      "activeRecord": "string",
      "activeRecordTime": "string",
      "activeRecordCreateBy": "string",
      "transferVisible": true,
      "bidRemainTime": "string"
    }
  ]
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|code|string|true|none||响应码|
|msg|string|false|none||响应消息|
|totalCount|integer|true|none||记录总数|
|pageSize|integer|true|none||分页大小|
|pageIndex|integer|true|none||页码，从1开始|
|dataList|[[CustomerChanceVO](#schemacustomerchancevo)]|true|none||数据列表|

<h2 id="tocS_PageRequestCustomerChanceDTO">PageRequestCustomerChanceDTO</h2>

<a id="schemapagerequestcustomerchancedto"></a>
<a id="schema_PageRequestCustomerChanceDTO"></a>
<a id="tocSpagerequestcustomerchancedto"></a>
<a id="tocspagerequestcustomerchancedto"></a>

```json
{
  "pageIndex": 0,
  "pageSize": 0,
  "body": {
    "groupName": [
      "string"
    ],
    "name": "string",
    "code": "string",
    "projectStatus": [
      0
    ],
    "projectRisk": [
      0
    ],
    "cooperationStatus": 0,
    "evaluationStartTime": "string",
    "evaluationEndTime": "string",
    "projectLeaderName": [
      "string"
    ],
    "projectLeaderNo": "string",
    "updateStartAt": "string",
    "updateEndAt": "string",
    "selectAll": true,
    "selectList": [
      0
    ],
    "notSelectList": [
      0
    ],
    "timeSort": 0
  }
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|pageIndex|integer|true|none||页码|
|pageSize|integer|true|none||每页大小|
|body|[CustomerChanceDTO](#schemacustomerchancedto)|true|none||请求体|

<h2 id="tocS_ContactPersonListDTO">ContactPersonListDTO</h2>

<a id="schemacontactpersonlistdto"></a>
<a id="schema_ContactPersonListDTO"></a>
<a id="tocScontactpersonlistdto"></a>
<a id="tocscontactpersonlistdto"></a>

```json
{
  "ccId": 0
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|ccId|integer|false|none||商机id|

<h2 id="tocS_BidInfoInnerDTO">BidInfoInnerDTO</h2>

<a id="schemabidinfoinnerdto"></a>
<a id="schema_BidInfoInnerDTO"></a>
<a id="tocSbidinfoinnerdto"></a>
<a id="tocsbidinfoinnerdto"></a>

```json
{
  "name": "string",
  "code": "string",
  "status": 0,
  "statusEditor": "string",
  "statusEditTime": "string",
  "feedback": "string"
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|name|string|false|none||名称|
|code|string|false|none||编号|
|status|integer|false|none||状态 1-弃标 2-中标 3-丢标 4-流标|
|statusEditor|string|false|none||状态变更人|
|statusEditTime|string|false|none||状态编辑时间 yyyy-MM-dd HH:mm:ss|
|feedback|string|false|none||项目状态反馈 json包含原因+友商+账期+备注+操作人+操作时间|

<h2 id="tocS_BidInfoSyncDTO">BidInfoSyncDTO</h2>

<a id="schemabidinfosyncdto"></a>
<a id="schema_BidInfoSyncDTO"></a>
<a id="tocSbidinfosyncdto"></a>
<a id="tocsbidinfosyncdto"></a>

```json
{
  "bidInfoList": [
    {
      "name": "string",
      "code": "string",
      "status": 0,
      "statusEditor": "string",
      "statusEditTime": "string",
      "feedback": "string"
    }
  ]
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|bidInfoList|[[BidInfoInnerDTO](#schemabidinfoinnerdto)]|false|none||none|

<h2 id="tocS_SingleResponse">SingleResponse</h2>

<a id="schemasingleresponse"></a>
<a id="schema_SingleResponse"></a>
<a id="tocSsingleresponse"></a>
<a id="tocssingleresponse"></a>

```json
{
  "type": "object",
  "properties": {
    "code": {
      "type": "string",
      "description": "响应码"
    },
    "msg": {
      "type": "string",
      "description": "响应消息"
    },
    "data": {
      "$ref": "#/components/schemas/1",
      "description": "返回数据"
    }
  },
  "required": [
    "code"
  ]
}

```

### 属性

|名称|类型|必选|约束|中文名|说明|
|---|---|---|---|---|---|
|code|string|true|none||响应码|
|msg|string|false|none||响应消息|
|data|[1](#schema1)|false|none||返回数据|

