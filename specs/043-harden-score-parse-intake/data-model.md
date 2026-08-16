# Data Model: 评分解析生产风险收口

本特性**不改表结构**。只明确已有实体在自动解析门闩与正文回退中的用法。

## 评分解析任务（已有 `score_parse_task`）

| 字段（逻辑名） | 用途 |
|---|---|
| projectId | 项目 |
| taskType | 本门闩只看 `PARSE` |
| status | `PENDING` / `PROCESSING` / `COMPLETED` / `FAILED` |
| errorMessage | 失败原因，回传抽屉 |
| completedAt | 成功/失败完成时间 |

**门闩规则**

- 该项目不存在任何 `PARSE` 行 → `lastParseStatus = null` → 允许打开抽屉自动**新建**一次
- 已有 `PENDING`/`PROCESSING` → 跟随（复用并轮询），不另建
- 最近一条 `COMPLETED` 且清单空 → 不自动新建
- 最近一条 `FAILED` → 展示 `errorMessage`，等用户点「重新解析」
- 无可用正文的 POST → 400，并写入一条 `FAILED`（`errorMessage` 为无文件或过大）

**状态迁移（本特性不新增状态）**

```
（无记录） --自动或手动--> PENDING --> PROCESSING --> COMPLETED
         \--无源/过大--> FAILED --手动重新解析--> PENDING
                                              \--> FAILED --手动重新解析--> PENDING
```

## 立项招标文件（已有 `project_document`）

| 条件 | 含义 |
|---|---|
| documentCategory = `TENDER` | 首选（立项） |
| documentCategory = `TENDER_FILE` | 次选（历史 Bid Agent） |
| fileUrl 有效且可读、正文非空、大小 ≤ 50MB | 可作为解析正文 |

排序：同类按创建时间倒序取最新一条。

## 历史解析底稿（已有 `bid_tender_document_snapshots`）

| 字段 | 用途 |
|---|---|
| projectId | 项目 |
| extractedText | 非空即视为可用底稿 |
| fileName / fileUrl | 回填来源信息栏 |

仅当立项文件读失败或抽不出正文时使用最新一条非空底稿。

## 评分项清单（已有 `score_item`）

空清单 + `lastParseStatus = null` → 允许自动新建。  
空清单 + `PENDING`/`PROCESSING` → 跟随已有任务。  
空清单 + `COMPLETED`/`FAILED` → 不自动新建。

## 校验规则

- 文件大小：≤ 50MB（含）；超限不得整包载入
- 正文：trim 后非空
- 「能不能解析」= 立项可读正文 **或** 可用底稿（同一套条件）
