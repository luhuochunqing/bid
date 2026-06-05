import { notifyExportSuccess, ExportType, exportApi } from '@/api/modules/export'

function buildRows(type, params = {}) {
  return [
    {
      exportType: type,
      generatedAt: new Date().toLocaleString('zh-CN', { hour12: false }),
      ...params,
    },
  ]
}

function buildFilename(type) {
  const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')
  return `${type || ExportType.TENDERS}-${stamp}.csv`
}

export function useExport() {
  async function exportExcel(type, params = {}, successMessage = '导出成功') {
    const rows = buildRows(type, params)
    const filename = buildFilename(type)
    const result = await exportApi.exportExcel(type, rows, filename)
    if (result?.success) {
      notifyExportSuccess(successMessage)
    }
    return result
  }

  return {
    exportExcel,
  }
}

export default useExport
