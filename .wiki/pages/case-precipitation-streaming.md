---
title: 案例沉淀超大标书完整链路（spec 049：流式 + 摘录化 + 三个验收坑）
space: engineering
category: guide
tags: [案例沉淀, 超大文件, 流式, 摘录化, LLM, choices包装, 唯一键, spec049]
sources:
  - specs/049-case-precipitation-streaming/
  - docs/implementation-notes/case-precipitation-spec049.md
  - docs/lessons/lessons-learned.md
backlinks:
  - _index
created: 2026-08-20
updated: 2026-09-05
health_checked: 2026-09-05
---
# 案例沉淀超大标书完整链路（spec 049）

> **目标**：让几百 MB～2GB 的真实标书成功沉淀案例（knowledge_case 入库 >0），无 OOM、<10min、无 30MB 拦截。
> 前置：[[case-precipitation-oversize-oom]] 记录了 spec 045 的 OOM 根因（整文件读堆 + 假成功上报），本页是 spec 049 的完整解法。

## 一、最终链路

```
POST /api/cases/precipitate?projectId=X → triggerPrecipitation 发 ProjectClosedEvent
  → ProjectClosedEventListener.onProjectClosed（@Async @EventListener，无 @Transactional）
      → StreamingBidFileFetcher（spec 045：自适应内存/磁盘 spill，不再整读堆）
      → SidecarStreamTextExtractor（/convert-stream，500 万字符上限）
      → CaseAiMatcher.extractSlicesWithAi（逐评分项摘录化 → LLM）
      → saveAll 落库（TransactionTemplate 包裹）
```

- **摘录化**：逐评分项 `ScoreDocExcerptExtractor.extractRelevantExcerpt(markdown, title, ruleText, 12000)`（关键词段落打分 + top 段落 + 相邻段落，12K 预算），单条 prompt 调 LLM。`draftId` 绑定 `criterion.id()`，不信任 LLM 回显编号（[[score-parse-service]] §133 同源教训）。
- **实测**：401MB 标书转出 467,936 字符，8 评分项摘录 5~11K 字符/项，7 项命中入库。

## 二、验收发现三个坑（spec 未涵盖，全部单测锁死）

### 坑 1：LLM 网关把 prompt 中 markdown base64 图片当多模态解析 → 500
- 现象：criterion 63 LLM 网关 500 `illegal base64 data at input byte 4`，整体沉淀失败。
- 根因：docx→markdown 把图片内联为 `![alt](data:image/png;base64,XXXX)`；网关（new-api 风格）按多模态解析 prompt，base64 非法即 500。
- 修复：`CaseAiMatcher.stripBase64Images()` 三正则兜底剥离 → 替换 `[image]` 占位符（标准闭合 / 截断无闭合 / 裸 data URI）。
- 验证：Python 6 边界用例 + Java 5 单测全绿。

### 坑 2：自研解析器没解 OpenAI choices 包装 → 摘录很好却全判"无证据"
- 现象：摘录质量良好（报价表/投标人/客服/平台功能），8 评分项全部"诚实降级"，knowledge_case 恒 0 条，仍发成功通知。
- 根因：`CaseAiMatcher.extractJson` 取首 `{` 末 `}`，把网关的 `{"choices":[{"message":{"content":"```json\n{...}\n```"}}]}` 整体当目标 JSON，`matchedSnippet` 取不到。
- 修复：新增 `unpackContent()` 先解 `choices[0].message.content`（失败回退裸 JSON），与 `AiResponseParser.extractContentFromResponse` 正规链路对齐。
- 教训：**调 LLM 网关一律走统一 client 的解包链路**，新写解析器要复用而非重造。

### 坑 3：唯一键粒度到标题 → 同标题多评分项整批回滚 + 被吞成"成功通知"
- 现象：评分项 61/62 标题同为"投标报价"（真实数据），`uk_source_proj_scoring_point` 覆盖 `(project_id, title)`，saveAll 同批撞唯一键整批回滚 → 0 条；`DataIntegrityViolationException` 被 catch 成"幂等成功"发通知。
- 修复：V1199 迁移新增 `scoring_point_id` 列，唯一键改 `(source_project_id, scoring_point_id)`；幂等去重改按评分项 ID。
- 教训：**唯一键必须建在领域唯一标识上，不能建在展示字段（标题）上**；幂等兜底要区分"并发竞态"与"数据语义冲突"。

## 三、验收结果（SC-001~SC-005 全过，测试环境 1350）

| 项 | 结果 |
|---|---|
| SC-001 入库 >0 | 7 条（61/62/63/65/66/67/68），响应文本 105~1059 字符，成功通知已发 |
| SC-002 无 OOM | 无（流式 + 摘录化；Xmx2g 下 RSS ~1.5GB 需留意） |
| SC-003 <10min | 1 分 48 秒 |
| SC-004 无 30MB 拦截 | 无（401MB 完整转换沉淀） |
| SC-005 失败语义可区分 | 评分项 64 无报价证据诚实降级，不影响其他项入库 |

## 四、部署验收时序陷阱

`deploy-test.sh` 输出 "deployment completed" 仅表示文件上传完成，**进程重启在随后异步发生**。紧接着触发验收会落在旧进程上跑旧代码，误判"修复无效"。正确做法：先 `ps -eo pid,lstart` 确认新进程启动时间，再触发；只信启动后产生的日志/结果。

## 五、相关页面

- [[case-precipitation-oversize-oom]] — spec 045 OOM 根因（前身）
- [[score-parse-service]] — 评分标准解析（摘录器复用）
- `docs/lessons/lessons-learned.md §136/§137/§138`
- `docs/implementation-notes/case-precipitation-spec049.md`
