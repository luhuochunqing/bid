---
title: AI 生成案例超大标书 OOM 导致入库 0 条 — 整文件读堆 + 二次分配与"假成功"上报
space: engineering
category: pitfalls
tags: [教训, OOM, docinsight, 案例沉淀, case, OBS, 超大文件, 大小防护, 根因, 修A破B]
sources:
  - docs/lessons/lessons-learned.md
  - backend/src/main/java/com/xiyu/bid/docinsight/infrastructure/extractor/MarkItDownSidecarExtractor.java
  - backend/src/main/java/com/xiyu/bid/casework/application/ProjectClosedEventListener.java
backlinks:
  - _index
  - case-precipitation-streaming
created: 2026-08-20
updated: 2026-09-05
health_checked: 2026-09-05
---
# AI 生成案例超大标书 OOM 导致入库 0 条

> **症状**：项目结项后点【AI 自动生成案例】，收到通知"AI 生成案例已完成，入库 0 条案例"。
> 表面是"成功"，实际是提取 401MB 标书 OOM 后回退占位文本，让 AI 对着"暂无标书正文"虚假产出 0 条。

## 一、根因链（5 Whys）

| 层 | 事实 |
|---|---|
| 症状 | 通知"成功但入库 0 条" |
| 为什么 1 | OBS 标书文本提取失败后 `loadBidFileMarkdown` 回退占位文本 `# 项目名\n暂无标书正文。` |
| 为什么 2 | 提取失败是因为 `java.lang.OutOfMemoryError: Java heap space` |
| 为什么 3 | 401MB 标书被 `RestTemplate.getForObject(url, byte[].class)` **整文件读进堆** |
| 为什么 4 | fallbackExtract 对二进制内容 `new String(content, UTF_8)` **二次分配**（同为几十上百 MB 级） |
| 根因 | ① 整文件读堆无大小防护；② OBS 是真实数据源却走"回退占位"的假成功路径 |
| 暴露背景 | **修A破B**：本次是修复 OBS 预签名 URL 二次编码(403)后，才走到"下载成功→整读→OOM"，暴露了框架理解偏差 |

## 二、框架理解偏差（修A破B 的根因）

- `RestTemplate.getForObject(byte[].class)` 会把响应**整体读进堆内存**，无大小防护。
- fallbackExtract 对二进制文件 `new String(content, UTF_8)` 再分配一份，堆峰值 ≈ 2× 文件大小。
- sidecar 端有 `SIDECAR_MAX_UPLOAD_MB`（默认 30MB）硬上限，超大文件即使上传也会被 413。

## 三、修复方案

### 3.1 提取器入口大小防护（`MarkItDownSidecarExtractor`）

- 新增 `maxUploadBytes` 字段，注入 `@Value("${app.doc-insight.sidecar-max-upload-mb:30}")`，与 sidecar 侧默认一致。
- `extract` 入口在进入任何大内存 POST/fallback **之前**做检查，超限抛 `DocInsightException`：
  - 抛领域异常（非通用 `IllegalArgumentException`），携带业务语义错误码；
  - **不在 `@Retryable(retryFor={RestClientException, IllegalStateException})` 内**，超限不重试；
  - 由调用方上报失败而非内存爆炸。

### 3.2 OBS 提取失败必须上报（`ProjectClosedEventListener.loadBidFileMarkdown`）

- OBS（`obs-direct:`）是**真实标书数据源**，读不到 / 提取失败 / 超限 → `throw e`，走 `notifyFailure`。
- **禁止**回退占位文本让 AI 对着"暂无标书正文"虚假"成功但入库 0 条"。
- 本地/挂载路径保留容错（文件可能已迁移），回退占位继续后续流程。

## 四、根因行为测试

| 测试 | 覆盖点 |
|---|---|
| `MarkItDownSidecarExtractorTest#oversizedContent_throwsControlledExceptionInsteadOfOom` | >30MB content 抛 `DocInsightException`（含"超过转换上限"/"30MB"），并校验 `restTemplate.postForObject` **never** —— 超限在进任何网络请求前兜住 |
| `ProjectClosedEventListenerTest#obsBidFile_extractFailure_notifiesFailureInsteadOfFalseSuccess` | OBS 提取抛超限异常 → `notifyFailure`（原因透出超限真相）+ `knowledgeCaseRepository.saveAll` **never** —— 绝不占位假成功 |

## 五、可复用规则

- **大文件处理先问三个问题**：a) 有没有大小上限？b) 超限走哪条路径（403/413/异常）？c) 失败是否被正确上报？
- **`new byte[].class` 传输无防护**：凡是 `getForObject(...byte[].class)` 或 `Files.readAllBytes` 的输入来自外部服务时可超限，必须在入口拦截后再分配。
- **修A破B 排查法**：修复一个错误（403）后出现的"新问题"（OOM），往往是被旧错误掩盖的同链路缺陷，不是回归，要沿同一调用链往前追。
- **真实数据源禁止假成功**：OBS 标书是真实数据源，提取失败必须上报失败通知；占位文本只能用于"可有可无"的容错路径。

## 六、相关文件

- `MarkItDownSidecarExtractor.java` — 大小防护入口
- `ProjectClosedEventListener.java` — OBS 失败 rethrow
- `ProjectClosedEventListener.loadFileBytes` — OBS 预签名 URL 用 `URI.create`（避免 % 二次编码 403，见 `docs/lessons/lessons-learned.md §132`）