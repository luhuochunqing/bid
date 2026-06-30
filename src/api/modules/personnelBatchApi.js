// src/api/modules/personnelBatchApi.js — maintained by @trae, last updated 2026-06-08
// Input: httpClient
// Output: personnelBatchApi - batch import/export/attachment accessors
// Pos: src/api/modules/ - Frontend API module layer for batch operations
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'
import { triggerBlobDownload } from '@/utils/download.js'

export const personnelBatchApi = {
  /**
   * 下载批量导入模板（4 Sheet Excel）
   */
  async downloadImportTemplate() {
    const res = await httpClient.get('/api/knowledge/personnel/import/template', {
      responseType: 'blob'
    })
    // CO-419: axios 拦截器对 blob 响应返回完整 response 对象，需取 res.data
    triggerBlobDownload(res.data, 'personnel_import_template.xlsx')
  },

  /**
   * 开始批量导入
   * @param {File} file - Excel文件
   */
  async startImport(file) {
    const fd = new FormData()
    fd.append('file', file)
    return httpClient.post('/api/knowledge/personnel/import', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  /**
   * 查询导入进度
   */
  async getImportProgress(taskId) {
    return httpClient.get(`/api/knowledge/personnel/import/${taskId}`)
  },

  /**
   * 下载导入错误报告
   */
  async downloadErrorReport(taskId) {
    const res = await httpClient.get(`/api/knowledge/personnel/import/${taskId}/report`, {
      responseType: 'blob'
    })
    // CO-419: axios 拦截器对 blob 响应返回完整 response 对象，需取 res.data
    triggerBlobDownload(res.data, `import_error_report_${taskId}.xlsx`)
  },

  /**
   * 开始批量导出
   * @param {Object} filters - 筛选条件
   */
  async startExport(filters = {}) {
    const params = new URLSearchParams()
    if (filters.keyword) params.set('keyword', filters.keyword)
    if (filters.status) params.set('status', filters.status)
    if (filters.departmentCode) params.set('departmentCode', filters.departmentCode)
    if (filters.certificateType) params.set('certificateType', filters.certificateType)
    if (filters.gender) params.set('gender', filters.gender)
    if (filters.majorKeyword) params.set('majorKeyword', filters.majorKeyword)
    if (filters.certificateKeyword) params.set('certificateKeyword', filters.certificateKeyword)
    if (filters.entryDateFrom) params.set('entryDateFrom', filters.entryDateFrom)
    if (filters.entryDateTo) params.set('entryDateTo', filters.entryDateTo)
    if (Array.isArray(filters.highestEducations)) {
      filters.highestEducations.forEach(v => params.append('highestEducations', v))
    }
    if (Array.isArray(filters.studyForms)) {
      filters.studyForms.forEach(v => params.append('studyForms', v))
    }
    const query = params.toString() ? `?${params.toString()}` : ''
    return httpClient.post(`/api/knowledge/personnel/export${query}`)
  },

  /**
   * 查询导出进度
   */
  async getExportProgress(taskId) {
    return httpClient.get(`/api/knowledge/personnel/export/${taskId}`)
  },

  /**
   * 下载导出文件
   */
  async downloadExportFile(taskId) {
    const res = await httpClient.get(`/api/knowledge/personnel/export/${taskId}/download`, {
      responseType: 'blob'
    })
    triggerBlobDownload(new Blob([res]), `personnel_export_${taskId}.zip`)
  },

  /**
   * 批量关联附件
   * @param {FileList|File[]} files - PER_姓名_工号_序号_证书名.pdf 文件
   */
  async batchAttachAttachments(files) {
    const fd = new FormData()
    for (const file of files) {
      fd.append('files', file)
    }
    return httpClient.post('/api/knowledge/personnel/attachments/batch-upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}

export default personnelBatchApi
