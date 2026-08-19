# Tasks: 投标文件多模态图片评分（方案A）

**Input**: Design documents from `/specs/048-score-parse-image-multimodal/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/sidecar-images-api.md, contracts/transport-multimodal.md, quickstart.md

**Tests**: Constitution III（TDD NON-NEGOTIABLE）+ spec SC-005（ImageAssigner 纯单测覆盖五分支）→ 纯核心与服务分发层按 TDD 含测试任务；sidecar 用已有 pytest 基建。

**Organization**: 任务按用户故事分组（US1/US2/US3），每个故事独立可测、独立交付。已并入 2026-08-18 设计评审修正（H1/H2/M1/M2/M3/M4，详见 research.md §Review 修正记录）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 归属用户故事
- 所有路径为仓库根相对路径

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖与配置就位（不写业务代码）

- [X] T001 [P] sidecar 新增抽图依赖：`document-converter-sidecar/requirements.txt` 追加 `python-docx`、`python-pptx`、`PyMuPDF`（pillow 已有）；本地 venv 安装并 `python -c "import docx, pptx, fitz"` 验证可导入
- [X] T002 [P] 后端多模态配置：`backend/src/main/resources/application-dev.yml` 新增 `app.score-parse.multimodal.*`（`enabled` 默认 true、`max-images-per-item` 3、`max-image-bytes-per-item` 5242880、`max-extract-response-bytes` 26214400；对齐既有模式——dev yml + 代码默认值，prod 靠 env 注入，核对 prod 环境变量清单，review L2）。数值与 quickstart.md 一致；注意与 sidecar 预算按 base64 ×4/3 校准（review H1）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 三个故事共享的值对象（data-model.md §1/§3），MUST 完成后故事才可开工

- [X] T003 [P] [值对象] 创建 `DocumentImage` 与 `ImageBudget` record 于 `backend/src/main/java/com/xiyu/bid/scoreparse/domain/DocumentImage.java`、`ImageBudget.java`（字段与 data-model.md §1 完全一致，无框架依赖）
- [X] T004 [P] [值对象] 创建 `ImageAssignmentPlan` record 于 `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ImageAssignmentPlan.java`（含 `none()`/`hasImages()`；不变量：`degraded=true ⇒ images 为空`，compact constructor 断言 + `ImageAssignmentPlanTest` 5 用例覆盖）
- [X] T005 [P] [值对象] 创建 `MultimodalImage` record 于 `backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/MultimodalImage.java`（含 `dataUrl()` 组装，`MultimodalImageTest` 2 用例覆盖）

**Checkpoint**: 数据形状就绪，三个故事可并行开工

---

## Phase 3: User Story 1 - 客观项标书截图/证书/图表能看图打分 (Priority: P1) 🎯 MVP

**Goal**: 端到端打通"抽图 → 传输 → 喂图打分"：含图片证据的客观评分项不再因"图片在纯文本缺失"误判不满足；含三条降级路径（无图/损坏/模型拒绝 → 纯文本不阻断）。

**Independent Test**: 真实投标文件（内含证书扫描件/图表，正文仅占位文字）→「AI 实际打分」→ 客观项 `evidence` 描述图片内容并判定"满足"（quickstart V1；注意 skip 指纹——需上传新文档或改评分项，review L4）。

### Tests for User Story 1（先写、确保 FAIL）

- [X] T006 [P] [US1] 新建 `backend/src/test/java/com/xiyu/bid/scoreparse/application/SidecarImageExtractorTest.java`（参照 `SidecarStreamTextExtractorTest`）：鉴权头携带、`ofFile` 流式上传与 `ofByteArray` 直传（InMemory 路径，review H2）双入口契约、响应超 `max-extract-response-bytes` 抛、非法 mimeType/空 base64 项防御式跳过、sidecar 失败返回空图降级
- [X] T007 [P] [US1] 扩展 `backend/src/test/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiStructuredOutputServiceTest.java`：多模态过载 `images` 为空 → 委托纯文本路径；`apiStyle=RESPONSES` 且 images 非空 → `IllegalStateException`
- [X] T008 [P] [US1] 扩展 `backend/src/test/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiSdkStructuredOutputTransportTest.java` 与 `backend/src/test/java/com/xiyu/bid/scoreparse/infrastructure/openai/ScoreParsePromptsTest.java`：多模态参数组装（多段 content 含 `image_url` data URL、json_schema/json_object 两路径）、objective prompt 含图片判定指引文案（中性措辞，见 T016）

### Implementation for User Story 1

**链A — sidecar 抽图**（契约 contracts/sidecar-images-api.md）：

- [X] T009 [US1] **先做 30 分钟 spike**（review L5）：验证本地 venv 的 markitdown docx 转换器能否配置输出 data-URI 图（若能则章节归属随位置免费获得，砍掉 docx 自建分支）；spike 结论写入 commit message。随后实现抽图纯函数：新建 `document-converter-sidecar/image_extractor.py`（docx→python-docx 遍历 inline image、pptx→python-pptx 遍历 PICTURE shape、pdf→PyMuPDF `page.get_images`；**PDF 有文本层时用"页码→最近标题"映射回填 `section`；纯扫描无文本层仅 `pageHint` + warnings 明示"扫描件无章节归属"**，review M4/D8；章节归属=图前最近标题/slide 标题；`SIDECAR_MAX_IMAGE_BYTES` 默认 `15728640`=15MB 累计预算截断 → `truncated`，与 Java 响应上限 25MB 按 base64 ×4/3 校准，review H1；其它类型返回空清单）+ 新建 `document-converter-sidecar/tests/test_convert_images.py`（pytest，参照 `test_convert_stream.py`，含扫描 PDF warnings 用例）
  - **Spike 结论（2026-08-19）**：markitdown `keep_data_uris=True` 验证通过——data-URI base64 无损回传内嵌图片字节，heading 按文档顺序出现，章节归属由"图前最近 markdown heading"免费获得，docx 自建分支已砍掉（见 image_extractor.py 模块头）。
- [X] T010 [US1] 端点接线：`document-converter-sidecar/app.py` 新增 `POST /convert-images`（复用 `_check_auth` 与 64KB 流式 size guard；响应字段 `images[]/truncated/warnings` 与契约一致；422/401/413 语义对齐）
- [X] T011 [US1] sidecar 真实样本验证（本地已完成）：`curl -F` 真实样本实测——含图 docx 返回 2 张带 `section` 归属的图片（base64 可解码）；损坏图片 docx 返回 `images:[]` + `warnings:["跳过无法解析的图片"]`；无图 docx 图片清单为空（warnings 含 `low_text_density`）。扫描件 warnings"扫描件无章节归属"用例已由 pytest 覆盖（2026-08-19 本地 dev 栈验证）

**链B — Java 加载集成**：

- [X] T012 [US1] 新建 `backend/src/main/java/com/xiyu/bid/scoreparse/application/SidecarImageExtractor.java`（`/convert-images` 客户端：`ofFile`（OnDisk 流式 `HttpRequest.BodyPublishers.ofFile`）与 `ofByteArray`（InMemory 直传，review H2）双入口、复用 `app.score-parse.sidecar-url`/`SIDECAR_SHARED_KEY`、响应整包字节上限、Jackson 反序列化 + 防御式过滤；实现 T006 测试转绿）
- [X] T013 [US1] 改造 `backend/src/main/java/com/xiyu/bid/scoreparse/application/BidFileLoadingCoordinator.java`：`LoadedBidDocument` 增加 `images`（旧构造器默认 `List.of()` 兼容现网调用点）；**`extractInMemory` 与 `extractOnDisk` 两路径均调 `SidecarImageExtractor`**（doc-insight:// 与本地存储文档全量 byte[] 走 InMemory，不可静默无图，review H2）；失败/超限 → `log.warn` + 空图降级，不阻断打分任务（Principle VII）

**链C — transport 多模态**（契约 contracts/transport-multimodal.md）：

- [X] T014 [US1] 接口扩展：`backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiStructuredOutputTransport.java` 新增 `requestWithChatCompletionsMultimodal`；`OpenAiStructuredOutputService.java` 新增多模态 `request` 过载（images 空 → 委托纯文本；RESPONSES + images → 抛 `IllegalStateException`；实现 T007 转绿）
- [X] T015 [US1] SDK 实现：`backend/src/main/java/com/xiyu/bid/biddraftagent/infrastructure/openai/OpenAiSdkStructuredOutputTransport.java` 实现多段 content 组装（文本 part = prompt、图片 part = `image_url` data URL；`json_schema`（`StructuredChatCompletionCreateParams`）与 `json_object`（`ChatCompletionCreateParams`）两路径同构；`jsonObjectPrompt` 前缀并入文本 part；实现 T008 转绿）

**链D — prompt 与调用层**：

- [X] T016 [US1] prompt 改造：`backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/ScoreParsePrompts.java` 的 `buildObjectiveAssessmentPrompt` 增加图片指引段，**US1 用中性措辞**（review M3，配给与评分项无关，声明"相关"会失真）："附图为投标文件中的部分图片，请先判断每张图是否与评分项相关，再结合相关图片判定满足度，引用依据时说明来自图片"（实现 T008 prompt 断言转绿）
- [X] T017 [US1] analyzer 过载：`backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiScoreAnalyzer.java` 新增 `assessObjective(detail, weight, excerpt, List<DocumentImage>)` 过载（`DocumentImage → MultimodalImage` 映射后走 service 多模态过载；旧签名保留委托，零回归）
- [X] T018 [US1] assessor 改造：`backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreItemAssessor.java` `assess` 签名扩展（接收 `List<DocumentImage>` 与 `ImageBudget`；构造器注入 budget——该类为每任务 `new` 构造、非 Spring Bean）objective 分支 US1 最简配给（预算内全量携带）；降级路径：无图/损坏项跳过 → 纯文本；多模态调用抛"不支持图片 part"类 `BadRequestException`（非 json_schema fallback 分支）→ 纯文本重试一次 + `log.warn` + Sentry 上报，不阻断任务
- [X] T019 [US1] 编排接线（review M1 补齐）：`backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreScoringAppService.java` `doExecuteScoring` 中 `assessor.assess(task, item, bidDocText)` 调用点传入 `loaded.images()`；`ImageBudget` 从 `app.score-parse.multimodal.*` 配置解析后经 `ScoreItemAssessor` 构造器注入（两处 `new ScoreItemAssessor(...)` 调用点同步更新）

### 验证

- [~] T020 [US1] 端到端真实样本验证（quickstart V1）：主工作区起 dev 栈，上传含图证据投标文件 →「AI 实际打分」→ 客观项 `evidence/quote` 引用图片内容判定满足；同时验证 US1 场景 2（无图文档行为与现状一致）与场景 3（构造损坏图 → 该项纯文本、任务完成）；doc-insight 上传路径（InMemory）同样有图（review H2 验证点）
  - **本地已完成（2026-08-19 dev 栈，DeepSeek 纯文本降级）**：场景 2/3 全过——损坏图片文件打分任务 COMPLETED 100%、客观项落库正常（`actualScore/evidence/evidence`）、主观项 PENDING、数据库唯一约束冲突修复生效（重新打分 DELETE+INSERT 不再 Duplicate entry）、sidecar 损坏图跳过+warning。
  - **已知限制（SC-001 未达成）**：含图证据"evidence 引用图片内容判定满足"（Gemini 真消费）需测试环境配置 Gemini-3.5-Flash（custom provider）后补测；本地 dev 栈 activeProvider=deepseek（纯文本），无 Gemini key，无法本地验证 SC-001。部署时再补测。

**Checkpoint**: US1 端到端可演示 —— MVP 达成

---

## Phase 4: User Story 2 - 图片按评分项正确关联配给 (Priority: P1)

**Goal**: 图片按"评分项 ↔ 章节"正确配给：多章节多图文档中任一评分项只携带其章节子集；超预算确定性裁剪；无命中走纯文本。

**Independent Test**: `ImageAssignerTest` 五分支全绿（SC-005）+ 真实多章节多图样本打分后日志核对配给子集（quickstart V2）。

### Tests for User Story 2（先写、确保 FAIL）

- [ ] T021 [P] [US2] 新建 `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ImageAssignerTest.java`（SC-005 五分支）：无图 → 空配给纯文本；单图命中章节 → 携带；超预算（图数/字节）→ 确定性裁剪且 `truncated=true`；多章节隔离 → 只带本章节图、跨章节图不混入；无章节命中 → 空配给

### Implementation for User Story 2

- [ ] T022 [US2] 实现纯核心 `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ImageAssigner.java`：章节匹配**复用 `ScoreItemChapterMatch` 的字面重叠方向（contains 六向判断），不复用 `related()` 方法本身**——`related()` 对 quote/evidence=null 恒返回 true（"全相关"陷阱，review M2），实现时把方向判断抽为可共享纯函数；命中集合超 `ImageBudget` 时按"章节匹配距离近者优先 + index 顺序"确定性裁剪；产出 `ImageAssignmentPlan`（实现 T021 转绿）
- [ ] T023 [US2] `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreItemAssessor.java` 替换 US1 最简配给 → `ImageAssigner.assign(item, images, budget)`（US1 的降级路径保留不动）；`ScoreParsePrompts` 换 US2 措辞（"附图为投标文件中与该评分项章节相关的节选图"）
- [ ] T024 [US2] 真实样本验证（quickstart V2）：多章节多图投标文件打分，核对每评分项请求日志只含其章节图子集；超预算样本触发 `truncated` 可观测信号；纯扫描 PDF 样本 warnings 含"扫描件无章节归属"、不参与章节配给（review M4 验证点）

**Checkpoint**: US1 + US2 均独立可用

---

## Phase 5: User Story 3 - 多模态 token 成本可观测可控 (Priority: P2)

**Goal**: 每次含图打分可观测"携带图片数 + 图片 token 估算占比"；`multimodal.enabled=false` 全链路回退纯文本。

**Independent Test**: 含图打分日志含 `imageCount/estimatedImageTokens/textTokens`；关闭开关后打分零图片（quickstart V3/V4）。

### Tests for User Story 3

- [ ] T025 [P] [US3] 新建 `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ImageTokenEstimatorTest.java`（tile 启发式：宽高→tile 数→token 数；非图片 mimeType 抛/零）+ 实现纯核心 `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ImageTokenEstimator.java`（data-model §1 `ImageTokenEstimate`）

### Implementation for User Story 3

- [ ] T026 [US3] 观测接线：`backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreItemAssessor.java` 每次含图调用记录结构化日志（`imageCount/estimatedImageTokens/textTokens`，估算用 T025 组件）；配给裁剪时记录 `truncated` 信号。**仅结构化日志，不动 `ScoreResult` 实体**（加字段=Flyway 迁移，违背 D6；review L3）
- [ ] T027 [US3] 兜底开关接线：`app.score-parse.multimodal.enabled=false` 时 `ScoreItemAssessor`（或 `BidFileLoadingCoordinator` 入口）短路跳过抽图/配给，全链路零图片、零额外调用
- [ ] T028 [US3] 真实样本验证（quickstart V3/V4）：超预算样本观测到裁剪信号；开关关闭重跑打分 → 请求不含任何图、token 与纯文本基线一致

**Checkpoint**: 三个故事全部独立可用

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 门禁收敛与交付验证

- [ ] T029 架构与回归：`cd backend && mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest` + `mvn test -Dtest='ScoreParse*,OpenAi*'` 受影响用例全绿（transport/分层无破坏）
- [ ] T030 质量门禁：`mvn -Pjava-quality -Dquality.skip=false checkstyle:check pmd:check` 针对新增文件通过；确认新增代码无 2 参数 `Collectors.toMap`（pre-push gate 会拦截）
- [ ] T031 交付收敛：回跑 quickstart.md V1–V4 全场景；`git status` 确认只改授权文件；push WIP 分支 `git push origin HEAD:agent/trae/score-parse-image-multimodal`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**：无依赖，立即开始（T001/T002 可并行）
- **Phase 2 Foundational**：依赖 Phase 1；**阻塞所有故事**（值对象是共享形状）
- **Phase 3 US1（MVP）**：依赖 Phase 2
- **Phase 4 US2**：依赖 Phase 2 + US1 编排接线（T018/T019 → T023 替换）
- **Phase 5 US3**：依赖 Phase 2 + US1（观测与开关作用于 US1 建立的链路）
- **Phase 6 Polish**：依赖全部目标故事完成

### User Story Dependencies

- **US1 (P1)**：Foundational 后即可开工，不依赖其它故事 —— MVP
- **US2 (P1)**：依赖 US1 的编排接线点（T018/T019 → T023 替换关系），但策略与测试（T021/T022）可与 US1 并行开发
- **US3 (P2)**：依赖 US1 链路存在；`ImageTokenEstimator`（T025）可与 US1/US2 并行开发

### Within Each User Story

- 测试先写并确认 FAIL → 实现转绿（Constitution III）
- 值对象 → 客户端/端点 → 编排集成 → 真实样本验证
- sidecar 链（T009→T010→T011）先于 Java 消费端联调

### Parallel Opportunities

**US1 内四条独立链**（不同文件、只共享契约文档，可并行）：

```text
链A sidecar：  T009(spike+抽图) → T010 → T011
链B Java加载： T006(测试) → T012 → T013
链C transport：T007/T008(测试) → T014 → T015
链D prompt：   T008(prompt断言) → T016
汇合点：       T017 → T018 → T019 → T020（依赖链A/B/C/D 产物）
```

- Phase 1/Phase 2 全部 [P] 任务可并行
- T021/T022（US2 纯核心）可与 US1 链并行开发（仅依赖 T003/T004 值对象）
- T025（US3 估算器）可与 US1/US2 并行开发

---

## Parallel Example: User Story 1

```bash
# 四条链并行启动（不同文件，无相互依赖）：
Task: T009  "markitdown spike + image_extractor.py 抽图纯函数 + tests/test_convert_images.py"
Task: T006  "SidecarImageExtractorTest（先 FAIL，含 ofByteArray 双入口用例）"
Task: T007  "OpenAiStructuredOutputServiceTest 多模态过载用例（先 FAIL）"
Task: T016  "ScoreParsePrompts 中性措辞图片指引 + prompt 测试断言"

