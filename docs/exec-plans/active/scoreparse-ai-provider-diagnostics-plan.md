# 评分解析 AI Provider 诊断透出执行计划 (scoreparse-ai-provider-diagnostics)

> 基准：`5588564444e366ec4aa7a52570737ae59a120c67`（第 195 次部署基线）
> 分支：`agent/codex/scoreparse-ai-provider-diagnostics`
> 范围：`scoreparse` 纯领域与应用层诊断透出、RunSummary 序列化及测试保障。

## 1. 目标与背景

当前评分解析链路在多模态与表格抽取阶段，AI Provider 调用结果（或失败异常）缺乏细粒度的诊断可观测性：
- 当模型请求失败（超时、鉴权错误、限流 429 或结构无效）时，系统直接走静默降级或粗粒度报错，排查人员无法快速知道本次解析实际使用了哪个 Provider、哪种模型、耗时多少毫秒、底层具体错误是什么。
- 在 `RunSummaryAssembler` 中，`llmModel` 字段在结构化抽取阶段被硬编码为 `""`，实际模型名丢失。

本任务将 AI Provider 调用诊断（Provider、模型、耗时、状态、脱敏后异常信息）作为一等公民结构化透出至 `run_summary.aiProviderDiagnostics`。

## 2. 核心架构与纪律

1. **FP-Java 纯核心**：
   - 领域对象 `AiProviderDiagnostics` 与 `AiCallDetail` 位于 `com.xiyu.bid.scoreparse.domain`，采用不可变 Java Record 实现，无 Spring 依赖，可纯单测。
   - 严格内建凭据脱敏逻辑（过滤 `sk-*`、Bearer Token 等敏感凭证）。
2. **RunSummaryAssembler 兼容性**：
   - 严格保持原有 JSON 键名顺序不变（`llmModel`, `lanes`, `tableRows`, `skippedChunks`, `aligned`, `merge`, `parentOnlyDimCount`, `items`, `finalTotalWeight`, `pipelineVersion`, `decisionStatus`, `fallbackReason`, `tableSplits`, `sourceDiagnostics`）。
   - 在其后追加 `aiProviderDiagnostics`，仅在存在诊断时序列化输出，完全避免影响历史数据回放。
   - 同步修正已知模型时的 `llmModel` 透出值。
3. **调用链无侵入采集**：
   - 在 `StructuredTableExtractionHandler` 中捕获模型调用前后的耗时与结果，优雅注入任务结果。

## 3. 任务清单

- [ ] T1: 编写 RED 测试 `AiProviderDiagnosticsTest`，定义脱敏与领域 Record 契约。
- [ ] T2: 实现 `AiProviderDiagnostics.java`（含脱敏与 map 序列化投影）。
- [ ] T3: 编写 `RunSummaryAssemblerTest` 扩展用例，验证 `aiProviderDiagnostics` 追加与旧字段顺序保持。
- [ ] T4: 在 `RunSummaryAssembler.java` 中增加支持 `AiProviderDiagnostics` 的装配重载。
- [ ] T5: 在 `StructuredTableExtractionHandler.java` 中接入诊断采集与装配透出。
- [ ] T6: 全量运行 `RunSummaryAssembler*`、`GoldenCasesReplayTest` 与本地门禁 `scripts/pre-push-gate.sh`。

## 4. 决策与复盘日志

- 2026-09-22：根据用户确认，聚焦"解析任务链路诊断透出"路径，通过不可变领域 Record + RunSummary 追加字段实现无破窗演进。
