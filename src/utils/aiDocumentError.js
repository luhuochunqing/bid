// Input: AI 文档解析/核查错误字符串
// Output: AI文档与实际评分的用户文案；技术诊断不透传至提示
// Pos: src/utils/ - AI 文档错误语义归一化

const TECHNICAL_DETAIL_PATTERN = /[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+|[0-9a-f]{8}-[0-9a-f-]{27,}|https?:\/\/|(?:java\.|Exception\b|\/opt\/|\/tmp\/)/i

function userMessage(raw, fallback) {
  return /[\u3400-\u9fff]/u.test(raw) && !TECHNICAL_DETAIL_PATTERN.test(raw) ? raw : fallback
}

const SCANNED_DOCUMENT_PATTERN = /low_text_density|扫描件|扫描版\s*pdf|无文本层|无可复制文本|未识别到可提取文本|需要\s*ocr/i

export function getAiDocumentErrorPresentation(error, fallback = 'AI 处理未完成，请稍后重试') {
  const raw = String(error || '').trim()
  const scanned = SCANNED_DOCUMENT_PATTERN.test(raw)

  if (scanned) {
    return {
      scanned: true,
      title: 'PDF 扫描件暂不支持识别',
      message: '当前文件是图片扫描件或没有可复制文本，AI 暂时无法识别。请上传文本型 PDF，或先完成 OCR 后重新提交。',
      detail: raw,
    }
  }

  return {
    scanned: false,
    title: 'AI 处理未完成',
    message: userMessage(raw, fallback),
    detail: '',
  }
}

const ACTUAL_SCORING_FAILURE_MESSAGES = {
  SCORING_EXTRACTION_FAILED: '投标文件未能完整读取，本次打分未完成。请稍后重试；如仍失败，请联系管理员检查文件识别服务。',
  BID_OCR_TIMEOUT: '投标文件文字识别超时，本次打分未完成。请稍后重试；如仍失败，请联系管理员检查处理资源。',
  BID_OCR_UNAVAILABLE: '投标文件识别服务暂不可用，请稍后重试；如仍失败，请联系管理员。',
  BID_OCR_EMPTY: '未能从投标文件识别出有效文字，请联系管理员核查识别结果。',
  SCORING_ITEM_FAILED: '部分评分项未能完成打分，请查看逐项结果并重试失败项。',
  SCORING_COVERAGE_UNKNOWN: '暂时无法确认投标文件是否读取完整，本次打分未完成，请稍后重试。',
  SCORING_REQUIRES_ALL: '原件已变化或来源未知，请点击「重新打分」全量重新评分；本次局部请求未扩大范围',
  REPORT_TOO_LARGE: '评分结果内容过多，暂时无法保存，请联系管理员处理。',
}

/** Actual scoring supports OCR: never reuse the legacy "scanned PDF unsupported" advice. */
export function getActualScoringFailureMessage(error) {
  const raw = String(error?.task?.errorMessage || error?.response?.data?.msg
    || error?.response?.data?.message || error?.message || '').trim()
  const code = error?.task?.errorCode || raw
  return Object.hasOwn(ACTUAL_SCORING_FAILURE_MESSAGES, code) ? ACTUAL_SCORING_FAILURE_MESSAGES[code]
    : userMessage(raw, '本次打分未完成，请稍后重试；如仍失败，请联系管理员。')
}
