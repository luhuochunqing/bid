# Implementation Plan: AI 自动生成案例超大标书流式化与 LLM 摘录化

**Branch**: `agent/zcode/case-precipitation-streaming` | **Date**: 2026-08-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/049-case-precipitation-streaming/spec.md`

## Summary

修复案例沉淀链路"超大标书全部被拒"问题（项目 1350 验收目标：成功生成案例、入库 >0）：

1. **流式加载接入**：`ProjectClosedEventListener` 读取 OBS 标书从 `RestTemplate.getForObject(byte[])` 整文件读堆，改为复用 spec 045 的流式组件（`StreamingBidFileFetcher` + `SidecarStreamTextExtractor` + `TempBidFileStore`），彻底解 OOM。
2. **LLM 摘录化**：`CaseAiMatcher` 从"整份提取文本拼单个 prompt"改为"每评分项 ≤12K 字符摘录逐项调用 LLM"，复用 `ScoreDocExcerptExtractor`，解决模型上下文超限（第二堵墙）。
3. **事务边界**：重活移出 `@Transactional`，仅落库持事务，避免长事务占用 DB 连接。

实测已验证（2026-08-20 测试环境）：401MB docx OBS 流式下载 3.2s、sidecar `/convert-stream` 转换 33s/927MB 容器峰值、输出 105.6 万字符有效文本。方案可行。

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2（单模块 Maven，`casework` 与 `scoreparse` 同模块，无跨模块边界）

**Primary Dependencies**: 复用 `scoreparse.application` 下 spec 045 组件（均 public、已有测试）：
- `StreamingBidFileFetcher`：OBS 签名 URL 流式下载，≤50MB 内存快路径 / 超限 spill 落盘，512MB 硬上限，600s 超时
- `SidecarStreamTextExtractor`：sidecar `/convert-stream` 磁盘临时文件流式上传 + 响应流式读，500 万字符文本量上限
- `TempBidFileStore`：`{taskId}.part` 临时文件生命周期 + 4GB 磁盘配额
- `ScoreDocExcerptExtractor`：评分项关键词段落打分摘录（纯静态方法，无框架依赖）
- `ObsShareUrlSigner.trySign`：OBS 预签名 URL（已有，案例链路已注入）

**Storage**: 无新表，无 Flyway 迁移（`KnowledgeCase` 现有实体）

**Testing**: JUnit 5 + ArchUnit（已有流式组件测试可参照；`CaseAiMatcher` 无现有单测，需新增）

**Target Platform**: 测试环境（172.16.38.78）验证，sidecar `/convert-stream` 已实测可用

**Performance Goals**: 401MB docx 端到端（下载 3.2s + 转换 33s + N 次 LLM 调用）< 10 分钟；JVM 堆峰值与文件大小解耦（无 GB 级尖峰）

**Constraints**: 真实 API 唯一源（禁止 Mock）；`DocInsightException` 30MB 上限不回退（docinsight 模块既有防护不动）；不改变 `scoreparse` 现有组件签名；OBS 读取/转换/LLM 失败必须失败通知不假成功

**Scale/Scope**: 单项目（1350）验收；>512MB 或 2G 级文件为 Phase 3（另立 spec），本 spec 明确失败提示

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- ✅ **I. FP-Java**：新增逻辑保持 pure core 与 imperative shell 分离；摘录算法走既有纯静态 `ScoreDocExcerptExtractor`
- ✅ **II. Real-API Only**：全链路真实 OBS + 真实 sidecar + 真实 LLM，零 Mock
- ✅ **VII. Defensive Collection & Graceful Degradation**：LLM 解析 0 片段/非 JSON → 诚实降级不崩溃；失败走失败通知
- ✅ **VIII. Boring Proven Patterns**：复用 spec 045 已验证流式组件，不发明新轮子
- ✅ **原子提交 + 测试证据**：每次提交含测试

## Project Structure

### Documentation (this feature)

```text
specs/049-case-precipitation-streaming/
├── spec.md              # 本规格
├── plan.md              # 本文件
├── checklists/requirements.md  # 质量清单
├── contracts/           # 待补：流式加载行为契约
├── research.md          # 待补：实测记录
└── tasks.md             # /speckit-tasks 输出
```

### Source Code (backend)

```text
backend/src/main/java/com/xiyu/bid/
├── casework/application/
│   ├── ProjectClosedEventListener.java   # 改：流式加载接入 + 事务边界
│   ├── CaseAiMatcher.java                # 改：摘录化逐项 LLM
│   └── ScoreItemSnapshot.java            # 不改（已有 record）
├── scoreparse/application/
│   └── ScoreDocExcerptExtractor.java     # 改：新增 dim/detail 重载（向后兼容）
└── scoreparse/application/               # 复用（不改）
    ├── StreamingBidFileFetcher.java
    ├── SidecarStreamTextExtractor.java
    └── TempBidFileStore.java

