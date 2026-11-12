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
updated: 2026-11-13
health_checked: 2026-11-13
---
# 项目580扫描投标PDF实际评分与预估边界

- 生产任务d4c579d0，release2960e7ea5：177MB下载到Sidecar后200空正文（chars0、low_text_density），extractOnDisk:140终止，AI未开始。
- 评分入口的 PDF OCR 必须覆盖内存/磁盘两种文件路径。表格 OCR 的 30 页样本不用于评分。候选不超过 40 时不另设页数上限。
- X-Pdf-Ocr:true 为评分专用 opt-in；默认转换不变。PDF：有图或无原生文字的页送现有 Tesseract；单页识别为空保留该页已有文字，整卷无文字才失败。引擎缺失或非零退出整卷失败。已经有正文时，识别超时交回正文，不再整卷作废；完全没有正文时超时仍失败。超过 40 个候选时，已有正文只光栅化 12 页，没有正文时 24 页；先选没有原生文字的页，同组再选嵌入图更大的页。跳过的页仍交回原生正文。送识别前长边缩到约 2000 像素。
- DOCX 走同一评分入口。原生文字保留。嵌入图片按体积从大到小识别，超过 40 张时只打开上述张数，其余媒体留在压缩包里，不解码。有正文后默认再识别 180 秒就停下，避免上千张图吃掉 30 分钟打分窗口。不按像素尺寸跳过小型证据截图；透明图（RGBA/LA/调色板透明度）先合成白底再识别，避免透明黑底变成全黑；图标识别为空可继续，整卷无文字才失败。引擎缺失或非零退出仍然整卷失败。超时且已有正文则交回正文。
- 旧版 .doc 内存文件保留原 textExtractor → DocInsight → POI HWPF 回退；原磁盘分流不变。Linux Sidecar 尚无已验证的 DOC 转 DOCX 工具，不宣称扫描 .doc 已支持 OCR。
- Java安全错误映射只认BID_OCR_UNAVAILABLE/TIMEOUT/EMPTY；任务和逐项missedReason同源。
- 预估5项主观+4项客观不支持自动匹配：显示待专家评审/需人工评估；不是零分，也不是实际评分失败导致。
- 跨模块：Casework/Compliance/Tender其他extract调用仍保留原契约。评分 HTTP 1800 秒；无正文时 OCR 上限 1500 秒，已有正文后再识别 180 秒。Sidecar和后端需配套发布。
- 交付界限：仅本地修复与合成真实OCR验证；未部署、未生产重算，177MB全卷时延待验收。
