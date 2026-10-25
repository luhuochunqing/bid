---
title: 超大标书流式解析与前端原生下载治理（2GB+ 内存防爆与 SAX 容灾）
space: engineering
category: guide
tags: [超大文件, 流式解析, SAX, iterparse, mammoth, 前端下载, 原生下载, 内存治理, OOM]
created: 2026-09-10
updated: 2026-09-10
health_checked: 2026-10-25
sources:
  - document-converter-sidecar/app.py
  - scripts/release/remote-deploy.sh
  - src/utils/download.js
  - src/views/Project/stages/components/ProjectDocumentTable.vue
  - src/views/Project/stages/DraftingStage.vue
backlinks:
  - _index
---
# 超大标书流式解析与前端原生下载治理

> 真实业务中投标文件可能包含数千张图片与数万段正文，单文件高达 1.95GB～2GB+。
> 本文记录超大文件在 Sidecar 文档转换服务与前端下载两端的 OOM 根因、流式架构改造与防护规范。

## 一、现象与根因剖析

以测试环境项目 1374（标书大小 1.95GB / 1994 MB，内含 3,025 张图片与 33.4MB \`word/document.xml\`、32,651 个正文段落）为例：

### 1. 标书质量核查失败（Sidecar cgroup OOM Killer）
- **现象**：后台核查任务抛错 \`标书质量核查前置校验失败：投标文件既无法从本地/OBS提取正文，也无历史缓存切片，请重新上传投标文件\`。
- **根因**：
  1. 测试环境 \`xiyu-sidecar\` Docker 容器内存上限配置为 \`--memory=1g\`，而环境变量配置了 \`SIDECAR_MAX_UPLOAD_MB=3072\`（3GB），参数存在硬性矛盾。
  2. Python 转换服务使用 \`markitdown\` / \`mammoth\` 解析 docx 时，会将 33.4MB 的 \`word/document.xml\` 构建为完整的内存 AST/DOM 树，瞬时内存消耗突破 1GB，触发 Linux 内核 cgroup OOM Killer（退出码 137，上游网关返回 HTTP 503）。
  3. 后端 Java 提取到空文本，前置校验拦截失败。

### 2. 投标文件下载失败（前端 Axios Blob 堆内存撑爆）
- **现象**：用户在项目文档列表或编制阶段点击投标文件“下载”无反应或提示“文件下载失败”。
- **根因**：
  1. \`downloadWithFilename\` 统一使用 Axios \`httpClient.get(apiUrl, { responseType: 'blob', timeout: 120000 })\`。
  2. Axios 会尝试将整个 1.95GB 文件作为连续 ArrayBuffer / Blob 缓冲进浏览器的 JavaScript V8 堆内存中。
  3. 超过浏览器单对象内存限制直接抛错，或因下载时长超过 120 秒触发 Axios 网络超时。

---

## 二、端到端解决方案

### 1. 后端 Sidecar：SAX 流式提取 + 容器内存配额对齐
- **容器内存配额对齐**：在 \`scripts/release/remote-deploy.sh\` 中将 \`xiyu-sidecar\` 容器内存调整为 \`--memory=3g --memory-swap=3g\`，与 \`SIDECAR_MAX_UPLOAD_MB=3072\` 严格对齐。
- **超大 docx 检测**：新增 \`_is_large_docx(docx_path, threshold_bytes=10MB)\`，直接通过 \`zipfile\` 读取 \`word/document.xml\` 的未压缩体积，超过阈值立即切换流式 SAX 解析器。
- **流式 SAX 解析器 (\`_extract_docx_streaming_sax\`)**：
  - 基于 Python 标准库 \`xml.etree.ElementTree.iterparse\` 监听 \`start\` 与 \`end\` 事件。
  - 流式逐段抽取标题（\`w:pStyle\`）、段落正文（\`w:t\`）、表格（\`w:tbl\`, \`w:tr\`, \`w:tc\`）。
  - 每个元素处理完成后立即执行 \`elem.clear()\`，避免 DOM 树在内存中累积。
  - **实测效果**：33.4MB XML / 39 万字正文，流式解析仅消耗 **13.58 MB** 内存，耗时 **3 秒** 提取出 116.3 万字符 Markdown，内存降低 **98.7%**，彻底杜绝 OOM。

### 2. 前端下载：原生 \`<a>\` 标签流式下载接管
- **原生下载函数**：在 \`src/utils/download.js\` 中增加 \`triggerNativeDownload(url, filename)\`，并通过隐藏的原生 \`<a download="...">\` 标签触发浏览器底层下载。
- **\`downloadWithFilename\` 扩展**：支持 \`options: { native: true }\` 选项。
- **业务组件改造**：
  - \`ProjectDocumentTable.vue\`：\`handleDownload\` 传入 \`{ native: true }\`。
  - \`DraftingStage.vue\`：\`handleDownloadBidFile\` 传入 \`{ native: true }\`。
- **流式接管机制**：
  - 用户点击后，浏览器原生发送带 Cookie（HttpOnly \`access_token\`）的 GET 请求。
  - 后端 \`ProjectDocumentController\` 鉴权通过后返回 \`302 Found\`（指向 OBS 签名下载直链）。
  - 浏览器原生下载引擎（Chrome/Edge 下载管理器）直接接管 OBS 流式写入本地磁盘，支持断点续传且零 JS 内存开销。

---

## 三、治理与工程规范

| 场景 | 严禁做法 | 规范做法 |
|---|---|---|
| **Docker 容器配置** | 容器内存 \`--memory\` 小于环境变量允许的最大上传大小 | 容器内存配额必须与应用配置的最大处理能力严格对齐 |
| **文档转换服务** | 对超大 XML/文档进行全量 DOM/AST 树解析 | 对超大文件必须提供基于流式 SAX/StAX 或逐行扫描的纯文本提取兜底，边处理边清理引用 |
| **前端大文件下载** | 使用 Axios \`responseType: 'blob'\` 将全量文件加载进内存 | 用户上传的大文件/交付物下载统一走原生 \`<a>\` 标签跳转，交由浏览器内核流式落盘 |
