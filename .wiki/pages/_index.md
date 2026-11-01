---
title: 西域投标管理平台知识库
space: engineering
category: guide
tags: [首页, 导航, wiki]
created: 2026-09-05
updated: 2026-10-28
health_checked: 2026-10-28
sources:
  - docs/specs/WIKI.md
  - .wiki/INDEX.md
  - .wiki/PAGE_INDEX.md
backlinks:
---
# 西域投标管理平台知识库

> 本知识库按研发与实施双空间组织，支持原始 Office 文件混合摄入和增量编译。

## 快速入口

- 规则协议：`WIKI.md`
- 源文档编目：`.wiki/INDEX.md`
- 页面索引：`.wiki/PAGE_INDEX.md`
- 抽取中间层：`.wiki/extracts/`
- 产物回流层：`.wiki/outputs/`

## Engineering Space

- [[_index]] — 西域投标管理平台知识库
- [[agent-sop-quickref]] — Agent 开发 SOP 快速参考
- [[ai-capabilities]] — AI 能力
- [[baidu-table-ocr-pipeline]] — 百度表格文字识别 (Table OCR) 旁路对照管线
- [[pr2948-scoreparse-scope-and-fallback-risk]] — PR2948评分前序漏扫修复范围与fallback证据退化复审记录
- [[ai-case-recommend-data-source-fix]] — AI 智能案例推荐契约与陷阱（数据源、裸响应、前端分数门禁、资质提示边界）
- [[ai-case-precipitation-concurrency]] — AI 案例沉淀并发任务与大文件隔离契约（projectId 门禁 + UUID 临时文件 + 前端状态）
- [[ai-provider-configuration]] — AI Provider 配置与陷阱指南
- [[db-schema-dictionary-comment-backfill]] — 数据字典补齐与生成器六类解析盲区（V1218 注释回填）
- [[ai-slop-cleanup-and-composable-inlining]] — AI Slop 代码清理与单组件专属小型 Composable 内联收敛
- [[acceptance-export-feature-removal]] — 验收导出——未开放功能代码级剔除方法论（隐藏菜单识别 + 共享符号白名单 + 删除型契约验证）
- [[analytics-dept-virtual-root-unwrap]] — 数据分析部门筛选虚拟根组织架构解包与业务根锚定机制
- [[api-openapi]] — OpenAPI/Swagger 接口规范
- [[architecture]] — 架构合成
- [[architecture/effective-role-resolution]] — 有效角色解析（Effective Role Resolution）规范
- [[audit-whitelist-pitfalls]] — 审计白名单陷阱——@Auditable action 命名必须对齐 AuditActionPolicy
- [[backend-test-baseline-green]] — 后端测试基线修复与 mock 用户种子机制
- [[bid-quality-check-pagination-fix]] — 标书质量核查分页丢失修复（\f 契约断裂 + 虚拟分页兜底 + 证据定位治理）
- [[bid-quality-check-realization]] — 标书质量核查实质化架构（spec 049）
- [[bid-quality-check-evidence-conflicts]] — 标书质量核查证据冲突、报告147招标原文引用排除、CA平台全称/别名与原文页码、组合 ISO 与激活版本边界
- [[bid-quality-multifile-check]] — AI 标书质量核查多册合并（价格/商务/技术分册 + Loader 边界合并 + Sidecar 空册陷阱）
- [[seal-vision-compliance]] — 标书签章完整性视觉核验（Sidecar 页面图像 + 多模态印章识别 + 页级证据）
- [[bid-quality-check-parse-timeout-fix]] — AI 标书质量核查解析失败误报修复（超时预算 × 文档规模 + 五分类失败语义 + Sidecar 分块 100）
- [[ai-document-scan-error-guidance]] — AI 文档扫描件不可识别提示契约（错误透传 + 前端显式告警）
- [[business-process]] — 业务流程
- [[case-library-prd-refine]] — 案例库按 PRD 精简重构（筛选区三字段 / 排序固定 / 详情返回招标主体）
- [[case-precipitation-streaming]] — 案例沉淀超大标书完整链路（spec 049：流式 + 摘录化 + 三个验收坑）
- [[case-source-actions-and-import-pitfalls]] — 案例详情源项目/标书原文操作、导入陷阱与只补不清安全回填契约
- [[competitor-library-governance]] — 竞品公司自定义输入与竞争对手库治理
- [[configuration-binding-silent-failure]] — 配置绑定静默失配 - isBound 探针与被动识别回归测试
- [[crm-integration-lessons]] — CRM 集成踩坑集
- [[dashboard-gap-analysis]] — 工作台卡片 vs 标书要求对照
- [[data-analysis-revamp]] — 数据分析页面重构 — 架构与数据口径
- [[data-model]] — 数据模型
- [[data-permission-hardening]] — 项目数据权限修复收口
- [[deployment]] — 部署与上线
- [[design-system]] — 设计系统基线
- [[docinsight-engine]] — DocInsight 文档智能引擎
- [[duplicate-implementation-gate]] — 重复实现门禁（防造轮子）
- [[dynamic-form-engine]] — 动态表单自定义引擎
- [[engineering-discipline]] — 工程纪律手册 — 反复修复的根因、根治与预防
- [[evaluation-form-field-adjustments]] — 评估表字段文案调整与 GAP 文本框下线
- [[event-library-integration-rca]] — 事件库两次集成反复失败根因复盘
- [[flyway-migration-pitfalls]] — Flyway 迁移陷阱集
- [[frontend-pitfalls]] — 前端 Vue3 / Element Plus 陷阱集
- [[gate-command-scope-pitfalls]] — 门禁命令的作用域陷阱（doc-governance 默认不扫 backend / line-budget 裸调用空转 / diff_hash 两种口径与调用位置 / wiki 回填也会改 hash）
- [[gitee-token-resolution]] — Gitee token 多源可见性与统一解析链（Agent 提 PR 找不到 token 的根因与唯一真相源规范）
- [[glossary]] — 术语表
- [[hibernate-string-enum-varchar-validate]] — Hibernate STRING 枚举 vs varchar 列校验陷阱
- [[historical-case-obs-download]] — 历史案例原文 OBS 直链下载（spec 058：三分流 + 预签名签发 + 中文键编码分工）
- [[initiation-form-fake-zero-display]] — 立项表单假0显示伪装成列表/详情数据不一致（PR !2436）
- [[integration-boran-permission-api]] — 西域给泊冉权限接口
- [[integration-oa-crm]] — CRM 对接规范
- [[integration-organization-event-sdk]] — 组织架构对接 - 客户事件库 SDK 方案
- [[integration-tender-api]] — 标讯集成接口（外部系统对接）
- [[integration-tender-event-push]] — 标讯创建事件推送 - 西域 CRM 事件总线
- [[integration-wecom]] — 系统集成中心 - 企业微信
- [[knowledge-base]] — 4.4 知识库 PRD (产品需求文档)
- [[knowledge-case-summary-source-title]] — 案例库应答片段摘要可读性 + 标书原文来源标题定位（WF 0915-01）
- [[large-file-streaming-and-memory-discipline]] — 超大标书流式解析与前端原生下载治理（2GB+ 内存防爆与 SAX 容灾）
- [[streaming-download-empty-body-async-401]] — 合订本流式下载空正文与异步 401（响应缓存、二次鉴权、中文文件名）
- [[lessons-learned]] — 工程经验总结
- [[lessons-learned/case-precipitation-oversize-oom]] — AI 生成案例超大标书 OOM 导致入库 0 条 — 整文件读堆 + 二次分配与"假成功"上报
- [[lessons-learned/CO-361-five-rounds-no-fix]] — CO-361 五次修复不彻底的教训 — 反复追症状不追根因的代价
- [[modules]] — 模块目录
- [[multi-agent-defense-playbook]] — 多 Agent 并行开发防御工程化手册
- [[notification-system-pitfalls]] — 通知系统陷阱集（含自动提醒失败诊断和消息中心邮箱透传）
- [[operations/logging-bug-investigation-guide]] — 日志系统查 Bug 手册
- [[opportunity-prediction-reminder]] — 商机预测提醒
- [[oss-organization-sync-playbook]] — OSS 组织架构同步实战手册
- [[overview]] — 项目综述
- [[production-deployment-lessons]] — 生产环境首次部署实战教训
- [[sidecar-dockerfile-dual-sync]] — Sidecar Dockerfile 双文件同步陷阱（Dockerfile.cn 为服务器实际构建源）
- [[closure-deposit-amount-baseline]] — 结项保证金金额口径（立项优先，转服务费必须等值）
- [[task-board-deposit-assignee-delegation]] — 全局任务看板保证金任务放权与响应式更新（项目负责人放权、上下文按需拉取、局部即时更新）
- [[project-detail-toolbar-back-fix]] — 项目详情页横条返回按钮无响应修复（el-page-header back 事件未接）
- [[project-list-server-side-paging]] — 项目列表服务端分页（快慢两路径路由、"一个筛选都不下推"的 collation 准入判据、与导出/工作台/CRM 共享的全序）
- [[project-search-owner-unit]] — 项目搜索按招标主体（ownerUnit 数据链路与关键词过滤契约）
- [[requirements]] — 需求追溯
- [[result-registration-immutability]] — 项目结果登记不可变链路与二次确认
- [[roles-and-permissions]] — 角色与权限
- [[root-cause-analysis-ijssgg]] — 立项招标文件上传 Bug 根因分析 (IJSSGG)
- [[score-item-select-short-label]] — 评分项下拉短标签（ScoreItemSelect：短标签契约 / filterable 原文搜索陷阱 / 测试 stub 反模式）
- [[score-parse-optimization]] — 评分解析优化记录
- [[scoreparse-1387-chapter-list-scope-rca]] — 项目1387章名列举、分组总分、无序号规则、候选复核、价格原文绑定、空规则采纳与task315行引文边界根因（隔离原文验证）
- [[scoreparse-1391-appendix-scope-weight-closure-rca]] — 项目1391前附表附件承载评分表未入作用域、权重制总分缺失与权重闭合口径
- [[scoreparse-1359-stale-200-total-rca]] — 项目1359旧200分、最低/最高小计冲突与最新单条总括事实
- [[scoreparse-48-chapter-heading-spacing-rca]] — 项目48标书章节标题字间空格与同级截断根因分析与作用域自然恢复
- [[score-subtotal-peritem-semantics-provenance-snapshot]] — 评分单项满分及 PDF 跨行范围判断、案例来源快照守卫
- [[score-parse-adoption-consistency]] — 评分候选采纳与最新解析任务一致性（长说明、锁、过期页面）
- [[score-parse-service]] — AI 评分标准解析后端服务
- [[scoreparse-certificate-estimate-staleness-rca]] — 证书评分历史漏认、预判残留与组合证书任一命中假满分根因（只读诊断，未修复）
- [[scoreparse-cross-chapter-qualification-performance]] — 评分项跨章节穿透资格项业绩检索与基础分递增计分规范
- [[scoreparse-performance-evidence-clarity]] — 业绩预计得分证据可信度、保守计分与中文解释契约
- [[scoreparse-1371-docx-merged-closure-rca]] — 项目 1371 DOCX 嵌套纵向合并打分表行闭合与标书原文回填根因
- [[scoreparse-1384-docx-scheme-contract-rca]] — 项目 1384 DOCX 评分方案资格失效根因（PR #2757 真实输入契约）
- [[scoreparse-1382-live-publish-gate]] — 项目1382线上100分被0分指引和空模型总分否决
- [[scoreparse-1382-pdf-scheme-gate-rca]] — 项目 1382 PDF 评分方案资格门失效根因（PDF 章节 / DOCX 真实跨层 / 全候选快照 / 生效版本与评分指纹 / 发布开关）
- [[scoreparse-multimodal-primary]] — 评分方案资格、原文总分、跨表父子、OCR 引文定位与文本分片审核契约；2026-10-30 补充项目1136空指针和价格排序边界。
- [[scoreparse-cross-format-strategy]] — 跨格式识别日志证据、图文结构联合方案与陌生模板验收边界
- [[spec059-score-parse-evidence-pipeline]] — spec 059 评分解析证据化 Phase 0-4 落地结论 + 合入前评审与去噪重构
- [[scoreparse-pdf-page-border-pseudo-table]] — PDF 全页装饰边框伪表格过滤与跨页续表合并契约
- [[scoreparse-text-paragraph-and-continuation-fix]] — 纯文本段落评分事实穿透还原与表格跨行续行识别契约
- [[score-parse-split-completeness-and-partial-overwrite-fix]] — 评分解析分表完整性范围误判 + 部分通过覆盖正式结果双修复
- [[spring-pitfalls]] — Spring Boot 陷阱集
- [[team-and-timeline]] — 团队与排期
- [[tender-create-upload-triage]] — 标讯人工录入"未上传"假象排障与 UX 缺陷修复
- [[tender-bulk-import-failure-metrics-and-error-table]] — 标讯批量导入校验失败计数回写与错误表格防御
- [[tender-deadline-auto-reminder]] — 标讯截止节点自动提醒（无配置也按 72 小时窗口每天发）
- [[tender-deadline-zero-hour-formatting]] — 标讯人工创建截止时间零点格式化规则与排错
- [[tender-event-pr2598-contract-review]] — PR2598 标讯事件推送契约与验证审查
- [[tenderevent-sdk-bean-conflict]] — TenderEvent SDK Bean 冲突陷阱
- [[testing/_index]] — 功能实现对照 — 测试说明文档索引
- [[testing/container-test-env-compat]] — 容器测试环境兼容（Testcontainers / Docker Desktop / MySQL sql_mode）
- [[testing/module-01-workbench]] — 工作台 — 蓝图功能实现对照
- [[testing/module-02-bidding]] — 标讯中心 — 蓝图功能实现对照
- [[testing/module-03-project]] — 投标项目 — 蓝图功能实现对照
- [[testing/module-04-knowledge]] — 知识库 — 蓝图功能实现对照
- [[testing/module-05-resource]] — 资源管理 — 蓝图功能实现对照
- [[testing/module-06-analytics]] — 数据分析 — 蓝图功能实现对照
- [[testing/module-07-settings]] — 系统设置 — 蓝图功能实现对照
- [[testing/module-08-ai]] — Module 8 AI 能力体系 — 蓝图功能实现对照
- [[testing/module-09-integration]] — Module 9 系统集成 — 蓝图功能实现对照
- [[workflow-form-center]] — 流程表单中心（OA 对接已取消）

