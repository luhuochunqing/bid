import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import ProjectArchive from '../ProjectArchive.vue'
import httpClient from '@/api/client.js'

vi.mock('@/api/client.js', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn()
  }
}))

describe('ProjectArchive', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads stats and project list on mount', async () => {
    httpClient.get
      .mockResolvedValueOnce({
        totalArchives: 10,
        closedProjects: 5,
        caseCount: 3,
        reuseCount: 8
      })
      .mockResolvedValueOnce({
        content: [
          { archiveId: 1, projectName: '项目A', projectStatus: 'WON', projectType: 'OFFICE', bidResult: 'WON', fileCount: 5, lastUploadedAt: '2026-05-10T12:00:00', projectManager: '张经理', bidManager: '李经理' },
          { archiveId: 2, projectName: '项目B', projectStatus: 'BIDDING', projectType: 'COMPREHENSIVE', bidResult: 'IN_PROGRESS', fileCount: 3, lastUploadedAt: '2026-05-11T12:00:00', projectManager: '王经理', bidManager: '赵经理' }
        ],
        totalElements: 2
      })

    const wrapper = mount(ProjectArchive, {
      global: {
        directives: {
          loading: vi.fn()
        },
        stubs: {
          ElCard: { template: '<div class="el-card-stub"><slot /><slot name="header" /></div>' },
          ElForm: { template: '<form><slot /></form>' },
          ElFormItem: { props: ['label'], template: '<div><slot /></div>' },
          ElInput: true,
          ElDatePicker: true,
          ElSelect: true,
          ElOption: true,
          ElButton: true,
          ElTable: {
            props: ['data'],
            template: '<div class="table-stub"><slot /></div>'
          },
          ElTableColumn: true,
          ElPagination: true,
          ElDrawer: true,
          ElRadioGroup: true,
          ElRadioButton: true,
          ElIcon: true,
          ElTag: true,
          ElEmpty: true,
          ElTimeline: true,
          ElTimelineItem: true,
          FileCategoryPopover: true,
          ArchiveStatsCards: true,
          ArchiveStatusTabs: true,
          ArchiveDetailDrawer: true,
          UserPicker: {
            name: 'UserPicker',
            props: ['modelValue', 'mode', 'valueField', 'placeholder', 'initialOptions', 'clearable'],
            emits: ['update:modelValue', 'select'],
            template: '<div class="user-picker-stub" />',
          },
          Files: true,
          Search: true,
          Refresh: true
        }
      },
      attachTo: document.body
    })

    // Wait for onMounted to fire and async API calls to complete
    await flushPromises()

    // Verify both API calls were made
    expect(httpClient.get).toHaveBeenCalledTimes(2)
    expect(httpClient.get).toHaveBeenCalledWith('/api/archive/stats')
    expect(httpClient.get).toHaveBeenCalledWith('/api/archive', expect.any(Object))

    // CO-422: stats 卡片已移除，仅校验列表数据
    expect(wrapper.vm.tableData).toHaveLength(2)
    expect(wrapper.vm.tableData[0].projectName).toBe('项目A')

    wrapper.unmount()
  })

  it('sends correct filter parameters when search is triggered', async () => {
    const getSpy = vi.fn()
      .mockResolvedValueOnce({
        totalArchives: 0,
        closedProjects: 0,
        caseCount: 0,
        reuseCount: 0
      })
      .mockResolvedValueOnce({
        content: [],
        totalElements: 0
      })
    httpClient.get = getSpy

    const wrapper = mount(ProjectArchive, {
      global: {
        stubs: {
          ElCard: { template: '<div class="el-card-stub"><slot /><slot name="header" /></div>' }, ElForm: true, ElFormItem: { props: ['label'], template: '<div><slot /></div>' }, ElInput: true, ElDatePicker: true,
          ElSelect: true, ElOption: true, ElButton: true, ElTable: true, ElTableColumn: true,
          ElPagination: true, ElDrawer: true, ElRadioGroup: true, ElRadioButton: true,
          ElIcon: true, ElTag: true, ElEmpty: true, ElTimeline: true, ElTimelineItem: true,
          FileCategoryPopover: true, ArchiveStatsCards: true,
          ArchiveStatusTabs: true, ArchiveDetailDrawer: true,
          UserPicker: {
            name: 'UserPicker',
            props: ['modelValue', 'mode', 'valueField', 'placeholder', 'initialOptions', 'clearable'],
            emits: ['update:modelValue', 'select'],
            template: '<div class="user-picker-stub" />',
          },
          Files: true, Search: true, Refresh: true
        }
      },
      attachTo: document.body
    })

    await flushPromises()

    // Trigger search with a project name filter
    wrapper.vm.filters.projectName = '西域'
    await wrapper.vm.handleSearch()
    await wrapper.vm.$nextTick()

    // Verify the list API was called with the filter
    // 3 calls: stats, initial list, search-triggered list
    expect(getSpy).toHaveBeenCalledTimes(3)
    const listCall = getSpy.mock.calls[2]
    expect(listCall[0]).toBe('/api/archive')
    expect(listCall[1].params.projectName).toBe('西域')

    wrapper.unmount()
  })

  it('renders UserPickers for project manager and bid manager filters', async () => {
    httpClient.get
      .mockResolvedValueOnce({
        totalArchives: 1,
        closedProjects: 0,
        caseCount: 0,
        reuseCount: 0,
        projectManagers: [{ id: 1, name: '张经理', employeeNumber: '20260509' }],
        bidManagers: [{ id: 2, name: '李经理', employeeNumber: '20260510' }],
      })
      .mockResolvedValueOnce({
        content: [],
        totalElements: 0
      })

    const wrapper = mount(ProjectArchive, {
      global: {
        stubs: {
          ElCard: { template: '<div class="el-card-stub"><slot /><slot name="header" /></div>' }, ElForm: true, ElFormItem: { props: ['label'], template: '<div><slot /></div>' }, ElInput: true, ElDatePicker: true,
          ElSelect: true, ElOption: true, ElButton: true, ElTable: true, ElTableColumn: true,
          ElPagination: true, ElDrawer: true, ElRadioGroup: true, ElRadioButton: true,
          ElIcon: true, ElTag: true, ElEmpty: true, ElTimeline: true, ElTimelineItem: true,
          FileCategoryPopover: true, ArchiveStatsCards: true,
          ArchiveStatusTabs: true, ArchiveDetailDrawer: true,
          UserPicker: {
            name: 'UserPicker',
            props: ['modelValue', 'mode', 'valueField', 'placeholder', 'initialOptions', 'clearable'],
            emits: ['update:modelValue', 'select'],
            template: '<div class="user-picker-stub" />',
          },
          Files: true, Search: true, Refresh: true
        }
      },
      attachTo: document.body
    })

    await flushPromises()

    expect(wrapper.vm.projectManagerOptions).toEqual([{ id: 1, name: '张经理', employeeNumber: '20260509' }])
    expect(wrapper.vm.bidManagerOptions).toEqual([{ id: 2, name: '李经理', employeeNumber: '20260510' }])

    wrapper.unmount()
  })

  // CO-453: 预览 PDF 时应传 res.data (Blob) 给 createObjectURL，而非整个 axios response 对象
  it('passes Blob (res.data) to createObjectURL when previewing PDF', async () => {
    const mockBlob = new Blob(['%PDF-1.4 mock'], { type: 'application/pdf' })
    httpClient.get
      .mockResolvedValueOnce({ totalArchives: 0, closedProjects: 0, caseCount: 0, reuseCount: 0 })
      .mockResolvedValueOnce({ content: [], totalElements: 0 })
      .mockResolvedValueOnce({ data: mockBlob, status: 200, headers: {} })

    // jsdom 未实现 URL.createObjectURL，需手动定义后再 spy
    if (!URL.createObjectURL) {
      URL.createObjectURL = vi.fn()
    }
    const createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url')
    const openSpy = vi.spyOn(window, 'open').mockReturnValue(null)

    const wrapper = mount(ProjectArchive, {
      global: {
        stubs: {
          ElCard: { template: '<div><slot /><slot name="header" /></div>' },
          ElForm: true, ElFormItem: { props: ['label'], template: '<div><slot /></div>' },
          ElInput: true, ElDatePicker: true, ElSelect: true, ElOption: true,
          ElButton: true, ElTable: true, ElTableColumn: true, ElPagination: true,
          ElDrawer: true, ElRadioGroup: true, ElRadioButton: true,
          ElIcon: true, ElTag: true, ElEmpty: true, ElTimeline: true, ElTimelineItem: true,
          FileCategoryPopover: true, ArchiveStatsCards: true, ArchiveStatusTabs: true,
          ArchiveDetailDrawer: true,
          UserPicker: { name: 'UserPicker', props: ['modelValue', 'mode', 'valueField', 'placeholder', 'initialOptions', 'clearable'], emits: ['update:modelValue', 'select'], template: '<div />' },
          Files: true, Search: true, Refresh: true
        }
      },
      attachTo: document.body
    })

    await flushPromises()

    await wrapper.vm.handlePreviewFile({ fileId: 1, fileName: 'test.pdf' })
    await flushPromises()

    expect(httpClient.get).toHaveBeenCalledWith('/api/archive/files/1/preview', { responseType: 'blob' })
    expect(createObjectURLSpy).toHaveBeenCalledTimes(1)
    const passedArg = createObjectURLSpy.mock.calls[0][0]
    expect(passedArg).toBe(mockBlob)
    expect(openSpy).toHaveBeenCalledWith('blob:mock-url', '_blank')

    createObjectURLSpy.mockRestore()
    openSpy.mockRestore()
    wrapper.unmount()
  })

  // CO-496: 切换文件视图时应自动加载数据
  it('loads data when switching from project view to file view', async () => {
    httpClient.get
      .mockResolvedValueOnce({ totalArchives: 0, closedProjects: 0, caseCount: 0, reuseCount: 0 })
      .mockResolvedValueOnce({ content: [], totalElements: 0 })
      .mockResolvedValueOnce({ content: [], totalElements: 0 })

    const wrapper = mount(ProjectArchive, {
      global: {
        stubs: {
          ElCard: { template: '<div><slot /><slot name="header" /></div>' },
          ElForm: true, ElFormItem: { props: ['label'], template: '<div><slot /></div>' },
          ElInput: true, ElDatePicker: true, ElSelect: true, ElOption: true,
          ElButton: true, ElTable: true, ElTableColumn: true, ElPagination: true,
          ElDrawer: true, ElRadioGroup: true, ElRadioButton: true,
          ElIcon: true, ElTag: true, ElEmpty: true, ElTimeline: true, ElTimelineItem: true,
          FileCategoryPopover: true, ArchiveStatsCards: true, ArchiveStatusTabs: true,
          ArchiveDetailDrawer: true,
          UserPicker: { name: 'UserPicker', props: ['modelValue', 'mode', 'valueField', 'placeholder', 'initialOptions', 'clearable'], emits: ['update:modelValue', 'select'], template: '<div />' },
          Files: true, Search: true, Refresh: true,
          FileListViewTable: { props: ['loading', 'tableData', 'totalElements', 'page', 'pageSize'], template: '<div />' }
        }
      },
      attachTo: document.body
    })

    await flushPromises()

    expect(httpClient.get).toHaveBeenCalledTimes(2)

    wrapper.vm.activeView = 'file'
    await wrapper.vm.$nextTick()
    await flushPromises()

    expect(httpClient.get).toHaveBeenCalledTimes(3)
    const lastCall = httpClient.get.mock.calls[2]
    expect(lastCall[0]).toBe('/api/archive/files')

    wrapper.unmount()
  })

  // CO-453: 下载文件时同样应传 res.data (Blob) 给 triggerBlobDownload，而非整个 axios response 对象
  it('passes Blob (res.data) to triggerBlobDownload when downloading file', async () => {
    const mockBlob = new Blob(['file content'], { type: 'application/octet-stream' })
    httpClient.get
      .mockResolvedValueOnce({ totalArchives: 0, closedProjects: 0, caseCount: 0, reuseCount: 0 })
      .mockResolvedValueOnce({ content: [], totalElements: 0 })
      .mockResolvedValueOnce({ data: mockBlob, status: 200, headers: {} })

    // jsdom 未实现 URL.createObjectURL，需手动定义后再 spy
    if (!URL.createObjectURL) {
      URL.createObjectURL = vi.fn()
    }
    const createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url')

    const wrapper = mount(ProjectArchive, {
      global: {
        stubs: {
          ElCard: { template: '<div><slot /><slot name="header" /></div>' },
          ElForm: true, ElFormItem: { props: ['label'], template: '<div><slot /></div>' },
          ElInput: true, ElDatePicker: true, ElSelect: true, ElOption: true,
          ElButton: true, ElTable: true, ElTableColumn: true, ElPagination: true,
          ElDrawer: true, ElRadioGroup: true, ElRadioButton: true,
          ElIcon: true, ElTag: true, ElEmpty: true, ElTimeline: true, ElTimelineItem: true,
          FileCategoryPopover: true, ArchiveStatsCards: true, ArchiveStatusTabs: true,
          ArchiveDetailDrawer: true,
          UserPicker: { name: 'UserPicker', props: ['modelValue', 'mode', 'valueField', 'placeholder', 'initialOptions', 'clearable'], emits: ['update:modelValue', 'select'], template: '<div />' },
          Files: true, Search: true, Refresh: true
        }
      },
      attachTo: document.body
    })

    await flushPromises()

    await wrapper.vm.handleDownloadFile({ fileId: 2, fileName: 'report.pdf' })
    await flushPromises()

    expect(httpClient.get).toHaveBeenCalledWith('/api/archive/files/2/download', { responseType: 'blob' })
    expect(createObjectURLSpy).toHaveBeenCalledTimes(1)
    // downloadBlob 内部走 new Blob([blob])，传入正确 Blob 时输出也是 Blob（内容为原始文件）
    // 若误传 response 对象，new Blob([res]) 会序列化为 "[object Object]"（size=15）
    const passedArg = createObjectURLSpy.mock.calls[0][0]
    expect(passedArg).toBeInstanceOf(Blob)
    expect(passedArg.size).toBe(mockBlob.size)

    createObjectURLSpy.mockRestore()
    wrapper.unmount()
  })
})
