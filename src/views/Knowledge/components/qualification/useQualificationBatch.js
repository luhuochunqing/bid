import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/client'

export function useQualificationBatch({ fetchQualifications }) {
  const tableRef = ref(null)
  const selectedRows = ref([])
  const selectedCount = computed(() => selectedRows.value.length)
  const hasSelection = computed(() => selectedCount.value > 0)

  const handleSelectionChange = (rows) => { selectedRows.value = rows || [] }

  const importUploadRef = ref(null)
  const importTriggerRef = ref(null)
  const handleImportLedgerClick = () => { importTriggerRef.value?.$el?.click() }
  const handleImportChange = (file) => {
    const formData = new FormData()
    formData.append('file', file.raw)
    http.post('/api/knowledge/qualifications/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
      .then(() => { ElMessage.success('导入台账成功'); fetchQualifications() })
      .catch(() => ElMessage.error('导入台账失败'))
  }

  const batchAttachUploadRef = ref(null)
  const batchAttachTriggerRef = ref(null)
  const handleBatchUploadClick = () => { batchAttachTriggerRef.value?.$el?.click() }
  const handleBatchAttachChange = (file) => {
    ElMessage.info(`附件 ${file.name} 已选择，待批量上传接口接入后处理`)
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
    importUploadRef,
    importTriggerRef,
    handleImportLedgerClick,
    handleImportChange,
    batchAttachUploadRef,
    batchAttachTriggerRef,
    handleBatchUploadClick,
    handleBatchAttachChange,
    handleBatchExport,
    handleBatchDownload
  }
}
