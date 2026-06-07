// Input: tender APIs, selection state, permissions, router, and refresh callbacks
// Output: command-side tender actions for claim, status, follow, and delete
// Pos: src/views/Bidding/list/ - Tender command actions composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ElMessage, ElMessageBox } from 'element-plus'
import { normalizeBatchResult } from '../bidding-utils.js'
import {
  getTenderStatusText,
  normalizeTenderStatusCode,
  TENDER_STATUSES,
} from '../bidding-utils-status.js'

export function useTenderBatchActions({
  batchTendersApi,
  tendersApi,
  selectedTenders,
  followedTenders,
  clearSelection,
  refreshTenderList,
  canManageTenders,
  canDeleteTenders,
  router,
}) {
  const showBatchOperationFeedback = (response, successVerb) => {
    const normalized = normalizeBatchResult(response)
    if (normalized.ok) {
      ElMessage.success(normalized.message.replace('操作成功', successVerb))
      return
    }
    if (response?.partialSuccess) {
      ElMessage.warning(normalized.message)
      return
    }
    ElMessage.error(normalized.message)
  }

  const requireManagePermission = () => {
    if (canManageTenders.value) return true
    ElMessage.error('当前账号无权操作标讯')
    return false
  }

  const handleBatchClaim = async () => {
    if (!requireManagePermission()) return
    if (selectedTenders.value.length === 0) {
      ElMessage.warning('请先选择要领取的标讯')
      return
    }
    try {
      await ElMessageBox.confirm(
        `确定要领取选中的 ${selectedTenders.value.length} 条标讯吗？`,
        '领取确认',
        { confirmButtonText: '确定领取', cancelButtonText: '取消', type: 'info' },
      )
      const result = await batchTendersApi.batchClaim(selectedTenders.value.map((item) => item.id))
      showBatchOperationFeedback(result, '领取成功')
      if (result.success || result.partialSuccess) {
        clearSelection()
        await refreshTenderList()
      }
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') ElMessage.error('领取失败，请重试')
    }
  }

  const handleBatchFollow = async () => {
    if (!requireManagePermission()) return
    if (selectedTenders.value.length === 0) {
      ElMessage.warning('请先选择要关注的标讯')
      return
    }
    try {
      const tenderIds = selectedTenders.value.map((item) => item.id)
      const result = await batchTendersApi.batchUpdateStatus(tenderIds, TENDER_STATUSES.TRACKING)
      if (result.success || result.partialSuccess) {
        const nextIds = selectedTenders.value
          .filter((item) => !followedTenders.value.includes(item.id))
          .map((item) => item.id)
        followedTenders.value.push(...nextIds)
        clearSelection()
        await refreshTenderList()
      }
      showBatchOperationFeedback(result, '关注成功')
    } catch {
      ElMessage.error('关注失败，请重试')
    }
  }

  const handleSingleClaim = async (row) => {
    if (!requireManagePermission()) return
    try {
      await ElMessageBox.confirm(`确定要领取"${row.title}"吗？`, '领取确认', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info',
      })
      const result = await batchTendersApi.batchClaim([row.id])
      showBatchOperationFeedback(result, '领取成功')
      if (result.success || result.partialSuccess) await refreshTenderList()
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') ElMessage.error('领取失败，请重试')
    }
  }

  const handleDeleteTender = async (row) => {
    if (!canDeleteTenders.value) {
      ElMessage.error('当前账号无权删除标讯')
      return
    }
    try {
      await ElMessageBox.confirm(`确定要删除"${row.title}"吗？删除后不可恢复。`, '删除确认', {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
      })
      const response = await tendersApi.delete(row.id)
      if (!response?.success) throw new Error(response?.msg || '删除失败')
      ElMessage.success('删除成功')
      await refreshTenderList()
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '删除失败')
    }
  }

  const handleUpdateStatus = async (row, newStatus) => {
    if (!requireManagePermission()) return
    const normalizedStatus = normalizeTenderStatusCode(newStatus)
    const statusText = getTenderStatusText(normalizedStatus)
    try {
      await ElMessageBox.confirm(`确定要将"${row.title}"状态变更为"${statusText}"吗？`, '状态变更', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: normalizedStatus === TENDER_STATUSES.ABANDONED ? 'error' : 'warning',
      })
      const result = await batchTendersApi.batchUpdateStatus([row.id], normalizedStatus)
      showBatchOperationFeedback(result, '状态更新成功')
      await refreshTenderList()
      if (result.success && normalizedStatus === TENDER_STATUSES.BIDDING) {
        router.push({ path: '/project/create', query: { tenderId: row.id } })
      }
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') ElMessage.error('状态更新失败')
    }
  }

  return {
    handleBatchClaim,
    handleBatchFollow,
    handleSingleClaim,
    handleDeleteTender,
    handleUpdateStatus,
    showBatchOperationFeedback,
  }
}
