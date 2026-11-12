---
title: 项目580扫描投标PDF实际评分与预估边界
space: engineering
category: lesson
tags: [scoreparse, OCR, 扫描件, 实际评分, 预估]
sources:
  - docs/lessons/lessons-learned.md
  - specs/20261111-scanned-bid-scoring/spec.md
  - document-converter-sidecar/converter.py
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/BidFileLoadingCoordinator.java
backlinks:
  - _index
  - score-parse-service
created: 2026-11-11
updated: 2026-11-12
health_checked: 2026-11-12
---
# 项目580扫描投标PDF实际评分与预估边界

- 生产任务d4c579d0，release2960e7ea5：177MB下载到Sidecar后200空正文（chars0、low_text_density），extractOnDisk:140终止，AI未开始。
- 评分入口的完整PDF OCR必须覆盖内存/磁盘两种文件路径；表格OCR30页样本不能替代1225页全卷。
- X-Pdf-Ocr:true 为评分专用 opt-in；默认转换不变。PDF：有图或无原生文字的页送现有 Tesseract；单页识别为空保留该页已有文字，整卷无文字才失败；引擎缺失、非零退出或超时整卷失败。
- DOCX 走同一评分入口。原生文字保留，可解码的非纯白嵌入图片均用同一套 Tesseract，不按像素尺寸跳过小型证据截图；透明图（RGBA/LA/调色板透明度）先合成白底再识别，避免透明黑底变成全黑；图标识别为空可继续，整卷无文字才失败。引擎缺失、非零退出或超时必须整卷失败，不因已有原生正文或前序图片文字而静默返回部分正文。
- 旧版 .doc 内存文件保留原 textExtractor → DocInsight → POI HWPF 回退；原磁盘分流不变。Linux Sidecar 尚无已验证的 DOC 转 DOCX 工具，不宣称扫描 .doc 已支持 OCR。
- Java安全错误映射只认BID_OCR_UNAVAILABLE/TIMEOUT/EMPTY；任务和逐项missedReason同源。
- 预估5项主观+4项客观不支持自动匹配：显示待专家评审/需人工评估；不是零分，也不是实际评分失败导致。
- 跨模块：Casework/Compliance/Tender其他extract调用仍保留原契约；评分HTTP1800s/OCR1500s独立预算。Sidecar和后端需配套发布。
- 交付界限：仅本地修复与合成真实OCR验证；未部署、未生产重算，177MB全卷时延待验收。
