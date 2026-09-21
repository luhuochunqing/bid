import { describe, expect, it } from 'vitest'
import { getAiDocumentErrorPresentation } from './aiDocumentError.js'

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
