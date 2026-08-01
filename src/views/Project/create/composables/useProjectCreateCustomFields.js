// Input: basicForm / detailForm / project.customFields
// Output: useProjectCreateCustomFields — schema 登记 / payload 收集 / 编辑回显
// Pos: src/views/Project/create/composables/ - 创建向导自定义字段注册表（自 useProjectCreateModel.js 拆出，line-budget 300 约束）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { collectCustomFields, mergeCustomFieldsIntoModel } from '@/composables/useCustomFields.js'
import { PROJECT_LOCKED_FIELD_KEYS } from '@/views/System/workflow-form-designer/workflowFormDesignerCore.js'

/**
 * CO-601: 创建向导自定义字段注册表。
 * AdaptiveFormPage 加载的 schema 字段按 scope 登记；收集/回显时以 PROJECT_LOCKED_FIELD_KEYS 为预置清单。
 */
export function useProjectCreateCustomFields() {
  const customFieldsSchemas = {}

  function setCustomFieldsSchema(scope, fields) {
    customFieldsSchemas[scope] = Array.isArray(fields) ? fields : []
  }

  /** 按 scope 收集 basic/detail 自定义字段（schema 减预置清单）；无自定义值时返回 {} */
  function collectAll(basicForm, detailForm) {
    return {
      ...collectCustomFields(basicForm, customFieldsSchemas['project.basic'], PROJECT_LOCKED_FIELD_KEYS['project.basic'], 'project.basic'),
      ...collectCustomFields(detailForm, customFieldsSchemas['project.detail'], PROJECT_LOCKED_FIELD_KEYS['project.detail'], 'project.detail')
    }
  }

  /** 编辑模式回显：按 scope 摊平进表单（预置 key 以 DTO 权威值为准，撞 key 脏数据忽略） */
  function mergeAll(basicForm, detailForm, customFields) {
    mergeCustomFieldsIntoModel(basicForm, customFields, 'project.basic', PROJECT_LOCKED_FIELD_KEYS['project.basic'])
    mergeCustomFieldsIntoModel(detailForm, customFields, 'project.detail', PROJECT_LOCKED_FIELD_KEYS['project.detail'])
  }

  return { setCustomFieldsSchema, collectAll, mergeAll }
}