## Implementation Space

- [[contract-constraints]] — 合同约束
- [[implementation/acceptance-and-closure]] — 实施验收与问题闭环
- [[implementation/attachment4-gap-matrix]] — 附件4客户要求差距矩阵
- [[implementation/attachment4-requirement-task-book]] — 附件4需求任务书交付基线
- [[implementation/attachment6-function-list-trace]] — 附件6需求功能清单追溯
- [[implementation/delivery-playbook]] — 实施交付作战包总览
- [[implementation/development-sprint-2026-05-23]] — 系统开发冲刺计划 2026-05-23 ~ 06-17
- [[implementation/document-delivery-ledger]] — 文档交付台账
- [[implementation/milestones]] — 实施里程碑与依赖
- [[implementation/org-sdk-deployment-handoff]] — 组织架构 SDK 集成 — 部署 Handoff
- [[implementation/risk-register]] — 实施风险台账
- [[implementation/sow-2026-v1-4]] — SOW 2026 V1.4 执行基准
- [[implementation/weekly-status]] — 实施周报与例会纪要模板
- [[implementation/xiyu-pending-confirmations]] — 西域待确认项清单

## 操作命令

1. `npm run wiki:ingest`
2. `npm run wiki:build`
3. `npm run wiki:check`


## 评分项证据实验

