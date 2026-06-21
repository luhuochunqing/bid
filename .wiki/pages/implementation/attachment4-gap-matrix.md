---
title: 附件4客户要求差距矩阵
space: implementation
category: reference
tags: [附件4, 差距矩阵, 验收证据, 需求追溯, UAT]
sources:
  - .wiki/sources/bidding/附件6：需求功能清单.md
  - .wiki/sources/bidding/附件6：需求功能清单.xlsx
  - .wiki/pages/implementation/attachment4-requirement-task-book.md
  - .wiki/pages/implementation/attachment6-function-list-trace.md
  - .wiki/pages/requirements.md
  - .wiki/pages/contract-constraints.md
  - docs/specs/需求闭环完成说明-2026-04-21.md
  - docs/specs/UAT_PLAN.md
  - docs/release/GO_LIVE_CHECKLIST.md
backlinks:
  - _index
  - implementation/attachment4-requirement-task-book
  - implementation/attachment6-function-list-trace
  - implementation/document-delivery-ledger
created: 2026-04-26
updated: 2026-06-21
health_checked: 2026-06-21
---
# 附件4客户要求差距矩阵

本页把《附件4：西域数智化投标管理平台建设项目需求任务书》拆成可验收矩阵，并通过 [[implementation/attachment6-function-list-trace]] 衔接《附件6：需求功能清单》的功能颗粒度。附件6优先使用人工结构化 Markdown，Excel 原件保留为原始来源，便于蓝图评审、UAT、上线和终验时逐条核对。状态口径如下：

| 状态 | 含义 |
|---|---|
| 已覆盖 | wiki、代码说明或验证记录已有明确实现与证据，可进入 UAT 抽测 |
| 部分覆盖 | 已有主链路或基础能力，但仍有正式联调、配置化、性能、安全或交付材料缺口 |
| 待实施 | 当前主要是预留、占位或计划项，尚不能作为已交付能力承诺 |
| 待确认 | 需求口径依赖 SOW、蓝图确认件、接口清单或客户环境，需书面确认后定交付范围 |

## 差距矩阵

