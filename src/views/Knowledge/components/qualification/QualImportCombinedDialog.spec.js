import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'

const mockHttp = vi.hoisted(() => ({
  post: vi.fn(),
  get: vi.fn()
}))

vi.mock('@/api/client', () => ({
  default: mockHttp
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

vi.mock('@element-plus/icons-vue', () => ({
  UploadFilled: { template: '<i />' },
  InfoFilled: { template: '<i />' },
  CircleCheckFilled: { template: '<i />' }
}))

import QualImportCombinedDialog from './QualImportCombinedDialog.vue'

const stubs = {
  'el-dialog': {
    template: `<div v-if="modelValue"><slot /><slot name="footer" /></div>`,
    props: ['modelValue', 'title', 'width', 'closeOnClickModal'],
    emits: ['close', 'update:modelValue']
  },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-upload': {
    template: '<div><slot /></div>',
    props: ['drag', 'autoUpload', 'limit', 'accept', 'fileList'],
    emits: ['change', 'remove']
  },
  'el-icon': { template: '<i><slot /></i>' },
  'el-table': { template: '<div><slot /></div>', props: ['data'] },
  'el-table-column': { template: '<div><slot /></div>', props: ['prop', 'label', 'width'] }
}

describe('QualImportCombinedDialog - CO-470', () => {
  let wrapper

  beforeEach(() => {
    mockHttp.post.mockReset()
    mockHttp.get.mockReset()
  })

  afterEach(() => {
    if (wrapper) wrapper.unmount()
  })

  function createWrapper(initialVisible = true) {
    return mount(QualImportCombinedDialog, {
      props: { modelValue: initialVisible },
      global: { stubs }
    })
  }

  it('should correctly parse import result with standard ApiResponse structure', async () => {
    const mockResponse = {
      success: true,
      code: 200,
      message: '导入完成',
      data: {
        import: {
          total: 2,
          success: 1,
          failed: 1,
          errors: []
        },
        attachments: null
      }
    }

    mockHttp.post.mockResolvedValue(mockResponse)

    wrapper = createWrapper(true)
    await flushPromises()

    await wrapper.vm.handleExcelChange({ raw: new File(['test'], 'test.xlsx') })
    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(wrapper.vm.result).not.toBeNull()
    expect(wrapper.vm.result.import.total).toBe(2)
    expect(wrapper.vm.result.import.success).toBe(1)
    expect(wrapper.vm.result.import.failed).toBe(1)
  })

  it('should show zero count when response data is empty', async () => {
    const mockResponse = {
      success: true,
      code: 200,
      message: '导入完成',
      data: {
        import: {
          total: 0,
          success: 0,
          failed: 0,
          errors: []
        },
        attachments: null
      }
    }

    mockHttp.post.mockResolvedValue(mockResponse)

    wrapper = createWrapper(true)
    await flushPromises()

    await wrapper.vm.handleExcelChange({ raw: new File(['test'], 'test.xlsx') })
    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(wrapper.vm.result?.import.total).toBe(0)
  })
})