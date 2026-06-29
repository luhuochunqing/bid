import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 账户列表批量操作（当前为占位实现）。
 *
 * @param {{ selectedRows: Ref<[]>, loadAccounts: () => Promise<void> }} deps
 */
export function useAccountBatchActions({ selectedRows, loadAccounts }) {
  const guardEmpty = () => {
    if (selectedRows.value.length === 0) {
      ElMessage.warning('请先选择要操作的账户')
      return false
    }
    return true
  }

  const handleBatchBorrow = () => {
    if (!guardEmpty()) return
    ElMessage.info(`批量借阅 ${selectedRows.value.length} 个账户（待实现）`)
  }

  const handleBatchReturn = () => {
    if (!guardEmpty()) return
    ElMessage.info(`批量归还 ${selectedRows.value.length} 个账户（待实现）`)
  }

  const handleBatchEdit = () => {
    if (!guardEmpty()) return
    ElMessage.info(`批量编辑 ${selectedRows.value.length} 个账户（待实现）`)
  }

  const handleBatchDelete = async () => {
    if (!guardEmpty()) return
    try {
      await ElMessageBox.confirm(
        `确定要删除选中的 ${selectedRows.value.length} 个账户吗？`,
        '确认删除',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
    ElMessage.success(`已删除 ${selectedRows.value.length} 个账户（待实现）`)
    selectedRows.value = []
    loadAccounts()
  }

  return {
    handleBatchBorrow,
    handleBatchReturn,
    handleBatchEdit,
    handleBatchDelete
  }
}
