import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import ArchiveDetailDrawer from '../ArchiveDetailDrawer.vue'
import httpClient from '@/api/client.js'

vi.mock('@/api/client.js', () => ({
  default: {
    get: vi.fn()
  }
}))

const stubs = {
  ElDrawer: { template: '<div class="el-drawer-stub"><slot /></div>' },
  ArchiveFileTable: {
    name: 'ArchiveFileTable',
    emits: ['download-package', 'preview', 'download'],
    template: '<button data-testid="trigger-download-package" @click="$emit(\'download-package\')" />'
  },
  ArchiveAuditLogTimeline: { template: '<div class="audit-log-stub" />' },
  ElDescriptions: { template: '<div class="desc-stub"><slot /></div>' },
  ElDescriptionsItem: { template: '<div class="desc-item-stub" />' },
  ElTag: { template: '<span class="tag-stub"><slot /></span>' }
}

const mountDrawer = (props = {}) => mount(ArchiveDetailDrawer, {
  props: {
    archive: { archiveId: 1, projectId: 10, projectName: '项目A' },
    visible: true,
    ...props
  },
  global: { stubs }
})

// jsdom 未实现 URL.createObjectURL，统一注入共享 mock 便于测试内覆写
const createObjectURLMock = vi.fn().mockReturnValue('blob:fake-url')

describe('ArchiveDetailDrawer - handleDownloadPackage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    createObjectURLMock.mockReturnValue('blob:fake-url')
    Object.defineProperty(URL, 'createObjectURL', {
      value: createObjectURLMock,
      writable: true,
      configurable: true
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      value: vi.fn(),
      writable: true,
      configurable: true
    })
    vi.spyOn(document.body, 'appendChild').mockImplementation(() => {})
    vi.spyOn(document.body, 'removeChild').mockImplementation(() => {})
    HTMLAnchorElement.prototype.click = vi.fn()
    // 默认给 fetchDetail 一个空响应，避免 ElMessage.warn 噪音（组件代码用 warn 而非 warning，是另一个问题）
    httpClient.get.mockResolvedValue({ files: [], logs: [] })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('调用 export-zip 接口且 responseType=blob', async () => {
    const fakeBlob = new Blob(['zip-bytes'], { type: 'application/zip' })
    httpClient.get.mockResolvedValue({ data: fakeBlob, headers: {}, config: {} })

    const wrapper = mountDrawer()
    await flushPromises()
    httpClient.get.mockClear()

    await wrapper.find('[data-testid="trigger-download-package"]').trigger('click')
    await flushPromises()

    expect(httpClient.get).toHaveBeenCalledWith(
      '/api/archive/export-zip/10',
      { responseType: 'blob' }
    )
    wrapper.unmount()
  })

  it('CO-429: 用 res.data 构造 Blob（而非整个 AxiosResponse），下载到正确 ZIP 内容', async () => {
    const fakeBlob = new Blob(['zip-bytes'], { type: 'application/zip' })
    const fakeResponse = { data: fakeBlob, headers: {}, config: { responseType: 'blob' } }
    httpClient.get.mockResolvedValue(fakeResponse)

    // spy 全局 Blob 构造函数，捕获传入的 parts[0]
    const originalBlob = globalThis.Blob
    let capturedParts = null
    globalThis.Blob = vi.fn((parts, options) => {
      capturedParts = parts
      return new originalBlob(parts, options)
    })

    const wrapper = mountDrawer()
    await flushPromises()

    await wrapper.find('[data-testid="trigger-download-package"]').trigger('click')
    await flushPromises()

    globalThis.Blob = originalBlob

    expect(capturedParts).not.toBeNull()
    // 关键断言：parts[0] 必须是 Blob（即 res.data），而非整个 AxiosResponse
    expect(capturedParts[0]).toBe(fakeBlob)
    wrapper.unmount()
  })

  it('档案信息缺少 projectId 时不调用 export-zip 接口', async () => {
    const wrapper = mountDrawer({ archive: { archiveId: 1, projectName: '项目A' } })
    await flushPromises()
    httpClient.get.mockClear()

    await wrapper.find('[data-testid="trigger-download-package"]').trigger('click')
    await flushPromises()

    // 仅断言 export-zip 接口未被调用（fetchDetail 的 /api/archive/{id} 调用不算）
    const exportZipCalls = httpClient.get.mock.calls.filter(
      ([url]) => typeof url === 'string' && url.startsWith('/api/archive/export-zip')
    )
    expect(exportZipCalls).toHaveLength(0)
    wrapper.unmount()
  })
})
