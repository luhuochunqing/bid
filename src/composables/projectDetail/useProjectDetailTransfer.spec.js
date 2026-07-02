import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useProjectDetailTransfer } from './useProjectDetailTransfer.js'

// Mock ElMessage
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}))

const mockUserStore = {
  isBidAdmin: true,
  isBidManager: true, // 保留以兼容其他可能引用的旧测试
  currentUser: { id: 999, name: '管理员' },
}

const mockProjectStore = {
  getProjectById: vi.fn().mockResolvedValue({ id: 135, name: '测试项目' }),
}

const mockProjectsApi = {
  transferProject: vi.fn(),
}

const mockMessage = { success: vi.fn(), warning: vi.fn(), error: vi.fn() }

function createMockContext(overrides = {}) {
  return {
    userStore: mockUserStore,
    projectStore: mockProjectStore,
    projectsApi: mockProjectsApi,
    project: { value: { id: 135, name: '测试项目', managerId: 7246, projectLeaderName: '陈梦瑶' } },
    message: mockMessage,
    ...overrides,
  }
}

describe('useProjectDetailTransfer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初始化返回所有必要字段', () => {
    const transfer = useProjectDetailTransfer(createMockContext())
    expect(transfer.transferDialogVisible).toBeDefined()
    expect(transfer.transferring).toBeDefined()
    expect(transfer.transferForm).toBeDefined()
    expect(transfer.canTransfer).toBeDefined()
    expect(transfer.excludeOwnerIds).toBeDefined()
    expect(transfer.openTransfer).toBeTypeOf('function')
    expect(transfer.closeTransfer).toBeTypeOf('function')
    expect(transfer.handleTransferConfirm).toBeTypeOf('function')
  })

  it('canTransfer 在投标管理员（/bidAdmin）登录且有项目时为 true', () => {
    const transfer = useProjectDetailTransfer(createMockContext({
      userStore: { isBidAdmin: true, currentUser: { id: 999, roleCode: '/bidAdmin' } },
    }))
    expect(transfer.canTransfer.value).toBe(true)
  })

  it('canTransfer 在系统管理员（admin，对应 OSS bid-SystemAdmin）登录时为 true', () => {
    const transfer = useProjectDetailTransfer(createMockContext({
      userStore: { isBidAdmin: true, currentUser: { id: 1, roleCode: 'admin' } },
    }))
    expect(transfer.canTransfer.value).toBe(true)
  })

  it('canTransfer 在投标组长（bid-TeamLeader）登录时为 false——组长不可操作项目转移', () => {
    // 投标组长在 isBidAdmin 中为 false（不在 admin//bidAdmin 白名单），但在 isBidManager 中为 true
    // 这个测试验证 canTransfer 用的是 isBidAdmin 而非 isBidManager
    const transfer = useProjectDetailTransfer(createMockContext({
      userStore: { isBidAdmin: false, isBidManager: true, currentUser: { id: 888, roleCode: 'bid-TeamLeader' } },
    }))
    expect(transfer.canTransfer.value).toBe(false)
  })

  it('canTransfer 在非管理员（如投标专员）时为 false', () => {
    const transfer = useProjectDetailTransfer(createMockContext({
      userStore: { isBidAdmin: false, isBidManager: false, currentUser: { id: 1, roleCode: 'bid-Team' } },
    }))
    expect(transfer.canTransfer.value).toBe(false)
  })

  it('canTransfer 在无项目时为 false', () => {
    const transfer = useProjectDetailTransfer(createMockContext({
      project: { value: null },
    }))
    expect(transfer.canTransfer.value).toBe(false)
  })

  it('excludeOwnerIds 排除当前负责人', () => {
    const transfer = useProjectDetailTransfer(createMockContext())
    expect(transfer.excludeOwnerIds.value).toEqual([7246])
  })

  it('excludeOwnerIds 在无 managerId 时为空数组', () => {
    const transfer = useProjectDetailTransfer(createMockContext({
      project: { value: { id: 135, name: '测试项目' } },
    }))
    expect(transfer.excludeOwnerIds.value).toEqual([])
  })

  it('openTransfer 重置表单并打开 dialog', () => {
    const transfer = useProjectDetailTransfer(createMockContext())
    transfer.transferForm.newOwnerUserId = 999
    transfer.transferForm.reason = '旧原因'
    transfer.openTransfer()
    expect(transfer.transferDialogVisible.value).toBe(true)
    expect(transfer.transferForm.newOwnerUserId).toBeNull()
    expect(transfer.transferForm.reason).toBe('')
  })

  it('closeTransfer 关闭 dialog 并重置表单', () => {
    const transfer = useProjectDetailTransfer(createMockContext())
    transfer.openTransfer()
    transfer.transferForm.newOwnerUserId = 999
    transfer.closeTransfer()
    expect(transfer.transferDialogVisible.value).toBe(false)
    expect(transfer.transferForm.newOwnerUserId).toBeNull()
  })

  it('handleTransferConfirm 未选新负责人时警告且不调 API', async () => {
    const transfer = useProjectDetailTransfer(createMockContext())
    transfer.openTransfer()
    await transfer.handleTransferConfirm()
    expect(mockProjectsApi.transferProject).not.toHaveBeenCalled()
  })

  it('handleTransferConfirm 成功时调 API、关 dialog、刷新项目', async () => {
    mockProjectsApi.transferProject.mockResolvedValueOnce({
      success: true,
      data: { projectId: 135, newOwnerName: '周子靖' },
    })
    const transfer = useProjectDetailTransfer(createMockContext())
    transfer.openTransfer()
    transfer.transferForm.newOwnerUserId = 7324
    transfer.transferForm.reason = '误派修正'

    await transfer.handleTransferConfirm()

    expect(mockProjectsApi.transferProject).toHaveBeenCalledWith(135, {
      newOwnerUserId: 7324,
      reason: '误派修正',
    })
    expect(mockProjectStore.getProjectById).toHaveBeenCalledWith(135)
    expect(transfer.transferDialogVisible.value).toBe(false)
    expect(transfer.transferring.value).toBe(false)
  })

  it('handleTransferConfirm API 返回失败时弹错误且保持 dialog 打开', async () => {
    mockProjectsApi.transferProject.mockResolvedValueOnce({
      success: false,
      message: '新负责人与当前负责人相同，无需转移',
    })
    const transfer = useProjectDetailTransfer(createMockContext())
    transfer.openTransfer()
    transfer.transferForm.newOwnerUserId = 7246

    await transfer.handleTransferConfirm()

    expect(transfer.transferDialogVisible.value).toBe(true)
    expect(transfer.transferring.value).toBe(false)
  })

  it('handleTransferConfirm API 抛异常时弹错误且保持 dialog 打开', async () => {
    mockProjectsApi.transferProject.mockRejectedValueOnce(new Error('网络错误'))
    const transfer = useProjectDetailTransfer(createMockContext())
    transfer.openTransfer()
    transfer.transferForm.newOwnerUserId = 7324

    await transfer.handleTransferConfirm()

    expect(transfer.transferDialogVisible.value).toBe(true)
    expect(transfer.transferring.value).toBe(false)
  })

  it('handleTransferConfirm reason 为空白时传 undefined', async () => {
    mockProjectsApi.transferProject.mockResolvedValueOnce({ success: true, data: {} })
    const transfer = useProjectDetailTransfer(createMockContext())
    transfer.openTransfer()
    transfer.transferForm.newOwnerUserId = 7324
    transfer.transferForm.reason = '   '

    await transfer.handleTransferConfirm()

    expect(mockProjectsApi.transferProject).toHaveBeenCalledWith(135, {
      newOwnerUserId: 7324,
      reason: undefined,
    })
  })

  it('handleTransferConfirm 项目 ID 缺失时警告且不调 API', async () => {
    const transfer = useProjectDetailTransfer(createMockContext({
      project: { value: { name: '无 ID 项目' } },
    }))
    transfer.openTransfer()
    transfer.transferForm.newOwnerUserId = 7324

    await transfer.handleTransferConfirm()

    expect(mockProjectsApi.transferProject).not.toHaveBeenCalled()
  })
})
