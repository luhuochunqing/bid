import { describe, expect, it } from 'vitest'
import {
  buildDefaultTemplate,
  buildMappingFromFields,
  buildSelectedTemplateState,
  createField,
  extractWorkflowFormError,
  isProjectBasicLockedField,
  moveField,
  removeField
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

  describe('isProjectBasicLockedField', () => {
    it('project.basic 系统字段（如 name）返回 true', () => {
      expect(isProjectBasicLockedField('project.basic', 'name')).toBe(true)
      expect(isProjectBasicLockedField('project.basic', 'managerId')).toBe(true)
      expect(isProjectBasicLockedField('project.basic', 'description')).toBe(true)
    })

    it('project.basic 自定义字段返回 false', () => {
      expect(isProjectBasicLockedField('project.basic', 'customField1')).toBe(false)
      expect(isProjectBasicLockedField('project.basic', 'myField')).toBe(false)
    })

    it('非 project.basic scope 一律返回 false（不影响 tender.entry 等）', () => {
      expect(isProjectBasicLockedField('tender.entry', 'name')).toBe(false)
      expect(isProjectBasicLockedField('project.initiation', 'name')).toBe(false)
      expect(isProjectBasicLockedField('project.detail', 'name')).toBe(false)
      expect(isProjectBasicLockedField('knowledge.case', 'title')).toBe(false)
      expect(isProjectBasicLockedField('', 'name')).toBe(false)
      expect(isProjectBasicLockedField(null, 'name')).toBe(false)
    })
  })
})
