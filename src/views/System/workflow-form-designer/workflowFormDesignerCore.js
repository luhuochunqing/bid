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
  { label: '表格编辑', value: 'table' }
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
  table: '多行数据表格编辑，支持columns定义列'
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
