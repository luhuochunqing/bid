// Input: create-page tender form object (useTenderCreateForm / useTenderAiParse)
// Output: labels of the 8 required fields that are still empty (canSave 的字段口径)
// Usage: canSave 计算 / 保存按钮禁用原因 tooltip / AI 解析回填后的缺失必填项提示
// 注意：字段集必须与 constants.js 的 MANUAL_FORM_RULES 保持同步（均含 projectType），
// 否则 tooltip/AI 提示会漏报，用户点保存仍被表单红字拦截（PR !2450 审查发现）。
export const TENDER_REQUIRED_FIELDS = [
  { key: 'title', label: '项目名称' },
  { key: 'purchaser', label: '招标主体' },
  { key: 'region', label: '总部所在地' },
  { key: 'deadline', label: '报名截止时间' },
  { key: 'bidOpeningTime', label: '开标时间' },
  { key: 'customerType', label: '客户类型' },
  { key: 'priority', label: '优先级' },
  { key: 'projectType', label: '项目类型' },
]

export function getMissingTenderRequiredFields(form = {}) {
  return TENDER_REQUIRED_FIELDS
    .filter(({ key }) => {
      const value = form[key]
      return typeof value === 'string' ? !value.trim() : !value
    })
    .map(({ label }) => label)
}

// 保存按钮禁用原因：解析中优先于缺失提示；无缺失返回空串（tooltip 隐藏）
export function buildSaveDisabledReason({ parsingDocument = false, form = {} } = {}) {
  if (parsingDocument) return '标讯附件正在上传或识别中，请稍候'
  const missing = getMissingTenderRequiredFields(form)
  return missing.length ? `还差必填项：${missing.join('、')}` : ''
}
