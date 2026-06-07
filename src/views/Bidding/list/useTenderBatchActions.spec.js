import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useTenderBatchActions } from './useTenderBatchActions.js'

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
  ElMessageBox: {
    confirm: vi.fn(() => Promise.resolve()),
  },
}))

function createActions(overrides = {}) {
  const batchTendersApi = {
    batchClaim: vi.fn(async () => ({
      success: true,
      data: { success: true, successCount: 1, failureCount: 0, totalCount: 1 },
    })),
    batchUpdateStatus: vi.fn(async () => ({
      success: true,
      data: { success: true, successCount: 1, failureCount: 0, totalCount: 1 },
    })),
  }
  const tendersApi = {
    delete: vi.fn(async () => ({ success: true })),
  }
  const refreshTenderList = vi.fn()
  const clearSelection = vi.fn()
  const actions = useTenderBatchActions({
    batchTendersApi,
    tendersApi,
    selectedTenders: ref([{ id: 7, title: '测试标讯' }]),
    followedTenders: ref([]),
    clearSelection,
    refreshTenderList,
    canManageTenders: ref(true),
    canDeleteTenders: ref(true),
    router: { push: vi.fn() },
    ...overrides,
  })
  return { actions, batchTendersApi, tendersApi, refreshTenderList, clearSelection }
}

describe('useTenderBatchActions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('claims tenders without sending a client controlled userId', async () => {
    const { actions, batchTendersApi } = createActions()

    await actions.handleBatchClaim()

    expect(batchTendersApi.batchClaim).toHaveBeenCalledWith([7])
    expect(batchTendersApi.batchClaim.mock.calls[0]).toHaveLength(1)
  })

  it('does not refresh list when delete API fails', async () => {
    const { actions, tendersApi, refreshTenderList } = createActions()
    tendersApi.delete.mockResolvedValueOnce({ success: false, msg: '没有权限' })

    await actions.handleDeleteTender({ id: 7, title: '测试标讯' })

    expect(tendersApi.delete).toHaveBeenCalledWith(7)
    expect(refreshTenderList).not.toHaveBeenCalled()
  })
})
