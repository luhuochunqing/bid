import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { collaborationApi } from '@/api'

function downloadTextFile(filename, content, mimeType = 'text/plain;charset=utf-8') {
  const blob = new Blob([content], { type: mimeType })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(link.href)
}

export function useDocumentExport({
  route,
  projectInfo,
  documentInfo,
  sectionData,
  isRemoteProjectId
}) {
  const exportHistory = ref([])
  const archiveHistory = ref([])

  async function loadExportArtifacts(projectId) {
    if (!isRemoteProjectId.value) {
      exportHistory.value = []
      archiveHistory.value = []
      return
    }

    try {
      const [exportResult, archiveResult] = await Promise.all([
        collaborationApi.exports.getExports(projectId),
        collaborationApi.exports.getArchiveRecords(projectId)
      ])
      exportHistory.value = Array.isArray(exportResult?.data) ? exportResult.data : []
      archiveHistory.value = Array.isArray(archiveResult?.data) ? archiveResult.data : []
    } catch (error) {
      exportHistory.value = []
      archiveHistory.value = []
    }
  }

  function handlePreview() {
    const previewContent = sectionData.value.sections
      .map((section) => `${section.name}\n${section.content || ''}`)
      .join('\n\n')
    downloadTextFile(`${projectInfo.value.name}_预览.txt`, previewContent)
    ElMessage.success('已生成本地预览文件')
  }

  function handleExport() {
    if (!isRemoteProjectId.value) {
      ElMessage.error('当前文档仅支持通过 API 导出')
      return
    }

    collaborationApi.exports.createExport(route.params.id, {
      format: 'json',
      exportedBy: null,
      exportedByName: '当前用户'
    }).then(async (result) => {
      if (!result?.success || !result?.data) {
        ElMessage.error(result?.msg || '导出失败')
        return
      }
      if (!result.data.content) {
        ElMessage.error('导出失败：后端未返回可下载内容')
        return
      }
      downloadTextFile(
        result.data.fileName,
        result.data.content || '',
        result.data.contentType || 'application/json;charset=utf-8'
      )
      await loadExportArtifacts(route.params.id)
      ElMessage.success('文档导出成功')
    }).catch(() => {
      ElMessage.error('导出失败')
    })
  }

  function handleArchive() {
        if (!isRemoteProjectId.value) {
      ElMessage.error('当前文档仅支持通过 API 归档')
      return
    }
    collaborationApi.exports.archive(route.params.id, {
      archivedBy: null,
      archivedByName: '当前用户',
      archiveReason: '标书编制完成，归档留存'
    }).then(async (result) => {
      if (!result?.success || !result?.data) {
        ElMessage.error(result?.msg || '归档失败')
        return
      }
      await loadExportArtifacts(route.params.id)
      ElMessage.success('文档归档成功')
    }).catch(() => {
      ElMessage.error('归档失败')
    })
  }

  return {
    exportHistory,
    archiveHistory,
    loadExportArtifacts,
    handlePreview,
    handleExport,
    handleArchive
  }
}