| # | 客户要求 | Wiki 条目 | 当前系统实现/缺口 | 验收证据 |
|---:|---|---|---|---|
| 1 | 全生命周期线上化：商机/标讯、评估、立项、任务、标书、评审、提交、结果归档闭环 | [[requirements]]、[[business-process]]、[[modules]] | 已覆盖主流程。标讯中心、项目立项、任务看板、文档编辑、投标结果闭环均有对应模块；需在 UAT 用真实 API 串联端到端场景。 | [[requirements]] 2.1；[[business-process]] 六阶段闭环；`docs/specs/UAT_PLAN.md` 场景 2 |
| 2 | 支持项目商机联动创建投标项目 | [[requirements]]、[[business-process]]、[[modules]] | 部分覆盖。wiki 记录项目立项可从 CRM/商机同步，但真实 CRM 适配仍属接口预留；本地项目创建主链路可验。 | [[requirements]] 2.1、2.6；[[business-process]] CRM 客户数据同步；UAT 需补“商机到项目”实测记录 |
| 3 | 可自定义流程引擎，驱动标讯追踪、立项、任务、编制、评审、中标反馈 | [[business-process]]、[[modules]]、[[roles-and-permissions]] | 部分覆盖。已有审批、任务、状态流转和角色权限；“可视化/低代码自定义流程引擎”需蓝图确认本期是否完整交付。 | [[business-process]] 第 3-7 阶段；[[roles-and-permissions]]；需补流程配置截图和流程变更测试 |
| 4 | 关键节点自动提醒与多级预警，覆盖标讯预知、合同续标、保证金、购买标书、答疑、提交、开标 | [[requirements]]、[[modules]]、[[deployment]] | 部分覆盖。费用保证金退还、日历/预警、告警模块已有记录；合同续标、答疑、标书购买等细粒度节点需按蓝图补齐触发规则与测试。 | [[requirements]] 2.1、2.3；[[modules]] 工作台/资源；需补预警规则配置与消息触达 UAT 证据 |
| 5 | 项目看板与进度视图，支持跨部门在线协同 | [[business-process]]、[[modules]] | 已覆盖主链路。四阶段任务看板、交付物、文档协作和角色分工已沉淀；需 UAT 抽测多人角色权限和任务流转。 | [[business-process]] 第 4 节；[[modules]] 2.3；`docs/specs/UAT_PLAN.md` 场景 2 |
| 6 | SOP、质量控制点和标准作业程序可配置到系统流程与规则 | [[requirements]]、[[business-process]]、[[ai-capabilities]] | 部分覆盖。AI 合规规则、项目流程、BAR SOP 有能力基础；企业级 SOP 配置台账与蓝图确认仍需补文档和配置样例。 | [[ai-capabilities]] 合规雷达；[[business-process]]；需补 SOP 配置清单和规则生效截图 |
| 7 | 企业统一模板库、资质证件库、历史案例库、竞争对手信息库 | [[requirements]]、[[modules]] | 已覆盖主要知识资产。资质、案例、模板、竞对情报均有模块说明；需按真实数据验证新增、检索、版本和权限。 | [[requirements]] 2.2；[[modules]] 2.4、3.4；`docs/specs/UAT_PLAN.md` 场景 3 |
| 8 | 知识资产集中存储、版本控制、授权共享 | [[modules]]、[[roles-and-permissions]]、[[business-process]] | 部分覆盖。模块与权限已有，文档版本历史也存在；知识资产细粒度授权、模板版本验收样例需补。 | [[modules]] 知识域/文档域；[[roles-and-permissions]]；需补知识库权限矩阵与版本回滚测试 |
| 9 | 标书制作辅助：模板填充、内容推荐、资质匹配、商务条款符合性、合规检查 | [[ai-capabilities]]、[[business-process]]、[[requirements]] | 已覆盖 AI 辅助主能力。DocInsight、智能装配、合规雷达、评分点覆盖均有说明；仍需用客户样例标书做验收样本。 | [[ai-capabilities]]；[[business-process]] 第 5 节；需补客户样例解析与合规报告 |
| 10 | 支持非专业投标人员独立完成高质量标书初稿 | [[ai-capabilities]]、[[business-process]]、[[roles-and-permissions]] | 部分覆盖。智能装配和模板库可支撑初稿，但“独立完成”属于效果验收，需用角色化 UAT 和示例文档证明。 | [[ai-capabilities]] 智能装配；需补销售/项目经理角色 UAT 记录 |
| 11 | 历史数据和案例支撑学习、对标分析、策略研讨和持续改进闭环 | [[business-process]]、[[modules]]、[[requirements]] | 已覆盖基础能力。结果闭环、案例沉淀、竞对情报和数据分析已有模块；复盘会议、策略研讨模板需实施文档补充。 | [[business-process]] 第 7 节；[[requirements]] 2.4；需补复盘记录模板和样例 |
| 12 | 统一采集项目信息、投入成本、结果数据，形成企业级投标数据中心 | [[requirements]]、[[data-model]]、[[modules]] | 已覆盖主数据链路。项目、费用、结果、分析看板均有说明；需在真实环境核对数据口径一致性。 | [[requirements]] 2.3、2.4；[[data-model]]；`docs/specs/UAT_PLAN.md` 场景 5 |
| 13 | 经营分析：中标率趋势、投入产出、竞对动态、报价策略、资源优化 | [[requirements]]、[[modules]]、[[ai-capabilities]] | 部分覆盖。数据分析、ROI、竞对情报已有；报价策略与资源优化需要明确 6 张报表口径和客户确认样表。 | [[requirements]] 2.4；[[contract-constraints]] 报表范围；需补 6 张报表定义和验收截图 |
| 14 | 历史投标趋势研判，辅助主动识别市场机会和风险 | [[requirements]]、[[modules]] | 部分覆盖。客户商机中心、市场洞察和采购方规律分析已有；需使用真实历史数据验证预测有效性。 | [[modules]] 标讯中心 Trend 口径；需补历史数据样本和预测复核记录 |
| 15 | 投标后结构化复盘：技术方案亮点、报价策略得失、客户反馈归档 | [[business-process]]、[[requirements]]、[[modules]] | 部分覆盖。结果闭环和案例沉淀已覆盖基础字段；“客户反馈、报价策略得失、亮点标签”需核对实体字段和页面表单。 | [[business-process]] 第 7 节；需补复盘字段清单和新增/查询测试 |
| 16 | OA、CRM、财务等系统安全、稳定、高效双向交互 | [[requirements]]、[[api-openapi]]、[[integration-wecom]] | 待实施/部分覆盖。开放 API 已落地；**OA 集成已取消**；CRM 当前为占位或接口预留，企业微信配置为 Mock 连通性，财务系统需另行确认。 | |
| 17 | 第三方商机/标讯服务集成 | [[requirements]]、[[architecture]] | 部分覆盖。标讯中心有外部标讯入库和 cebpubservice 口径；正式第三方账号、费用、白名单由甲方/第三方提供。 | [[contract-constraints]] 固定价边界；需补第三方 API 联调报告 |
| 18 | 按组织架构、项目组、用户配置系统/数据权限 | [[roles-and-permissions]]、[[data-permission-hardening]]、[[requirements]] | 已覆盖核心权限治理。RBAC、数据范围、项目访问守卫和自动化门禁已有；组织架构同步仍依赖企业微信/客户接口。 | [[roles-and-permissions]] 第 5 节；后端 `ProjectAccessGuardCoverageTest` 作为开发门禁；需补客户角色矩阵签字 |
| 19 | 私有云/本地化部署，B/S 架构，主流浏览器无插件访问 | [[deployment]]、[[architecture]]、[[contract-constraints]] | 已覆盖方案基线。Vue + Spring Boot B/S、端口、私有化部署和容量基线已登记；生产环境部署需现场验证。 | [[deployment]] 运行模式/容量基线；`docs/release/GO_LIVE_CHECKLIST.md` |
| 20 | HTTPS、细粒度权限、全流程操作日志、满足等保要求 | [[deployment]]、[[roles-and-permissions]]、[[contract-constraints]] | 部分覆盖。JWT/RBAC、审计与权限治理已有；HTTPS 证书、等保二/三级最终等级、安全测评报告需客户环境确认。 | [[contract-constraints]] 非功能与运维；需补安全方案、证书配置、等保测评/整改记录 |
| 21 | 7x24 稳定运行、99.5% 可用、普通页面 2 秒以内响应 | [[deployment]]、[[contract-constraints]] | 待确认/部分覆盖。指标已进合同和上线口径，但需要生产或压测环境的监控、压测和 SLA 报告证明。 | [[deployment]] 发布前检查；需补性能测试报告、监控截图、容量压测结果 |
| 22 | 移动端支持：微信/企业微信小程序或移动浏览器审批、进度、预警 | [[requirements]]、[[integration-wecom]]、[[modules]] | 待实施/部分覆盖。前端有响应式基础和企微配置入口；小程序、企微实际消息、移动审批闭环未作为已完成证据。 | [[requirements]] 非功能追溯；[[integration-wecom]] 后续工作；需补移动端 UAT |
| 23 | 报表、页面、工作流、表单、BI 图表、数据表可配置，支持非 IT 自定义 | [[implementation/attachment4-requirement-task-book]]、[[requirements]] | 待确认。本仓库已有系统设置、报表和图表能力，但低代码级配置能力需明确是否纳入本期范围。 | [[implementation/attachment4-requirement-task-book]] 第 5 节；需蓝图确认和配置演示证据 |
| 24 | 数据集成：主数据映射、自动/手动取数、异常报警、接口日志、调度、Kafka、统一认证授权 | [[implementation/attachment4-requirement-task-book]]、[[api-openapi]]、[[integration-wecom]] | 待实施/部分覆盖。OpenAPI 和企微配置为基础；主数据映射平台、调度、Kafka 消费、接口异常报警需正式设计和联调。 | [[implementation/attachment4-requirement-task-book]] 第 6 节；需补接口设计文档和联调问题闭环 |
| 25 | Office/WPS 集成，系统生成 Word、PDF、Excel 报告 | [[modules]]、[[deployment]] | 部分覆盖。文档导出、Excel 导出和文档域存在；Office/WPS 无缝编辑或插件式集成需确认口径。 | [[modules]] 文档域；需补 Word/PDF/Excel 导出样例和 Office/WPS 操作说明 |
| 26 | 数据库级、表级、字段级安全控制，备份、转储、迁移、灾备策略 | [[deployment]]、[[contract-constraints]] | 部分覆盖。备份恢复、MySQL 8、发布回滚口径已有；字段级安全和灾备演练需在生产方案中补证据。 | [[deployment]] 回滚策略；`docs/release/GO_LIVE_CHECKLIST.md`；需补备份恢复演练报告 |
| 27 | 自动化发布、主要功能自动化验证、失败快速回滚 | [[deployment]] | 已覆盖方案，待生产演练。发布、演练、回滚脚本和检查清单已登记；上线前需执行并归档报告。 | [[deployment]] 发布流程/回滚策略；`docs/release/GO_LIVE_CHECKLIST.md` |
| 28 | 实施服务：项目计划、部署、配置、测试数据、UAT、上线切换、每周汇报 | [[team-and-timeline]]、[[implementation/milestones]]、[[implementation/weekly-status]] | 已覆盖实施治理框架。仍需按项目周期持续产出周报、问题清单和阶段签字件。 | [[team-and-timeline]]；[[implementation/weekly-status]]；需补实际周报/会议纪要 |
| 29 | 蓝图设计、业务流程图、方案、接口/流程/原型设计、变更评估 | [[contract-constraints]]、[[implementation/attachment4-requirement-task-book]] | 部分覆盖。wiki 已列出交付清单和约束；正式文档需按双方确认模板输出、评审、签字。 | [[contract-constraints]] 第 8、9 节；需补蓝图确认函和变更评估记录 |
| 30 | 分层培训：管理员、关键用户、最终用户、开发/运维人员，培训不到位可延迟付款 | [[contract-constraints]]、[[implementation/acceptance-and-closure]] | 待执行。培训要求已纳入验收口径，但需培训计划、课件、签到、考试/反馈和补训记录。 | [[implementation/acceptance-and-closure]]；需补培训资料与签收记录 |
| 31 | 完整项目文档：准备、蓝图、实现、上线准备、上线支持各阶段文档 | [[implementation/attachment4-requirement-task-book]]、[[implementation/attachment6-function-list-trace]]、[[contract-constraints]]、[[implementation/document-delivery-ledger]] | 部分覆盖。wiki 已形成文档清单、附件6功能追溯和交付台账；实际交付物需逐项生成并获得甲方确认。 | [[implementation/document-delivery-ledger]]；后续需补正式文档路径、附件6裁剪确认和签字件 |
| 32 | 人员配置：全职现场项目经理、方案负责人、需求顾问、模块骨干顾问、人员稳定 | [[implementation/attachment4-requirement-task-book]]、[[team-and-timeline]] | 待确认。wiki 有人员与排期口径；是否满足附件4否决条款需以人员简历、驻场记录和甲方确认表为准。 | [[team-and-timeline]]；需补人员配置计划、简历和驻场考勤 |
| 33 | 质量控制点评审：蓝图、方案、实施、上线准备、上线支持均需签字通过 | [[contract-constraints]]、[[implementation/acceptance-and-closure]] | 部分覆盖。质量门禁已进入 wiki；执行证据需随阶段补齐。 | [[contract-constraints]] 第 9 节；需补质量控制点评审记录 |
| 34 | 验收：软件、文档、培训、实施过程、系统整体五类验收 | [[implementation/acceptance-and-closure]]、[[contract-constraints]] | 已形成验收框架，待执行。UAT、签字、试运行和终验材料需按阶段归档。 | [[implementation/acceptance-and-closure]]；`docs/specs/UAT_PLAN.md`、`docs/specs/UAT_SIGNOFF_TEMPLATE.md` |
| 35 | 售后质保：试运行保障、12 个月免费技术支持、每三个月安全检测、维保报价 | [[contract-constraints]]、[[implementation/attachment4-requirement-task-book]] | 待执行/待确认。合同约束和附件4均已吸收；试运行时长存在 3 个月与合同/SOW 6 个月口径差异，需以最终签署文本为准。 | [[contract-constraints]] 付款节点/非功能与运维；需补 SLA、运维计划和安全检测记录 |
| 36 | 知识产权、源码、过程文档、业务数据归属和保密 | [[contract-constraints]]、[[implementation/attachment4-requirement-task-book]] | 已覆盖合同约束。交付时需源码包、设计文档、数据交接清单、保密承诺和权限回收记录。 | [[contract-constraints]] 第 2、11 节；需补源码交付清单和保密交接记录 |

