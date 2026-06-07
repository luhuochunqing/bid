import { describe, expect, it } from 'vitest'
import {
  buildDefaultTemplate,
  buildMappingFromFields,
  buildSelectedTemplateState,
  createField,
  extractWorkflowFormError,
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
})
