// Input: httpClient and bid match scoring endpoint payloads
// Output: bidMatchScoringApi plus model/rule and score normalizers
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

const toArray = (value) => {
  if (Array.isArray(value)) return value
  if (value == null || value === '') return []
  return [value]
}

const toFiniteNumber = (value, fallback = 0) => {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : fallback
}

const clampScore = (value) => Math.max(0, Math.min(100, Math.round(toFiniteNumber(value, 0))))

const normalizeText = (value, fallback = '') => String(value ?? fallback).trim()

const normalizeEvidence = (item = {}) => {
  if (typeof item === 'string') {
    return { title: item, content: '', source: '' }
  }

  return {
    title: normalizeText(item.title || item.name || item.label || item.source, '评分证据'),
    content: normalizeText(item.content || item.description || item.text),
    source: normalizeText(item.source || item.sourceName || item.url),
  }
}

const tryParseJson = (value, fallback = null) => {
  if (!value || typeof value !== 'string') return fallback
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

export const normalizeScoringRule = (rule = {}) => {
  const key = normalizeText(rule.key || rule.code || rule.id || rule.ruleKey || rule.name)
  const type = normalizeText(rule.type || rule.ruleType, 'KEYWORD').toUpperCase()

  return {
    ...rule,
    key,
    code: key,
    name: normalizeText(rule.name || rule.label || rule.title || key, '未命名规则'),
    type,
    evidenceKey: normalizeText(rule.evidenceKey || rule.field || rule.ruleField),
    keywords: toArray(rule.keywords || rule.ruleBase || rule.values)
      .map((keyword) => normalizeText(keyword))
      .filter(Boolean),
    minValue: rule.minValue ?? rule.minimum ?? null,
    maxValue: rule.maxValue ?? rule.maximum ?? null,
    enabled: Boolean(rule.enabled ?? true),
    weight: toFiniteNumber(rule.weight, 0),
    matched: Boolean(rule.matched),
    status: normalizeText(rule.status),
    score: clampScore(rule.score ?? 0),
  }
}

export const normalizeScoringDimension = (dimension = {}) => {
  const key = normalizeText(
    dimension.key || dimension.code || dimension.id || dimension.dimensionKey || dimension.dimension || dimension.name,
  )
  const name = normalizeText(
    dimension.name || dimension.label || dimension.title || dimension.dimensionName || dimension.dimension || key,
    '未命名维度',
  )
  const rules = toArray(dimension.rules || dimension.ruleScores || dimension.criteria).map(normalizeScoringRule)
  const ruleBase = toArray(dimension.ruleBase || dimension.ruleFields)
    .map((rule) => normalizeText(rule?.name || rule?.label || rule))
    .filter(Boolean)

  return {
    ...dimension,
    key,
    name,
    enabled: Boolean(dimension.enabled ?? true),
    weight: toFiniteNumber(dimension.weight, 0),
    score: clampScore(dimension.score ?? dimension.percentage ?? dimension.value ?? 0),
    maxScore: toFiniteNumber(dimension.maxScore, 100),
    ruleBase: ruleBase.length ? ruleBase : rules.flatMap((rule) => rule.keywords).filter(Boolean),
    ruleField: normalizeText(dimension.ruleField || dimension.field),
    ruleOperator: normalizeText(dimension.ruleOperator || dimension.operator),
    ruleValue: normalizeText(dimension.ruleValue ?? dimension.valueText ?? ''),
    rules,
    description: normalizeText(dimension.description || dimension.comment || dimension.comments),
    suggestion: normalizeText(dimension.suggestion || dimension.recommendation),
    evidence: toArray(dimension.evidence || dimension.evidences || dimension.sources || dimension.sourceReferences)
      .map(normalizeEvidence),
  }
}

export const normalizeMatchScoringModel = (model = {}) => ({
  ...model,
  id: model.id ?? model.modelId ?? null,
  name: normalizeText(model.name || model.modelName || model.title, '投标匹配评分模型'),
  version: normalizeText(model.version || model.modelVersion || model.activeVersionNo),
  enabled: normalizeText(model.status).toUpperCase() !== 'INACTIVE' && Boolean(model.enabled ?? true),
  active: Boolean(model.active ?? model.isActive ?? normalizeText(model.status).toUpperCase() === 'ACTIVE'),
  status: normalizeText(model.status || (model.active ? 'ACTIVE' : 'INACTIVE'), 'INACTIVE'),
  description: normalizeText(model.description || model.summary),
  dimensions: toArray(model.dimensions || model.dimensionConfigs).map(normalizeScoringDimension),
  updatedAt: model.updatedAt || model.modifiedAt || '',
  validationErrors: toArray(model.validationErrors).map((item) => normalizeText(item)).filter(Boolean),
})

const normalizeStatus = (status) => {
  const normalized = normalizeText(status).toUpperCase()
  if (['COMPLETED', 'SUCCESS', 'READY', 'DONE'].includes(normalized)) return 'READY'
  if (['RUNNING', 'PENDING', 'GENERATING', 'PROCESSING'].includes(normalized)) return 'GENERATING'
  if (['FAILED', 'ERROR'].includes(normalized)) return 'FAILED'
  if (['NOT_CONFIGURED', 'CONFIG_MISSING', 'NO_MODEL'].includes(normalized)) return 'NOT_CONFIGURED'
  if (['EMPTY', 'NONE', 'NO_SCORE'].includes(normalized)) return 'EMPTY'
  return normalized || 'EMPTY'
}

export const normalizeMatchScore = (score = null) => {
  if (!score) return null

  const modelSnapshot = tryParseJson(score.modelSnapshotJson)
  const model = modelSnapshot?.model || score.model
  const totalScore = clampScore(score.totalScore ?? score.overallScore ?? score.score ?? score.winScore ?? 0)
  const rawDimensions = score.dimensions || score.dimensionScores || score.dimensionResults

  return {
    ...score,
    id: score.id ?? score.scoreId ?? null,
    tenderId: score.tenderId ?? null,
    modelId: score.modelId ?? null,
    modelName: normalizeText(score.modelName || model?.name),
    modelVersion: normalizeText(score.modelVersion || score.version || score.modelVersionNo || modelSnapshot?.versionNo),
    status: normalizeStatus(score.status || score.state || score.resultStatus || (rawDimensions ? 'READY' : 'EMPTY')),
    totalScore,
    summary: normalizeText(score.summary || score.suggestion || score.recommendation),
    failureReason: normalizeText(score.failureReason || score.errorMessage || score.message),
    generatedAt: score.generatedAt || score.evaluatedAt || score.createdAt || score.updatedAt || '',
    stale: Boolean(score.stale),
    dimensions: toArray(rawDimensions).map(normalizeScoringDimension),
  }
}

const buildModelPayload = (model = {}) => ({
  id: model.id ?? null,
  name: normalizeText(model.name || model.modelName, '投标匹配评分模型'),
  description: normalizeText(model.description),
  dimensions: toArray(model.dimensions).map((dimension) => {
    const normalized = normalizeScoringDimension(dimension)
    return {
      code: normalized.key,
      name: normalized.name,
      enabled: normalized.enabled,
      weight: normalized.weight,
      rules: normalized.rules.map((rule) => ({
        code: rule.key,
        name: rule.name,
        type: rule.type,
        evidenceKey: rule.evidenceKey,
        keywords: rule.keywords,
        minValue: rule.minValue === '' ? null : rule.minValue,
        maxValue: rule.maxValue === '' ? null : rule.maxValue,
        weight: rule.weight,
        enabled: rule.enabled,
      })),
    }
  }),
})

export const bidMatchScoringApi = {
  async getModels() {
    const response = await httpClient.get('/api/bid-match/models')
    return {
      ...response,
      data: toArray(response?.data).map(normalizeMatchScoringModel),
    }
  },

  async saveModel(model) {
    const payload = buildModelPayload(model)
    const response = payload.id
      ? await httpClient.put('/api/bid-match/models', payload)
      : await httpClient.post('/api/bid-match/models', payload)
    return {
      ...response,
      data: normalizeMatchScoringModel(response?.data),
    }
  },

  async activateModel(modelId) {
    return httpClient.post(`/api/bid-match/models/${modelId}/activate`)
  },

  async generateScore(tenderId, payload = {}) {
    const response = await httpClient.post(`/api/tenders/${tenderId}/match-score/evaluate`, payload)
    return {
      ...response,
      data: normalizeMatchScore(response?.data),
    }
  },

  async getLatestScore(tenderId) {
    const response = await httpClient.get(`/api/tenders/${tenderId}/match-score/latest`)
    return {
      ...response,
      data: normalizeMatchScore(response?.data),
    }
  },

  async getScoreHistory(tenderId) {
    const response = await httpClient.get(`/api/tenders/${tenderId}/match-score/history`)
    return {
      ...response,
      data: toArray(response?.data).map(normalizeMatchScore),
    }
  },
}

export default bidMatchScoringApi
