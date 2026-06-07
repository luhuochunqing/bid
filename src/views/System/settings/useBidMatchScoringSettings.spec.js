import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const getModels = vi.fn()
const saveModel = vi.fn()
const activateModel = vi.fn()

vi.mock('@/api', () => ({
  bidMatchScoringApi: {
    getModels,
    saveModel,
    activateModel,
  },
}))

const elMessage = {
  success: vi.fn(),
  error: vi.fn(),
}

vi.mock('element-plus', () => ({
  ElMessage: elMessage,
}))

const {
  createDefaultModel,
  normalizeEditableDimension,
  normalizeEditableRule,
  useBidMatchScoringSettings,
  validateEditableModel,
  validateEnabledWeightTotal,
} = await import('./useBidMatchScoringSettings.js')

function createHarness() {
  return defineComponent({
    template: '<div />',
    setup() {
      return useBidMatchScoringSettings()
    },
  })
}

const validDimensions = () => [
  {
    key: 'budgetFit',
    name: '预算匹配',
    enabled: true,
    weight: 60,
    rules: [{
      key: 'budgetKeyword',
      name: '预算关键词',
      type: 'KEYWORD',
      evidenceKey: 'tender.searchText',
      keywords: ['预算'],
      weight: 100,
      enabled: true,
    }],
  },
  {
    key: 'delivery',
    name: '交付窗口',
    enabled: true,
    weight: 40,
    rules: [{
      key: 'caseCount',
      name: '中标案例数量',
      type: 'QUANTITY',
      evidenceKey: 'case.wonCount',
      minValue: 1,
      weight: 100,
      enabled: true,
    }],
  },
]

describe('useBidMatchScoringSettings helpers', () => {
  it('normalizes editable dimensions with nested rules', () => {
    expect(normalizeEditableDimension({
      code: 'budgetFit',
      name: '预算匹配',
      weight: '40',
      rules: [{ code: 'budgetKeyword', type: 'KEYWORD', evidenceKey: 'tender.searchText', keywords: ['预算'], weight: '100' }],
    })).toMatchObject({
      key: 'budgetFit',
      name: '预算匹配',
      enabled: true,
      weight: 40,
      rules: [expect.objectContaining({ key: 'budgetKeyword', evidenceKey: 'tender.searchText', weight: 100 })],
    })
  })

  it('normalizes editable rules and validates model weights', () => {
    expect(normalizeEditableRule({ code: 'exists', type: 'EXISTS', evidenceKey: 'qualification.active' })).toMatchObject({
      key: 'exists',
      type: 'EXISTS',
      evidenceKey: 'qualification.active',
    })

    expect(validateEnabledWeightTotal([
      { enabled: true, weight: 60 },
      { enabled: true, weight: 40 },
      { enabled: false, weight: 80 },
    ])).toMatchObject({ valid: true, total: 100 })
  })

  it('keeps the first model empty so customers define dimensions themselves', () => {
    const model = createDefaultModel()

    expect(model.name).toBe('投标匹配评分模型')
    expect(model.dimensions).toEqual([])
    expect(validateEditableModel(model)).toMatchObject({
      valid: false,
      message: '至少启用一个评分维度。',
    })
  })

  it('rejects enabled dimensions without complete rule weights', () => {
    expect(validateEditableModel({
      dimensions: [{
        enabled: true,
        name: '预算匹配',
        weight: 100,
        rules: [{ enabled: true, name: '预算关键词', type: 'KEYWORD', evidenceKey: 'tender.searchText', keywords: ['预算'], weight: 80 }],
      }],
    })).toMatchObject({
      valid: false,
      message: '维度「预算匹配」的规则权重总和必须为 100%。',
    })
  })
})

describe('useBidMatchScoringSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getModels.mockResolvedValue({
      success: true,
      data: [{
        id: 'm1',
        name: '销售模型',
        active: true,
        enabled: true,
        dimensions: validDimensions(),
      }],
    })
    saveModel.mockResolvedValue({ success: true, data: { id: 'm1', name: '销售模型', dimensions: validDimensions() } })
    activateModel.mockResolvedValue({ success: true, data: { id: 'm1', active: true } })
  })

  it('loads active model from API', async () => {
    const wrapper = mount(createHarness())

    await wrapper.vm.load()
    await flushPromises()

    expect(getModels).toHaveBeenCalled()
    expect(wrapper.vm.currentModel.name).toBe('销售模型')
    expect(wrapper.vm.weightValidation.valid).toBe(true)
  })

  it('blocks save when enabled weights are not 100', async () => {
    const wrapper = mount(createHarness())
    await wrapper.vm.load()

    wrapper.vm.currentModel.dimensions[0].weight = 50
    await wrapper.vm.save()

    expect(saveModel).not.toHaveBeenCalled()
    expect(elMessage.error).toHaveBeenCalledWith('启用维度权重总和必须为 100%，当前为 90%。')
  })

  it('saves model and activates it when requested', async () => {
    const wrapper = mount(createHarness())
    await wrapper.vm.load()

    await wrapper.vm.save()
    await wrapper.vm.activateCurrentModel()

    expect(saveModel).toHaveBeenCalledWith(expect.objectContaining({
      id: 'm1',
      dimensions: expect.arrayContaining([
        expect.objectContaining({ key: 'budgetFit', weight: 60 }),
      ]),
    }))
    expect(activateModel).toHaveBeenCalledWith('m1')
    expect(elMessage.success).toHaveBeenCalledWith('投标匹配评分模型已保存')
  })

  it('adds and removes configurable dimensions and rules', async () => {
    const wrapper = mount(createHarness())
    await wrapper.vm.load()

    wrapper.vm.addDimension()
    const dimension = wrapper.vm.currentModel.dimensions[wrapper.vm.currentModel.dimensions.length - 1]
    expect(dimension.name).toBe('新评分维度')

    wrapper.vm.addRule(dimension)
    expect(dimension.rules[0].name).toBe('新评分规则')

    wrapper.vm.removeRule(dimension, dimension.rules[0].key)
    expect(dimension.rules).toEqual([])

    wrapper.vm.removeDimension(dimension.key)
    expect(wrapper.vm.currentModel.dimensions.some((item) => item.key === dimension.key)).toBe(false)
  })
})
