import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAccountBatchActions } from './useAccountBatchActions.js'

vi.mock('element-plus', () => ({
  ElMessage: {
    warning: vi.fn(),
    info: vi.fn(),
    success: vi.fn(),
    error: vi.fn()
  },
  ElMessageBox: {
    confirm: vi.fn()
  }
}))

describe('useAccountBatchActions', () => {
  const setup = (rows = []) => {
    const selectedRows = ref(rows)
    const loadAccounts = vi.fn()
    const actions = useAccountBatchActions({ selectedRows, loadAccounts })
    return { selectedRows, loadAccounts, actions }
  }

  it('警告并跳过空选择', () => {
    const { actions } = setup([])
    actions.handleBatchBorrow()
    expect(ElMessage.warning).toHaveBeenCalledWith('请先选择要操作的账户')
    expect(ElMessage.info).not.toHaveBeenCalled()
  })

  it('批量借阅提示待实现', () => {
    const { actions } = setup([{ id: 1 }])
    actions.handleBatchBorrow()
    expect(ElMessage.info).toHaveBeenCalledWith('批量借阅 1 个账户（待实现）')
  })

  it('批量删除确认后清空选择并刷新列表', async () => {
    ElMessageBox.confirm.mockResolvedValueOnce(undefined)
    const { selectedRows, loadAccounts, actions } = setup([{ id: 1 }, { id: 2 }])
    await actions.handleBatchDelete()
    expect(ElMessageBox.confirm).toHaveBeenCalledWith(
      '确定要删除选中的 2 个账户吗？',
      '确认删除',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    expect(ElMessage.success).toHaveBeenCalledWith('已删除 2 个账户（待实现）')
    expect(selectedRows.value).toEqual([])
    expect(loadAccounts).toHaveBeenCalled()
  })

  it('批量删除取消后不刷新列表', async () => {
    ElMessageBox.confirm.mockRejectedValueOnce(new Error('cancel'))
    const { loadAccounts, actions } = setup([{ id: 1 }])
    await actions.handleBatchDelete()
    expect(loadAccounts).not.toHaveBeenCalled()
  })
})
