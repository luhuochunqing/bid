// Input: score facts and scheme metadata returned by the review API
// Output: user-facing Chinese labels and only readable source quotations
// Pos: Project score candidate review presentation boundary

export function scopeLabel(status) {
  if (status === 'OUT_OF_SCHEME') return '方案外'
  if (status === 'SOURCE_UNVERIFIED') return '来源未验证'
  return '资格未确认'
}

export function readableEvidences(fact) {
  return (fact?.evidences || []).filter((evidence) => evidence?.kind !== 'SCHEME_QUALIFICATION'
    && evidence?.kind !== 'ITEM_SCHEME_SCOPE_MISMATCH'
    && typeof evidence?.quotedText === 'string' && evidence.quotedText.trim())
}

export function validationMessages(fact) {
  const source = [fact?.validationStatus, fact?.validationMessage, fact?.fallbackReason]
    .filter(Boolean).join(' ')
  const messages = []
  if (source.includes('ITEM_SOURCE_SCOPE_MISMATCH')) {
    messages.push('评分项类别与引用的原文行暂时无法对应，请核对该评分项原文。')
  }
  if (source.includes('ITEM_WEIGHT_SOURCE_UNKNOWN')) {
    messages.push('评分权重已识别，但原文暂时无法单独确认该分值，请核对标书中的评分细则。')
  }
  if (source.includes('WEIGHT_SUM_MISMATCH')) {
    messages.push('候选评分项合计与原文声明总分存在差额，请核对是否有评分细则未识别。')
  }
  return messages.length ? messages : ['部分校验信息需要人工核对，请结合标书原文确认后再采纳。']
}

export function exclusionLabel(reason) {
  if (reason === 'ITEM_SCHEME_SCOPE_MISMATCH') return '无法确认属于当前评分方案'
  if (reason === 'SOURCE_SCHEME_NOT_VERIFIED') return '评分方案来源未能确认'
  return '来源资格未能确认'
}

export function scoreTypeLabel(scoreType) {
  if (scoreType === 'OBJECTIVE' || scoreType === '客观项') return '客观项'
  if (scoreType === 'SUBJECTIVE' || scoreType === '主观项') return '主观项'
  return '待确认'
}

export function schemeLabel(scheme, index) {
  return typeof scheme?.title === 'string' && scheme.title.trim()
    ? scheme.title.trim()
    : `评分方案${index + 1}`
}
