// Input: AI 文档解析/核查错误字符串
// Output: 扫描件专用前台提示语或普通错误展示数据
// Pos: src/utils/ - AI 文档错误语义归一化

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
    message: raw || fallback,
    detail: '',
  }
}
