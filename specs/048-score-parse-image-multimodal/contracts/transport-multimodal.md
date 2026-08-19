# Contract: transport 多模态 content 支持

**Feature**: 048-score-parse-image-multimodal | **Date**: 2026-08-18

> 目标：`OpenAiStructuredOutputTransport` / `OpenAiStructuredOutputService` 支持"一条请求含文本 prompt +
> 多条图片"的混合 content，专供阶段2 客观项对标打分（research R2/D1）。

## 接口形状

### `OpenAiStructuredOutputTransport`（接口扩一条过载）

```java
interface OpenAiStructuredOutputTransport {
    // 既有（RESPONSES）不变
    <T> Optional<T> requestWithResponses(String prompt, Class<T> responseType, OpenAiBidAgentRequestConfig config);

    // 既有（CHAT_COMPLETIONS）不变——纯文本同一对称路径
    <T> Optional<T> requestWithChatCompletions(String prompt, Class<T> responseType, OpenAiBidAgentRequestConfig config);

    // 新增：多模态 CHAT_COMPLETIONS
    <T> Optional<T> requestWithChatCompletionsMultimodal(
            String prompt,
            List<MultimodalImage> images,   // 已上限配给后的图片
            Class<T> responseType,
            OpenAiBidAgentRequestConfig config);
}
```

### `OpenAiStructuredOutputService`（新增过载）

```java
public <T> T request(String prompt, List<MultimodalImage> images,
                     Class<T> responseType, OpenAiBidAgentRequestConfig config, String missingOutputMessage);
```

- `images` 为空 → 委托纯文本 `request(...)`（保持不变，零回归）。
- `images` 非空 → `requestWithChatCompletionsMultimodal(...)`；`apiStyle` 为 RESPONSES 时（当前不会发生，见
  research R2）抛 `IllegalStateException` 说明不支持多模态 → 调用层降级纯文本。
- 缺少该过载的 case，`OpenAiScoreAnalyzer.assessObjective` new overload 走它。

## 实现语义（`OpenAiSdkStructuredOutputTransport`）

多模态消息把 `prompt` 作为文本 content part、每张图作为 `image_url` content part 组合为一条用户消息：

```
user-message:
  content = [
    { type: "text", text: <prompt of buildObjectiveAssessmentPrompt> },
    { type: "image_url", image_url: { url: "data:image/png;base64,<b64>" } },
    ...
  ]
```

- `json_schema` 路径：`StructuredChatCompletionCreateParams` 构造同样走多段 content（非 `addUserMessage(String)`）。
- `json_object` 路径（`jsonSchemaUnsupported` fallback）：`ChatCompletionCreateParams` 同构。
- `json_object` 包装 prompt（`Return exactly one valid JSON object...` 前缀）追加到文本 part 后，图片 part 不变。
- 错误语义：构造多段 content 或上游拒绝图片 part 时抛 `BadRequestException`/`RuntimeException` →
  调用层按 FR-005 降级纯文本并 `log.warn` + Sentry（Principle VII §3）。不允许静默吞图。

### 开放项（实现阶段以真实 SDK 版本编译为准，research R2）
- 精确 SDK builder API：用户消息多段 content 的构造、`ChatCompletionContentPart.ofImageUrl(...)` / `ofText(...)`
  的确切签名。仅以 POC 验证过的语义（data URL + base64, image_url）为准，不预设 SDK 内部字段。

## 配置

`OpenAiBidAgentRequestConfig` 不改（模型/超时/风格不变，图片不改变 provider 选择）。多模态只受
`app.score-parse.multimodal.*`（新增）控制（见 quickstart）。

## 可观测性

多模态调用在编排层记录：`imageCount`、`estimatedImageTokens`、`textTokens`（research R5）。transport 只发请求，
不做计量——观测责任归属调用层（`ScoreItemAssessor` / `OpenAiScoreAnalyzer`）。

## 降级矩阵

| 场景 | 行为 |
|---|---|
| `images` 为空 | 纯文本路径，零回归 |
| 图片损坏/解码失败 | 该项改纯文本，`log.warn` |
| 上游模型拒绝图片 part | 该项改纯文本，`log.warn` + Sentry |
| 超单请求预算（`ImageAssigner` 已裁剪） | 仅携带预算内图片，`truncated=true` 可观测 |
| 全局开关关闭 | 打分完全纯文本，不携带任何图（FR-006） |