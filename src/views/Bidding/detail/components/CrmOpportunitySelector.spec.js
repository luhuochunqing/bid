import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref, nextTick } from 'vue'
import ElementPlus from 'element-plus'

// ---------------------------------------------------------------------------
// Mocks — 只 mock composable，组件模板行为保持真实
// ---------------------------------------------------------------------------

const mockLinkedOpportunity = ref(null)

vi.mock('./useCrmOpportunitySelector.js', () => ({
  useCrmOpportunitySelector: () => ({
    linkedOpportunity: mockLinkedOpportunity,
    showDialog: ref(false),
    searching: ref(false),
    loading: ref(false),
    searchPerformed: ref(false),
    results: ref([]),
    selectedId: ref(null),
    selectedChance: ref(null),
    totalCount: ref(0),
    currentPage: ref(1),
    pageSize: ref(10),
    showManualForm: ref(false),
    manualConfirmed: ref(false),
    searchForm: { name: '', code: '', projectStatus: [] },
    manualForm: {
      name: '', code: '', projectLeaderName: '', evaluationTime: '',
      projectStatusText: '', projectRiskText: '', remark: '',
    },
    openSearch: vi.fn(),
    doSearch: vi.fn(),
    onSelect: vi.fn(),
    confirmManual: vi.fn(),
    confirmLink: vi.fn(),
    resetSearch: vi.fn(),
  }),
}))

// 延迟 import，确保 mock 生效
const CrmOpportunitySelector = (await import('./CrmOpportunitySelector.vue')).default

function mountComponent(props = {}) {
  return mount(CrmOpportunitySelector, {
    props: {
      enabled: true,
      tenderer: '',
      registrationDeadline: '',
      bidOpeningTime: '',
      alreadyLinkedName: '',
      linkFailed: 0,
      ...props,
    },
    global: { plugins: [ElementPlus] },
  })
}

// ===========================================================================
// Tests — CO-308: 关联失败后 UI 状态回滚
// ===========================================================================

describe('CrmOpportunitySelector — CO-308 linkFailed prop 重置 UI 状态', () => {
  beforeEach(() => {
    mockLinkedOpportunity.value = null
  })

  it('初始 linkFailed=0 时,linkedOpportunity 不被重置(保留已有值)', async () => {
    mockLinkedOpportunity.value = { name: '已关联商机', code: 'C001', id: 1 }
    const wrapper = mountComponent({ linkFailed: 0 })
    await nextTick()
    expect(mockLinkedOpportunity.value).toEqual({ name: '已关联商机', code: 'C001', id: 1 })
    // 字段应展示已关联商机名
    expect(wrapper.text()).toContain('已关联商机')
  })

  it('linkFailed 从 0 → 1 时,linkedOpportunity 被重置为 null(关联失败回滚)', async () => {
    mockLinkedOpportunity.value = { name: '失败商机', code: 'C002', id: 2 }
    const wrapper = mountComponent({ linkFailed: 0 })
    await nextTick()
    expect(wrapper.text()).toContain('失败商机')

    // 模拟父组件 catch 块递增信号
    await wrapper.setProps({ linkFailed: 1 })
    await nextTick()

    expect(mockLinkedOpportunity.value).toBeNull()
    // 字段应回到"点击关联CRM商机"按钮状态,不再展示失败商机名
    expect(wrapper.text()).not.toContain('失败商机')
  })

  it('linkFailed 不变时,linkedOpportunity 不被重置(避免误触发)', async () => {
    mockLinkedOpportunity.value = { name: '稳定商机', code: 'C003', id: 3 }
    const wrapper = mountComponent({ linkFailed: 1 })
    await nextTick()
    expect(mockLinkedOpportunity.value).toEqual({ name: '稳定商机', code: 'C003', id: 3 })

    // 重新设置相同值,不应触发重置
    await wrapper.setProps({ linkFailed: 1 })
    await nextTick()
    expect(mockLinkedOpportunity.value).toEqual({ name: '稳定商机', code: 'C003', id: 3 })
  })

  it('linkFailed 多次递增,每次都触发重置(支持连续失败重试)', async () => {
    // 第一次失败
    mockLinkedOpportunity.value = { name: '失败1', code: 'C1', id: 11 }
    const wrapper = mountComponent({ linkFailed: 0 })
    await nextTick()
    await wrapper.setProps({ linkFailed: 1 })
    await nextTick()
    expect(mockLinkedOpportunity.value).toBeNull()

    // 用户重新选择,再次失败
    mockLinkedOpportunity.value = { name: '失败2', code: 'C2', id: 22 }
    await nextTick()
    expect(wrapper.text()).toContain('失败2')

    await wrapper.setProps({ linkFailed: 2 })
    await nextTick()
    expect(mockLinkedOpportunity.value).toBeNull()
    expect(wrapper.text()).not.toContain('失败2')
  })
})
