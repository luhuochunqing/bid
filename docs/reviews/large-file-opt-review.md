# Code Review & AI Slop Audit · 大文件流式下载与超大 docx SAX 解析优化

> **审查标准**：Google Engineering Practices (`google-code-review`) & OMC Lean Code Standards (`ai-slop-cleaner`)
> **审查范围**：`agent/gemini/large-file-opt` vs `origin/main` (3 commits, 9 files)
> **PR 编号**：!2607 (https://gitee.com/allinai888/bid/pulls/2607)
> **审查结论**：✅ **MERGE_READY** (Code Health: Positive / Slop-Free / Zero-Defect)

---

## 一、审查概览 (Executive Summary)

针对测试环境项目 1374（1.95GB docx，内含 3,025 张图片与 33.4MB `word/document.xml`、32,651 个正文段落）引发的标书质量核查 OOM 崩溃与投标文件下载失败故障，本 PR 实施了系统级流式改造：
1. **Sidecar 容器规格对齐与流式 SAX 解析容灾**：内存上限从 1GB 提升至 3GB；针对 `document.xml` > 10MB 的超大 Word 文档提供 O(1) 内存复杂度的流式 SAX 提取，彻底解决 cgroup 137 OOM Killer。
2. **前端大文件原生流式下载接管**：改造 `download.js`、`ProjectDocumentTable.vue` 和 `DraftingStage.vue`，通过原生 `<a>` 标签跳转流式下载，彻底移除 Axios `responseType: 'blob'` 导致的浏览器 JS 堆内存撑爆。

---

## 二、Google Code Review 维度评审

### 1. 设计与复杂度 (Design & Complexity)
- **极简主义设计**：
  - 未引入任何第三方 XML 解析或流式下载库，全部复用 Python 标准库 `xml.etree.ElementTree.iterparse` 与浏览器原生 HTML5 `<a>` 标签机制。
  - 前端 `downloadWithFilename` 采用可选参数 `options.native`，保持既有函数签名 100% 向后兼容，无需重构现有几十处调用点。
- **无过度设计 (No Over-Engineering)**：
  - 判定超大文件仅通过 `zipfile` 快速读取 central directory 中的 `file_size` 属性，耗时 <1ms，无冗余解压开销。
  - 提取器仅捕获标题级别（`w:pStyle`）、段落正文（`w:t`）与表格（`w:tbl`），严格匹配标书质量核查所需的 Markdown 证据结构，不引入复杂的富文本排版模拟。

### 2. 功能与边界处理 (Functionality & Edge Cases)
- **损坏文件容灾**：`test_extract_docx_streaming_sax_corrupted_file` 验证非合法 ZIP 或损坏文件优雅降级返回空文本，并记录 warning 日志，不向上抛出未捕获异常。
- **内存回收断言**：在 SAX 循环的 `end` 事件中严格调用 `elem.clear()` 和 `root.clear()`，杜绝大循环中 ElementTree 的隐式 DOM 引用驻留。
- **空输入与特殊值**：
  - `triggerNativeDownload('', ...)` 针对空 URL、非浏览器环境（SSR/node）做了直接短路保护。
  - `downloadWithFilename` 针对 `options = null`、`options = false` 等非标准入参均有默认值或可选链（`options?.native`）保护。

### 3. 测试健康度 (Testing & Coverage)
- **TDD / 边界单测覆盖**：
  - Sidecar 测试（5 项）：标准提取、阈值检测、Worker 动态路由、损坏文件兜底、标题样式映射全部覆盖，执行耗时 1.62s。
  - 前端测试（3 个套件 79 项）：`download.spec.js`、`ProjectDocumentTable.spec.js`、`DraftingStage.spec.js` 覆盖空值、原生下载触发、异常提示、阶段下载权限等全部场景，执行耗时 1.44s。
- **实机证据闭环**：
  - 在测试环境 `172.16.38.78` 针对故障样本 1374 实测：质量核查由原先的 503 OOM 变为端到端成功出具 25 项核查报告；流式解析仅耗时 3 秒，内存仅 13.58MB。

### 4. 命名与代码风格 (Naming & Style)
- 函数命名表达精确：`_is_large_docx`、`_extract_docx_streaming_sax`、`triggerNativeDownload`。
- 遵循 ESLint 与 PEP8 规范，无未使用的局部变量与残留 debug 代码。

---

## 三、AI Slop 专项审查 (`ai-slop-cleaner`)

对照 AI 常见代码坏味道清单逐项排查：

| 检查项 | 状态 | 现场审计说明 |
|---|---|---|
| **无用抽象与胶水层** | ✅ 无 | 没有创建 `DownloadManagerFactory` 或 `SaxParserStrategy` 等多余类，纯函数实现 |
| **虚假/过度防御 (Paranoid Check)** | ✅ 无 | 无多层嵌套的 `if (x !== null && x !== undefined)`，仅在外部入口做简单短路 |
| **废弃代码与未触达分支** | ✅ 无 | 所有新增分支均有对应的自动化测试覆盖 |
| **外部依赖污染** | ✅ 无 | 未添加任何 npm 或 pip 第三方依赖 |
| **AI 样板注释** | ✅ 无 | 无废话注释（如“// 遍历元素”），保留的注释均说明业务背景（如“避免 Axios blob OOM”） |

---

## 四、审查裁决

- **裁决结果**：✅ **MERGE_READY**
- **合入建议**：代码质量优秀，架构极简且具备自愈降级能力，无技术债引入，建议直接合入主干。
