/**
 * AI 评分标准解析默认模版与打分数据字典
 * Pos: src/composables/projectDetail/scoreParseDefaults.js
 */

export const defaultScoreTemplate = [
  { code: 'A1', dim: '技术方案', req: '总体架构设计（架构图、组件划分、技术选型）', detail: '总体架构设计（架构图、组件划分、技术选型）', weight: 10, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '需评标专家根据标书技术方案描述人工评审' },
  { code: 'A2', dim: '技术方案', req: '微服务架构与高可用设计', detail: '微服务架构与高可用设计', weight: 8, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '需评标专家根据标书技术方案描述人工评审' },
  { code: 'A3', dim: '技术方案', req: '数据安全与备份恢复方案', detail: '数据安全与备份恢复方案', weight: 6, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '需评标专家根据标书技术方案描述人工评审' },
  { code: 'A4', dim: '技术方案', req: '接口设计与开放能力', detail: '接口设计与开放能力', weight: 5, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '需评标专家根据标书技术方案描述人工评审' },
  { code: 'B1', dim: '商务方案', req: '报价合理性（与市场均价对比）', detail: '报价合理性（与市场均价对比）', weight: 10, status: 'neutral', statusText: '待确认', scoreType: '客观项', estScore: 9, estBasis: 'AI 预计报价处于市场均价合理区间，预计得分 9' },
  { code: 'B2', dim: '商务方案', req: '付款方式响应', detail: '付款方式响应', weight: 5, status: 'neutral', statusText: '待确认', scoreType: '客观项', estScore: 5, estBasis: 'AI 预计标书将完全响应招标付款方式，预计满分' },
  { code: 'C1', dim: '实施服务', req: '项目实施计划与里程碑', detail: '项目实施计划与里程碑', weight: 7, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '需评标专家根据标书实施计划人工评审' },
  { code: 'C2', dim: '实施服务', req: '团队配置（项目经理 + 核心成员）', detail: '团队配置（项目经理 + 核心成员）', weight: 8, status: 'ok', statusText: '✓ 满足', scoreType: '客观项', estScore: 8, estBasis: '知识库命中：人员库匹配项目经理资质与核心成员，预计满分', kbHit: true },
  { code: 'C3', dim: '实施服务', req: '培训方案与知识转移', detail: '培训方案与知识转移', weight: 5, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '需评标专家根据标书培训方案人工评审' },
  { code: 'D1', dim: '资质业绩', req: '信息系统集成及服务资质', detail: '信息系统集成及服务资质', weight: 6, status: 'ok', statusText: '✓ 满足', scoreType: '客观项', estScore: 6, estBasis: '知识库命中：资质库匹配证书「信息系统集成及服务资质一级」，预计满分', kbHit: true },
  { code: 'D2', dim: '资质业绩', req: 'CMMI 5 级认证', detail: 'CMMI 5 级认证', weight: 5, status: 'danger', statusText: '✗ 不满足', scoreType: '客观项', estScore: 0, estBasis: '知识库未匹配 CMMI 5 级证书（最高为 CMMI 3 级），预计 0 分' },
  { code: 'D3', dim: '资质业绩', req: '近 3 年类似项目业绩（≥3 项）', detail: '近 3 年类似项目业绩（≥3 项）', weight: 7, status: 'ok', statusText: '✓ 满足', scoreType: '客观项', estScore: 7, estBasis: '知识库命中：业绩库匹配近 3 年类似项目 5 项，预计满分', kbHit: true },
  { code: 'E1', dim: '加分项', req: '本地化服务能力（本地团队 / 办公场地）', detail: '本地化服务能力（本地团队 / 办公场地）', weight: 18, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '需评标专家根据标书本地化服务承诺人工评审' },
]

