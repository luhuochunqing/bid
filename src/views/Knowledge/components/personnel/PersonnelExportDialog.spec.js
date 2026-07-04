// Input: src/views/Knowledge/components/personnel/PersonnelExportDialog.vue
// Output: 验证导出完成态 UI：0 条记录时不显示下载按钮
// Pos: src/views/Knowledge/components/personnel/__tests__/

import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

// 异步 composable 工厂，避免不同测试共享 ref
function createMockTask(overrides = {}) {
  const taskId = { value: overrides.taskId ?? 'exp-001' }
  const status = { value: overrides.status ?? 'COMPLETED' }
  const progressPercent = { value: overrides.progressPercent ?? 100 }
  const progressText = { value: overrides.progressText ?? '导出完成' }
  const totalCount = { value: overrides.totalCount ?? 0 }
  const errorMessage = { value: overrides.errorMessage ?? '' }
  const active = { value: false }

  return {
    taskId,
    status,
    progressPercent,
    progressText,
    totalCount,
    errorMessage,
    active,
    isProcessing: { value: status.value === 'PROCESSING' || status.value === 'PENDING' },
    isCompleted: { value: status.value === 'COMPLETED' || status.value === 'PARTIAL_SUCCESS' },
    isFailed: { value: status.value === 'FAILED' || status.value === 'UNKNOWN' || status.value === 'NOT_FOUND' },
    startTask: vi.fn(),
    reset: vi.fn()
  }
}

const mockDownloadExportFile = vi.fn()

vi.mock('./usePersonnelBatchTask.js', () => ({
  usePersonnelBatchTask: vi.fn(() => createMockTask())
}))

vi.mock('@/api/modules/personnelBatchApi.js', () => ({
  default: {
    downloadExportFile: (...args) => mockDownloadExportFile(...args)
  }
}))

import { usePersonnelBatchTask } from './usePersonnelBatchTask.js'
import PersonnelExportDialog from './PersonnelExportDialog.vue'

describe('PersonnelExportDialog.vue', () => {
  const stubs = {
    'el-dialog': {
      template: '<div class="el-dialog" :data-title="title"><slot /><slot name="footer" /></div>',
      props: ['title', 'modelValue']
    },
    'el-result': {
      template: '<div class="el-result"><slot /><slot name="extra" /></div>',
      props: ['icon', 'title', 'subTitle']
    },
    'el-form': { template: '<form class="el-form"><slot /></form>' },
    'el-form-item': { template: '<div class="el-form-item"><slot /></div>' },
    'el-input': { template: '<input class="el-input" />', props: ['modelValue'] },
    'el-select': { template: '<select class="el-select"><slot /></select>', props: ['modelValue'] },
    'el-option': { template: '<option class="el-option" />', props: ['label', 'value'] },
    'el-button': {
      template: '<button class="el-button" :data-type="type"><slot /></button>',
      props: ['type']
    },
    'el-progress': { template: '<div class="el-progress"><slot /></div>', props: ['percentage', 'status'] },
    'el-icon': { template: '<span class="el-icon"><slot /></span>' }
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('导出 0 条记录完成态：不显示"下载导出文件"按钮', async () => {
    usePersonnelBatchTask.mockReturnValue(createMockTask({ totalCount: 0 }))

    const wrapper = mount(PersonnelExportDialog, {
      props: { modelValue: true },
      global: { stubs }
    })
    await nextTick()

    const buttons = wrapper.findAll('.el-button')
    const downloadButton = buttons.find(btn => btn.text() === '下载导出文件')
    const closeButton = buttons.find(btn => btn.text() === '关闭')

    expect(downloadButton).toBeUndefined()
    expect(closeButton).toBeDefined()
  })

  it('导出 > 0 条记录完成态：显示"下载导出文件"按钮', async () => {
    usePersonnelBatchTask.mockReturnValue(createMockTask({ totalCount: 5 }))

    const wrapper = mount(PersonnelExportDialog, {
      props: { modelValue: true },
      global: { stubs }
    })
    await nextTick()

    const buttons = wrapper.findAll('.el-button')
    const downloadButton = buttons.find(btn => btn.text() === '下载导出文件')
    const closeButton = buttons.find(btn => btn.text() === '关闭')

    expect(downloadButton).toBeDefined()
    expect(closeButton).toBeDefined()
  })

  it('点击下载按钮调用 personnelBatchApi.downloadExportFile', async () => {
    usePersonnelBatchTask.mockReturnValue(createMockTask({ totalCount: 3, taskId: 'exp-003' }))

    const wrapper = mount(PersonnelExportDialog, {
      props: { modelValue: true },
      global: { stubs }
    })
    await nextTick()

    const buttons = wrapper.findAll('.el-button')
    const downloadButton = buttons.find(btn => btn.text() === '下载导出文件')
    expect(downloadButton).toBeDefined()

    await downloadButton.trigger('click')
    expect(mockDownloadExportFile).toHaveBeenCalledWith('exp-003')
  })
})
