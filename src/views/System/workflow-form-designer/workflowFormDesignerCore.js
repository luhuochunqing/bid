// Input: workflow form designer state
// Output: deterministic field/template helpers for admin configuration UI
// Pos: src/views/System/workflow-form-designer/ - Flow form designer pure helpers

export const FIELD_TYPES = [
  // --- 基础类型 ---
  { label: '文本', value: 'text' },
  { label: '多行文本', value: 'textarea' },
  { label: '数字', value: 'number' },
  { label: '日期', value: 'date' },
  { label: '下拉', value: 'select' },
  { label: '人员', value: 'person' },
  { label: '项目', value: 'project' },
  { label: '附件', value: 'attachment' },
  { label: '说明文本', value: 'info' },
  // --- 扩展类型（M1 基础设施，V140） ---
  { label: '手机号', value: 'phone' },
  { label: '邮箱', value: 'email' },
  { label: '网址链接', value: 'url' },
  { label: '金额', value: 'currency' },
  { label: '百分比', value: 'percent' },
  { label: '地址', value: 'address' },
  { label: '分隔标题', value: 'section' },
  { label: '分隔线', value: 'divider' },
  { label: '标讯来源', value: 'tender_source' },
  { label: '项目状态', value: 'project_status' },
  { label: '资质类型', value: 'qualification_type' },
  { label: '表格编辑', value: 'table' },
  { label: '省市区级联', value: 'cascader' },
  { label: '日期时间', value: 'datetime' }
]

export const FIELD_TYPE_HELP_TEXT = {
  text: '单行文本输入',
  textarea: '多行文本，支持rows配置行数',
  number: '整数输入，支持min/max范围',
  date: '日期选择器',
  select: '下拉单选，options配置选项',
  person: '人员名称文本输入（可扩展为人员选择器）',
  project: '项目名称文本输入（可扩展为项目选择器）',
  attachment: '文件上传，支持limit/accept配置',
  info: '只读说明文本，显示content内容',
  phone: '手机号输入，内置+86前缀',
  email: '邮箱输入',
  url: '网址链接输入',
  currency: '金额输入，支持min/max/精度配置',
  percent: '百分比滑块，0-100',
  address: '省市区级联选择',
  section: '字段分组标题，配合divider使用',
  divider: '视觉分隔线',
  tender_source: '标讯来源枚举（招标/比选/竞争性谈判等）',
  project_status: '项目状态枚举（进行中/已暂停/已结项等）',
  qualification_type: '资质类型枚举（营业执照/资质证书等）',
  table: '多行数据表格编辑，支持columns定义列',
  cascader: '省市区级联选择（如总部所在地）',
  datetime: '日期时间选择器（如报名截止时间）'
}

// tender.entry 业务页有特殊渲染分支的字段（8 个）
// 锁定字段：key + type 不可修改，不可删除（改 type 会导致业务页渲染异常）
export const LOCKED_FIELD_KEYS = [
  'region', 'priority', 'deadline', 'bidOpeningTime',
  'customerType', 'projectType', 'pastedText', 'attachments'
]

// tender.entry 联系人1/联系人2 固定分组字段 key 列表（8 个）
// 固定分组字段：不可拖拽排序 + key 不可修改（业务页硬编码依赖 key），type 可改，不可删除
export const FIXED_GROUP_KEYS = [
  'contact', 'phone', 'landline', 'mail',
  'contact2', 'phone2', 'landline2', 'mail2'
]

// 所有可能需要锁定 key 的字段（LOCKED + FIXED_GROUP）
// 用于判断 key 输入框是否 disabled 和删除按钮是否隐藏
export const KEY_LOCKED_FIELD_KEYS = [...LOCKED_FIELD_KEYS, ...FIXED_GROUP_KEYS]

