---
title: AI 标书质量核查解析失败误报修复（超时预算 × 文档规模 + 失败语义分类）
space: engineering
category: lesson
tags: [质量核查, compliance, sidecar, 超时预算, 失败分类, 大文件, 扫描件, spec-066]
created: 2026-09-20
updated: 2026-09-20
health_checked: 2026-10-25
sources:
  - specs/066-fix-quality-check-parse/spec.md
  - specs/066-fix-quality-check-parse/research.md
  - specs/066-fix-quality-check-parse/contracts/parse-outcome-contract.md
  - backend/src/main/java/com/xiyu/bid/compliance/service/SidecarStreamDocumentAdapter.java
  - document-converter-sidecar/converter.py
backlinks:
  - _index
  - lessons-learned
  - bid-quality-check-realization
  - bid-quality-multifile-check
---
# AI 标书质量核查解析失败误报修复（spec 066）

> 生产 4 个项目核查 100% 失败、报「请确认文件可正常打开后重新上传」，实际文件完好。

## 故障形态与证据

| 项目 | 文件 | 真实机制 | 证据 |
|---|---|---|---|
| 22 | 165MB PDF / 2462 页 / 有文本层 | 300s 大文件档超时 | 失败 301s；同文件评分链路解析 203s |
| 46 | 441MB PDF / 4101 页 / 有文本层 | 300s 档超时 | 失败 302s；评分链路同文件 1133s；实测 reopen 2.5s×411 次=1027s（占 90%） |
| 35 | 565MB docx / 媒体占 97% | 瘦身后 <50MB 掉回 60s 档 | 失败 65s |
| 580 | 177MB PDF / 1225 页 | **纯图片扫描件**（每页 chars=0、images=1），Sidecar 75s 返回 200 空文本 | Java 丢弃 `low_text_density` 警告后误报 |

## 根因链

- 适配器 `catch (Throwable)` 把超时/HTTP 错误/空文本一律吞成**空文档哨兵**，失败不可区分。
- 等待上限与文档规模错配：60s 基准、>50MB 仅 ×5=300s；docx 档位按**瘦身后**大小判定。
- 放大因素：Sidecar 单 Worker 串行 + 项目 30 连续 6 次 ~183s 重试造成解析饥饿。
- 流程根因：1373「根治」只测了 `resolveTimeoutSeconds` 算术（测试不测根因），生产 3 周持续失败。

## 修复（spec 066）

- `SidecarParseOutcome` 五分类（OK/TIMEOUT/SERVICE_ERROR/EMPTY_TEXT/UNPARSEABLE），保留永不抛契约；连接建立超时归服务错误、413 不承诺重试、2xx 空响应/非法 JSON 归服务错误。
- `SidecarTimeoutBudget`：≤50MB 用基准档；>50MB 起 `base×5 + (sizeMB−50)×3`，封顶 1800s；**未知大小走大文件档下限**；档位按**原始文件**大小（瘦身不降档）。
- 文案契约：只有 UNPARSEABLE/存储缺失允许引导重传；超时/服务异常/无文本层给准确动作。
- Sidecar PDF 分块 10→100 页（`SIDECAR_PDF_CHUNK_PAGES` 可覆盖），441MB 解析 1133s→预期 ~224s；每次转换结构化日志（file/size/duration_ms/chars/pages/warnings）。
- 前端等待窗口 10→30 分钟，到顶转「后台执行中」态（不误报失败、可刷新恢复轮询）。
- 招标链路（TenderDocumentLoader）非 OK 容忍降级空文档+WARN，不阻断评分解析。

## 跨模块副作用（RAG 易丢）

- 同一 `SidecarStreamDocumentAdapter` 有两个调用方：`ComplianceTargetLoader`（抛准确业务异常）与 `TenderDocumentLoader`（容忍降级）——改返回类型必须同时适配。
- 前端停止轮询必须新增模板分支，否则落入「尚未执行核查」空白态。
- Sidecar 分块参数是**免重建热调**开关（环境变量），上线顺序：sidecar 镜像/环境变量 → 后端 jar → 前端。
- 纯图片扫描件在不引入 OCR 前不可核查，只能给能力边界提示。

## 验证与上线

- 后端 70 项切片绿；全量 9918 项失败集合与基线逐项一致（34/34 环境性），另修复 1 个基线红测。
- sidecar pytest 112 绿（含 O(chunk) 内存守卫 + 默认分块剖面）；vitest 10 绿；`npm run build` 通过。
- 真机复跑（22/35/46/580）需部署授权后执行。
