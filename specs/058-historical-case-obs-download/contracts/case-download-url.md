# Contract: 案例原文下载签发端点

## GET /api/cases/{caseId}/download-url

权限：登录用户（与案例列表一致）。路径参数仅接受库内案例 id，不接受任何对象路径参数（FR-005）。

### 200 OK

```json
{
  "url": "https://ehsy-widbid.obs.cn-east-3.myhuaweicloud.com/2026%E5%B9%B4...docx?AccessKeyId=...&Expires=...&Signature=...",
  "filename": "项目复盘-中广核.docx",
  "expireSeconds": 300
}
```

### 错误

| 状态 | 场景 | 响应体 |
|---|---|---|
| 404 | 案例不存在 | `{ "msg": "案例不存在" }` |
| 409 | 案例无原文来源记录（`obs_bid_file_key` 为空） | `{ "msg": "该案例暂无原版标书" }` |
| 502 | 签发失败（OBS 网关异常） | `{ "msg": "下载链接生成失败，请稍后重试" }` |

### 前端调用契约（useBidCaseSourceActions 三分支）

| 案例状态 | 行为 |
|---|---|
| `obsBidFileKey` 非空 | 调本端点 → `<a download>` 触发 |
| 仅 `sourceProjectId` | 档案预检 → `exportProjectArchive`（现状不动） |
| 两者皆无 | 提示"标书原文暂不可用"（现状不动） |
| 本端点 409/502 | 提示响应 `msg`，不落盘 |
