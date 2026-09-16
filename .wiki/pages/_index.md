---
title: 西域投标管理平台知识库
space: engineering
category: guide
tags: [首页, 导航, wiki]
created: 2026-09-05
updated: 2026-09-16
health_checked: 2026-09-16
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
- [[ai-case-recommend-data-source-fix]] — AI 智能案例推荐数据源修复（score_item 新契约切换 + casework 裸响应陷阱）
- [[ai-provider-configuration]] — AI Provider 配置与陷阱指南
- [[ai-slop-cleanup-and-composable-inlining]] — AI Slop 代码清理与单组件专属小型 Composable 内联收敛
- [[analytics-dept-virtual-root-unwrap]] — 数据分析部门筛选虚拟根组织架构解包与业务根锚定机制
- [[api-openapi]] — OpenAPI/Swagger 接口规范
- [[architecture]] — 架构合成
- [[architecture/effective-role-resolution]] — 有效角色解析（Effective Role Resolution）规范
- [[audit-whitelist-pitfalls]] — 审计白名单陷阱——@Auditable action 命名必须对齐 AuditActionPolicy
- [[backend-test-baseline-green]] — 后端测试基线修复与 mock 用户种子机制
- [[bid-quality-check-pagination-fix]] — 标书质量核查分页丢失修复（\f 契约断裂 + 虚拟分页兜底 + 证据定位治理）
- [[bid-quality-check-realization]] — 标书质量核查实质化架构（spec 049）
- [[bid-quality-multifile-check]] — AI 标书质量核查多册合并（价格/商务/技术分册 + Loader 边界合并 + Sidecar 空册陷阱）
- [[business-process]] — 业务流程
- [[case-library-prd-refine]] — 案例库按 PRD 精简重构（筛选区三字段 / 排序固定 / 详情返回招标主体）
- [[knowledge-case-summary-source-title]] — 案例库摘要可读性 + 标书原文来源标题定位（WF 0915-01）
- [[case-precipitation-streaming]] — 案例沉淀超大标书完整链路（spec 049：流式 + 摘录化 + 三个验收坑）
- [[case-source-actions-and-import-pitfalls]] — 案例详情"查看源项目 / 标书原文"操作契约与导入数据陷阱
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
- [[large-file-streaming-and-memory-discipline]] — 超大标书流式解析与前端原生下载治理（2GB+ 内存防爆与 SAX 容灾）
- [[lessons-learned]] — 工程经验总结
- [[lessons-learned/case-precipitation-oversize-oom]] — AI 生成案例超大标书 OOM 导致入库 0 条 — 整文件读堆 + 二次分配与"假成功"上报
- [[lessons-learned/CO-361-five-rounds-no-fix]] — CO-361 五次修复不彻底的教训 — 反复追症状不追根因的代价
- [[modules]] — 模块目录
- [[multi-agent-defense-playbook]] — 多 Agent 并行开发防御工程化手册
- [[notification-system-pitfalls]] — 通知系统陷阱集
- [[operations/logging-bug-investigation-guide]] — 日志系统查 Bug 手册
- [[opportunity-prediction-reminder]] — 商机预测提醒（OSS 实时收件人、一次性历史补发、站内/企微送达分层验收）
- [[oss-organization-sync-playbook]] — OSS 组织架构同步实战手册
- [[overview]] — 项目综述
- [[production-deployment-lessons]] — 生产环境首次部署实战教训
- [[project-search-owner-unit]] — 项目搜索按招标主体（ownerUnit 数据链路与关键词过滤契约）
- [[requirements]] — 需求追溯
- [[result-registration-immutability]] — 项目结果登记不可变链路与二次确认
- [[roles-and-permissions]] — 角色与权限
- [[root-cause-analysis-ijssgg]] — 立项招标文件上传 Bug 根因分析 (IJSSGG)
- [[score-item-select-short-label]] — 评分项下拉短标签（ScoreItemSelect：短标签契约 / filterable 原文搜索陷阱 / 测试 stub 反模式）
- [[score-parse-optimization]] — 评分解析优化记录
- [[score-parse-service]] — AI 评分标准解析后端服务
- [[spec059-score-parse-evidence-pipeline]] — spec 059 评分解析证据化 Phase 0-4 落地结论 + 合入前评审与去噪重构
- [[spring-pitfalls]] — Spring Boot 陷阱集
- [[team-and-timeline]] — 团队与排期
- [[tender-create-upload-triage]] — 标讯人工录入"未上传"假象排障与 UX 缺陷修复
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
