---
title: 术语表
space: engineering
category: reference
tags: [术语, 投标, 招标, MRO, 词汇]
sources:
  - docs/specs/业务流程图.md
  - docs/research/COMMERCIAL_SCOPE.md
  - README.md
backlinks:
  - _index
  - data-model
created: 2026-04-15
updated: 2026-06-21
health_checked: 2026-06-21
---
# 术语表

本术语表汇集了西域数智化投标管理平台涉及的业务术语、技术术语和系统概念，按拼音排序，供项目团队统一认知参考。

| 术语 | 英文 | 释义 |
|------|------|------|
| BAR（投标资产台账） | Bid Asset Registry | 平台扩展模块，对投标相关站点、UK/CA 证书、SOP 等资产进行集中登记、借还管理和生命周期跟踪 |
| CRM | Customer Relationship Management | 客户关系管理系统，平台通过接口同步客户信息和商机数据，支撑项目立项时的客户数据预填 |
| 废标 | Bid Disqualification | 因标书不合规（缺少必要文件、格式错误、资质过期等）导致投标被判无效的结果，平台通过 AI 合规检查降低废标风险 |
| Flyway | Flyway | 数据库版本迁移工具，用于管理 MySQL 8.0 的结构变更脚本，确保数据库 schema 的版本一致性 |
| 合规雷达 | Compliance Radar | 平台 AI 能力之一，对标书进行强制条款检查、格式检查和签章资质检查，以雷达图方式呈现合规覆盖度 |
| 集采 | Centralized Procurement | 集中采购，指由采购方统一组织的大规模批量采购活动，常见于央企和大型集团 |
| 竞争情报 | Competition Intelligence | 平台对竞争对手的分析能力，包括竞对参与项目、报价策略、SKU、折扣率、账期等信息的记录和分析 |
| 讲标 | Bid Presentation | 投标人在开标或评标环节向评标委员会进行方案演示和答辩的过程 |
| JWT | JSON Web Token | 一种基于 JSON 的开放标准令牌格式，平台后端使用 JWT 实现用户认证和会话管理 |
| 开标 | Bid Opening | 招标方在规定时间和地点公开拆封投标文件、宣布投标人名称和报价的程序 |
| 客户商机中心 | Customer Opportunity Center | 平台模块，以客户维度聚合标讯和商机信息，帮助销售人员识别和跟进高价值客户机会；是否进入本期验收以 SOW、蓝图确认件和变更单为准 |
| 立项 | Project Initiation | 投标项目的正式创建过程，包括填写项目基本信息、同步 CRM 客户数据、分解任务、提交审批 |
| MRO | Maintenance, Repair & Operations | 非生产性物料的维护、维修和运营用品，西域集团的核心业务领域，涵盖工业品供应链服务 |
| OA | Office Automation | 办公自动化系统。**OA 流程对接已取消**（2026-05-28 确认），不再纳入本系统范围。 |
| 评标 | Bid Evaluation | 评标委员会按照招标文件规定的标准和方法对投标文件进行审查、评价和比较的过程 |
| 评分覆盖 | Score Coverage Analysis | 平台 AI 能力之一，分析标书内容对招标文件评分标准的覆盖程度，确保各评分项均有对应响应 |
| POC | Proof of Concept | 概念验证，指通过构建原型系统验证技术方案和业务流程可行性的阶段，本项目当前已完成 POC 并进入正式实施 |
| ROI | Return on Investment | 投资回报率，数据分析模块中用于衡量投标投入与中标产出比的关键指标 |
| SIT | System Integration Testing | 系统集成测试，在 UAT 之前由乙方团队执行的内部测试，重点验证主业务链路、权限、数据一致性和接口联动 |
| 四阶段看板 | Four-Phase Kanban | 平台任务管理的核心交互模式，将任务按"待办 - 进行中 - 待审核 - 已完成"四个阶段组织和流转 |
| 标讯 | Tender Notice / Bid Information | 招标公告或商机信息的统称，平台通过外部获取和第三方服务将标讯入库，并进行智能分发和跟进管理 |
| 投标 | Bidding / Tendering | 投标人按照招标文件要求编制投标文件并提交的行为，平台覆盖从标讯获取到结果闭环的全流程 |
| UAT | User Acceptance Testing | 用户验收测试，由甲方关键用户基于真实业务场景执行的最终验收测试，是上线前的关键门禁 |
| 用印 | Seal Application | 在投标文件上加盖企业公章的申请与审批流程，属于投标提交阶段的关键控制点 |
| 招标 | Tendering / Invitation for Bids | 招标方发布采购需求、邀请合格供应商参与竞争的活动，是投标业务的起点 |
| 智能装配 | Intelligent Assembly | 平台 AI 能力之一，根据招标文件要求和项目特征，从模板库中自动匹配和组装标书章节内容 |
| 中标 | Bid Winning | 投标人的投标文件经评审后被确定为中标候选人或中标人的结果，平台通过结果登记完成投标闭环 |
| 资质 | Qualification / Credential | 企业参与投标所需的各类证照和资质文件，如营业执照、ISO 认证、行业资质等，平台提供到期预警和借阅管理 |

> 本术语表随项目演进持续更新。如需补充术语，请在对应的源文档中标注后同步更新本表。
