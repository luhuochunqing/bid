// CO-155 refactor: 把 QualFormDialog 中 onCertFileSelect 的 AI 解析逻辑抽出。
// 单独可测 + 降低 QualFormDialog.vue 行数（line-budget 约束）。

import { ref } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import http from '@/api/client'

/**
 * Manage AI parsing of a certificate file.
 * Returns parsingAi ref + an onCertFileSelect handler that triggers parsing on file select.
 *
 * @param {import('vue').Ref<number>} maxBytes - reactive max file size, e.g. ref(50 * 1024 * 1024)
 * @param {import('vue').Ref<File|null>} certFile - reactive file ref to populate
 * @param {(parsed: object) => void} applyParsed - callback invoked with parsed DTO
 * @returns {{ parsingAi: import('vue').Ref<boolean>, onCertFileSelect: (uploadFile: any) => Promise<void> }}
 */
export function useCertAiParser(maxBytes, certFile, applyParsed) {
  const parsingAi = ref(false)

  async function onCertFileSelect(uploadFile) {
    if (!uploadFile?.raw) return
    if (uploadFile.raw.size > maxBytes.value) {
      ElMessage.error(`附件不能超过${Math.round(maxBytes.value / 1024 / 1024)}MB`)
      return
    }
    certFile.value = uploadFile.raw
    parsingAi.value = true
    ElMessage.info('AI 正在全息解析证书内容...')
    const fd = new FormData()
    fd.append('file', uploadFile.raw)
    try {
      const resp = await http.post('/api/knowledge/qualifications/upload-parse', fd, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      if (resp?.code === 200 && resp.data) {
        applyParsed(resp.data)
        ElNotification({ title: 'AI 提取成功', message: '已自动填入证书特征与有效期等字段', type: 'success' })
      }
    } catch {
      // AI 失败不阻塞提交 — 用户可手动填写
      ElMessage.warning('AI解析失败，您可以手动填写')
    } finally {
      parsingAi.value = false
    }
  }

  return { parsingAi, onCertFileSelect }
}
