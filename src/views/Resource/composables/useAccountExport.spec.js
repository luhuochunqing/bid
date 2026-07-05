import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ElMessage } from 'element-plus'
import { useAccountExport } from './useAccountExport.js'
import { resourcesApi } from '@/api'

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    info: vi.fn(),
    warning: vi.fn()
  }
}))

vi.mock('@/api', () => ({
  resourcesApi: {
    accounts: {
      exportAccounts: vi.fn()
    }
  }
}))

describe('useAccountExport', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:test-url'),
      revokeObjectURL: vi.fn()
    })
    document.body.innerHTML = ''
  })

  afterEach(() => {
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })

  it('返回 exporting ref 和 handleExport 函数', () => {
    const { exporting, handleExport } = useAccountExport()
    expect(exporting.value).toBe(false)
    expect(typeof handleExport).toBe('function')
  })

  it('导出全部时不传 selectedIds 参数', async () => {
    const mockBlob = new Blob(['fake excel content'])
    resourcesApi.accounts.exportAccounts.mockResolvedValue({ data: mockBlob })

    const { handleExport } = useAccountExport()
    await handleExport([])

    expect(resourcesApi.accounts.exportAccounts).toHaveBeenCalledWith({})
  })

  it('按选中导出时传递 selectedIds 逗号分隔字符串', async () => {
    const mockBlob = new Blob(['fake excel content'])
    resourcesApi.accounts.exportAccounts.mockResolvedValue({ data: mockBlob })

    const { handleExport } = useAccountExport()
    await handleExport([{ id: 1 }, { id: 2 }, { id: 3 }])

    expect(resourcesApi.accounts.exportAccounts).toHaveBeenCalledWith({ selectedIds: '1,2,3' })
  })

  it('导出成功时显示成功提示', async () => {
    const mockBlob = new Blob(['fake excel content'])
    resourcesApi.accounts.exportAccounts.mockResolvedValue({ data: mockBlob })

    const { handleExport } = useAccountExport()
    await handleExport([])

    expect(ElMessage.success).toHaveBeenCalledWith('导出成功')
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('导出失败时显示错误提示', async () => {
    resourcesApi.accounts.exportAccounts.mockRejectedValue(new Error('network error'))

    const { handleExport } = useAccountExport()
    await handleExport([])

    expect(ElMessage.error).toHaveBeenCalledWith('导出失败，请稍后重试')
    expect(ElMessage.success).not.toHaveBeenCalled()
  })

  it('导出过程中 exporting 为 true，完成后为 false', async () => {
    let resolveExport
    const exportPromise = new Promise(resolve => {
      resolveExport = resolve
    })
    resourcesApi.accounts.exportAccounts.mockReturnValue(exportPromise)

    const { exporting, handleExport } = useAccountExport()
    const exportCall = handleExport([])

    expect(exporting.value).toBe(true)

    resolveExport({ data: new Blob(['fake']) })
    await exportCall

    expect(exporting.value).toBe(false)
  })
})
