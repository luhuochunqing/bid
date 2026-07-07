/**
 * useQualificationImportExport — §4.1.3.4 批量导入导出 composable
 *
 * 职责：
 *  - downloadBlob 通用 blob 触发浏览器下载
 *  - handleDownloadTemplate / handleExportLedger / handleBatchExport（鉴权下载，避免 403）
 *  - beforeImportUpload（格式/大小校验）
 *  - handleImportUpload（POST /import + dialog 反馈）
 *
 * 依赖：vue ref/computed + ElementPlus ElMessage + http client + importResultDialogVisible/importResult
 * 不在 composable 内维护 selection（依赖父组件 el-table 的 selection-change 事件）
 */
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/client'
import { triggerBlobDownload } from '@/utils/download.js'
import { ApiCode } from '@/constants/apiCode'

export function useQualificationImportExport({ userStore, fetchQualifications, filtersRef }) {
  const selectedRows = ref([])
  const importing = ref(false)
  const importResultDialogVisible = ref(false)
  const importResult = ref(null)
  const importFailedRows = computed(() => (importResult.value?.results || []).filter(r => !r.success))

  const onSelectionChange = (rows) => { selectedRows.value = rows }

  const downloadBlob = (blob, filename) => {
    triggerBlobDownload(blob, filename)
  }

  const handleDownloadTemplate = async () => {
    try {
      const resp = await http.get('/api/knowledge/qualifications/template', { responseType: 'blob' })
      downloadBlob(resp.data, '资质证书导入模板.xlsx')
      ElMessage.success('模板已下载')
    } catch (e) {
      ElMessage.error('下载模板失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    }
  }

  const buildExportQuery = () => {
    const q = new URLSearchParams()
    const filters = filtersRef && filtersRef.value
    if (filters && filters.keyword) q.set('keyword', filters.keyword)
    if (filters && Array.isArray(filters.statuses) && filters.statuses.length) {
      filters.statuses.forEach(s => q.append('status', s))
    }
    return q.toString()
  }

  const handleExportLedger = async () => {
    try {
      const qs = buildExportQuery()
      const url = `/api/knowledge/qualifications/export${qs ? '?' + qs : ''}`
      const resp = await http.get(url, { responseType: 'blob' })
      const filename = `资质证书台账_${new Date().toISOString().slice(0, 10)}.xlsx`
      downloadBlob(resp.data, filename)
      ElMessage.success('台账已导出')
    } catch (e) {
      ElMessage.error('导出失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    }
  }

  const handleBatchExport = async () => {
    if (selectedRows.value.length === 0) {
      ElMessage.warning('请先选择要导出的资质')
      return
    }
    try {
      const ids = selectedRows.value.map(r => r.id).filter(id => id != null).join(',')
      const resp = await http.get(`/api/knowledge/qualifications/export?ids=${encodeURIComponent(ids)}`, { responseType: 'blob' })
      const filename = `资质证书批量导出_${new Date().toISOString().slice(0, 10)}.xlsx`
      downloadBlob(resp.data, filename)
      ElMessage.success(`已导出选中 ${selectedRows.value.length} 条`)
    } catch (e) {
      ElMessage.error('批量导出失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    }
  }

  const beforeImportUpload = (file) => {
    const isXlsx = /\.xlsx$/i.test(file.name)
    if (!isXlsx) { ElMessage.error('仅支持 .xlsx 格式'); return false }
    const isLt10M = file.size / 1024 / 1024 < 10
    if (!isLt10M) { ElMessage.error('文件大小不能超过 10MB'); return false }
    return true
  }

  const handleImportUpload = async ({ file }) => {
    importing.value = true
    try {
      const fd = new FormData()
      fd.append('file', file)
      const operator = userStore?.currentUser?.name || userStore?.currentUser?.username || '系统导入'
      fd.append('operator', operator)
      const resp = await http.post('/api/knowledge/qualifications/import', fd, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      if (resp?.code === ApiCode.SUCCESS) {
        importResult.value = resp.data
        importResultDialogVisible.value = true
        ElMessage.success(`导入完成：成功 ${resp.data?.success ?? 0} 条，失败 ${resp.data?.failed ?? 0} 条`)
        if (typeof fetchQualifications === 'function') await fetchQualifications()
      } else {
        ElMessage.error(resp?.message || '导入失败')
      }
    } catch (e) {
      ElMessage.error('导入失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    } finally {
      importing.value = false
    }
  }

  return {
    selectedRows,
    importing,
    importResultDialogVisible,
    importResult,
    importFailedRows,
    onSelectionChange,
    handleDownloadTemplate,
    handleExportLedger,
    handleBatchExport,
    beforeImportUpload,
    handleImportUpload
  }
}
