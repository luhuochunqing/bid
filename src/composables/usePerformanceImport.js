import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import performanceApi from '@/api/modules/performance.js'

export function usePerformanceImport(loadData) {
  const importVisible = ref(false)
  const importStep = ref(0)
  const importFile = ref(null)
  const importLoading = ref(false)
  const importResult = ref({ successCount: 0, failureCount: 0, failures: [] })

  const openImport = () => {
    importVisible.value = true
    importStep.value = 0
    importFile.value = null
  }

  const downloadTemplate = async () => {
    try {
      await performanceApi.downloadTemplate()
      ElMessage.success('模板下载成功')
    } catch {
      ElMessage.error('模板下载失败')
    }
  }

  const onImportFileChange = (uploadFile) => {
    importFile.value = uploadFile.raw
  }

  const confirmImport = async () => {
    if (!importFile.value) return
    importLoading.value = true
    try {
      const res = await performanceApi.batchImport(importFile.value)
      importResult.value = res.data || { successCount: 0, failureCount: 0, failures: [] }
      importStep.value = 2
      loadData()
    } catch (e) {
      ElMessage.error(e.message || '导入失败')
    } finally {
      importLoading.value = false
    }
  }

  const closeImport = () => {
    importVisible.value = false
    importStep.value = 0
  }

  return {
    importVisible, importStep, importFile, importLoading, importResult,
    openImport, downloadTemplate, onImportFileChange, confirmImport, closeImport
  }
}