export const defaultScoreResults = {
  A1: { actualScore: null, scoreType: 'subjective', status: 'subjective', evidence: '技术方案描述类评分项，需评标专家人工评审' },
  A2: { actualScore: null, scoreType: 'subjective', status: 'subjective', evidence: '技术方案描述类评分项，需评标专家人工评审' },
  A3: { actualScore: null, scoreType: 'subjective', status: 'subjective', evidence: '技术方案描述类评分项，需评标专家人工评审' },
  A4: { actualScore: null, scoreType: 'subjective', status: 'subjective', evidence: '技术方案描述类评分项，需评标专家人工评审' },
  B1: { actualScore: 9, scoreType: 'objective', status: 'full', evidence: '报价 580 万元，处于市场合理均价区间（550-620 万元）', quote: '第 3 章 投标报价（第 12 页）：我方投标总价：人民币 580 万元整（含税）' },
  B2: { actualScore: 5, scoreType: 'objective', status: 'full', evidence: '完全响应招标文件付款方式（30%+60%+10%）', quote: '第 5 章 商务条款响应（第 18 页）：我方完全接受招标文件规定的付款方式：合同签订后预付 30%，验收合格后付 60%，质保期满付 10%。' },
  C1: { actualScore: null, scoreType: 'subjective', status: 'subjective', evidence: '实施计划类评分项，需评标专家人工评审' },
  C2: { actualScore: 8, scoreType: 'objective', status: 'full', evidence: '标书配置项目经理 1 名 + 核心成员 5 名，人员资质均符合招标要求', quote: '第 6 章 实施服务方案（第 22 页）：项目经理：张三（PMP 认证）；核心成员：架构师 1、前端 1、后端 2、测试 1。' },
  C3: { actualScore: null, scoreType: 'subjective', status: 'subjective', evidence: '培训方案类评分项，需评标专家人工评审' },
  D1: { actualScore: 6, scoreType: 'objective', status: 'full', evidence: '知识库命中：资质库匹配证书「信息系统集成及服务资质一级（有效期至 2027-08-12）」', kbHit: true },
  D2: { actualScore: 3, scoreType: 'objective', status: 'partial', evidence: '标书已补充 CMMI 3 级证书说明及替代方案，部分满足要求', quote: '第 7 章 资质证明（第 28 页）：我方虽未取得 CMMI 5 级认证，但已通过 CMMI 3 级认证，并建立了完整的研发管理体系。', missedReason: 'CMMI 5 级认证未找到匹配证书，标书已补充 CMMI 3 级说明，部分得分' },
  D3: { actualScore: 7, scoreType: 'objective', status: 'full', evidence: '知识库命中：业绩库匹配近 3 年类似项目 5 项（≥3 项要求）', quote: '第 7 章 资质证明（第 30 页）：近 3 年类似项目业绩 5 项，含智慧园区项目 3 项' },
  E1: { actualScore: null, scoreType: 'subjective', status: 'subjective', evidence: '本地化服务能力评分项，需评标专家人工评审' },
}

export const defaultSuggestions = {
  A1: '建议在标书中补充完整的总体架构设计图，明确组件划分和技术选型依据，确保架构方案与招标要求逐条对齐',
  A2: '建议补充微服务架构图、服务治理方案（注册中心、配置中心、网关）及高可用容灾设计说明',
  A3: '建议详细描述数据加密方案（含国密算法支持）、备份策略（全量+增量）及灾难恢复流程',
  A4: '建议补充 API 接口规范文档，提供第三方系统集成示例及开放能力清单',
  B1: '建议补充报价明细构成说明，确保报价完整覆盖软硬件、实施、运维全部费用，避免漏项',
  B2: '建议在标书中逐条明确响应招标文件规定的付款方式条款，避免模糊表述',
  C1: '建议补充项目实施计划甘特图，明确各里程碑节点、交付物及责任人',
  C3: '建议补充培训方案详细计划，包括培训内容、课时安排、考核方式及知识转移保障措施',
  D2: '建议尽快启动 CMMI 5 级认证评估流程，或在标书中提供更充分的替代方案说明（如 CMMI 3 级 + 研发管理体系证明）',
  E1: '建议在标书中补充本地化服务承诺，包括本地团队派驻计划、办公场地租赁证明或合作伙伴协议',
}
