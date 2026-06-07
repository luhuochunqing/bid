import { describe, expect, it, vi } from 'vitest'
import { useWorkbenchApprovals } from '@/views/Dashboard/useWorkbenchApprovals.js'

describe('useWorkbenchApprovals', () => {
  it('loads pending approvals and stores total count', async () => {
    const api = {
      getPendingApprovals: vi.fn().mockResolvedValue({
        totalCount: 11,
        data: [{ id: 1, projectName: '项目A', typeName: '预算审批', approvalType: 'budget' }],
      }),
      getMyApprovals: vi.fn(),
    }
    const approvals = useWorkbenchApprovals({ api, initialProcesses: [] })

    await approvals.loadPendingApprovals()

    expect(api.getPendingApprovals).toHaveBeenCalledWith({ page: 0, size: 8 })
    expect(approvals.pendingApprovalsTotalCount.value).toBe(11)
    expect(approvals.pendingApprovals.value[0]).toMatchObject({
      title: '项目A - 预算审批',
      type: 'budget',
      department: '投标管理部',
    })
  })

  it('loads my approval processes with status fallback mapping', async () => {
    const api = {
      getPendingApprovals: vi.fn(),
      getMyApprovals: vi.fn().mockResolvedValue({
        data: [
          { id: 1, projectName: '项目A', typeName: '支持申请', status: 'APPROVED' },
          { id: 2, projectName: '项目B', typeName: '支持申请', status: 'REJECTED' },
        ],
      }),
    }
    const approvals = useWorkbenchApprovals({ api, initialProcesses: [] })

    await approvals.loadMyProcesses()

    expect(api.getMyApprovals).toHaveBeenCalledWith({ page: 0, size: 8 })
    expect(approvals.myProcesses.value.map((item) => item.status)).toEqual(['in-progress', 'urgent'])
  })

  it('opens approval dialog in approve or reject mode', () => {
    const approvals = useWorkbenchApprovals({ api: {}, initialProcesses: [] })
    const item = { id: 7 }

    approvals.handleApprove(item)
    expect(approvals.approvalMode.value).toBe('approve')
    expect(approvals.currentApprovalItem.value).toEqual(item)
    expect(approvals.approvalDialogVisible.value).toBe(true)

    approvals.handleReject(item)
    expect(approvals.approvalMode.value).toBe('reject')
  })

  it('reloads both approval lists after approval success', async () => {
    const api = {
      getPendingApprovals: vi.fn().mockResolvedValue({ totalCount: 0, data: [] }),
      getMyApprovals: vi.fn().mockResolvedValue({ data: [] }),
    }
    const approvals = useWorkbenchApprovals({ api })

    await approvals.handleApprovalSuccess()

    expect(api.getPendingApprovals).toHaveBeenCalledTimes(1)
    expect(api.getMyApprovals).toHaveBeenCalledTimes(1)
  })

  it('falls back to empty state when approval APIs reject', async () => {
    const approvals = useWorkbenchApprovals({
      api: {
        getPendingApprovals: vi.fn().mockRejectedValue(new Error('pending down')),
        getMyApprovals: vi.fn().mockRejectedValue(new Error('my down')),
      },
    })

    await approvals.loadPendingApprovals()
    await approvals.loadMyProcesses()

    expect(approvals.pendingApprovals.value).toEqual([])
    expect(approvals.myProcesses.value).toEqual([])
  })
})

  it('bidReviewApprovalCount counts bid_review approvals', async () => {
    const api = {
      getPendingApprovals: vi.fn().mockResolvedValue({
        totalCount: 3,
        data: [
          { id: 1, projectName: '项目A', typeName: '标书评审', approvalType: 'bid_review' },
          { id: 2, projectName: '项目B', typeName: '预算审批', approvalType: 'budget' },
          { id: 3, projectName: '项目C', typeName: '标书评审', approvalType: 'bid_review' },
        ],
      }),
      getMyApprovals: vi.fn(),
    }
    const approvals = useWorkbenchApprovals({ api, initialProcesses: [] })

    await approvals.loadPendingApprovals()

    expect(approvals.bidReviewApprovalCount.value).toBe(2)
    expect(approvals.pendingApprovals.value[0]).toMatchObject({
      title: '项目A - 标书评审',
      type: 'bid_review',
    })
  })