- [[scoreparse-callsite-scope-experiment-20261003]] — 三个反例通过，新增退化与陌生文件来源/召回阻断保留，整体未通过
- [[scoreparse-source-constraints-experiment-20261003]] — 三项统一约束复核；新文件入口14处找全，Word控制符丢失、全文超限与缺附件仍阻断泛化

- [[scoreparse-structured-retrieval-experiment-20261003]] — 统一来源检索改善容量，七个入口语义失败仍阻止泛化验收。

- [[scoreparse-entry-constraints-and-source-generalization-20261003]] — 已知入口门禁通过，新Word嵌套段落与新PDF漏检/续表反例阻止泛化。

- [[scoreparse-canonical-source-and-logical-tables-20261003]] — 全量117单元审计仍漏1引用，Word四个旧来源映射未闭合。

- [[scoreparse-comparison-reference-and-word-bridge-20261004]] — 比较基准引用两轮通过，Word11段规则兼容性仍阻塞。

- [[scoreparse-frozen-field-native-evidence-contract-20261004]] — 11项完整原文独立绑定通过，56错误注入拒绝，字段差异保留。

- [[scoreparse-native-reference-scope-regression-20261004]] — 14映射正确，资格前提漏入口，旧严格关联审计32/34。

- [[scoreparse-dependency-splitting-and-proof-record-experiment-20261004]] — 多依赖入口找出，逐项证据完整但适用语义与旧回归未通过。

- [[scoreparse-exclusion-and-ambiguity-gates-20261004]] — 模糊目标两轮未确定适用，非适用报价分支仍未排除，完整续文出现退步。

- [[scoreparse-extraction-stability-code-review-20261004]] — 主链已有原文校验，但逻辑单元、覆盖、来源合并尚不足以证明跨文件稳定提取。

- [[scoreparse-langextract-ab-pilot-20261004]] — 两文件两轮真实提取AB，LangExtract名称更直接但粒度与完整原文定位未通过。

- [标书质量核查证据边界](quality-evidence-boundary.md)：规则引用、抽样页码、证书身份、占位适用性、材料事实及编号边界（spec079、quality1030）。
- [[scoreparse-single-digit-code-and-manual-adopt-rca]] — 单数字评分项识别缺陷与人工复核采纳死锁根因分析。

- [[scoreparse-multimodal-primary]] — 1135 跨页评分表、OCR合行与来源校验回放边界。
