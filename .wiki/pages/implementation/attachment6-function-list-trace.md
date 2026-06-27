---
title: 附件6需求功能清单追溯
space: implementation
category: reference
tags: [附件6, 需求功能清单, 功能追溯, 验收颗粒度]
sources:
  - .wiki/sources/bidding/附件6：需求功能清单.md
  - .wiki/sources/bidding/附件6：需求功能清单.xlsx
  - .wiki/pages/requirements.md
  - .wiki/pages/modules.md
  - .wiki/pages/implementation/attachment4-gap-matrix.md
backlinks:
  - _index
  - implementation/acceptance-and-closure
  - implementation/attachment4-gap-matrix
  - implementation/delivery-playbook
  - implementation/document-delivery-ledger
created: 2026-04-26
updated: 2026-06-27
health_checked: 2026-06-27
---
# 附件6需求功能清单追溯

本页把《附件6：需求功能清单》作为功能验收颗粒度来源接入 wiki。当前以人工结构化 Markdown 作为优先阅读基线，Excel 原件继续保留为原始来源。附件4定义项目建设目标、实施服务、验收和非功能要求；附件6补齐具体功能点，后续需求裁剪、UAT 用例和交付证明应同时核对 [[implementation/attachment4-gap-matrix]] 与本页。

## 文件定位

| 项 | 口径 |
|---|---|
| 文件性质 | 招标/需求附件中的功能清单，是功能点拆解和 UAT 场景设计的直接输入 |
| 当前 wiki 状态 | Markdown 与 Excel 原件均已进入 `.wiki/sources/bidding/`，并完成抽取 |
| 优先阅读基线 | `.wiki/sources/bidding/附件6：需求功能清单.md` |
| 原始 Excel 来源 | `.wiki/sources/bidding/附件6：需求功能清单.xlsx` |
| 抽取结果 | `.wiki/extracts/bidding__附件6：需求功能清单.md.md`、`.wiki/extracts/bidding__附件6：需求功能清单.xlsx.md` |
| 追溯入口 | [[requirements]] 的“功能需求清单（需求→实现追溯矩阵）”已合成该附件 |
| 执行关系 | 与附件4、主合同、SOW V1.4、蓝图确认件共同构成功能范围和验收基线 |

## 功能清单追溯矩阵

