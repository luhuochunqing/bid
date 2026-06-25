import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import OperationLogTab from './OperationLogTab.vue'
import auditApi from '@/api/modules/audit.js'

const { mockWarning } = vi.hoisted(() => ({
  mockWarning: vi.fn(),
}))

vi.mock('@/api/modules/audit.js', () => ({
  default: { getQualificationLogs: vi.fn() },
}))

vi.mock('element-plus', () => ({
  ElMessage: { warning: mockWarning, info: vi.fn(), success: vi.fn(), error: vi.fn() },
}))

describe('OperationLogTab', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows "操作日志暂时无法加载" on 403', async () => {
    const err403 = { response: { status: 403 } }
    auditApi.getQualificationLogs.mockRejectedValue(err403)

    mount(OperationLogTab, {
      props: { qualificationId: 1 },
      global: { stubs: { ElDatePicker: true, ElButton: true, ElTable: true, ElEmpty: true, ElTag: true } },
    })

    await flushPromises()

    expect(mockWarning).toHaveBeenCalledWith('操作日志暂时无法加载')
  })

  it('shows "操作日志加载失败" on non-403 error', async () => {
    const err500 = { response: { status: 500 } }
    auditApi.getQualificationLogs.mockRejectedValue(err500)

    mount(OperationLogTab, {
      props: { qualificationId: 1 },
      global: { stubs: { ElDatePicker: true, ElButton: true, ElTable: true, ElEmpty: true, ElTag: true } },
    })

    await flushPromises()

    expect(mockWarning).toHaveBeenCalledWith('操作日志加载失败')
  })
})
