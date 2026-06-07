// 输入：使用真实 API 模块 mock 的工作流表单设计器 composable
// 输出：覆盖来源切换与表单引擎规则共享状态的回归测试
// 位置：src/views/System/workflow-form-designer/ - 表单设计器状态编排测试

import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn()
  }
}))

vi.mock('@/api/modules/workflowForm.js', () => ({
  workflowFormApi: {
    listAdminTemplates: vi.fn(),
    listBusinessTypes: vi.fn(),
    listTemplateVersions: vi.fn(),
    createTemplateDraft: vi.fn(),
    saveOaBinding: vi.fn(),
    publishTemplate: vi.fn(),
    rollbackTemplateVersion: vi.fn(),
    testSubmitTemplate: vi.fn()
  },
  formDefinitionApi: {
    listFormDefinitions: vi.fn(),
    updateFormDefinition: vi.fn(),
    publishFormDefinition: vi.fn(),
    saveVisibilityRules: vi.fn(),
    saveConditionRules: vi.fn(),
    getVisibilityRules: vi.fn(),
    getConditionRules: vi.fn(),
    getCrossFieldRules: vi.fn(),
    saveCrossFieldRules: vi.fn(),
    getTenantOverrides: vi.fn(),
    saveTenantOverrides: vi.fn()
  }
}))

import { workflowFormApi, formDefinitionApi } from '@/api/modules/workflowForm.js'
import { useWorkflowFormDesigner } from './useWorkflowFormDesigner.js'

function mountDesigner() {
  let designer
  mount({
    template: '<div />',
    setup() {
      designer = useWorkflowFormDesigner()
      return {}
    }
  })
  return designer
}

function mockInitialWorkflowLoad() {
  workflowFormApi.listAdminTemplates.mockResolvedValue({ data: [] })
  workflowFormApi.listBusinessTypes.mockResolvedValue({ data: ['GENERAL_WORKFLOW'] })
}

describe('useWorkflowFormDesigner', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockInitialWorkflowLoad()
    formDefinitionApi.listFormDefinitions.mockResolvedValue({
      data: {
        content: [
          {
            id: 7,
            scope: 'PROJECT_INITIATION',
            scopeLabel: '项目立项',
            enabled: true,
            schemaJson: JSON.stringify({ fields: [{ key: 'amount', label: '金额', type: 'number' }] })
          }
        ]
      }
    })
    formDefinitionApi.getVisibilityRules.mockResolvedValue({ data: [] })
    formDefinitionApi.getConditionRules.mockResolvedValue({ data: [] })
    formDefinitionApi.getCrossFieldRules.mockResolvedValue({ data: [] })
    formDefinitionApi.getTenantOverrides.mockResolvedValue({
      data: [{ fieldKey: 'amount', overrideType: 'label', overrideValue: '投标金额' }]
    })
  })

  it('暴露来源切换函数并加载所选真实 API 集合', async () => {
    const designer = mountDesigner()
    await flushPromises()

    expect(typeof designer.onSourceChange).toBe('function')

    await designer.onSourceChange('formengine')

    expect(designer.activeSource.value).toBe('formengine')
    expect(formDefinitionApi.listFormDefinitions).toHaveBeenCalledWith(0, 100)
    expect(designer.formDefinitions.value).toHaveLength(1)
  })

  it('租户覆盖编辑与 saveAll 持久化使用同一份状态', async () => {
    const designer = mountDesigner()
    await flushPromises()
    await designer.onSourceChange('formengine')

    designer.selectFormDefinition(designer.formDefinitions.value[0])
    await flushPromises()

    expect(designer.tenantOverrides.value).toEqual([
      { fieldKey: 'amount', overrideType: 'label', overrideValue: '投标金额' }
    ])

    designer.tenantOverrides.value.push({
      fieldKey: 'amount',
      overrideType: 'required',
      overrideValue: 'true'
    })
    formDefinitionApi.updateFormDefinition.mockResolvedValue({ data: {} })
    formDefinitionApi.saveVisibilityRules.mockResolvedValue({ data: {} })
    formDefinitionApi.saveConditionRules.mockResolvedValue({ data: {} })
    formDefinitionApi.saveCrossFieldRules.mockResolvedValue({ data: {} })
    formDefinitionApi.saveTenantOverrides.mockResolvedValue({ data: {} })

    await designer.saveAll()

    expect(formDefinitionApi.saveTenantOverrides).toHaveBeenCalledWith(7, [
      { fieldKey: 'amount', overrideType: 'label', overrideValue: '投标金额' },
      { fieldKey: 'amount', overrideType: 'required', overrideValue: 'true' }
    ])
  })
})
