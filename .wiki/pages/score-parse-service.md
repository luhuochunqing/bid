---
title: AI 评分标准解析后端服务
space: engineering
category: module
tags: [评分标准, scoreparse, LLM, 知识库匹配, 异步任务, 阶段打分]
created: 2026-08-15
updated: 2026-08-16
health_checked: 2026-08-16
sources:
  - specs/041-ai-score-parse-backend/spec.md
  - specs/041-ai-score-parse-backend/plan.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/
  - docs/implementation-notes/ai-score-parse-backend.md
backlinks:
  - _index
  - ai-capabilities
  - architecture
  - data-model
  - spring-pitfalls
---
# AI 评分标准解析后端服务

## 1. 定位（spec 041）

从招标文件评分办法中结构化抽取评分项，联动知识库完成「预计得分（阶段1）」与「实际打分（阶段2）」。独立于旧 `scoreanalysis` 模块（旧模块是前端手工表格，本模块是 LLM 解析 + 知识库匹配）。

## 2. 三张新表（V1187/V1188）

| 表 | 职责 | 关键约束 |
|----|------|----------|
| `score_parse_task` | 解析/打分任务（PENDING→PROCESSING→COMPLETED/FAILED） | `task_id` 唯一；`task_type` PARSE/SCORING；30min 超时扫描 |
| `score_item` | 评分项（解析产物） | FR-021: 重新解析按 `project_id` 覆盖清理；`score_type` OBJECTIVE/SUBJECTIVE 由域策略判定 |
| `score_result` | 打分结果（阶段1/2） | `score_item_id` 无 FK 级联，须先删 result 再删 item |
| `performance_record`（改） | 加 `contract_amount` 列（V1188） | 业绩匹配金额字段 |

## 3. 解析管线（US1，@Async 分钟级）

```
trigger（同步<1s，互斥校验+建任务）
  └→ executeParse（异步）
      四路召回（关键词/文档结构/评分规则语义/LLM全文）
      → ScoreItemMergePolicy 合并去重
      → WeightSumCheck + ItemCountCheck 闭环校验（差异触发二次回补）
      → ScoreTypeClassificationPolicy 客观/主观分类
      → ScoreItemPersistenceService 覆盖落库
      → EstimatedScoreService 阶段1预计得分（链尾）
```

- **正文来源（2026-08-16）**：立项 `TENDER` 优先，其次 Bid Agent `TENDER_FILE`，再兜底旧快照。不再要求走「启动 AI 生成初稿」。
- **自动解析门闩（spec 043）**：仅当从未有过 PARSE 任务且无评分项时自动新建；PENDING/PROCESSING 跟随已有任务；FAILED 展示原因、不自动重打。无源 400 会落 FAILED，避免每次打开抽屉再 POST。
- **读取上限**：远程/本地招标文件硬限制 50MB（先看 Content-Length，再流式累计）。超大无底稿提示「招标文件超过 50MB，无法解析」；有底稿则回退。`hasSource` 与 `resolve` 同一套成功条件。
- 事件挂链：`TenderDocumentStoredEvent` 仍可触发解析，但产品主路径是立项文件 + 评分抽屉
- 进度：Redis 缓存 by taskId（`ScoreParseProgressService`），非 DB 轮询

## 4. 知识库五类匹配（US2，`/api/score-parse/match/*`）

| 接口 | 数据源 | 降级匹配 |
|------|--------|----------|
| `cert:match` | 资质表 | 证书名称标准化后前缀匹配 |
| `person:match` | 人员表 | 证书/岗位/专业分字段匹配 |
| `project:match` | 业绩表 | 金额/类型/时间窗 |
| `warehouse:match` | 仓库表 | 库存数量语义 |
| `brand:match` | 品牌授权表 | 授权有效期校验 |

- 返回语义：`tier`（NONE/PARTIAL/FULL）+ `ratio`（0~1）
- tier 规则：0 命中=NONE；存在标记项/降级匹配/比例<100%=PARTIAL；100%=FULL

## 5. 阶段化打分（US3/US4）

| 阶段 | 触发 | 输入 | 输出 |
|------|------|------|------|
| 阶段1 预计得分 | 解析完成后自动 | 知识库匹配结果 | 客观项按 ratio 四舍五入取整；主观项强制 null；得分开区间 (0, weight) 钳位 |
| 阶段2 实际打分 | 手动（前置：投标文件已上传+解析完成） | 投标文件内容 | LLM 对标输出得分/依据/引用/建议；超区间得分置空；主观项仅保留建议 |

