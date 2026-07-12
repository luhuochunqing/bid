import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { resourcesApi } from '@/api'
import { triggerBlobDownload } from '@/utils/download.js'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'

export function useAccountExport() {
  const exporting = ref(false)

  const handleExport = async (selectedRows = []) => {
    const hasSelected = selectedRows.length > 0
    const params = {}
    if (hasSelected) {
      params.selectedIds = selectedRows.map(r => r.id).join(',')
    }

    exporting.value = true
    try {
      const response = await resourcesApi.accounts.exportAccounts(params)
      const blob = response?.data
      if (!blob || !(blob instanceof Blob)) {
        ElMessage.error('导出失败：未收到文件数据')
        return
      }
      triggerBlobDownload(blob, `平台账户台账_${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.xlsx`)
      ElMessage.success('导出成功')
    } catch (e) {
      // 429 已由全局 axios interceptor 展示友好提示，业务层不再重复弹窗
      notifyErrorUnlessRateLimit(e, '导出失败，请稍后重试')
    } finally {
      exporting.value = false
    }
  }

  return {
    exporting,
    handleExport
  }
}

export default useAccountExport
