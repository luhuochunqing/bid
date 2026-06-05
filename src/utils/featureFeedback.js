import { getFeaturePlaceholder, isFeatureUnavailableResponse } from '@/api/featureAvailability'

export function notifyFeatureUnavailable(response, options = {}) {
  if (!isFeatureUnavailableResponse(response)) {
    return null
  }

  const placeholder = getFeaturePlaceholder(response) || {}
  return {
    ...placeholder,
    ...(options.fallback || {}),
    level: options.level || placeholder.level || 'info',
  }
}

export default notifyFeatureUnavailable