## 高优先级缺口

| 优先级 | 缺口 | 原因 | 建议动作 |
|---|---|---|---|
| P0 | CRM/企业微信/组织架构真实联调（OA 集成已取消） | 当前 OpenAPI 已覆盖，企业微信为配置入口和 Mock 连通性，CRM 仍需客户接口规范 | 蓝图阶段冻结接口清单、联调环境、账号权限、字段映射和验收样例 |
| P0 | 验收证据台账 | 附件4大量要求是“交付文档 + 签字 + 试运行”型，不是代码实现即可闭环 | 已建立 [[implementation/document-delivery-ledger]]；后续按阶段挂接签字件、测试报告、培训记录和问题闭环 |
| P1 | 性能、安全、等保与灾备证明 | wiki 已有目标值，但缺正式压测、安全方案和灾备演练证据 | 在 UAT 前完成性能测试、安全方案、备份恢复演练和监控截图 |
| P1 | 移动端和消息触达 | 响应式基础不等于微信/企微审批、进度、预警闭环 | 明确本期是移动浏览器适配、企业微信消息，还是小程序；按确认范围补测 |
| P1 | 可配置流程/表单/报表边界 | 附件4提出低代码/参数化能力，容易和当前标准产品能力混淆 | 在蓝图确认件中明确“配置项清单”和“不纳入本期项” |

## 使用方式

1. 蓝图评审时，先冻结“待确认”项的范围、接口、角色和验收样例。
2. UAT 准备时，对所有“已覆盖/部分覆盖”项补真实 API 操作截图、接口记录和测试报告。
3. 上线前，对 P0/P1 缺口逐项给出 owner、计划日期和书面豁免或完成证据。
4. 终验前，把本矩阵中的“需补”证据替换为正式文档编号、签字件路径或报告链接。
