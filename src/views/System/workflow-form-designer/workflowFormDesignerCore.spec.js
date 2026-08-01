import { describe, expect, it } from 'vitest'
import {
  buildDefaultTemplate,
  buildMappingFromFields,
  buildSelectedTemplateState,
  createField,
  extractWorkflowFormError,
  moveField,
  removeField,
  validateCustomFieldKeyConflicts
} from './workflowFormDesignerCore.js'

describe('workflowFormDesignerCore', () => {
  it('creates productized default template draft', () => {
    const draft = buildDefaultTemplate()

    expect(draft.businessType).toBe('GENERAL_WORKFLOW')
    expect(draft.enabled).toBe(true)
    expect(draft.schema.fields[0]).toMatchObject({ type: 'text', required: true })
  })

  it('supports field add remove and deterministic ordering', () => {
    const fields = [createField('title', '标题', 'text'), createField('amount', '金额', 'number')]

    expect(moveField(fields, 1, -1).map((field) => field.key)).toEqual(['amount', 'title'])
    expect(removeField(fields, 'title').map((field) => field.key)).toEqual(['amount'])
  })

  it('builds safe OA mapping from configured fields', () => {
    const mapping = buildMappingFromFields('WF_SEAL', [
      createField('title', '标题', 'text'),
      createField('projectId', '项目', 'project')
    ])

    expect(mapping.workflowCode).toBe('WF_SEAL')
    expect(mapping.mainFields).toContainEqual(expect.objectContaining({
      source: 'formData.title',
      target: 'field_title'
    }))
  })

  it('restores persisted OA binding when editing an existing template', () => {
    const state = buildSelectedTemplateState({
      templateCode: 'SEAL_APPLY',
      name: '用章申请',
      businessType: 'GENERAL_WORKFLOW',
      enabled: true,
      schema: { fields: [createField('title', '标题', 'text')] },
      oaBinding: {
        provider: 'WEAVER',
        workflowCode: 'WF_SEAL',
        fieldMapping: { workflowCode: 'WF_SEAL', mainFields: [{ source: 'formData.title', target: 'oa_title' }] }
      }
    })

    expect(state.oa.workflowCode).toBe('WF_SEAL')
    expect(state.oa.fieldMapping.mainFields[0].target).toBe('oa_title')
  })

  it('extracts clear workflow form operation errors', () => {
    expect(extractWorkflowFormError({ response: { data: { msg: '映射错误' } } })).toBe('映射错误')
    expect(extractWorkflowFormError(null, '默认错误')).toBe('默认错误')
  })

  // CO-601 US2：与后端 CustomFieldsSchemaPolicy 同一语义，改动必须双向同步
  describe('validateCustomFieldKeyConflicts', () => {
    it('非项目 scope 不校验，直接放行', () => {
      const errors = validateCustomFieldKeyConflicts('tender.entry', [
        { key: 'region', label: '总部所在地', type: 'cascader' },
        { key: 'region', label: '重复', type: 'text' }
      ])
      expect(errors).toEqual([])
    })

    it('hybrid scope（project.initiation）自定义 key 撞预置清单被拒绝', () => {
      const errors = validateCustomFieldKeyConflicts('project.initiation', [
        { key: 'projectName', label: '项目名称', type: 'text' }
      ])
      expect(errors).toHaveLength(1)
      expect(errors[0]).toContain('projectName')
    })

    it('hybrid scope（project.detail）自定义 key 撞预置清单被拒绝', () => {
      const errors = validateCustomFieldKeyConflicts('project.detail', [
        { key: 'description', label: '项目描述', type: 'textarea' }
      ])
      expect(errors).toHaveLength(1)
      expect(errors[0]).toContain('description')
    })

    it('自定义 key 互撞（重复）被拒绝', () => {
      const errors = validateCustomFieldKeyConflicts('project.initiation', [
        { key: 'internalNote', label: '内部备注', type: 'text' },
        { key: 'internalNote', label: '内部备注2', type: 'textarea' }
      ])
      expect(errors).toHaveLength(1)
      expect(errors[0]).toContain('internalNote')
      expect(errors[0]).toContain('重复')
    })

    it('project.basic 预置字段合法存在（纯 schema 渲染），不命中冲突', () => {
      const presetFields = ['name', 'customer', 'budget', 'industry', 'region', 'platform', 'deadline', 'manager', 'competitors']
        .map((key) => ({ key, label: key, type: 'text' }))
      const errors = validateCustomFieldKeyConflicts('project.basic', [
        ...presetFields,
        { key: 'budgetLevel', label: '客户预算等级', type: 'text' }
      ])
      expect(errors).toEqual([])
    })

    it('project.basic 重复 key 仍被拒绝', () => {
      const errors = validateCustomFieldKeyConflicts('project.basic', [
        { key: 'name', label: '项目名称', type: 'text' },
        { key: 'name', label: '项目名称2', type: 'text' }
      ])
      expect(errors).toHaveLength(1)
      expect(errors[0]).toContain('name')
    })

    it('合法自定义 schema 放行', () => {
      const errors = validateCustomFieldKeyConflicts('project.initiation', [
        { key: 'internalReviewNote', label: '内审备注', type: 'textarea' },
        { key: 'legalSignOff', label: '法会签', type: 'select' }
      ])
      expect(errors).toEqual([])
    })

    it('空 fields / 空 key 跳过不报错', () => {
      expect(validateCustomFieldKeyConflicts('project.basic', [])).toEqual([])
      expect(validateCustomFieldKeyConflicts('project.basic', null)).toEqual([])
      expect(validateCustomFieldKeyConflicts('project.initiation', [{ key: '', label: '空' }, { label: '无key' }, null])).toEqual([])
    })
  })
})
