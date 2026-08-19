# Implementation Plan: 投标文件多模态图片评分（方案A）

**Branch**: `agent/trae/score-parse-image-multimodal` | **Date**: 2026-08-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/048-score-parse-image-multimodal/spec.md`

## Summary

阶段2 客观项打分链路目前在转换阶段就丢弃图片、transport 只发文本、调用层只给文本 excerpt，导致"证书/图表等图内证据"在纯文本里不存在而误判不满足。本计划按方案A 三处改造：①sidecar 新增 `POST /convert-images` 抽图（含章节/页码归属 + 预算截断）；②transport 支持"文本 + image_url"多段 content；③调用层用纯核心 `ImageAssigner` 按评分项章节配图并附观测/降级。多模态模型已配 gemini-3.5-flash。详见 [research.md](research.md)（D1–D8，含 2026-08-18 设计评审修正：H1 预算数值校准 / H2 两路径均抽图 / M1 编排接线补齐 / M3 prompt 中性措辞 / M4 PDF 章节映射）。

## Technical Context

**Language/Version**: Java 21 / Spring Boot 3.2.0（后端）；Python 3 / FastAPI（sidecar）
**Primary Dependencies**: openai-java SDK（chat.completions）、markitdown（sidecar 已有）、python-docx/python-pptx/PyMuPDF(新，抽图)；Java HttpClient（已有）
**Storage**: 无新增 DB 表——图片随打分任务内存流转，不落库（research D6）
**Testing**: 纯核心单测（`ImageAssigner`/`ImageAssignmentPlan`）+ ArchUnit（`ArchitectureTest`/`FPJavaArchitectureTest`）+ 受影响用例 + 真实样本 V1；sidecar 用 curl + pytest 校验 `/convert-images`
**Target Platform**: 后端服务 + document-converter-sidecar 容器（host 8000，dev 8009）
**Project Type**: Web 后端链路能力改造（scoreparse + biddraftagent.transport）
**Performance Goals**: 单评分项图片配给 ≤ 预算（默认 3 张 / 5MB）；抽图响应整包 ≤ 25MB；不得复现 GB 文件整载内存导致的 OOM
**Constraints**: sidecar 转换阶段保持轻量（流式 + 抽图预算截断）；transport 不改变 provider/超时/风格；Config 不扩 schema
**Scale/Scope**: 阶段2 objective 对标喂图；subjective、阶段1、前端不改；InMemory 路径降级纯文本

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 评估 |
|---|---|
| I. FP-Java / 纯核心 | ✅ `ImageAssigner`/`DocumentImage`/`ImageAssignmentPlan` 为无框架依赖纯核心，编排在 application/infrastructure |
| II. Real-API Only | ✅ 真实 sidecar + 真实 gemini-3.5-flash，无 mock |
| III. TDD | ✅ 纯核心单测先写（Red→Green）+ ArchUnit 全绿 + 受影响用例；蓝图 E2E 用 `[skip e2e-scope]`（纯后端链路） |
| IV. Split-First | ✅ 新增类均 <300 行，单文件单一职责 |
| VII. Defensive & Degradation | ✅ FR-005/FR-006：失败/超预算/模型不支持 → 该项降级纯文本，`log.warn` + Sentry，不阻断 |
| Code Quality (§toMap merge) | ✅ 新增 stream 均带 merge function，不新增 2 参数 toMap |

**结果：无 violation → Complexity Tracking 留空。**

## Project Structure

### Documentation (this feature)

```text
specs/048-score-parse-image-multimodal/
├── plan.md              # 本文件
├── research.md          # Phase 0：根因 + D1–D7 决策
├── data-model.md        # Phase 1：值对象/DTO/职责转换矩阵
├── quickstart.md        # Phase 1：配置 + 端到端验证场景
├── contracts/
│   ├── sidecar-images-api.md      # POST /convert-images 契约
│   └── transport-multimodal.md    # transport 多模态 content 契约
└── tasks.md             # Phase 2（/speckit-tasks 生成，非本命令）
```

### Source Code

```text
document-converter-sidecar/
├── app.py                       # 新增 POST /convert-images 端点
├── image_extractor.py           # 抽图纯函数（docx/pptx/pdf 分派 + 章节归属 + 预算截断）
└── converter.py                 # 保持（extract_headings）

backend/src/main/java/com/xiyu/bid/
├── scoreparse/domain/
│   ├── DocumentImage.java            # record：图片值对象
│   ├── ImageAssignmentPlan.java      # record：配给 + 裁剪/降级信号
│   ├── ImageAssigner.java            # 纯核心：章节配给 + 确定性裁剪
│   └── ImageTokenEstimator.java      # 纯核心：图片 token 估算（观测）
├── scoreparse/application/
│   ├── BidFileLoadingCoordinator.java   # LoadedBidDocument 增加 images；InMemory/OnDisk 两路径均调抽图（H2）
│   ├── SidecarImageExtractor.java       # /convert-images 客户端（新）：ofFile 流式 + ofByteArray 直传双入口
│   ├── ScoreItemAssessor.java           # assess 签名扩展（images+budget）；objective 分支配图 → assessObjective 新过载
│   └── ScoreScoringAppService.java      # doExecuteScoring 传 loaded.images() 与 ImageBudget（M1）
├── scoreparse/infrastructure/openai/
│   ├── OpenAiScoreAnalyzer.java        # assessObjective 新过载透传 images
│   └── ScoreParsePrompts.java          # objective prompt 加图片指引（US1 中性措辞：先判相关性再判定，review M3）
└── biddraftagent/infrastructure/openai/
    ├── OpenAiStructuredOutputTransport.java    # 新增 requestWithChatCompletionsMultimodal
    ├── OpenAiStructuredOutputService.java       # 新增多模态 request 过载
    ├── OpenAiBidAgentRequestConfig.java        # 不变
    ├── OpenAiSdkStructuredOutputTransport.java # 多段 content 组装（json_schema + json_object）
    └── MultimodalImage.java                     # record：image_url 内容模型（新）
```

**Structure Decision**: 图片属于 scoreparse（打分能力），transport 多模态属于 biddraftagent.infrastructure.openai
（AI 传输能力），与既有代码归属一致，不新增模块。sidecar 抽图放 `document-converter-sidecar`（与转换纯函数同层）。

## Phase 0 → Phase 1 决策映射

| research 决策 | 落地点 |
|---|---|
| D1 transport 仅聊 chat.completions | transport-multimodal.md 契约 |
| D2 新增 /convert-images | sidecar-images-api.md |
| D3 一阶段抽图 + 预算截断 | SidecarImageExtractor + image_extractor.py |
| D4 纯核心 ImageAssigner | scoreparse/domain/ImageAssigner.java |
| D5 两路径均抽图（InMemory byte[] 直传 / OnDisk 流式） | BidFileLoadingCoordinator + SidecarImageExtractor（review H2） |
| D6 图片不落库 | data-model.md 概览 |
| D7 配额开关 + 观测 | quickstart.md 配置 |
| D8 PDF 文本层页码→标题映射；纯扫描件 warnings 明示 | image_extractor.py / sidecar-images-api.md（review M4） |

## Complexity Tracking

> 无 Constitution violation，无需填写。