import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/client'

export function useQualificationBatch({ fetchQualifications }) {
  const tableRef = ref(null)
  const selectedRows = ref([])
  const selectedCount = computed(() => selectedRows.value.length)
  const hasSelection = computed(() => selectedCount.value > 0)

  const handleSelectionChange = (rows) => { selectedRows.value = rows || [] }

  // 导入结果报告
  const importResultVisible = ref(false)
  const importResultData = ref({ total: 0, success: 0, failed: 0, errors: [] })

  const importUploadRef = ref(null)
  const importTriggerRef = ref(null)
  const handleImportLedgerClick = () => { importTriggerRef.value?.$el?.click() }
  const handleImportChange = (file) => {
    const formData = new FormData()
    formData.append('file', file.raw)
    http.post('/api/knowledge/qualifications/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
      .then((res) => {
        const data = res?.data?.data || {}
        importResultData.value = {
          total: data.total || 0,
          success: data.success || 0,
          failed: data.failed || 0,
          errors: Array.isArray(data.errors) ? data.errors : []
        }
        importResultVisible.value = true
        if (data.success > 0) fetchQualifications()
      })
      .catch(() => ElMessage.error('导入台账失败'))
  }
  const handleImportResultClosed = () => {
    importResultVisible.value = false
    fetchQualifications()
  }

  // 批量关联附件结果
  const attachResultVisible = ref(false)
  const attachResultData = ref({ total: 0, success: 0, failed: 0, matched: [], unmatched: [] })

  // 批量关联附件结果
  const attachResultVisible = ref(false)
  const attachResultData = ref({ total: 0, success: 0, failed: 0, matched: [], unmatched: [] })

  const batchAttachUploadRef = ref(null)
  const batchAttachTriggerRef = ref(null)
  const handleBatchUploadClick = () => { batchAttachTriggerRef.value?.$el?.click() }
  const handleBatchAttachChange = (file) => {
    const files = file.raw ? [file.raw] : []
    if (!files.length) return
    const formData = new FormData()
    files.forEach((f) => formData.append('files', f))
    http.post('/api/knowledge/qualifications/batch-attach', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
      .then((res) => {
        const data = res?.data?.data || {}
        attachResultData.value = {
          total: data.total || 0,
          success: data.success || 0,
          failed: data.failed || 0,
          matched: Array.isArray(data.matched) ? data.matched : [],
          unmatched: Array.isArray(data.unmatched) ? data.unmatched : []
        }
        attachResultVisible.value = true
        if (data.success > 0) fetchQualifications()
      })
      .catch(() => ElMessage.error('批量关联附件失败'))
  }
  const handleAttachResultClosed = () => {
    attachResultVisible.value = false
    fetchQualifications()
  }

  const handleBatchExport = () => {
    const ids = selectedRows.value.map(r => r.id)
    http.post('/api/knowledge/qualifications/batch-export', { ids }, { responseType: 'blob' })
      .then(res => {
        const url = window.URL.createObjectURL(new Blob([res.data]))
        const link = document.createElement('a')
        link.href = url
        link.setAttribute('download', `资质证书台账批量导出_${new Date().toISOString().slice(0, 10)}.xlsx`)
        document.body.appendChild(link)
        link.click()
        link.remove()
        window.URL.revokeObjectURL(url)
      })
      .catch(() => ElMessage.error('批量导出失败'))
  }

  const handleBatchDownload = () => {
    const ids = selectedRows.value.map(r => r.id)
    http.post('/api/knowledge/qualifications/batch-download', { ids }, { responseType: 'blob' })
      .then(res => {
        const url = window.URL.createObjectURL(new Blob([res.data]))
        const link = document.createElement('a')
        link.href = url
        link.setAttribute('download', `资质附件批量下载_${new Date().toISOString().slice(0, 10)}.zip`)
        document.body.appendChild(link)
        link.click()
        link.remove()
        window.URL.revokeObjectURL(url)
      })
      .catch(() => ElMessage.error('批量下载失败'))
  }

  return {
    tableRef,
    selectedRows,
    selectedCount,
    hasSelection,
    handleSelectionChange,
    importResultVisible,
    importResultData,
    importUploadRef,
    importTriggerRef,
    handleImportLedgerClick,
    handleImportChange,
    importResultVisible,
    importResultData,
    handleImportResultClosed,
    batchAttachUploadRef,
    batchAttachTriggerRef,
    handleBatchUploadClick,
    handleBatchAttachChange,
    attachResultVisible,
    attachResultData,
    handleAttachResultClosed,
    handleBatchExport,
    handleBatchDownload
  }
}
