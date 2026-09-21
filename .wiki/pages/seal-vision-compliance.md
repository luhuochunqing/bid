---
title: 标书签章完整性视觉核验
space: engineering
category: reference
tags: [标书质检, 签章, Sidecar, 多模态]
sources:
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/SealVisionFusionPolicy.java
  - backend/src/main/java/com/xiyu/bid/ai/compliance/OpenAiSealVisionDetector.java
  - backend/src/main/java/com/xiyu/bid/compliance/service/SidecarComplianceDocumentAdapter.java
backlinks:
  - backend/src/main/java/com/xiyu/bid/compliance/application/BidQualityCheckAsyncExecutor.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/DocumentContent.java
created: 2026-09-21
updated: 2026-09-21
health_checked: 2026-09-21
archive: false
---

# 标书签章完整性视觉核验

## 链路

1. `SidecarStreamDocumentAdapter` 解析文本与物理分页。
2. `SidecarPageImageExtractorAdapter` 调用 `/convert-images`，页面图像通过 `DocumentContent.images` 传入质检链路。
3. `OpenAiSealVisionDetector` 使用 CHAT_COMPLETIONS 多模态通道，返回目标页、印章类型、置信度与归一化坐标。
4. `SealVisionFusionPolicy` 将视觉证据追加到 `signature_seal`；视觉不可用、目标页缺章或置信度不足时降级 `MANUAL_REVIEW`。

## 关键边界

- 文本层强证据不能替代指定页面覆盖判定。
- 页面图像抽取失败只降级视觉通道，不吞掉文本解析结果。
- AI 调用失败不得生成 PASS；必须保留人工复核路径。
- 视觉证据来源类型为 `BID_IMAGE`，包含页码、置信度与坐标。

## 验收状态

- 开发宿主真实 Sidecar：鉴权与真实 PDF 抽图通过。
- 真实 DeepSeek/DashScope 多模态调用：当前凭证均返回 401，识别结果保持不可用，未伪造结论。
