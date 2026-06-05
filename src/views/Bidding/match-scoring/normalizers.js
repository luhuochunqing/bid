const toArray = (value) => {
  if (Array.isArray(value)) return value
  if (value == null || value === '') return []
  return [value]
}

const toNumber = (value, fallback = 0) => {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : fallback
}

const clampScore = (value) => Math.max(0, Math.min(100, Math.round(toNumber(value, 0))))

const normalizeText = (value, fallback = '') => String(value ?? fallback).trim()

export function getScoreTone(score) {
  const value = clampScore(score)
  if (value >= 90) return 'excellent'
  if (value >= 70) return 'good'
  if (value >= 40) return 'warning'
  return 'danger'
}

export function getScoreTagType(score) {
  const tone = getScoreTone(score)
  if (tone === 'excellent' || tone === 'good') return 'success'
  if (tone === 'warning') return 'warning'
  return 'danger'
}

export function normalizeEvidenceItem(item = {}) {
  if (typeof item === 'string') {
    return { title: item, content: '', source: '' }
  }

  return {
    title: normalizeText(item.title || item.name || item.label || item.source, '评分证据'),
    content: normalizeText(item.content || item.description || item.text),
    source: normalizeText(item.source || item.sourceName || item.url),
  }
}

const normalizeRuleEvidence = (rule = {}) => {
  const statusText = {
    MATCHED: '命中',
    UNMATCHED: '未命中',
    MISSING: '缺少证据',
  }[normalizeText(rule.status).toUpperCase()] || normalizeText(rule.status, '待评估')

  return {
    title: normalizeText(rule.name || rule.code, '评分规则'),
    content: `${statusText}，权重 ${toNumber(rule.weight, 0)}%，得分 ${clampScore(rule.score)} 分`,
    source: normalizeText(rule.evidenceKey),
  }
}

export function normalizeDimensionForView(dimension = {}) {
  const key = normalizeText(
    dimension.key || dimension.code || dimension.id || dimension.dimensionKey || dimension.dimension || dimension.name,
  )
  const name = normalizeText(
    dimension.name || dimension.label || dimension.title || dimension.dimensionName || dimension.dimension || key,
    '未命名维度',
  )
  const score = clampScore(dimension.score ?? dimension.percentage ?? dimension.value ?? 0)
  const weight = toNumber(dimension.weight, 0)
  const directEvidence = toArray(dimension.evidence || dimension.evidences || dimension.sources || dimension.sourceReferences)
    .map(normalizeEvidenceItem)
  const ruleEvidence = toArray(dimension.rules || dimension.ruleScores)
    .map(normalizeRuleEvidence)

  return {
    key,
    name,
    enabled: Boolean(dimension.enabled ?? true),
    score,
    percentage: score,
    weight,
    weightText: `${weight}%`,
    tone: getScoreTone(score),
    tagType: getScoreTagType(score),
    description: normalizeText(dimension.description || dimension.comment || dimension.comments),
    suggestion: normalizeText(dimension.suggestion || dimension.recommendation),
    evidence: directEvidence.length ? directEvidence : ruleEvidence,
  }
}

const normalizeStatus = (status) => {
  const value = normalizeText(status).toUpperCase()
  if (['COMPLETED', 'SUCCESS', 'READY', 'DONE'].includes(value)) return 'READY'
  if (['RUNNING', 'PENDING', 'GENERATING', 'PROCESSING'].includes(value)) return 'GENERATING'
  if (['FAILED', 'ERROR'].includes(value)) return 'FAILED'
  if (['NOT_CONFIGURED', 'CONFIG_MISSING', 'NO_MODEL'].includes(value)) return 'NOT_CONFIGURED'
  if (['EMPTY', 'NONE', 'NO_SCORE'].includes(value)) return 'EMPTY'
  return value || 'EMPTY'
}

export function normalizeMatchScoreForView(score = null) {
  if (!score) return null
  const dimensions = toArray(score.dimensionSummaries || score.dimensions || score.dimensionScores || score.dimensionResults)
    .map(normalizeDimensionForView)
    .filter((dimension) => dimension.enabled)

  return {
    ...score,
    id: score.id ?? score.scoreId ?? null,
    totalScore: clampScore(score.totalScore ?? score.overallScore ?? score.score ?? score.winScore ?? 0),
    status: normalizeStatus(score.status || score.state || score.resultStatus || (dimensions.length ? 'READY' : 'EMPTY')),
    modelName: normalizeText(score.modelName || score.model?.name),
    modelVersion: normalizeText(score.modelVersion || score.version || score.model?.version),
    summary: normalizeText(score.summary || score.suggestion || score.recommendation),
    failureReason: normalizeText(score.failureReason || score.errorMessage || score.message),
    generatedAt: score.generatedAt || score.evaluatedAt || score.createdAt || score.updatedAt || '',
    stale: Boolean(score.stale),
    dimensionSummaries: dimensions,
  }
}

export function summarizeScoreState({ loading = false, generating = false, error = '', score = null } = {}) {
  if (loading) {
    return { state: 'loading', text: '正在加载评分', description: '正在读取最新真实评分。', actionText: '' }
  }
  if (generating) {
    return { state: 'generating', text: '正在生成评分', description: '正在基于当前模型生成真实评分。', actionText: '' }
  }
  if (error) {
    return { state: 'error', text: '评分加载失败', description: error, actionText: '重新加载' }
  }
  if (!score || score.status === 'EMPTY') {
    return { state: 'empty', text: '暂无评分', description: '当前标讯暂无真实匹配评分。', actionText: '生成匹配评分' }
  }
  if (score.status === 'NOT_CONFIGURED') {
    return {
      state: 'not-configured',
      text: '配置缺失',
      description: '请先在系统设置中启用投标匹配评分模型。',
      actionText: '前往配置',
    }
  }
  if (score.status === 'FAILED') {
    return {
      state: 'failed',
      text: '评分生成失败',
      description: score.failureReason || '请检查评分模型配置后重新生成。',
      actionText: '重新生成',
    }
  }
  return { state: 'ready', text: '真实评分已生成', description: score.summary, actionText: '重新生成' }
}

export function buildScoreFromAnalysis(analysisData = null) {
  if (!analysisData) return null
  return normalizeMatchScoreForView({
    id: `analysis-${analysisData.tenderId || 'current'}`,
    status: 'READY',
    totalScore: analysisData.winScore,
    summary: analysisData.suggestion,
    modelName: 'AI 分析结果',
    dimensions: toArray(analysisData.dimensionScores).map((dimension) => ({
      ...dimension,
      evidence: [
        ...(dimension.description ? [{ title: '评估说明', content: dimension.description }] : []),
        ...(dimension.suggestion ? [{ title: '改进建议', content: dimension.suggestion }] : []),
      ],
    })),
  })
}
