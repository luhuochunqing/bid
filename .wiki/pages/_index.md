---
title: 西域投标管理平台知识库
space: engineering
category: guide
tags: [首页, 导航, wiki]
sources:
  - docs/specs/WIKI.md
  - .wiki/INDEX.md
  - .wiki/PAGE_INDEX.md
backlinks:
created: 2026-06-27
updated: 2026-06-27
health_checked: 2026-07-06
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
- [[ai-provider-configuration]] — AI Provider 配置与陷阱指南（activeProvider / Doubao / 降级策略 / Sidecar）
- [[engineering-discipline]] — 工程纪律手册（反复修复根因 / Bug修复SOP / 开发规范 / 经验积累机制）
- [[api-openapi]] — OpenAPI/Swagger 接口规范
- [[architecture]] — 架构合成
- [[architecture/effective-role-resolution]] — 有效角色解析规范（Effective Role Resolution）
- [[business-process]] — 业务流程
- [[crm-integration-lessons]] — CRM 集成踩坑集（商机状态 / webhook / OAuth / CallerContext）
- [[dashboard-gap-analysis]] — 工作台卡片 vs 标书要求对照
- [[data-model]] — 数据模型
- [[data-permission-hardening]] — 项目数据权限修复收口
- [[deployment]] — 部署与上线
- [[production-deployment-lessons]] — 生产环境首次部署实战教训（2026-07-09 首次上线）
- [[oss-organization-sync-playbook]] — OSS 组织架构同步实战手册（skipUnmappedUsers / LoginRoleWhitelist / Kafka）
- [[design-system]] — 设计系统基线
- [[docinsight-engine]] — DocInsight 文档智能引擎
- [[dynamic-form-engine]] — 动态表单自定义引擎
- [[flyway-migration-pitfalls]] — Flyway 迁移陷阱集（collation / 版本号冲突 / baseline-on-migrate / 回滚脚本）
- [[frontend-pitfalls]] — 前端 Vue3 / Element Plus 陷阱集（reactive / el-upload / el-form / 权限 / E2E）
- [[glossary]] — 术语表
- [[integration-boran-permission-api]] — 西域给泊冉权限接口
- [[integration-oa-crm]] — CRM 对接规范
- [[integration-organization-event-sdk]] — 组织架构对接 - 客户事件库 SDK 方案
- [[integration-tender-api]] — 标讯集成接口（外部系统对接）
- [[integration-wecom]] — 系统集成中心 - 企业微信
- [[knowledge-base]] — 4.4 知识库 PRD (产品需求文档)
- [[lessons-learned]] — 工程经验总结
- [[lessons-learned/CO-361-five-rounds-no-fix]] — CO-361 五次修复不彻底的教训（反复追症状不追根因的代价）
- [[modules]] — 模块目录
- [[multi-agent-defense-playbook]] — 多 Agent 并行开发防御工程化手册
- [[notification-system-pitfalls]] — 通知系统陷阱集（targetUrl / SYSTEM_USER_ID / @TransactionalEventListener / 静默吞异常）
- [[operations/logging-bug-investigation-guide]] — 日志系统查 Bug 手册
- [[overview]] — 项目综述
- [[requirements]] — 需求追溯
- [[roles-and-permissions]] — 角色与权限
- [[root-cause-analysis-ijssgg]] — 立项招标文件上传 Bug 根因分析 (IJSSGG)
- [[spring-pitfalls]] — Spring Boot 陷阱集（@Transactional / @Async / SPRING_CONFIG_IMPORT / 配置优先级）
- [[team-and-timeline]] — 团队与排期
- [[testing/_index]] — 功能实现对照 — 测试说明文档索引
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

