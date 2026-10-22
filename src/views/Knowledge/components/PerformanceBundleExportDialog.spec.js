import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { ElMessage } from 'element-plus'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn() }
}))

vi.mock('@/api/modules/performanceBundleExport', () => ({
  performanceBundleExportApi: { triggerExport: vi.fn() }
}))

import PerformanceBundleExportDialog from './PerformanceBundleExportDialog.vue'

describe('PerformanceBundleExportDialog', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('下载 HTTP 失败时提示错误，不把拒绝抛给页面', async () => {
    const errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => {})
    vi.spyOn(global, 'fetch').mockResolvedValue({
      ok: false,
      status: 401,
      headers: { get: () => null },
      body: null
    })

    const wrapper = mount(PerformanceBundleExportDialog, {
      props: { visible: true },
      global: {
        stubs: {
          'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
          'el-button': true,
          'el-icon': true,
          'el-checkbox': true,
          'el-checkbox-group': true,
          'el-alert': true,
          'el-tag': true,
          'el-progress': true,
          'el-result': true
        }
      }
    })
    wrapper.vm.taskId = 10

    await expect(wrapper.vm.handleDownload()).resolves.toBeUndefined()
    expect(errorSpy).toHaveBeenCalled()
    const message = errorSpy.mock.calls[0][0]
    expect(String(message)).toContain('401')
  })
})