backend/src/test/java/com/xiyu/bid/
├── casework/application/
│   ├── CaseAiMatcherTest.java            # 新增：摘录化单测
│   └── ProjectClosedEventListenerStreamingTest.java  # 新增：流式接入单测
```

## Design

### Phase 1 — 流式加载接入（`ProjectClosedEventListener`）

**注入**：新增依赖 `StreamingBidFileFetcher`、`SidecarStreamTextExtractor`、`TempBidFileStore`。

**`loadBidFileMarkdown` 改造**：

```
OBS 路径 (obs-direct:)：
  taskId = "case-precip-" + projectId
  signedUrl = obsShareUrlSigner.trySign(filePath)  # 无 → IllegalStateException（保持现状）
  loaded = streamingFetcher.fetch(signedUrl, taskId, fileName, contentType)
  try:
    InMemory → docTextExtractor.extract(fileName, contentType, inMemory.content())   # 快路径不变
    OnDisk   → streamTextExtractor.extract(tempFile, fileName)                       # 流式转换
  finally:
    OnDisk → tempFileStore.release(taskId)                                           # 用毕即释
本地路径：保持 Files.readAllBytes 现状（本地文件可能已迁移，容错语义不变）
```

**失败语义不变**：OBS 读取/转换失败 → rethrow → `notifyFailure`（禁止回退占位文本）。

**事务边界**：`onProjectClosed` 去掉方法级 `@Transactional`；新增 `TransactionTemplate`（注入），仅 `knowledgeCaseRepository.saveAll` 与幂等校验包在事务内。下载/转换/LLM 调用不持连接。

### Phase 2 — CaseAiMatcher 摘录化

**`ScoreDocExcerptExtractor` 加纯静态重载**（向后兼容，`ScoreItemAssessor` 原调用不动）：

```java
public static String extractRelevantExcerpt(String fullText, String dim, String detail, int maxChars)
// 复用现有关键词段落打分 + top10 + 兜底逻辑
```

**`CaseAiMatcher.extractSlicesWithAi` 重构**：外层循环评分项，逐项处理：

```
for each criterion in criteria:
  excerpt = ScoreDocExcerptExtractor.extractRelevantExcerpt(markdown, criterion.title, criterion.ruleText, 12000)
  prompt = 该评分项 + excerpt（不含全文）
  response = callLlm(prompt)              # 复用现有 restTemplate + RoutingAiProvider 调用
  slice = parseSingleSlice(response)      # 单条 {criteriaId, matchedSnippet, confidence}
```

**保留**：AI Provider 未配置报错语义、JSON 解析降级（0 片段不算失败）、`extractCategory` 归类、诚实降级（无证据 → 该评分项无匹配，不伪造）。

### 配置项

- 复用 `app.score-parse.bid-file.*` 现有配置（ram-buffer-bytes 50MB / max-file-bytes 512MB / max-text-chars 500 万 / extract-timeout-seconds 600 / temp-dir / max-total-bytes 4GB）。案例链路直接继承，不新增配置 key。
- 摘录预算 12K 字符与打分链路 `BID_DOC_EXCERPT_MAX_CHARS` 同口径（常量，不新增配置）。

## Testing Strategy

| 层级 | 内容 |
|---|---|
| 单测（新增 `CaseAiMatcherTest`） | 摘录化：prompt 不含全文只含摘录；逐项调用次数 = 评分项数；非 JSON 降级；无关键词命中兜底不伪造 |
| 单测（新增 `ProjectClosedEventListenerStreamingTest`） | 流式加载：InMemory 走快路径 / OnDisk 走 streamExtractor / finally release 调用 / OBS 失败 rethrow / 事务只包落库 |
| 复用已有 | `StreamingBidFileFetcherTest` / `SidecarStreamTextExtractorTest` / `TempBidFileStoreTest` 不受影响（组件签名不变） |
| 端到端（测试环境，验收） | 项目 1350 触发【AI自动生成案例】→ 成功通知 + `knowledge_case` 新增 >0；后端日志无 OOM、无 30MB 上限报错 |

## Risks

1. **LLM 逐项调用成本**：评分项 N 个 = N 次 LLM 调用。与打分链路同口径（几十项在可接受范围）。若未来成本敏感可加并发或合并，不在本 spec。
2. **`TransactionTemplate` 引入**：需注入 `PlatformTransactionManager`，注意与现有 `@Transactional` 混用的事务传播语义。
3. **markitdown 对 docx 无 OOM**：已实测（33s/927MB），风险已排除；>512MB 是 Phase 3 边界。
4. **`ScoreDocExcerptExtractor` 重载影响面**：仅新增方法，不改变现有签名与行为，`ScoreItemAssessor` 回归测试兜底。
5. **验收依赖真实环境**：需测试环境 DB/前端可访问（sidecar 已确认可用），若 1350 数据状态变化（评分项被删）需在验收时复检。

## Steps

1. Phase 1：`ScoreDocExcerptExtractor` 加 dim/detail 重载 + 单测
2. Phase 1：`ProjectClosedEventListener` 流式加载接入 + 事务边界 + 单测
3. Phase 2：`CaseAiMatcher` 摘录化重构 + 单测
4. 全量 `mvn test` 回归（重点 scoreparse/casework 包）
5. 测试环境部署 + 项目 1350 端到端验收（SC-001~SC-005）
6. 知识沉淀 + wiki 回填 + PR（含 spec）
