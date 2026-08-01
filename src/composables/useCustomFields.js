// Input: model / schemaFields / presetKeys / scope
// Output: collectCustomFields / mergeCustomFieldsIntoModel 纯函数
// Pos: src/composables/ - 自定义字段收集与回显摊平（不含 UI 提示逻辑，UI 提示由组件层负责）
// 预置清单单一来源：@/views/System/workflow-form-designer/workflowFormDesignerCore.js 的 PROJECT_LOCKED_FIELD_KEYS
// 后端 CustomFieldsSchemaPolicy 内嵌同一清单，改动必须双向同步

/**
 * 从业务表单 model 收集自定义字段值，按 scope 分组。
 * 自定义 key 集 = schemaFields.map(f => f.key) − presetKeys
 * 跳过 undefined（未填写）；null / 空串保留（支持清空字段）。
 * @returns {{ [scope]: { [key]: value } }} 无自定义值时返回 {}
 */
export function collectCustomFields(model, schemaFields, presetKeys, scope) {
  if (!model || !Array.isArray(schemaFields) || !scope) return {}
  const preset = new Set(presetKeys || [])
  const collected = {}
  for (const field of schemaFields) {
    const key = field?.key
    if (!key || preset.has(key)) continue
    if (model[key] === undefined) continue
    collected[key] = model[key]
  }
  return Object.keys(collected).length > 0 ? { [scope]: collected } : {}
}

/**
 * 回显：把 customFields[scope] 摊平进 model 顶层（原地修改）。
 * presetKeys 用于过滤脏数据（撞预置 key 的存值不覆盖 DTO 权威值）。
 */
export function mergeCustomFieldsIntoModel(model, customFields, scope, presetKeys = []) {
  if (!model || !customFields || !scope) return
  const values = customFields[scope]
  if (!values || typeof values !== 'object' || Array.isArray(values)) return
  const preset = new Set(presetKeys)
  for (const [key, value] of Object.entries(values)) {
    if (preset.has(key)) continue
    model[key] = value
  }
}
