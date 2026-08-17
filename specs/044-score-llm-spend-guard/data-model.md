# Data Model: 评分解析与打分的花费守卫

无新表。给已有任务/结果加可空列。

## 评分解析任务（已有 `score_parse_task`）

| 新增列 | 含义 |
|---|---|
| trigger_source | `AUTO` / `MANUAL`。事件与抽屉静默打开为 AUTO；「重新解析/重新打分」为 MANUAL。旧行视为 MANUAL |
| bid_content_hash | 仅 SCORING：投标文件字节 SHA-256。成功打分后必有 |
| item_set_hash | 仅 SCORING：当时评分项清单指纹 |
| chapter_hashes | 仅 SCORING：JSON，章标题 → 章正文哈希。切不出则为 null |

**自动新建解析**

- 无 PARSE 行且无评分项 → 允许 AUTO
- 有任意 PARSE 行（含 FAILED）→ 禁止 AUTO；MANUAL 仍可
- PENDING/PROCESSING → 跟随，不另建

**熔断**

```
窗口 = 现在往前 30 分钟
n = 该项目 AUTO 且 FAILED 且 completed_at 在窗口内的任务数（PARSE+SCORING）
n ≥ 2 → 拒绝新的 AUTO
出现一条 MANUAL + COMPLETED → 熔断立即解除
```

**跳过打分**

- 存在最近 COMPLETED SCORING
- 当前投标字节哈希 = 该任务 bid_content_hash
- 当前清单指纹 = 该任务 item_set_hash
- → 不跑 LLM，沿用 `score_result`

## 打分结果（已有 `score_result`）

| 新增列 | 含义 |
|---|---|
| reuse_kind | `FRESH`（本次评估）/ `REUSED`（沿用上次）。旧行视为 FRESH |

增量重打：相关项写 FRESH 并更新字段；其余拷贝上次值、reuse_kind=REUSED、scoring_task_id 指向本任务。

## 校验

- 哈希：小写 hex，长度 64
- trigger_source 仅 AUTO/MANUAL
- reuse_kind 仅 FRESH/REUSED
- 跳过不得改写成功结果的得分口径（公式不变）