# 链内顺序推进，汇合后：
Task: T017 → T018 → T019 → T020
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup（T001/T002）
2. Phase 2 Foundational（T003–T005）
3. Phase 3 US1 全链（T006–T020，四链并行 + 汇合）
4. **STOP and VALIDATE**：T020 真实样本 V1 通过（含图证据客观项判定满足）→ 可演示/可部署
5. US1 最简配给 = 预算内全量携带（可接受：图多的文档浪费 token；prompt 已用中性措辞防误判，review M3）

### Incremental Delivery

1. Setup + Foundational → 值对象就绪
2. US1 → 端到端看图打分（MVP，验证多模态能力真实可用）
3. US2 → 配给正确性（省 token + 防错配），替换最简配给
4. US3 → 成本可观测 + 兜底开关（运维安全感）
5. Polish → 门禁全绿交付

---

## Notes

- [P] = 不同文件且无未完成依赖
- [Story] 标签映射 spec.md 用户故事，可追溯
- US2 的 T023 是"替换"而非"新写"——US1 用最简配给先打通端到端，US2 再换正确配给，两条 story 各自独立可测
- **评审修正落位**（research.md §Review 修正记录）：H1→T009/T002（预算 15MB 校准）；H2→T006/T012/T013/T020（双路径抽图）；M1→T018/T019（assessor+编排双任务）；M2→T022（不复用 related()）；M3→T016/T023（中性措辞→US2 换措辞）；M4→T009/T011/T024（PDF 章节映射+扫描件降级）；L2→T002；L3→T026；L5→T009 spike
- 真实样本验证任务（T011/T020/T024/T028）只在主工作区 trae 执行（多 Agent 执行手册约束），测试环境限定 winbid-01.test.rds.ehsy.com / 172.16.38.78（用户红线：禁止碰生产）
- **已知限制（2026-08-19）**：US1 端到端中"Gemini 真消费图片（SC-001：evidence 引用图片内容判定满足）"仅在测试环境可用——本地 dev 栈无 Gemini（activeProvider=deepseek 纯文本，`GEMINI_API_KEY` 未配置，DB 各 provider key 均未配置）。本地已完成：sidecar 抽图全链路（T011）、打分降级路径（T020 场景 2/3）、数据库唯一约束修复。SC-001 与自定义 provider 配置 Gemini-3.5-Flash 后待测试环境补测，记录于 T020。
- 每个任务或逻辑组完成后原子提交（RELIABILITY 约束）