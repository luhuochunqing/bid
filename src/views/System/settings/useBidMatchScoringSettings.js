import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { bidMatchScoringApi } from '@/api'

const toArray = (value) => (Array.isArray(value) ? value : [])

const toNumber = (value, fallback = 0) => {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : fallback
}

const normalizeText = (value, fallback = '') => String(value ?? fallback).trim()

const makeKey = (prefix) => `${prefix}-${Date.now()}`

export const EVIDENCE_KEY_OPTIONS = [
  { label: '标讯全文', value: 'tender.searchText' },
  { label: '预算金额', value: 'tender.budget' },
  { label: '资质名称', value: 'qualification.names' },
  { label: '有效资质数量', value: 'qualification.validCount' },
  { label: '存在有效资质', value: 'qualification.active' },
  { label: '案例全文', value: 'case.searchText' },
  { label: '中标案例数量', value: 'case.wonCount' },
  { label: '确认中标数量', value: 'bidResult.confirmedWinCount' },
]

export const RULE_TYPE_OPTIONS = [
  { label: '关键词', value: 'KEYWORD' },
  { label: '存在', value: 'EXISTS' },
  { label: '数量下限', value: 'QUANTITY' },
  { label: '数值区间', value: 'RANGE' },
]

export function normalizeEditableRule(rule = {}) {
  const key = normalizeText(rule.key || rule.code || rule.id || makeKey('rule'))
  return {
    key,
    code: key,
    name: normalizeText(rule.name || rule.label || rule.title, '新评分规则'),
    type: normalizeText(rule.type || rule.ruleType, 'KEYWORD').toUpperCase(),
    evidenceKey: normalizeText(rule.evidenceKey || rule.field || rule.ruleField, 'tender.searchText'),
    keywords: toArray(rule.keywords || rule.ruleBase || rule.values)
      .map((keyword) => normalizeText(keyword))
      .filter(Boolean),
    minValue: rule.minValue ?? '',
    maxValue: rule.maxValue ?? '',
    enabled: Boolean(rule.enabled ?? true),
    weight: toNumber(rule.weight, 0),
  }
}

export function normalizeEditableDimension(dimension = {}) {
  const key = normalizeText(dimension.key || dimension.code || dimension.id || makeKey('dimension'))
  return {
    key,
    code: key,
    name: normalizeText(dimension.name || dimension.label || dimension.title, '新评分维度'),
    enabled: Boolean(dimension.enabled ?? true),
    weight: toNumber(dimension.weight, 0),
    rules: toArray(dimension.rules || dimension.criteria).map(normalizeEditableRule),
  }
}

export function createDefaultModel() {
  return {
    id: null,
    name: '投标匹配评分模型',
    version: '',
    enabled: true,
    active: false,
    status: 'INACTIVE',
    description: '',
    dimensions: [],
    validationErrors: [],
  }
}

export function validateEnabledWeightTotal(dimensions = []) {
  const enabledDimensions = toArray(dimensions).filter((dimension) => dimension.enabled)
  const total = enabledDimensions.reduce((sum, dimension) => sum + toNumber(dimension.weight, 0), 0)
  const roundedTotal = Math.round(total * 100) / 100

  if (enabledDimensions.length === 0) {
    return { valid: false, total: 0, message: '至少启用一个评分维度。' }
  }
  if (roundedTotal !== 100) {
    return {
      valid: false,
      total: roundedTotal,
      message: `启用维度权重总和必须为 100%，当前为 ${roundedTotal}%。`,
    }
  }
  return { valid: true, total: roundedTotal, message: '' }
}

export function validateEditableModel(model = {}) {
  const weightValidation = validateEnabledWeightTotal(model.dimensions)
  if (!weightValidation.valid) return weightValidation

  for (const dimension of toArray(model.dimensions).filter((item) => item.enabled)) {
    const enabledRules = toArray(dimension.rules).filter((rule) => rule.enabled)
    if (!normalizeText(dimension.name)) {
      return { valid: false, total: weightValidation.total, message: '启用维度名称不能为空。' }
    }
    if (enabledRules.length === 0) {
      return { valid: false, total: weightValidation.total, message: `维度「${dimension.name}」至少启用一个评分规则。` }
    }
    const ruleTotal = enabledRules.reduce((sum, rule) => sum + toNumber(rule.weight, 0), 0)
    if (Math.round(ruleTotal * 100) / 100 !== 100) {
      return { valid: false, total: weightValidation.total, message: `维度「${dimension.name}」的规则权重总和必须为 100%。` }
    }
    const invalidRule = enabledRules.find((rule) => !normalizeText(rule.name) || !normalizeText(rule.evidenceKey))
    if (invalidRule) {
      return { valid: false, total: weightValidation.total, message: `维度「${dimension.name}」存在未完整配置的规则。` }
    }
    const invalidKeywordRule = enabledRules.find((rule) => rule.type === 'KEYWORD' && rule.keywords.length === 0)
    if (invalidKeywordRule) {
      return { valid: false, total: weightValidation.total, message: `规则「${invalidKeywordRule.name}」至少需要一个关键词。` }
    }
    const invalidNumberRule = enabledRules.find((rule) => ['QUANTITY', 'RANGE'].includes(rule.type) && rule.minValue === '')
    if (invalidNumberRule) {
      return { valid: false, total: weightValidation.total, message: `规则「${invalidNumberRule.name}」需要填写最小值。` }
    }
    const invalidRangeRule = enabledRules.find((rule) => rule.type === 'RANGE' && rule.maxValue === '')
    if (invalidRangeRule) {
      return { valid: false, total: weightValidation.total, message: `规则「${invalidRangeRule.name}」需要填写最大值。` }
    }
  }
  return weightValidation
}