- 满足状态：客观项满分=OK / 零分=DANGER / 部分得分或过期=PENDING；主观项恒 PENDING
- 守卫：`ScoreAssessmentGuard`（域纯函数）统一钳位与置空
- **空值语义（spec 044 / PRD 1.3）**：客观项无预判得分（类别未识别 / 单项匹配失败）时 `est_score` 必须保留 `null` + `PENDING`，禁止兜底转 `0`（否则会被误渲染为红色 0 分）
  - 前端 `scoreParseTask.js normalizeScoreItem`：空值原样透传 null；`est_basis` 缺失时兜底"待人工确认预计得分"
  - UI：待确认（灰字 + 蓝点）≠ DANGER（红色 0 分），两者语义必须区分

## 6. 触发控制与超时（US5）

- `ScoreParseTimeoutScanJob`：定时扫描 PROCESSING 超 30min 任务 → 标记 FAILED（超时）；单任务失败不中断整批
- `ScoreParseTaskRecoveryRunner`：服务启动时恢复卡死任务（标记 FAILED + 保留上次成功结果）
- 任务互斥：同 project 已有 PENDING/PROCESSING 时 trigger 返回既有任务（幂等）

## 7. 关键工程约束（踩坑点）

- **FP-Java 分层**：`domain/` 纯函数（MergePolicy/ClassificationPolicy/WeightSumCheck/ItemCountCheck/SummaryAggregator/AssessmentGuard）可单测不依赖框架；`application/` 只编排
- **权限守卫链**：Controller → AppService 入口 `assertCurrentUserCanAccessProject`；异步管线内服务（EstimatedScore/ScoreItemPersistence/StateService/ProgressService）无 SecurityContext，靠 baseline 声明（见 `project-access-guard-baseline.txt`）
- **@Async 自代理**：`@Lazy @Autowired self` 解决自调用失效（同 spec 031 范式）
- **行预算 300**：ScoreParseAppService 拆出 ScoreItemPersistenceService；持久化细节测试同步迁移，勿在编排类里堆持久化断言
- **FR-021 覆盖语义**：重解析必须先删 `score_result`（by 旧 item IDs）再删 `score_item`，顺序不可反
- **UI 对齐（spec 044 / PRD 6.4-6.5）**：
  - 详情弹窗整体高度 ≤70vh（`el-dialog` 为 append-to-body 挂载，scoped 样式不可达，须用非 scoped 块 + `.el-dialog__body { overflow-y:auto }`）
  - 待确认状态 = 灰字 + 蓝点前缀（非 DANGER 红色）
  - 表格用 `table-layout: fixed` 时，窄列（编号 48px）放 nowrap/不可断内容会溢出叠压相邻列；tfoot 多格统计（nowrap 文案塞进 80px 满足状态列）同理全叠。修法：编号列加宽 + `word-break: break-all`，tfoot 改 `colspan=8` 单条 flex（`flex-wrap: wrap`）横向排布（2026-08-17 项目 225 UI 事故）
- **投标文件走 OBS 对象存储（2026-08-17）**：投标文件编制阶段通过 `useObsProjectDocumentUpload` 走华为云 OBS 直传（`obs-direct:xxx`），不设 50MB 业务上传限制。阶段 2 实际打分服务 `ScoreBidDocumentLookup` 透明兼容 `ObsShareUrlSigner` / `ProjectDocumentFileStorage` / `TenderDocumentStorage` 提取文本。
- **prompt 模板是 Formatter 格式串**：`ScoreParsePrompts` 的 text block 配 `.formatted()`，模板内字面 `%` 必须写 `%%`（如示例文案 `占30%%`），否则 `UnknownFormatConversionException` 且与输入无关 100% 必炸（2026-08-17 项目 225 线上事故，详见 lessons-learned §117；回归测试 `ScoreParsePromptsTest`）
- **toCandidate 对 null section 必须降级**：召回一/三以 `null` section 调用 `toCandidate`，裸访问 `section.sectionTitle()` 即 NPE 全任务终止（2026-08-17 项目 226 事故，详见 §118；回归测试 `OpenAiScoreAnalyzerTest`）；新召回路或转换方法允许"无此数据"语义时必须显式处理 null 分支
- **扫描件输入的失败文案**：sidecar 提取文本 <10 字符（`low_text_density` 警告）时四路召回必然空手，最终报"未识别到评分标准章节"——文案有误导性，真实原因是文件无文本层（扫描件/图片型 docx）；排查时先看 `markdownLength` 而不是怀疑解析逻辑

## 8. 验证基线

- 单测：解析编排/持久化覆盖/匹配五类/预计得分/阶段2打分/超时扫描/启动恢复 全绿（~143 用例含 biddraftagent 回归）
- 架构：ArchitectureTest + FPJavaArchitectureTest + ProjectAccessGuardCoverageTest 通过
