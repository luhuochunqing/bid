// Input: mocked organization integration API and Element Plus message bridge
// Output: composable error-state and disabled-operation coverage
// Pos: src/views/System/settings/ - organization integration operation tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ElMessage } from 'element-plus'
import { organizationIntegrationApi } from '@/api/modules/systemIntegration.js'
import { useOrganizationIntegrationOperations } from './useOrganizationIntegrationOperations.js'

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
}))

vi.mock('@/api/modules/systemIntegration.js', () => ({
  organizationIntegrationApi: {
    getOperationsStatus: vi.fn(),
    startSyncRun: vi.fn(),
    resyncUser: vi.fn(),
    resyncDepartment: vi.fn(),
    replayDeadLetter: vi.fn(),
  },
}))

describe('useOrganizationIntegrationOperations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('keeps failed status load explicit instead of showing disabled zero state', async () => {
    organizationIntegrationApi.getOperationsStatus.mockRejectedValue(new Error('状态接口失败'))
    const operations = useOrganizationIntegrationOperations()

    await operations.load()

    expect(operations.loaded.value).toBe(false)
    expect(operations.status.value).toBeNull()
    expect(operations.errorText.value).toBe('状态接口失败')
    expect(operations.canOperate.value).toBe(false)
    expect(ElMessage.error).toHaveBeenCalledWith('状态接口失败')
  })

  it('blocks reconciliation when organization integration is disabled', async () => {
    organizationIntegrationApi.getOperationsStatus.mockResolvedValue({ enabled: false })
    const operations = useOrganizationIntegrationOperations()
    await operations.load()

    await operations.startSyncRun()

    expect(organizationIntegrationApi.startSyncRun).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('组织架构集成未启用')
  })

  it('replays dead letter event from its event key and reloads status', async () => {
    organizationIntegrationApi.getOperationsStatus.mockResolvedValue({ enabled: true })
    organizationIntegrationApi.replayDeadLetter.mockResolvedValue({ code: '200' })
    const operations = useOrganizationIntegrationOperations()
    await operations.load()
    operations.deadLetterEventKey.value = ' event-key '

    await operations.replayDeadLetter()

    expect(organizationIntegrationApi.replayDeadLetter).toHaveBeenCalledWith('event-key')
    expect(ElMessage.success).toHaveBeenCalledWith('死信事件重放成功')
    expect(operations.deadLetterEventKey.value).toBe('')
    expect(organizationIntegrationApi.getOperationsStatus).toHaveBeenCalledTimes(2)
  })
})
