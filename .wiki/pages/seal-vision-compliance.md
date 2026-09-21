---
title: 标书签章完整性视觉核验
space: engineering
category: reference
tags: [标书质检, 签章, Sidecar, 多模态, 假通过]
sources:
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/SealIntegrityCheckPolicy.java
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
- **强证据必须是「动作+结果」**：`验签有效|防伪验签|电子签章防伪码|数字证书防伪|国密SM2/SM3签名|电子签章验证通过` 才直通 PASS。
- **领域名词只能作弱信号**：`电子印章|电子签章|数字签名` 属落款标签（如「供应商（电子签章）：」），一律降级 `MANUAL_REVIEW`，且不得豁免 `[UNFILLED_SEAL_PLACEHOLDER]` 硬 FAIL。
- **融合必须在必经路径上**：执行器不得用 `images` 非空做前置门（抽图失败时检测器返回 `unavailable`，由融合降级人工复核）；`targetPages` 为空（无页码线索）时不得维持 PASS。
- 页面图像抽取失败只降级视觉通道，不吞掉文本解析结果。
- AI 调用失败不得生成 PASS；必须保留人工复核路径。
- **.docx 能力边界**：Sidecar 对 `.docx` 只抽内嵌图片、不做页面渲染（`pageHint` 为空），无法做页面级签章覆盖判定；该格式的签章项按人工复核处理，检查项文案不得承诺「逐页图像识别」。
- 视觉证据来源类型为 `BID_IMAGE`，包含页码、置信度与坐标。

## 抽图内存护栏（项目 1382，2026-09-21）

- `SIDECAR_MAX_IMAGE_BYTES`（15MB）只裁剪响应体，**不约束峰值内存**；另设两道源侧护栏：
  - `.docx`：`SIDECAR_MAX_DOCX_MEDIA_BYTES`（64MB）读 zip 中央目录统计 `word/media/` 未压缩总量，超预算不进入 markitdown（mammoth 内联 base64 峰值 ≈ 14.7× 媒体体积）；该路径同时幂等绕过 `pre_process_docx`。
  - `.pdf` 两分支：`SIDECAR_MAX_SOURCE_IMAGE_BYTES`（64MB）累计超限提前停止，峰值不随页数增长。
- 降级有信号：warning 进 sidecar 日志与 Java `log.warn`，空图在 `withPageImages` 显式 WARN，UI 侧转人工复核。
- 检查项说明已对齐能力边界（`SIGNATURE_SEAL`，含契约测试）。

## 假通过与修复（项目 1382，2026-09-21）

- **现象**：未盖章标书「签章完整性」判通过，结论「已识别到合法有效的电子签章防伪验签标记，签章完备」。
- **根因三层**：(1) 强证据正则含裸词「电子签章」，落款标签即命中；(2) 执行器 `images` 非空门导致视觉兜底被短路（当时 Sidecar OOM 被杀，抽图报 `HTTP/1.1 header parser received no bytes`）；(3) 检查项文案承诺「AI 图像识别逐页检测」，但 docx 无页图。
- **修复**：模式拆分为 verified / caption 两级；执行器去掉 `images` 前置门；`targetPages` 为空降级；承诺书反假共享强证据同步收紧。
- **证据**：`compliance_check_results.id=125` 12 条证据全为落款标签；库内 82 条已完成质检中 `BID_IMAGE` 证据 0 条（视觉通道从未产出结论）。
- 详见 `docs/lessons/lessons-learned.md` Lesson 392。

## 验收状态

- 开发宿主真实 Sidecar：鉴权与真实 PDF 抽图通过。
- 真实 DeepSeek/DashScope 多模态调用：当前凭证均返回 401，识别结果保持不可用，未伪造结论。
- 回归：compliance 包 685/685 绿（含 6 例签章假通过新回归：落款标签、数字签名名词、标签+占位符硬 FAIL、文本 PASS+视觉不可用、无页码线索、承诺书电子签章标签）。
