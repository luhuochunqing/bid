import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

// CO-370 场景5 防复发测试：锁定 QualBatchUploadDialog 正确解包 res.data
// 历史缺陷：曾误写成 res?.data?.data（多解一层），导致 total/success/failed 全部为 0
const mockHttpPost = vi.fn()
vi.mock('@/api/client', () => ({
  default: { post: (...args) => mockHttpPost(...args) },
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('@element-plus/icons-vue', () => ({
  UploadFilled: { template: '<span />' },
  InfoFilled: { template: '<span />' },
}))

import QualBatchUploadDialog from './QualBatchUploadDialog.vue'

const stubs = {
  'el-dialog': {
    template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width'],
  },
  'el-upload': {
    template: '<div class="el-upload-stub" />',
    props: ['autoUpload', 'onChange', 'accept', 'drag', 'multiple'],
  },
  'el-button': { props: ['loading', 'disabled', 'type'], template: '<button :disabled="disabled || loading"><slot /></button>' },
  'el-icon': { template: '<span><slot /></span>' },
}

function createWrapper() {
  return mount(QualBatchUploadDialog, {
    global: { stubs, plugins: [createPinia()] },
    props: { modelValue: true },
  })
}

// 模拟后端响应：axios 拦截器已解包 response.data，所以 res.data = BatchAttachResultDTO
// BatchAttachResultDTO 结构：{ total, success, failed, unmatched }
function mockBatchAttachResponse({ total = 3, success = 2, failed = 1, unmatched = [] } = {}) {
  mockHttpPost.mockResolvedValue({
    data: { total, success, failed, unmatched },
  })
}

describe('CO-370 场景5 QualBatchUploadDialog 防复发：res.data 正确解包', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('上传成功后正确显示 total/success/failed 统计（不能多解一层 .data）', async () => {
    mockBatchAttachResponse({ total: 3, success: 2, failed: 1, unmatched: [{ fileName: 'bad.pdf', reason: '证书编号不存在' }] })
    const wrapper = createWrapper()
    const vm = wrapper.vm
    vm.fileList = [{ raw: new File([''], 'test.pdf') }]
    await vm.handleUpload?.()
    await flushPromises()

    const result = vm.result
    expect(result).not.toBeNull()
    expect(result.total).toBe(3)
    expect(result.success).toBe(2)
    expect(result.failed).toBe(1)
  })

  it('不使用 res.data.data 错误解包（历史缺陷场景）', async () => {
    // 模拟正确的后端响应：拦截器返回 { data: BatchAttachResultDTO }
    // 如果代码误用 res?.data?.data，会得到 undefined
    mockBatchAttachResponse({ total: 5, success: 5, failed: 0 })
    const wrapper = createWrapper()
    const vm = wrapper.vm
    vm.fileList = [{ raw: new File([''], 'a.pdf') }]
    await vm.handleUpload?.()
    await flushPromises()

    const result = vm.result
    expect(result.total).toBe(5)
    expect(result.success).toBe(5)
    // 如果代码回退到 res.data.data，total/success 都会是 undefined -> 默认 fileList.length / 0
    // 这里强校验 total 必须等于后端返回值，而不是 fallback 到 fileList.length
    expect(result.total).not.toBe(1) // 1 是 fileList.length，若误用 fallback 会得到 1
  })
})
