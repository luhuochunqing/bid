// Input: frozen extraction DTO values; Output: Chinese evidence and state labels
// Pos: projectDetail presentation helpers; no computed business eligibility
export const scoreNumber = value => value == null || value === '' ? '待确认' : String(value)
const reasons = {
  SOURCE_SCORE_ROW_UNCOVERED: '原文评分表仍有未完整覆盖的评分行，请对照招标文件复核。',
  FALLBACK_SCHEME_CONFLICT: '不同提取结果的方案总分存在冲突，请对照原文确认。',
  SOURCE_RECHECK_LIMIT_REMAINING: '部分原文评分内容尚未完成复核，请对照招标文件检查。',
  OUT_OF_SCHEME: '方案外内容，不计分', SCHEME_UNRESOLVED: '所属评分方案待确认',
  NOT_EFFECTIVE_SCORING_UNIT: '结构汇总或其他非计分单元，不重复计分',
  FULL_RULE_UNIT_UNRESOLVED: '本项完整原文边界待核对', WEIGHT_UNKNOWN: '最大分未知',
  WEIGHT_INVALID: '最大分数值无效', WEIGHT_PRECISION_UNSUPPORTED: '分值精度暂不支持计算',
  RATIO_UNRESOLVED: '比例换算依据待确认，暂不可计算',
  PARENT_STRUCTURE_UNRESOLVED: '父子计分关系待确认', CHILD_AGGREGATION_UNSUPPORTED: '父子合计方式待确认',
  DUPLICATE_SOURCE_UNIT: '同一原文计分单元存在多个候选', DUPLICATE_FACT_ID: '候选身份重复',
  DUPLICATE_ITEM: '存在重复候选项', DUPLICATE_WEIGHT_CONFLICT: '重复候选分值冲突',
  ITEM_WEIGHT_SOURCE_MISMATCH: '候选分值与原文依据不一致',
  PARENT_WEIGHT_MISMATCH: '父子分值核验有差异，请查看原文',
  ALTERNATIVE_EXTRACTION_SET_NOT_SELECTED: '其他提取路径的候选，仅供核对，不计分',
  LEGACY_PAYLOAD_MISSING: '历史提取缺少完整版本记录，来源未核验',
  SOURCE_SPAN_UNRESOLVED: '原文位置尚未核验', SOURCE_UNAVAILABLE: '原文来源暂不可用',
}
export function extractionReason(code) {
  const [key, page, cause] = String(code || '').split(':')
  if (key === 'BAIDU_OCR_PAGE_FAILED') {
    const location = /^\d+$/.test(page) ? `第 ${page} 页` : '部分页面'
    if (cause === 'PROVIDER_ERROR_6') return `${location}表格识别失败：百度应用未开通表格文字识别 V2 权限，请联系管理员开启后重新解析。`
    if (['HTTP_401', 'HTTP_403', 'PROVIDER_ERROR_110', 'PROVIDER_ERROR_111'].includes(cause)) return `${location}表格识别鉴权失败，请联系管理员检查百度 OCR 凭证和权限后重新解析。`
    return `${location}表格识别未完成，请检查 OCR 服务后重新解析，并对照原文复核候选。`
  }
  if (key === 'BAIDU_OCR_ENRICHMENT_FAILED') return '招标文件表格识别未完成，请检查文件及 OCR 服务后重新解析。'
  return Object.hasOwn(reasons, key) ? reasons[key] : '该项来源或规则仍有待核对的问题，请结合原文检查'
}

export function sourceDiagnosticsFromSummary(summary) {
  try {
    const value = typeof summary === 'string' ? JSON.parse(summary) : summary
    return Array.isArray(value?.sourceDiagnostics) ? value.sourceDiagnostics.filter(note => typeof note === 'string') : []
  } catch { return [] }
}
export function extractionTaskLabel(status) {
  return ({ COMPLETED: '提取完成', PROCESSING: '解析中', PENDING: '等待解析', FAILED: '解析失败' })[status] || '状态待确认'
}