// 项目三表单（project.basic / project.initiation / project.detail）预置字段锁定清单（CO-601）
// 锁定字段：key + type 不可修改，不可删除（与 tender.entry LOCKED_FIELD_KEYS 同语义）
// 清单来源 = 业务页 form model 真实绑定 key（不是 DTO 字段名）：
// - project.basic：useProjectCreateModel.js basicForm（9 个）
// - project.detail：useProjectCreateModel.js detailForm（7 个）
// - project.initiation：InitiationStage.vue form reactive（42 个）+ custFixedRows 客户信息矩阵
//   核对结论（2026-07-31）：form 内 annualRevenue/customerRevenue 双 key 并存（buildPayload 映射
//   annualRevenue: form.customerRevenue || form.annualRevenue），两 key 均锁定；
//   customerInfoRows 为 DTO 侧 key（对应客户端 custFixedRows），一并保留防碰撞。
// ⚠️ 互指注释：后端 CustomFieldsSchemaPolicy（formengine/domain）内嵌同一清单，改动必须双向同步
export const PROJECT_LOCKED_FIELD_KEYS = {
  'project.basic': [
    'name', 'customer', 'budget', 'industry', 'region', 'platform',
    'deadline', 'manager', 'competitors'
  ],
  'project.detail': [
    'description', 'tags', 'startDate', 'endDate', 'remark',
    'projectLeaderName', 'leaderDepartment'
  ],
  'project.initiation': [
    'projectName', 'ownerUnit', 'createTime', 'projectType', 'customerType',
    'priorityLevel', 'headquartersLocation', 'projectLeaderName', 'projectLeaderUserId',
    'leaderDepartment', 'contactName', 'contactPhone', 'contactTel', 'contactMail',
    'contactName2', 'contactPhone2', 'contactTel2', 'contactMail2',
    'tenderId', 'expectedBidders', 'annualEcommerceAmount', 'annualRevenue', 'customerRevenue',
    'bidOpenTime', 'bidMonth', 'biddingPlatform',
    'needDeposit', 'depositAmount', 'depositPaymentMethod', 'depositDueDate',
    'tenderAdverseItems', 'riskAssessment', 'riskMitigationPlan', 'pmUnderstandsProcess',
    'supportNeeded', 'projectPlanGap', 'projectPlanGapFiles',
    'tenderDocumentId', 'aiRiskLevel', 'aiRiskAssessmentNotes',
    'biddingLeaderName', 'biddingAssistantName',
    'custFixedRows', 'customerInfoRows'
  ]
}

// 项目 scope 判定（tender.entry 等其他 scope 走原 LOCKED_FIELD_KEYS/FIXED_GROUP_KEYS）
export function isProjectScope(scope) {
  return Object.prototype.hasOwnProperty.call(PROJECT_LOCKED_FIELD_KEYS, scope)
}

export function createField(key = 'field1', label = '字段', type = 'text') {
  const field = { key, label, type, required: type !== 'info' && type !== 'section' && type !== 'divider' && type !== 'info' }
  switch (type) {
    case 'select':
    case 'tender_source':
    case 'project_status':
    case 'qualification_type':
      field.options = [{ label: '选项一', value: 'option_1' }]
      break
    case 'info':
      field.content = '请填写说明内容'
      break
    case 'table':
      field.columns = [
        { key: 'col1', label: '列1', type: 'text', required: false },
        { key: 'col2', label: '列2', type: 'text', required: false }
      ]
      field.minRows = 1
      field.maxRows = 20
      break
  }
  return field
}

export function buildDefaultTemplate(scope = 'GENERAL') {
  const templates = {
    GENERAL: {
      templateCode: 'GENERAL_APPLY',
      name: '通用申请',
      businessType: 'GENERAL_WORKFLOW',
      enabled: true,
      schema: { fields: [createField('title', '申请标题', 'text')] }
    },
    TENDER: {
      templateCode: 'TENDER_ENTRY',
      name: '标讯录入',
      businessType: 'TENDER_WORKFLOW',
      enabled: true,
      schema: { fields: [createField('title', '标讯标题', 'text')] }
    },
    PROJECT: {
      templateCode: 'PROJECT_BASIC',
      name: '项目信息',
      businessType: 'PROJECT_WORKFLOW',
      enabled: true,
      schema: { fields: [createField('name', '项目名称', 'text')] }
    }
  }
  return templates[scope] || templates.GENERAL
}

export function removeField(fields, key) {
  return fields.filter((field) => field.key !== key)
}

export function moveField(fields, index, direction) {
  const next = [...fields]
  const target = index + direction
  if (target < 0 || target >= next.length) return next
  const [field] = next.splice(index, 1)
  next.splice(target, 0, field)
  return next
}

export function buildMappingFromFields(workflowCode, fields) {
  return {
    workflowCode,
    mainFields: fields
      .filter((field) => field.type !== 'info')
      .map((field) => ({
        source: `formData.${field.key}`,
        target: `field_${field.key}`,
        targetName: field.label,
        type: field.type === 'date' ? 'date' : 'string',
        required: Boolean(field.required)
      }))
  }
}

export function buildSelectedTemplateState(template = {}) {
  const schema = { fields: (template.schema?.fields || []).map((field) => ({ ...field })) }
  const binding = template.oaBinding || {}
  return {
    draft: {
      templateCode: template.templateCode,
      name: template.name,
      businessType: template.businessType,
      enabled: template.enabled,
      schema
    },
    oa: {
      provider: binding.provider || 'WEAVER',
      workflowCode: binding.workflowCode || '',
      fieldMapping: binding.fieldMapping || { workflowCode: binding.workflowCode || '', mainFields: [] }
    }
  }
}

export function extractWorkflowFormError(error, fallback = '流程表单操作失败') {
  return error?.response?.data?.msg || error?.message || fallback
}
