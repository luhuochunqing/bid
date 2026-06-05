export function buildFeatureUnavailableResponse(feature, overrides = {}) {
  return {
    success: false,
    code: 'FEATURE_UNAVAILABLE',
    feature,
    message: overrides.message || '当前功能暂未接入',
    ...overrides,
  }
}

export function isFeatureUnavailableResponse(response) {
  return Boolean(response && (response.code === 'FEATURE_UNAVAILABLE' || response.feature))
}

export function getFeaturePlaceholder(response) {
  if (!response) return null

  return {
    feature: response.feature || '',
    title: response.title || '功能暂未接入',
    message: response.msg || '当前功能暂未开放，请先使用现有流程继续操作。',
    hint: response.hint || '',
    level: response.level || 'info',
  }
}