| 一级域 | 二级模块/功能点 | 附件6核心要求 | Wiki 对应 | 当前口径 |
|---|---|---|---|---|
| 项目全流程管理 | 统一项目工作台 | 角色化视图，集中展示个人待办、负责项目、全局投标日历 | [[requirements]]、[[modules]]、[[business-process]] | 已纳入主链路 UAT |
| 项目全流程管理 | 一站式流程发起 | 销售快速发起标书支持、资质/合同借阅、投标费用申请 | [[requirements]]、[[modules]] | 合同借阅、费用、资质需分别取证 |
| 项目全流程管理 | 智能日程与预警 | 截标、开标、合同续标等关键节点多级预警，支持客户/项目维度提醒配置 | [[requirements]]、[[implementation/attachment4-gap-matrix]] | 部分覆盖，合同续标和消息触达需补证据 |
| 标讯信息管理 | 外部标讯获取与入库 | 集成外部招标平台或第三方商机服务，支持关键字配置和人工录入 | [[requirements]]、[[modules]] | 第三方联调待客户资源 |
| 标讯信息管理 | 标讯分发与指派跟进 | 按预设规则自动指派给销售，也支持管理员手动分发 | [[requirements]]、[[business-process]] | 需蓝图确认分发规则 |
| 标讯信息管理 | 超前预测与市场洞察 | 建立企业标讯库，支持预测和洞察 | [[requirements]]、[[modules]] | 需历史数据样本验证 |
| 投标项目过程管理 | 项目立项与信息登记 | 从 CRM 获取客户信息，创建投标项目并记录客户、平台、时间、标讯、竞对等核心信息 | [[requirements]]、[[business-process]] | CRM 真实对接待实施 |
| 投标项目过程管理 | 流程驱动与任务协同 | 可配置审批流程和字段要求，在线分解、分配标书编制任务 | [[business-process]]、[[roles-and-permissions]] | 部分覆盖，低代码流程边界待确认 |
| 投标项目过程管理 | 执行跟踪与交付物关联 | 跟踪任务进度和交付物，提交至标书编写流程 | [[business-process]]、[[modules]] | 已纳入主链路 UAT |
| 投标项目过程管理 | 标书编制发起与核查 | 管理初稿、内部评审、用印、封装提交状态，关联最终标书文件 | [[business-process]]、[[ai-capabilities]] | 需客户样例标书验证 |
| 投标项目过程管理 | 投标结果闭环 | 人工登记中标/未中标、合同期限、中标 SKU 备注；上传中标通知书或分析报告；自动抓取公开结果并提醒确认 | [[requirements]]、[[business-process]] | 部分覆盖，公开结果自动抓取需补联调证据 |
| 企业知识资产中心 | 商务资质库 | 管理公司及子公司证照、产品资质、人员证书，支持有效期提醒和借阅 | [[requirements]]、[[modules]] | 已纳入知识库/资源 UAT |
| 企业知识资产中心 | 历史项目与案例库 | 结构化归档技术/商务标书，支持标签分类和全文检索 | [[requirements]]、[[modules]] | 需真实案例样本取证 |
| 企业知识资产中心 | 标准模板库 | 按产品类型、行业、文档类型分类管理标书模板 | [[requirements]]、[[modules]] | 需模板版本和使用记录取证 |
| 资源管理 | 费用申请与支付跟踪 | 标书购买费、保证金、差旅、制作费等归集到项目，线上申请审批与支付记录跟踪 | [[requirements]]、[[modules]] | 已纳入资源 UAT |
| 资源管理 | 保证金归还提醒 | 根据开标结果和退款时间自动触发保证金退还跟踪提醒 | [[requirements]]、[[implementation/attachment4-gap-matrix]] | 已有实现说明，需操作证据 |
| 资源管理 | 费用台账与统计 | 按项目、时间、部门等多维度查询统计投标费用 | [[requirements]]、[[modules]] | 已有实现说明，需报表截图 |
| 资源管理 | 平台账户集中管理 | 建立外部招标平台账户统一登记簿 | [[requirements]]、[[modules]] | 已纳入 BAR/资源验收 |
| 资源管理 | 账户借阅管理 | 在线申请借阅账户，记录用途并关联项目，形成可审计轨迹 | [[requirements]]、[[modules]] | 已纳入 BAR/资源验收 |
| 数据分析 | 管理层可视化仪表盘 | 展示年度/季度投标数量、中标率、中标金额趋势 | [[requirements]]、[[modules]] | 需 6 张报表口径确认 |
| 数据分析 | 多维深度分析报表 | 按区域、产品线、销售团队、客户类型、竞对等维度分析中标和投入产出 | [[requirements]]、[[modules]] | 需样表与下钻证据 |
| 数据分析 | 数据穿透下钻 | 从看板/报表点击查看项目详情、过程文件和团队信息 | [[requirements]]、[[modules]] | 需端到端截图 |
| 权限管理 | 权限配置与管理 | 按组织架构、项目组、用户等维度配置系统/数据权限 | [[roles-and-permissions]]、[[data-permission-hardening]] | 权限治理已覆盖，组织架构同步待联调 |
| 系统集成与扩展 | CRM 系统集成 | 获取客户信息和商机信息 | [[api-openapi]]、[[integration-wecom]]、[[implementation/attachment4-gap-matrix]] | 待客户接口规范和联调 |
| 系统集成与扩展 | 组织架构系统集成 | 获取西域内部组织架构信息 | [[integration-wecom]]、[[roles-and-permissions]] | 企业微信/组织架构真实同步待实施 |
| 系统集成与扩展 | OA/审批流集成 | ~~组织架构用户同步、单点登录，获取用印、合同评审、付款申请等 OA 审批流程~~ **已取消** | — | — |
| 系统集成与扩展 | 开放 API 接口 | 与财务系统、电子签章等集成预留能力 | [[api-openapi]]、[[deployment]] | OpenAPI 已落地，机器身份/Webhook 待补 |
| 智能辅助 | 标书合规性自动检查 | 基于规则库自动初检必备资质、禁止性内容并提示风险，模拟专家评审评分 | [[ai-capabilities]]、[[business-process]] | 已有能力说明，需客户样例验收 |
| 智能辅助 | 文本书写质量辅助 | 错别字、语法、格式规范智能检查和修改建议 | [[ai-capabilities]] | 需样例报告取证 |

## 与交付台账的关系

| 台账项 | 衔接方式 |
|---|---|
| [[implementation/document-delivery-ledger]] | 将附件6作为“功能范围确认、UAT 用例、验收测试报告”的输入来源 |
| DD-BP-02 需求适配分析报告 | 需引用附件6功能点，并标注本期纳入、待实施、待确认、非本期 |
| DD-BP-03 配套需求适配确认书 | 需把附件6裁剪结果作为双方签字范围 |
| DD-TEST-01 测试用例 | UAT/SIT 用例应覆盖附件6一级域和关键二级功能点 |
| DD-GO-01 项目验收测试报告 | 验收结论需回填附件6功能点覆盖状态和证据路径 |

## 使用规则

1. 任何功能范围判断，先同时核对附件4交付基线、附件6功能清单、SOW V1.4 和蓝图确认件。
2. 附件6中的功能点若被裁剪或延期，必须进入需求适配确认书或变更评估，不应只在口头会议中处理。
3. UAT 用例缺失附件6中的一级域时，应视为验收证据不足。
