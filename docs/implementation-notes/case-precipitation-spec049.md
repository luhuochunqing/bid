# 案例沉淀超大标书（spec 049）实现笔记

> 任务：让几百 MB～2GB 的真实标书成功沉淀案例（知识案例入库 >0），无 OOM、<10min、无 30MB 拦截。
> 验收：SC-001~SC-005（见 `specs/049-case-precipitation-streaming/`）。
> 状态：实现中（流式化 + 摘录化已合；LLM 网关 base64 图片缺陷修复中）。

## 1. 链路总览

```
POST /api/cases/precipitate?projectId=X   (KnowledgeCaseController)
  → CasePrecipitationAppService.triggerPrecipitation（校验前置条件，发 ProjectClosedEvent）
  → ProjectClosedEventListener.onProjectClosed（@Async @EventListener，无 @Transactional）
      → StreamingBidFileFetcher（spec 045，自适应内存/磁盘 spill）
      → SidecarStreamTextExtractor（/convert-stream，500 万字符上限）
      → CaseAiMatcher.extractSlicesWithAi（逐评分项摘录化 → LLM）
      → saveAll 落库（TransactionTemplate 包裹）
```

## 2. Spec 未涵盖、实现中必须做出的决策

### D1. 摘录化路径（spec 049 T007）
- 真实 401MB 标书转出文本 **467,936 字符**，整段直灌 LLM 上下文必超限。
- 决策：逐评分项循环，每项先 `ScoreDocExcerptExtractor.extractRelevantExcerpt(markdown, title, ruleText, 12000)`（关键词段落打分，top-10 段落 + 相邻段落，12K 字符预算），再单条 prompt 调 LLM。
- `draftId` 绑定 `criterion.id()`，不信任 LLM 回显编号（lessons §133：LLM 原文复述/编号不可靠）。
- tradeoff：摘录可能丢掉跨章节证据（如"业绩"证据散布多处）；12K 预算对超长证据可能不足。可接受——超大标书下保底可用优于全文不可用。

### D2. base64 图片缺陷（验收发现，spec 未涵盖）
- **现象**：criterion 63（公司综合实力）LLM 网关返回 500 `decode markdown base64 image data failed: illegal base64 data at input byte 4`，整体沉淀失败 notifyFailure，knowledge_case 0 条。
- **根因**：docx→markdown 转换把图片内联为 `![alt](data:image/png;base64,XXXX)`；LLM 网关 ai-tech.ehsy.com（new-api 风格）把 prompt 中 markdown 图片当**多模态输入**解析，base64 非法（摘录 12K 截断切在 base64 中间 / 含换行 / mime 带空格等形态）即 500。
- **修复**：`CaseAiMatcher.stripBase64Images()` 三正则兜底剥离，统一替换为 `[image]` 占位符：
  1. 标准闭合：`!\[[^\]]*\]\(\s*data:image/[^;)]*;base64,[^)]*\)`
  2. 截断无闭合（摘录截断切掉右括号）：`!\[[^\]]*\]\(\s*data:image/[^;)]*;base64,[A-Za-z0-9+/=\s]*`
  3. 裸 data URI 残留：`data:image/[A-Za-z0-9.+-]+;base64,[A-Za-z0-9+/=\s]*`
- **验证**：Python 本地 6 边界用例全 PASS（标准/截断/截断紧接中文/含换行/带空格mime/空alt）→ 转 Java 转译，新增 5 个单测，25 测试全绿。
- **遗留风险**：若剥离后仍含 `data:image`，WARN 日志打印片段头 300 字符（已加诊断）——真实形态可能还有盲区，需重跑验收确认。

### D3. 认证/测试账号（验收前置，spec 未涵盖）
- 测试环境 admin 密码 hash 与 env 不匹配（Bad credentials），OSS 用户鉴权链路复杂。
- 决策：测试 DB 创建本地测试账号 `zcode_e2e`（非 OSS 用户），密码 123456，role=bid-TeamLeader（data_scope=all）——本地用户走 DB 密码校验 + menu_permissions，绕开 OSS 实时鉴权。
- 注意：测试账号是验收临时资产，需在收尾时决定保留/清理并说明。

## 3. 验收结果追踪

| 轮次 | 触发 | criterion 63 | knowledge_case | 结论 |
|---|---|---|---|---|
| 第 1 轮 | 1350 | 500（base64 解码失败） | 0 | FAIL，发现 D2 缺陷 |
| 第 2 轮（2 正则版） | 1350 | 仍 500（同错误） | 0 | FAIL，2 正则未覆盖真实形态 |
| 第 3 轮（3 正则版） | 待重跑 | — | — | 待验收 |

## 4. 相关链接

- spec：`specs/049-case-precipitation-streaming/`
- 可复用组件：`StreamingBidFileFetcher`、`SidecarStreamTextExtractor`、`TempBidFileStore`（spec 045）
- 教训：LLM 网关 base64 多模态 500（待回填 lessons-learned）
