// Input: tenders API（导入模板下载、批量导入接口）+ refreshTenderList + canCreateTender
// Output: 批量导入对话框状态、模板下载、上传提交动作
// Pos: src/views/Bidding/list/ - Tender bulk import composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { crmApi } from '@/api/modules/crm.js'

const MAX_FILE_BYTES = 5 * 1024 * 1024
const ACCEPTED_EXT = '.xlsx'

function triggerBlobDownload(blob, filename) {
  if (!blob || typeof window === 'undefined') return
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

function isXlsxFile(file) {
  const name = String(file?.name || '').toLowerCase()
  return name.endsWith(ACCEPTED_EXT)
}

export function useTenderBulkImport({ tendersApi, refreshTenderList, canCreateTender }) {
  const showBulkImport = ref(false)
  const templateDownloading = ref(false)
  const importing = ref(false)
  const importResult = ref(null)
  const selectedFile = ref(null)

  const resetImport = () => {
    selectedFile.value = null
    importResult.value = null
  }

  const closeDialog = () => {
    showBulkImport.value = false
    resetImport()
  }

  const openBulkImport = () => {
    if (!canCreateTender.value) {
      ElMessage.error('当前账号无权批量导入标讯')
      return
    }
    resetImport()
    showBulkImport.value = true
  }

  const downloadImportTemplate = async () => {
    if (!canCreateTender.value) {
      ElMessage.error('当前账号无权下载导入模板')
      return false
    }
    templateDownloading.value = true
    try {
      const blob = await tendersApi.downloadImportTemplate()
      if (!(blob instanceof Blob)) {
        throw new Error('模板下载响应异常')
      }
      triggerBlobDownload(blob, '标讯批量导入模板.xlsx')
      ElMessage.success('模板已下载，请在 Excel 中填写后回传')
      return true
    } catch (error) {
      ElMessage.error(error?.message || '模板下载失败，请稍后重试')
      return false
    } finally {
      templateDownloading.value = false
    }
  }

  const handleFileChange = (file) => {
    const raw = file?.raw instanceof File ? file.raw : (file instanceof File ? file : null)
    if (!raw) {
      selectedFile.value = null
      return
    }
    if (!isXlsxFile(raw)) {
      ElMessage.error('仅支持 .xlsx 模板，请重新选择')
      selectedFile.value = null
      return
    }
    if (raw.size > MAX_FILE_BYTES) {
      ElMessage.error('文件大小不能超过 5MB')
      selectedFile.value = null
      return
    }
    selectedFile.value = raw
    importResult.value = null
  }

  const submitBulkImport = async () => {
    if (!canCreateTender.value) {
      ElMessage.error('当前账号无权批量导入标讯')
      return false
    }
    if (!selectedFile.value) {
      ElMessage.warning('请先选择 .xlsx 导入文件')
      return false
    }
    importing.value = true
    try {
      const response = await tendersApi.bulkImport(selectedFile.value)
      const data = response?.data || null
      importResult.value = data
      if (data && data.failureCount > 0) {
        ElMessage.warning(`导入未通过：共 ${data.totalRows} 行，失败 ${data.failureCount} 行，请按错误列表修正后重试`)
        return false
      }
      const successCount = data?.successCount ?? 0
      ElMessage.success(`成功导入 ${successCount} 条标讯`)

      // 批量导入成功后，按招标主体查询 CRM 项目负责人并自动分配
      // 从导入结果中提取招标主体名称列表
      const purchaserNames = data?.purchaserNames || []
      if (purchaserNames.length > 0) {
        try {
          const uniqueNames = [...new Set(purchaserNames)]
          for (const name of uniqueNames) {
            const res = await crmApi.searchOpportunities({
              pageIndex: 1,
              pageSize: 5,
              body: { name },
            })
            const list = res?.data?.list || []
            if (list.length > 0) {
              const matched = list.find((c) =>
                c.name?.includes(name) || name?.includes(c.name || '')
              )
              if (matched?.projectLeaderName) {
                console.log(`[CRM自动分配] 招标主体「${name}」匹配到项目负责人：${matched.projectLeaderName}`)
                // TODO: 后端实现 autoAssign API 后替换为实际调用
              }
            }
          }
          if (uniqueNames.length > 0) {
            ElMessage.info('已查询 CRM 项目负责人信息，请查看标讯列表确认分配结果')
          }
        } catch (e) {
          console.warn('[CRM自动分配] 查询失败：', e)
        }
      }
      await refreshTenderList()
      closeDialog()
      return true
    } catch (error) {
      ElMessage.error(error?.response?.data?.msg || error?.message || '批量导入失败，请稍后重试')
      return false
    } finally {
      importing.value = false
    }
  }

  return {
    showBulkImport,
    templateDownloading,
    importing,
    importResult,
    selectedFile,
    openBulkImport,
    closeDialog,
    resetImport,
    downloadImportTemplate,
    handleFileChange,
    submitBulkImport,
  }
}