const normalizeEditableModel = (model = {}) => ({
  id: model.id ?? null,
  name: normalizeText(model.name || model.modelName, '投标匹配评分模型'),
  version: normalizeText(model.version || model.modelVersion),
  enabled: Boolean(model.enabled ?? true),
  active: Boolean(model.active ?? model.isActive),
  status: normalizeText(model.status, 'INACTIVE'),
  description: normalizeText(model.description || model.summary),
  validationErrors: toArray(model.validationErrors).map((error) => normalizeText(error)).filter(Boolean),
  dimensions: toArray(model.dimensions || model.dimensionConfigs).map(normalizeEditableDimension),
})

const buildSavePayload = (model) => ({
  ...model,
  dimensions: toArray(model.dimensions).map(normalizeEditableDimension),
})

export function useBidMatchScoringSettings() {
  const loading = ref(false)
  const saving = ref(false)
  const activating = ref(false)
  const models = ref([])
  const currentModel = ref(createDefaultModel())

  const weightValidation = computed(() => validateEditableModel(currentModel.value))
  const enabledDimensionCount = computed(() => currentModel.value.dimensions.filter((dimension) => dimension.enabled).length)

  const setCurrentModel = (model) => {
    const normalized = normalizeEditableModel(model)
    currentModel.value = {
      ...createDefaultModel(),
      ...normalized,
      dimensions: normalized.dimensions,
    }
  }

  const load = async () => {
    loading.value = true
    try {
      const result = await bidMatchScoringApi.getModels()
      if (!result?.success) throw new Error(result?.msg || '加载投标匹配评分模型失败')
      models.value = toArray(result.data).map(normalizeEditableModel)
      setCurrentModel(models.value.find((model) => model.active) || models.value[0] || createDefaultModel())
    } catch (error) {
      ElMessage.error(error?.message || '加载投标匹配评分模型失败')
    } finally {
      loading.value = false
    }
  }

  const save = async () => {
    const validation = weightValidation.value
    if (!validation.valid) {
      ElMessage.error(validation.message)
      return
    }

    saving.value = true
    try {
      const result = await bidMatchScoringApi.saveModel(buildSavePayload(currentModel.value))
      if (!result?.success) throw new Error(result?.msg || '保存投标匹配评分模型失败')
      const savedModel = normalizeEditableModel({ ...currentModel.value, ...result.data })
      setCurrentModel(savedModel)
      models.value = [savedModel, ...models.value.filter((model) => model.id !== savedModel.id)]
      ElMessage.success('投标匹配评分模型已保存')
    } catch (error) {
      ElMessage.error(error?.message || '保存投标匹配评分模型失败')
    } finally {
      saving.value = false
    }
  }

  const activateCurrentModel = async () => {
    if (!currentModel.value.id) {
      ElMessage.error('请先保存模型再设为当前')
      return
    }

    activating.value = true
    try {
      const result = await bidMatchScoringApi.activateModel(currentModel.value.id)
      if (!result?.success) throw new Error(result?.msg || '启用投标匹配评分模型失败')
      currentModel.value.active = true
      currentModel.value.status = 'ACTIVE'
      models.value = models.value.map((model) => ({
        ...model,
        active: model.id === currentModel.value.id,
        status: model.id === currentModel.value.id ? 'ACTIVE' : 'INACTIVE',
      }))
      ElMessage.success('投标匹配评分模型已设为当前')
    } catch (error) {
      ElMessage.error(error?.message || '启用投标匹配评分模型失败')
    } finally {
      activating.value = false
    }
  }

  const addDimension = () => {
    currentModel.value.dimensions.push(normalizeEditableDimension({
      key: makeKey('dimension'),
      name: '新评分维度',
      weight: 0,
      rules: [],
    }))
  }

  const removeDimension = (key) => {
    currentModel.value.dimensions = currentModel.value.dimensions.filter((dimension) => dimension.key !== key)
  }

  const addRule = (dimension) => {
    dimension.rules.push(normalizeEditableRule({
      key: makeKey('rule'),
      name: '新评分规则',
      type: 'KEYWORD',
      evidenceKey: 'tender.searchText',
      weight: 0,
      keywords: [],
    }))
  }

  const removeRule = (dimension, ruleKey) => {
    dimension.rules = dimension.rules.filter((rule) => rule.key !== ruleKey)
  }

  return {
    loading,
    saving,
    activating,
    models,
    currentModel,
    weightValidation,
    enabledDimensionCount,
    load,
    save,
    activateCurrentModel,
    addDimension,
    removeDimension,
    addRule,
    removeRule,
  }
}
