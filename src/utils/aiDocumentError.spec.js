import { describe, expect, it } from 'vitest'
import { getAiDocumentErrorPresentation, getActualScoringFailureMessage } from './aiDocumentError.js'

describe('getAiDocumentErrorPresentation', () => {
  it('recognizes the backend scan-PDF/empty-text messages', () => {
    expect(getAiDocumentErrorPresentation('招标文件未识别到可提取文本，可能为扫描版 PDF，需要 OCR 识别')).toMatchObject({
      scanned: true,
      title: 'PDF 扫描件暂不支持识别',
    })
    expect(getAiDocumentErrorPresentation('warnings=[low_text_density]')).toMatchObject({ scanned: true })
  })

  it('keeps ordinary failures as ordinary failures', () => {
    expect(getAiDocumentErrorPresentation('解析服务超时')).toEqual({
      scanned: false,
      title: 'AI 处理未完成',
      message: '解析服务超时',
      detail: '',
    })
  })
})


it.each(['SCORING_EXTRACTION_FAILED', 'UNKNOWN_INTERNAL_FAILURE', 'HTTP 502', 'java.io.IOException: file=/opt/private.pdf'])('never displays technical error %s as a user message', (raw) => {
  const presentation = getAiDocumentErrorPresentation(raw, '本次处理未完成，请稍后重试')
  expect(presentation.message).toBe('本次处理未完成，请稍后重试')
  expect(presentation.detail).toBe('')
})


it.each([
  ['SCORING_EXTRACTION_FAILED', '完整读取'], ['BID_OCR_TIMEOUT', '文字识别超时'],
  ['BID_OCR_UNAVAILABLE', '识别服务暂不可用'], ['BID_OCR_EMPTY', '有效文字'],
  ['SCORING_ITEM_FAILED', '部分评分项'], ['SCORING_COVERAGE_UNKNOWN', '读取完整'],
  ['SCORING_REQUIRES_ALL', '全量重新评分'], ['REPORT_TOO_LARGE', '无法保存'],
])('maps actual-scoring reason %s to actionable Chinese', (code, meaning) => {
  const message = getActualScoringFailureMessage({ task: { errorCode: code, errorMessage: code } })
  expect(message).toContain(meaning)
  expect(message).not.toContain(code)
  expect(message).not.toContain('暂不支持识别')
})

it.each(['NEW_UNKNOWN_REASON', '识别错误：NEW_UNKNOWN_REASON', '任务 b5252e88-ca71-412d-a8d7-80ac6d337908 异常', 'java.io.IOException: 原件'])('hides unknown diagnostics in scoring message: %s', raw => {
  expect(getActualScoringFailureMessage({ message: raw })).toBe('本次打分未完成，请稍后重试；如仍失败，请联系管理员。')
})

it('retains a useful Chinese business rejection and uses response.message too', () => {
  const message = '请等待招标文件解析完成后再进行打分'
  expect(getActualScoringFailureMessage({ response: { data: { message } } })).toBe(message)
})
