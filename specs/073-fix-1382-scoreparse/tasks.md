# Tasks: 评分项证据链修复与跨文件隔离

**Input**: `spec.md`, `plan.md`, `research.md`, `data-model.md`
**Organization**: 按用户场景和根因顺序；所有实现任务严格先RED。

## Phase 1 - Baseline and fixture safety
- [ ] T001 固定候选SHA、Sidecar产物、任务272/273摘要及源文件类型映射；确认真实PDF本机SHA和脱敏策略。
- [ ] T002 建立输出差分记录格式，记录叶子、资格、证据范围、权重、父子关系及发布状态。
- [ ] T003 盘点其他PDF/DOCX/text/OCR/mixed样本，标明缺失样本而不假设通过。

## Phase 2 - US1 PDF structure
- [x] T004 [US1] 对当前候选执行现有PDF结构定向测试并用真实结构复现页38/39归属。
- [x] T005 [US1] 若RED，先在 document-converter-sidecar/tests/test_pdf_table_artifact.py 加子标题、真章、表外标题、未知章及跨页测试。
- [x] T006 [US1] 仅修 document-converter-sidecar/converter.py 的PDF章级事件判定，保持逐表与页末事件同源。
- [x] T007 [US1] 验证目录排除、跨章断开、未知章阻断及跨页续接正反例。

## Phase 3 - US2 evidence binding and semantics
- [x] T008 [US2] 在 TextPrimaryEvidenceBinderTest 写PDF多格名称+规则RED、单格优先、同名多候选、跨章和续页反例。
- [x] T009 [US2] 在 PdfRowEvidenceBinder.java 以确定的PDF来源限定分支实现精确结构范围绑定；保留document/section/table/row/cell和片段坐标。
- [ ] T010 [US2] 为PDF+DOCX文件顺序正反序写产物与mapper差分测试，先确认当前类型泄漏是否仍复现。
- [ ] T011 [US2] 如RED，修改artifact构建/传递按源文档关联fileType；不得以首artifact代表整批。
- [ ] T012 [US2] 对OriginalScoreWeightEvidencePolicy增加表格/文本相同条目证据路由RED及范围、多候选、区间、扣分、比例、共享上限、择优、否定、更正案例。
- [ ] T013 [US2] 实现同范围名称/类别/最大分策略；身份及父子聚合在事实规范化之后且不丢唯一性。
- [ ] T014 [US2] 回放固定模型候选和fallback，验证资格/权重无越界变化，旧成功和拒绝项差分逐条归因。

## Phase 4 - US3 UI state
- [x] T015 [US3] 在 ScoreParseDrawer.spec.js/useScoreParseDrawer.spec.js 添加旧3项58分与新待复核候选并存RED。
- [x] T016 [US3] 修复候选显示条件；明确区分已生效项与最新任务阻断状态，不伪造当前DTO未提供的旧项任务ID。
- [ ] T017 [US3] 验证无历史项、解析中、失败、通过、人工采纳、过期任务和版本/权限保护。

## Phase 5 - Cross-file acceptance
- [x] T018 运行完整Sidecar、scoreparse Java Golden、前端受影响测试并记录精确结果。
- [ ] T019 运行跨文件固定候选差分：1381、1384、1385可用样本、PDF/DOCX/text/OCR/mixed-order、未知总分、多方案及歧义。
- [ ] T020 对每个资格提升审阅原文证据；确认DOCX与非目标文件无未解释语义变化。
- [ ] T021 运行 ai-slop-cleaner Mode B，再运行全量受影响验证。
- [ ] T022 真实测试服务恢复后执行真实重解析三次、隔离项目验证、API/DB/浏览器核验；未执行则保留发布阻断。
- [ ] T023 运行健康基线对比、xiyu-code-review和Spec Kit analyze；留存 review/verify 工件。

## Dependencies
T001-T003 -> T004/T008/T010/T012/T015 -> implementation T006/T009/T011/T013/T016 -> T018-T021 -> T022 live acceptance -> review/delivery.
