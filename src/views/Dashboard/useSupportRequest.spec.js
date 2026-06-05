import { describe, expect, it, vi } from 'vitest'
import { useSupportRequest } from '@/views/Dashboard/useSupportRequest.js'

describe('useSupportRequest', () => {
  it('loads project options and resets form to the first project', async () => {
    const support = useSupportRequest({
      projectsApi: {
        getList: vi.fn().mockResolvedValue({
          success: true,
          data: [{ id: '3', projectName: '项目A' }, { id: 'bad', name: '坏数据' }],
        }),
      },
      approvalApi: { submitApproval: vi.fn() },
    })

    await support.loadSupportRequestProjects()
    support.resetSupportRequestForm()

    expect(support.supportRequestProjects.value).toEqual([{ id: 3, name: '项目A' }])
    expect(support.myProjectCount.value).toBe(1)
    expect(support.supportRequestForm.value.projectId).toBe(3)
  })

  it('opens dialog and loads projects when project cache is empty', async () => {
    const projectsApi = { getList: vi.fn().mockResolvedValue({ success: true, data: [{ id: 8, name: '项目B' }] }) }
    const support = useSupportRequest({ projectsApi, approvalApi: { submitApproval: vi.fn() } })

    await support.openSupportRequestDialog()

    expect(support.supportRequestDialogVisible.value).toBe(true)
    expect(projectsApi.getList).toHaveBeenCalledTimes(1)
    expect(support.supportRequestForm.value.projectId).toBe(8)
  })

  it('warns when required submit fields are missing', async () => {
    const message = { warning: vi.fn(), success: vi.fn(), error: vi.fn() }
    const support = useSupportRequest({
      message,
      approvalApi: { submitApproval: vi.fn() },
      projectsApi: { getList: vi.fn() },
    })

    await expect(support.submitSupportRequest()).resolves.toBe(false)
    expect(message.warning).toHaveBeenCalledWith('请选择关联项目')

    support.supportRequestForm.value.projectId = 1
    await expect(support.submitSupportRequest()).resolves.toBe(false)
    expect(message.warning).toHaveBeenCalledWith('请填写需求说明')
  })

  it('submits support request payload and refreshes after success', async () => {
    const message = { warning: vi.fn(), success: vi.fn(), error: vi.fn() }
    const onSubmitted = vi.fn().mockResolvedValue(undefined)
    const approvalApi = { submitApproval: vi.fn().mockResolvedValue({ success: true }) }
    const support = useSupportRequest({
      message,
      onSubmitted,
      approvalApi,
      projectsApi: { getList: vi.fn() },
    })

    support.supportRequestProjects.value = [{ id: 2, name: '项目C' }]
    support.supportRequestDialogVisible.value = true
    support.supportRequestForm.value = {
      projectId: 2,
      type: 'technical_support',
      dueDate: '2026-05-01',
      description: '  需要技术方案  ',
    }

    await expect(support.submitSupportRequest()).resolves.toBe(true)

    expect(approvalApi.submitApproval).toHaveBeenCalledWith({
      projectId: 2,
      projectName: '项目C',
      approvalType: 'technical_support',
      title: '项目C - 标书支持申请',
      description: '需要技术方案',
      dueDate: '2026-05-01',
      priority: 1,
    })
    expect(message.success).toHaveBeenCalledWith('标书支持申请已提交')
    expect(support.supportRequestDialogVisible.value).toBe(false)
    expect(onSubmitted).toHaveBeenCalledTimes(1)
    expect(support.supportRequestSubmitting.value).toBe(false)
  })

  it('reports submit API failures and clears submitting state', async () => {
    const message = { warning: vi.fn(), success: vi.fn(), error: vi.fn() }
    const support = useSupportRequest({
      message,
      approvalApi: { submitApproval: vi.fn().mockResolvedValue({ success: false, msg: '后端拒绝' }) },
      projectsApi: { getList: vi.fn() },
    })
    support.supportRequestProjects.value = [{ id: 2, name: '项目C' }]
    support.supportRequestForm.value = { projectId: 2, type: 'bid_support', dueDate: '', description: '说明' }

    await expect(support.submitSupportRequest()).resolves.toBe(false)

    expect(message.error).toHaveBeenCalledWith('后端拒绝')
    expect(support.supportRequestSubmitting.value).toBe(false)
  })
})
