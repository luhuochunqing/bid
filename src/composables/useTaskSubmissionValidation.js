export function validateSubmitForReview(payload = {}) {
  const { deliverables, deliverableFiles, hasDeliverable } = payload

  const hasDeliverableResult =
    (Array.isArray(deliverables) && deliverables.length > 0) ||
    (Array.isArray(deliverableFiles) && deliverableFiles.length > 0) ||
    hasDeliverable === true

  if (!hasDeliverableResult) {
    return { valid: false, message: '提交审核时必须上传交付物' }
  }

  return { valid: true, message: '' }
}
