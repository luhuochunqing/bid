// CO-449: 批量导入CA信息后，批量导入弹窗停留在导入成功页面
// 根因：CAImportDialog.vue 缺少手动状态重置，依赖 destroy-on-close 不完全生效
// 修复：移除 destroy-on-close，添加 @close="resetAll"，新增 resetAll 函数

import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { nextTick } from 'vue'

// Mock caApi
const mockImportTask = vi.hoisted(() => ({
  taskId: 'task-123',
  status: 'COMPLETED',
  totalRows: 10,
  importedRows: 8,
  invalidRows: 2,
  errorDetails: '行3: 平台名称不能为空\n行5: CA类型无效'
}))

vi.mock('@/api/modules/ca.js', () => ({
  caApi: {
    importFile: vi.fn().mockResolvedValue({ data: { taskId: 'task-123' } }),
    getImportTask: vi.fn().mockResolvedValue({ data: mockImportTask }),
    getList: vi.fn().mockResolvedValue({ data: [] }),
    getOverview: vi.fn().mockResolvedValue({ data: { total: 0, expiring: 0, expired: 0, borrowed: 0 } })
  }
}))

vi.mock('@/api/client', () => ({
  default: {
    get: vi.fn().mockResolvedValue({ data: new Blob() }),
    defaults: { baseURL: 'http://localhost:18089' }
  }
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

import CAImportDialog from '../CAImportDialog.vue'

const stubs = {
  'el-dialog': {
    template: `<div v-if="modelValue"><slot /><slot name="footer" /></div>`,
    props: ['modelValue', 'title', 'width', 'closeOnClickModal'],
    emits: ['close', 'update:modelValue']
  },
  'el-alert': { template: '<div><slot /></div>' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-upload': {
    template: '<div><slot /><slot name="tip" /></div>',
    props: ['drag', 'autoUpload', 'limit', 'accept', 'fileList']
  },
  'el-progress': { template: '<div />' },
  'el-result': { template: '<div><slot name="sub-title" /><slot name="extra" /></div>', props: ['icon', 'title', 'subTitle'] },
  'el-icon': { template: '<i><slot /></i>' }
}

describe('CAImportDialog — CO-449 状态重置', () => {
  let wrapper

  function createWrapper(initialVisible = false) {
    return mount(CAImportDialog, {
      props: { modelValue: initialVisible },
      global: { stubs }
    })
  }

  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
    if (wrapper) wrapper.unmount()
  })

  it('初始状态：弹窗首次打开，应显示初始态（下载模板 + 上传区域）', async () => {
    wrapper = createWrapper(true)
    await flushPromises()

    // taskId 为 null，应渲染初始态
    expect(wrapper.vm.taskId).toBe(null)
    expect(wrapper.vm.status).toBe('')
    expect(wrapper.vm.fileList).toEqual([])

    // DOM 应包含下载模板提示
    const html = wrapper.html()
    expect(html).toContain('下载批量导入模板')
    expect(html).toContain('将 Excel 文件拖到此处')
  })

  it('导入成功后：taskId/status/task 应有值', async () => {
    wrapper = createWrapper(true)
    await flushPromises()

    // 模拟导入流程
    wrapper.vm.fileList = [{ name: 'test.xlsx', raw: new File([], 'test.xlsx') }]
    await wrapper.vm.startImport()
    await flushPromises()

    // 模拟轮询完成（advanceTimersByTime 会触发 setInterval 回调）
    vi.advanceTimersByTime(2500)
    await flushPromises()

    // 状态应变为成功（getImportTask mock 返回 COMPLETED）
    expect(wrapper.vm.taskId).toBe('task-123')
    expect(wrapper.vm.status).toBe('COMPLETED')
    expect(wrapper.vm.task.totalRows).toBe(10)
  })

  it('关闭弹窗时：resetAll 应清除所有状态', async () => {
    wrapper = createWrapper(true)
    await flushPromises()

    // 模拟导入成功后的状态
    wrapper.vm.taskId = 'task-123'
    wrapper.vm.status = 'COMPLETED'
    wrapper.vm.task = { totalRows: 10, importedRows: 8 }
    wrapper.vm.fileList = [{ name: 'test.xlsx' }]
    wrapper.vm.pollTimer = setInterval(() => {}, 1000)

    await nextTick()

    // 直接调用 resetAll 方法（模拟 @close 事件触发）
    wrapper.vm.resetAll()
    await flushPromises()

    // 所有状态应被清除
    expect(wrapper.vm.taskId).toBe(null)
    expect(wrapper.vm.status).toBe('')
    expect(wrapper.vm.task).toEqual({})
    expect(wrapper.vm.fileList).toEqual([])
    expect(wrapper.vm.pollTimer).toBe(null)
  })

  it('再次打开弹窗：应显示初始态，而非成功态', async () => {
    wrapper = createWrapper(true)
    await flushPromises()

    // 模拟导入成功后的状态
    wrapper.vm.taskId = 'task-123'
    wrapper.vm.status = 'COMPLETED'
    wrapper.vm.task = { totalRows: 10 }
    await nextTick()

    // 直接调用 resetAll（模拟关闭弹窗）
    wrapper.vm.resetAll()
    await flushPromises()

    // 验证状态已清除
    expect(wrapper.vm.taskId).toBe(null)
    expect(wrapper.vm.status).toBe('')
    expect(wrapper.vm.fileList).toEqual([])

    // DOM 应显示初始态
    const html = wrapper.html()
    expect(html).toContain('下载批量导入模板')
    expect(html).not.toContain('导入完成')
  })
})