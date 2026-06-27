// Input: projectId
// Output: exporting ref + exportDocumentsAsZip() — 拉取项目文档列表，逐个下载文件，前端打包为 zip 触发下载
// Pos: src/composables/projectDetail/ - 项目文档导出复用逻辑（DraftingStage / ClosureStage 共用，CO-378）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import JSZip from 'jszip'
import { getDocuments as getProjectDocuments } from '@/api/modules/projectDocuments.js'
import httpClient from '@/api/client.js'
import { triggerBlobDownload } from '@/utils/download.js'

/**
 * CO-378: 项目文档打包导出 composable
 *
 * 复用方：DraftingStage.vue（ProjectDocumentTable 的 @export）、ClosureStage.vue（文档导出按钮）。
 * 两个入口导出内容一致：项目文档表中的所有文件，压缩为 zip。
 *
 * @param {import('vue').Ref<string|number>|string|number|(() => string|number)} projectIdRef projectId，可为值/ref/getter 函数
 */
export function useProjectDocumentsExport(projectIdRef) {
  const exporting = ref(false)

  const resolveProjectId = () => {
    if (typeof projectIdRef === 'function') return projectIdRef()
    if (typeof projectIdRef === 'object' && projectIdRef !== null && 'value' in projectIdRef) {
      return projectIdRef.value
    }
    return projectIdRef
  }

  /**
   * 下载单个项目文档为 Blob。
   * 走 httpClient 以复用鉴权头与拦截器；路径与 ProjectDocumentTable.handleDownload 一致。
   */
  async function fetchDocumentBlob(projectId, documentId) {
    const url = `/api/projects/${projectId}/documents/${documentId}/download`
    const resp = await httpClient.get(url, {
      responseType: 'blob',
      timeout: 120000,
      skipGlobalErrorMessage: true,
    })
    return resp.data
  }

  /**
   * 处理 zip 内重名：同名文件追加序号后缀。
   * 例：a.pdf / a (1).pdf / a (2).pdf
   */
  function resolveUniqueName(used, name) {
    if (!used.has(name)) {
      used.add(name)
      return name
    }
    const dot = name.lastIndexOf('.')
    const base = dot > 0 ? name.slice(0, dot) : name
    const ext = dot > 0 ? name.slice(dot) : ''
    let i = 1
    let candidate = `${base} (${i})${ext}`
    while (used.has(candidate)) {
      i += 1
      candidate = `${base} (${i})${ext}`
    }
    used.add(candidate)
    return candidate
  }

  /**
   * 拉取项目文档列表并打包为 zip 下载。
   * @returns {Promise<void>}
   */
  async function exportDocumentsAsZip() {
    const projectId = resolveProjectId()
    if (!projectId) {
      ElMessage.warning('项目信息缺失，无法导出')
      return
    }

    exporting.value = true
    try {
      const resp = await getProjectDocuments(projectId)
      const documents = Array.isArray(resp?.data) ? resp.data : []
      if (documents.length === 0) {
        ElMessage.info('当前项目暂无文档，无法导出')
        return
      }

      const zip = new JSZip()
      const usedNames = new Set()
      let successCount = 0
      const failures = []

      for (const doc of documents) {
        const originalName = doc.name || `document-${doc.id}`
        try {
          const blob = await fetchDocumentBlob(projectId, doc.id)
          if (!blob) {
            failures.push(originalName)
            continue
          }
          const uniqueName = resolveUniqueName(usedNames, originalName)
          zip.file(uniqueName, blob)
          successCount += 1
        } catch {
          failures.push(originalName)
        }
      }

      if (successCount === 0) {
        ElMessage.error('文档导出失败：所有文件均下载失败，请稍后重试')
        return
      }

      const zipBlob = await zip.generateAsync({ type: 'blob', compression: 'DEFLATE' })
      const timestamp = new Date().toISOString().slice(0, 10).replace(/-/g, '')
      triggerBlobDownload(zipBlob, `项目文档_${projectId}_${timestamp}.zip`)

      if (failures.length > 0) {
        ElMessage.warning(`导出完成：成功 ${successCount} 个，失败 ${failures.length} 个（${failures.join('、')}）`)
      } else {
        ElMessage.success(`已导出 ${successCount} 个项目文档`)
      }
    } catch (e) {
      ElMessage.error(e?.response?.data?.msg || '文档导出失败，请稍后重试')
    } finally {
      exporting.value = false
    }
  }

  return { exporting, exportDocumentsAsZip }
}

export default useProjectDocumentsExport
