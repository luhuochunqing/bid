import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const {
  routerPush,
  fetchTemplateList,
  saveTemplate,
  getCurrentUserId,
  warning,
  error,
  success
} = vi.hoisted(() => ({
  routerPush: vi.fn(),
  fetchTemplateList: vi.fn(),
  saveTemplate: vi.fn(),
  getCurrentUserId: vi.fn(() => 42),
  warning: vi.fn(),
  error: vi.fn(),
  success: vi.fn()
}))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRouter: () => ({
      push: routerPush
    })
  }
})

vi.mock('@/stores/project', () => ({
  useProjectStore: () => ({
    inProgressProjects: []
  })
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    currentUser: {
      id: 42,
      name: '测试用户'
    }
  })
}))

vi.mock('@/api', () => ({
  knowledgeApi: {
    templates: {
      getDetail: vi.fn()
    }
  }
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual('element-plus')
  return {
    ...actual,
    ElMessage: { warning, error, success },
    ElMessageBox: { confirm: vi.fn() }
  }
})

vi.mock('./templateLibraryRemote.js', () => ({
  fetchTemplateList,
  saveTemplate,
  getCurrentUserId,
  copyTemplateRecord: vi.fn(),
  deleteTemplateRecord: vi.fn(),
  loadTemplateVersions: vi.fn(),
  recordTemplateDownload: vi.fn(),
  recordTemplateUse: vi.fn(),
  upsertTemplateInCollection: vi.fn()
}))

import { useTemplateLibraryPage } from './useTemplateLibraryPage.js'

const Harness = defineComponent({
  setup() {
    return useTemplateLibraryPage()
  },
  template: '<div />'
})

describe('useTemplateLibraryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    fetchTemplateList.mockResolvedValue({
      templates: [],
      featurePlaceholder: null,
      errorMessage: ''
    })
    saveTemplate.mockResolvedValue({
      success: true,
      data: {
        id: 1001,
        name: '测试模板',
        productType: '智慧交通',
        industry: '交通',
        documentType: '技术方案'
      }
    })
  })

  it('keeps the dialog open and warns when required three-dimensional fields are missing', async () => {
    const wrapper = mount(Harness)
    await flushPromises()

    wrapper.vm.openCreateDialog()
    wrapper.vm.templateForm.name = '缺少维度模板'

    await wrapper.vm.submitTemplate()

    expect(wrapper.vm.submitError).toBe('请选择产品类型、行业和文档类型')
    expect(wrapper.vm.templateFormErrors).toMatchObject({
      productType: '请选择产品类型',
      industry: '请选择行业'
    })
    expect(saveTemplate).not.toHaveBeenCalled()
    expect(wrapper.vm.upsertDialogVisible).toBe(true)
  })

  it('resets filters back to the default state and reloads with the active category only', async () => {
    const wrapper = mount(Harness)
    await flushPromises()

    wrapper.vm.filters.name = '智慧'
    wrapper.vm.filters.productType = '智慧交通'
    wrapper.vm.filters.industry = '交通'
    wrapper.vm.filters.documentType = '技术方案'
    wrapper.vm.filters.tags = ['模板']
    wrapper.vm.filters.sort = 'downloads'

    await wrapper.vm.handleReset()

    expect(wrapper.vm.filters).toMatchObject({
      name: '',
      productType: '',
      industry: '',
      documentType: '',
      tags: [],
      sort: 'default'
    })
    expect(fetchTemplateList).toHaveBeenLastCalledWith({
      category: 'all',
      name: '',
      productType: '',
      industry: '',
      documentType: ''
    })
  })

  it('keeps an explicit empty list state when the backend returns no matching templates', async () => {
    fetchTemplateList.mockResolvedValueOnce({
      templates: [],
      featurePlaceholder: null,
      errorMessage: ''
    })

    const wrapper = mount(Harness)
    await flushPromises()

    expect(wrapper.vm.filteredTemplates).toEqual([])
    expect(wrapper.vm.pagedTemplates).toEqual([])
    expect(wrapper.vm.featurePlaceholder).toBeNull()
    expect(wrapper.vm.workspaceEmptyState).toMatchObject({
      type: 'initial',
      title: '模板资产工作台已就绪'
    })
  })

  it('shows filtered empty state and summary items when official filters return no results', async () => {
    fetchTemplateList.mockResolvedValue({
      templates: [],
      featurePlaceholder: null,
      errorMessage: ''
    })

    const wrapper = mount(Harness)
    await flushPromises()

    wrapper.vm.filters.productType = '智慧交通'
    wrapper.vm.filters.industry = '交通'

    await wrapper.vm.handleSearch()

    expect(fetchTemplateList).toHaveBeenLastCalledWith({
      category: 'all',
      name: '',
      productType: '智慧交通',
      industry: '交通',
      documentType: ''
    })
    expect(wrapper.vm.filterSummaryItems).toEqual([
      { key: 'productType', label: '产品类型', value: '智慧交通', emphasis: 'primary' },
      { key: 'industry', label: '行业', value: '交通', emphasis: 'primary' }
    ])
    expect(wrapper.vm.workspaceEmptyState).toMatchObject({
      type: 'filtered',
      title: '没有匹配当前条件的模板'
    })
  })
})
