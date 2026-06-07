// Input: optional tender document file and project/tender identifiers
// Output: DocInsight parse state and action for project creation conversion
// Pos: src/composables/projectDetail/ - Project conversion evidence extraction
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref } from 'vue'
import { httpClient } from '@/api'

function getResponseData(response) {
  return response?.data ?? null
}

function resolveFile(candidate) {
  if (candidate instanceof File || candidate instanceof Blob) return candidate
  return candidate?.raw instanceof File || candidate?.raw instanceof Blob ? candidate.raw : null
}

function normalizeParseResult(result) {
  if (!result) return null
  return {
    document: {
      id: result.documentId,
      extractedText: result.rawMarkdown || ''
    },
    requirementProfile: {
      projectName: result.extractedData?.projectName || '',
      purchaserName: result.extractedData?.purchaserName || '',
      budget: result.extractedData?.budget || null,
      items: Array.isArray(result.requirements) ? result.requirements : []
    },
    warnings: Array.isArray(result.warnings) ? result.warnings : []
  }
}

export function useProjectConversion() {
  const showWorkbench = ref(false)
  const isParsing = ref(false)
  const parseResult = ref(null)

  const parseTender = async (projectId, tenderDocument) => {
    const file = resolveFile(tenderDocument)
    if (!projectId || !file) return null

    isParsing.value = true
    try {
      const formData = new FormData()
      formData.set('profile', 'TENDER')
      formData.set('entityId', String(projectId))
      formData.set('file', file, file.name || 'tender-document')

      const response = await httpClient.post('/api/doc-insight/parse', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 120000
      })
      const normalized = normalizeParseResult(getResponseData(response))
      parseResult.value = normalized
      showWorkbench.value = Boolean(normalized)
      return normalized
    } finally {
      isParsing.value = false
    }
  }

  return {
    showWorkbench,
    isParsing,
    parseResult,
    parseTender
  }
}
