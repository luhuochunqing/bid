import { mount } from '@vue/test-utils'
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
          ElCard: true,
          ElForm: true,
          ElFormItem: true,
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
          Files: true,
          Search: true,
          Refresh: true
        }
      },
      attachTo: document.body
    })

    // Wait for onMounted to fire and async API calls to complete
    await new Promise(resolve => setTimeout(resolve, 150))
    await wrapper.vm.$nextTick()

    // Verify both API calls were made
    expect(httpClient.get).toHaveBeenCalledTimes(2)
    expect(httpClient.get).toHaveBeenCalledWith('/api/archive/stats')
    expect(httpClient.get).toHaveBeenCalledWith('/api/archive', expect.any(Object))

    // Verify stats data
    expect(wrapper.vm.stats.totalArchives).toBe(10)
    expect(wrapper.vm.stats.caseCount).toBe(3)

    // Verify table data
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
          ElCard: true, ElForm: true, ElFormItem: true, ElInput: true, ElDatePicker: true,
          ElSelect: true, ElOption: true, ElButton: true, ElTable: true, ElTableColumn: true,
          ElPagination: true, ElDrawer: true, ElRadioGroup: true, ElRadioButton: true,
          ElIcon: true, ElTag: true, ElEmpty: true, ElTimeline: true, ElTimelineItem: true,
          FileCategoryPopover: true, ArchiveStatsCards: true,
          ArchiveStatusTabs: true, ArchiveDetailDrawer: true, Files: true, Search: true, Refresh: true
        }
      },
      attachTo: document.body
    })

    await new Promise(resolve => setTimeout(resolve, 150))
    await wrapper.vm.$nextTick()

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
})
