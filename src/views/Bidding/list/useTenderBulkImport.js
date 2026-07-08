// Input: tenders API（导入模板下载、批量导入接口、进度查询接口）+ refreshTenderList + canCreateTender
// Output: 批量导入对话框状态、模板下载、上传提交动作、异步进度轮询
// Pos: src/views/Bidding/list/ - Tender bulk import composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { triggerBlobDownload } from '@/utils/download.js'

const MAX_FILE_BYTES = 5 * 1024 * 1024
const ACCEPTED_EXT = '.xlsx'
const POLL_INTERVAL_MS = 2000
const MAX_POLL_DURATION_MS = 5 * 60 * 1000
const TERMINAL_STATUSES = ['COMPLETED', 'PARTIAL_SUCCESS', 'FAILED']

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
  const importProgress = ref(null)
  const polling = ref(false)

  let pollTimer = null

  const clearPollTimer = () => {
    if (pollTimer) {
      clearTimeout(pollTimer)
      pollTimer = null
    }
  }

  onUnmounted(() => {
    clearPollTimer()
  })

  const resetImport = () => {
    clearPollTimer()
    selectedFile.value = null
    importResult.value = null
    importProgress.value = null
    polling.value = false
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
    importProgress.value = null
  }

  /**
   * 处理终态：显示对应提示 + 刷新列表 + 关闭对话框（仅 COMPLETED 时自动关闭）。
   */
  const handleTerminalState = async (data) => {
    const { status, totalRows, successCount, failureCount } = data

    if (status === 'COMPLETED') {
      ElMessage.success(`成功导入 ${successCount} 条标讯`)
      await refreshTenderList()
      closeDialog()
    } else if (status === 'PARTIAL_SUCCESS') {
      ElMessage.warning(`导入完成（部分成功）：共 ${totalRows} 行，成功 ${successCount} 行，失败 ${failureCount} 行，请查看下方错误明细`)
      await refreshTenderList()
    } else if (status === 'FAILED') {
      ElMessage.error(`导入失败：共 ${totalRows} 行，失败 ${failureCount} 行，请查看下方错误明细逐行修正后重新上传`)
    }
  }

  /**
   * 轮询导入进度。
   * <p>每 POLL_INTERVAL_MS 毫秒查询一次进度，终态时停止轮询并设置 importResult。
   * <p>轮询失败时（如网络异常）不立即终止，下次轮询会重试。
   * <p>如果轮询总时长超过 MAX_POLL_DURATION_MS（5 分钟），停止轮询并提示用户
   * （任务仍在后端继续处理，结果会写入 DB，后端 RecoveryRunner 兜底卡死任务）。
   */
  const pollImportProgress = (taskId) => {
    clearPollTimer()
    polling.value = true

    const startTime = Date.now()

    const stopPollingWithTimeout = () => {
      polling.value = false
      clearPollTimer()
      ElMessage.warning('导入处理时间较长，请稍后在标讯列表查看导入结果')
    }

    const poll = async () => {
      if (Date.now() - startTime > MAX_POLL_DURATION_MS) {
        stopPollingWithTimeout()
        return
      }

      try {
        const response = await tendersApi.getImportProgress(taskId)
        const data = response?.data || null
        if (!data) {
          // 响应异常，下次轮询重试
          pollTimer = setTimeout(poll, POLL_INTERVAL_MS)
          return
        }

        importProgress.value = data

        if (TERMINAL_STATUSES.includes(data.status)) {
          // 终态：停止轮询，设置结果
          polling.value = false
          importResult.value = data
          clearPollTimer()
          handleTerminalState(data)
        } else {
          // 非终态：继续轮询
          pollTimer = setTimeout(poll, POLL_INTERVAL_MS)
        }
      } catch (error) {
        // 网络异常等：下次轮询重试（不立即终止）
        pollTimer = setTimeout(poll, POLL_INTERVAL_MS)
      }
    }

    poll()
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
    importResult.value = null
    importProgress.value = null
    try {
      const response = await tendersApi.bulkImport(selectedFile.value)
      const data = response?.data || null
      if (!data || !data.taskId) {
        throw new Error('导入任务创建失败：响应缺少 taskId')
      }

      // 同步阶段已完成，任务已创建，开始异步轮询进度
      importing.value = false
      ElMessage.info('导入任务已创建，正在后台处理...')

      // 启动进度轮询
      pollImportProgress(data.taskId)
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
    importProgress,
    polling,
    selectedFile,
    openBulkImport,
    closeDialog,
    resetImport,
    downloadImportTemplate,
    handleFileChange,
    submitBulkImport,
  }
}
