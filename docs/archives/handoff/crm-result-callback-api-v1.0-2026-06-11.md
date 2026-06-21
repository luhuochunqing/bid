# CRM 结果确认回调接口

> 投标系统 → CRM 系统（单向推送）  
> 触发时机：项目结果确认登记成功后  
> 文档版本：v1.0 | 2026-06-11

---

## 1. 概述

投标管理平台在项目「结果确认」阶段登记完成后，主动回调 CRM 系统，推送投标结果及竞争对手信息。

**调用方**：西域数智化投标管理平台（Bid）  
**被调用方**：CRM 系统  
**触发时机**：用户在结果确认页提交并持久化成功后，**异步**回调（失败不影响主流程，有重试）

---

## 2. 接口定义

### 2.1 基本信息

| 项目 | 内容 |
|------|------|
| 方法 | `POST` |
| Content-Type | `application/json; charset=utf-8` |
| 认证方式 | 待定（由 CRM 方指定：API Key / OAuth2 Bearer / IP 白名单） |
| 超时 | 30s |
| 重试策略 | 失败后间隔 1min / 5min / 15min 重试 3 次，仍失败则记录告警 |

### 2.2 请求体

```json
{
  "tenderId": 256,
  "projectId": 128,
  "sourceId": "CRM-OPP-2026-0510-003",
  "bidResult": "WON",
  "evidenceFiles": [
    {
      "fileName": "中标通知书-中石化MRO采购框架协议.pdf",
      "fileUrl": "https://bid.xiyu.com/api/projects/128/documents/1032/download",
      "fileSize": 2048000
    }
  ],
  "competitors": [
    {
      "name": "京东企业购",
      "discount": "95折",
      "paymentTerm": "月结60天",
      "notes": "含仓储托管服务"
    },
    {
      "name": "得力集团",
      "discount": "92折",
      "paymentTerm": "月结30天",
      "notes": ""
    }
  ],
  "systemName": "西域数智化投标管理平台",
  "operatorName": "张三",
  "operatorEmployeeId": "EMP001",
  "operatedAt": "2026-06-11T14:30:00+08:00"
}
```

### 2.3 响应

```json
{
  "code": 0,
  "message": "ok"
}
```

---

## 3. 字段详解

### 3.1 顶层字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `tenderId` | Long | ✅ | 投标系统内部标讯ID |
| `projectId` | Long | ✅ | 投标系统内部项目ID |
| `systemName` | String | ✅ | 调用方系统名称，固定值 `西域数智化投标管理平台` |
| `sourceId` | String | ✅ | 来源系统的数据唯一 ID（与标讯推送接口的 sourceId 取值一致），用于 CRM 侧关联回对应商机 |
| `bidResult` | Enum | ✅ | 投标结果：`WON` / `LOST` / `FAILED` / `ABANDONED` |
| `evidenceFiles` | Array\<FileInfo\> | ✅ | 凭证文件列表，每个结果类型至少 1 个 |
| `competitors` | Array\<Competitor\> | ⭕ | 竞争对手情况表，**仅 WON / LOST 时有值**，FAILED / ABANDONED 时为空数组 |
| `operatorName` | String | ✅ | 操作人姓名，≤50 字符 |
| `operatorEmployeeId` | String | ✅ | 操作人工号 |
| `operatedAt` | String | ✅ | 操作时间，ISO 8601 格式（`yyyy-MM-ddTHH:mm:ss+08:00`） |

### 3.2 FileInfo（证据文件）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `fileName` | String | ✅ | 原始文件名，≤255 字符 |
| `fileUrl` | String | ✅ | 文件下载地址（投标系统内部 URL, CRM 可下载后存档） |
| `fileSize` | Long | ⭕ | 文件大小（字节） |

### 3.3 Competitor（竞争对手）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `name` | String | ⭕ | 竞争对手名称，≤200 字符 |
| `discount` | String | ⭕ | 折扣，如 "95折"，≤100 字符 |
| `paymentTerm` | String | ⭕ | 账期，如 "月结60天"，≤100 字符 |
| `notes` | String | ⭕ | 其他说明，≤500 字符 |

> ⚠️ 四字段均为非必填（前端允许空行），但空行（name/discount/paymentTerm/notes 全空）不会传给 CRM。

---

## 4. bidResult 枚举

| 值 | 中文 | 说明 | 凭证类型 |
|----|------|------|---------|
| `WON` | 中标 | 我方中标 | 中标通知书 |
| `LOST` | 未中标 | 我方未中标 | 未中标说明或官方结果公告 |
| `FAILED` | 流标 | 项目流标 | 流标公告 |
| `ABANDONED` | 弃标 | 我方主动弃标 | 弃标说明函 |

---

## 5. 业务规则

1. **幂等性**：同一 `projectId` 只会回调一次（结果登记接口有唯一约束）
2. **结果类型与凭证对应**：见 §4 对照表
3. **竞争对手非必填**：FAILED / ABANDONED 时 `competitors` 为 `[]`
4. **回调失败不阻断主流程**：用户侧结果登记正常完成，回调在后端异步执行
5. **文件下载时效**：`fileUrl` 有效期 7 天，CRM 侧建议及时下载存档

---

## 6. 完整示例

### 示例 1：中标（WON）

```json
{
  "tenderId": 256,
  "projectId": 128,
  "sourceId": "CRM-OPP-2026-0510-003",
  "bidResult": "WON",
  "evidenceFiles": [
    {
      "fileName": "中标通知书-中石化MRO采购框架协议.pdf",
      "fileUrl": "https://bid.xiyu.com/api/projects/128/documents/1032/download",
      "fileSize": 2048000
    }
  ],
  "competitors": [
    {
      "name": "京东企业购",
      "discount": "95折",
      "paymentTerm": "月结60天",
      "notes": "含仓储托管服务"
    }
  ],
  "systemName": "西域数智化投标管理平台",
  "operatorName": "张三",
  "operatorEmployeeId": "EMP001",
  "operatedAt": "2026-06-11T14:30:00+08:00"
}
```

### 示例 2：弃标（ABANDONED）

```json
{
  "tenderId": 301,
  "projectId": 156,
  "sourceId": "CRM-OPP-2026-0615-012",
  "bidResult": "ABANDONED",
  "evidenceFiles": [
    {
      "fileName": "弃标说明函.pdf",
      "fileUrl": "https://bid.xiyu.com/api/projects/156/documents/2058/download",
      "fileSize": 512000
    }
  ],
  "systemName": "西域数智化投标管理平台",
  "competitors": [],
  "operatorName": "李四",
  "operatorEmployeeId": "EMP042",
  "operatedAt": "2026-06-11T16:45:00+08:00"
}
```

---

## 7. 待 CRM 方确认

| 序号 | 事项 | 状态 |
|:---:|------|:---:|
| 1 | 回调 URL 地址 | 待 CRM 提供 |
| 2 | 认证方式（API Key / OAuth2 / IP 白名单） | 待 CRM 确认 |
| 3 | 响应格式（当前约定 `{code: 0, message: "ok"}`） | 待 CRM 确认 |
| 4 | 文件下载是否需要改为外部可访问的 CDN 地址 | 待双方评估 |
| 5 | 是否需要在回调中增加 `awardAmount`（中标金额）字段 | 待 CRM 确认 |

---

> 📖 依据文档：产品蓝图 V1.0 §3.3.1.4 结果确认  
> 🤖 Generated with Claude Code
