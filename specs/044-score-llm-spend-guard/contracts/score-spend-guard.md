# Contract: 花费守卫（增量）

基线：`specs/041-ai-score-parse-backend/contracts/score-parse-api.md`、`specs/043-harden-score-parse-intake/contracts/score-parse-auto-trigger.md`。

## POST `/api/projects/{projectId}/score-parse/parse`

手动「重新解析」：语义同现网（可建新任务）。请求可不带 body。

系统自动（监听器、抽屉静默）：仅当 `AutoParseGate` 允许且未熔断才建任务；否则不建、不打 LLM。监听器失败只打日志。抽屉静默被拒则展示已有失败原因或空态。

可选 query/header 不新增也可：前端静默与手点都走同一 POST，由服务端根据「是否已有 PARSE 历史」区分——**手点在已有历史时仍建任务**。因此服务端需要知道是不是手点：

- Body 可选 `{ "source": "AUTO" | "MANUAL" }`，缺省 `MANUAL`（旧客户端=手点，兼容）
- 抽屉静默传 `AUTO`；「重新解析」传 `MANUAL` 或不传
- 监听器走内部 `source=AUTO`，不经过 HTTP

熔断中的 AUTO：不建任务。MANUAL：照常建。

## POST `/api/projects/{projectId}/score-parse/scoring`

Body 可选（缺省全量手点）：

```json
{
  "source": "MANUAL",
  "scope": "ALL",
  "itemIds": []
}
```

| 字段 | 缺省 | 含义 |
|---|---|---|
| source | MANUAL | AUTO 受熔断；MANUAL 不受 |
| scope | ALL | ALL / UNSATISFIED / ITEMS |
| itemIds | [] | scope=ITEMS 时必填 |

**结果形态**（触发响应增加可空字段，旧前端可忽略）：

| outcome | 含义 |
|---|---|
| SKIPPED | 投标指纹+清单指纹与上次成功打分相同，未跑 LLM |
| INCREMENTAL | 只评估脏章相关项（或范围内的子集） |
| FULL | 全量评估 |

文件未变：即使手点、即使传了 scope，也为 SKIPPED（澄清：无强制重打）。

进行中打分：仍 `TASK_IN_PROGRESS`，不另开。

## GET `/api/projects/{projectId}/score-parse/items` 的 meta

可空增补：

| 字段 | 含义 |
|---|---|
| lastScoringOutcome | SKIPPED / INCREMENTAL / FULL / null |
| lastScoringHint | 给人看的一句：文件未变化 / 重评 N 项（第 x 章）/ 全量 |
| circuitOpen | 自动路径是否熔断 |
| reusedItemCount | 本次沿用项数，无则 null |

## 监听器（内部）

`TenderDocumentStoredListener`：先 `AutoParseGate` + 熔断，不通过则 info 日志并返回。通过才 `triggerParseFromEvent`。换招标文件不构成例外。
