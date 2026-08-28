// Input: create-page tender form object (useTenderCreateForm / useTenderAiParse)
// Output: labels of the 7 required fields that are still empty (canSave 的字段口径)
// Usage: canSave 计算 / 保存按钮禁用原因 tooltip / AI 解析回填后的缺失必填项提示
export const TENDER_REQUIRED_FIELDS = [
  { key: 'title', label: '项目名称' },
  { key: 'purchaser', label: '招标主体' },
  { key: 'region', label: '总部所在地' },
  { key: 'deadline', label: '报名截止时间' },
  { key: 'bidOpeningTime', label: '开标时间' },
  { key: 'customerType', label: '客户类型' },
  { key: 'priority', label: '优先级' },
]

export function getMissingTenderRequiredFields(form = {}) {
  return TENDER_REQUIRED_FIELDS
    .filter(({ key }) => {
      const value = form[key]
      return typeof value === 'string' ? !value.trim() : !value
    })
    .map(({ label }) => label)
}
