// Input: OpportunityPredictionCard + mocked workbenchApi
// Output: 表格渲染 / 分页 / 空态 / 标签映射 / 临近红点 / 搜索筛选与导出覆盖
// Pos: src/views/Dashboard/__tests__/ - 工作台商机预测卡片组件测试

import { flushPromises, mount } from '@vue/test-utils'
import { computed } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = vi.hoisted(() => ({
  getOpportunityPredictions: vi.fn(),
  routerPush: vi.fn(),
  exportExcel: vi.fn(),
  notifyExportSuccess: vi.fn(),
  messageWarning: vi.fn(),
}))

vi.mock('@/api', () => ({ workbenchApi: { getOpportunityPredictions: apiMock.getOpportunityPredictions } }))
vi.mock('@/api/modules/export.js', () => ({
  exportApi: { exportExcel: apiMock.exportExcel },
  notifyExportSuccess: apiMock.notifyExportSuccess,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, ElMessage: { warning: apiMock.messageWarning, success: vi.fn() } }
})
vi.mock('vue-router', () => ({ useRouter: () => ({ push: apiMock.routerPush }) }))

import OpportunityPredictionCard from '@/views/Dashboard/components/OpportunityPredictionCard.vue'

const stubs = {
  'el-table': {
    props: ['data', 'rowClassName', 'size'],
    emits: ['row-click'],
    provide() { return { tableRows: computed(() => this.data || []) } },
    template: '<div class="el-table-stub"><slot /></div>',
  },
  'el-table-column': {
    props: ['label', 'prop', 'width', 'minWidth', 'align', 'fixed', 'showOverflowTooltip'],
    inject: ['tableRows'],
    template:
      '<div class="el-table-column-stub" :data-label="label">' +
      '<template v-for="(row, idx) in tableRows" :key="idx"><slot :row="row" :$index="idx" /></template>' +
      '</div>',
  },
  'el-tag': { props: ['type', 'size', 'effect'], template: '<span class="el-tag-stub"><slot /></span>' },
  'el-pagination': {
    props: ['currentPage', 'pageSize', 'total', 'layout', 'small'],
    emits: ['update:currentPage'],
    template: '<div class="el-pagination-stub" />',
  },
  'el-empty': { props: ['description', 'imageSize'], template: '<div class="el-empty-stub">{{ description }}</div>' },
  'el-input': {
    props: ['modelValue', 'placeholder', 'clearable', 'size'],
    emits: ['update:modelValue'],
    template: '<input class="el-input-stub" :placeholder="placeholder" :value="modelValue || \'\'" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  'el-select': {
    props: ['modelValue', 'clearable', 'placeholder', 'size'],
    emits: ['update:modelValue'],
    template: '<div class="el-select-stub" :data-value="modelValue || \'\'"><slot /></div>',
  },
  'el-option': { props: ['label', 'value'], template: '<div class="el-option-stub" :data-label="label" :data-value="value" />' },
  'el-date-picker': {
    props: ['modelValue', 'type', 'valueFormat', 'rangeSeparator', 'startPlaceholder', 'endPlaceholder', 'size'],
    emits: ['update:modelValue'],
    template: '<div class="el-date-picker-stub" />',
  },
  'el-button': {
    props: ['link', 'type', 'size', 'icon', 'disabled'],
    template: '<button class="el-button-stub" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
  },
  loading: {},
}

const mountCard = () => mount(OpportunityPredictionCard, { global: { stubs, directives: { loading: {} } } })

const baseRow = (overrides = {}) => ({
  projectId: 1,
  projectName: '智慧水务一期工程',
  resultType: 'WON',
  customer: 'XX市水务局',
  projectLeaderName: '张明',
  customerType: 'CENTRAL_SOE',
  projectType: 'INDUSTRIAL',
  bidOpenTime: '2021-08-15',
  servicePeriodYears: 5,
  servicePeriodEndDate: '2026-08-14',
  predictedBidDate: '2026-09-14',
  near: true,
  ...overrides,
})

describe('OpportunityPredictionCard', () => {
  beforeEach(() => {
    apiMock.getOpportunityPredictions.mockReset()
    apiMock.routerPush.mockReset()
    apiMock.exportExcel.mockReset()
    apiMock.notifyExportSuccess.mockReset()
    apiMock.messageWarning.mockReset()
  })

  it('挂载时调用商机预测接口并渲染表格字段', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({ success: true, data: [baseRow()] })

    const wrapper = mountCard()
    await flushPromises()

    expect(apiMock.getOpportunityPredictions).toHaveBeenCalledTimes(1)
    const text = wrapper.text()
    expect(text).toContain('AI 商机预测')
    expect(text).toContain('只显示近 1 年商机')
    expect(text).toContain('智慧水务一期工程')
    expect(text).toContain('XX市水务局')
    expect(text).toContain('张明')
    expect(text).toContain('央企')
    expect(text).toContain('工业品')
    expect(text).toContain('已中标')
    expect(text).toContain('2021-08-15')
    expect(text).toContain('2026-08-14')
    expect(text).toContain('2026-09-14')
    expect(wrapper.find('.op-near-dot').exists()).toBe(true)
  })

  it('流标行渲染流标标签且不显示临近红点', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({
      success: true,
      data: [baseRow({ projectId: 2, resultType: 'FAILED', near: false, predictedBidDate: '2026-12-20' })],
    })

    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.text()).toContain('流标')
    expect(wrapper.find('.op-near-dot').exists()).toBe(false)
  })

  it('超过单页 5 条时显示分页并只渲染当前页数据', async () => {
    const rows = Array.from({ length: 7 }, (_, i) => baseRow({ projectId: i + 1, projectName: `项目${i + 1}` }))
    apiMock.getOpportunityPredictions.mockResolvedValue({ success: true, data: rows })

    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.text()).toContain('共')
    expect(wrapper.text()).toContain('7')
    expect(wrapper.find('.el-pagination-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('项目5')
    expect(wrapper.text()).not.toContain('项目6')

    wrapper.vm.currentPage = 2
    await flushPromises()
    expect(wrapper.text()).toContain('项目6')
    expect(wrapper.text()).toContain('项目7')
    expect(wrapper.text()).not.toContain('项目1')
  })

  it('接口返回空数据时展示近 1 年空态文案', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({ success: true, data: [] })

    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.find('.el-empty-stub').text()).toContain('暂无近 1 年商机预测')
  })

  it('接口失败时降级为空列表，不抛错', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({ success: false, data: [] })

    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.find('.el-empty-stub').exists()).toBe(true)
  })

  it('渲染搜索输入框/类型下拉/时间范围/导出按钮', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({ success: true, data: [baseRow()] })

    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.find('.op-filters-grid').exists()).toBe(true)
    expect(wrapper.findAll('.op-filter-input').length).toBe(2)
    expect(wrapper.findAll('.op-filter-select').length).toBe(2)
    expect(wrapper.find('.op-filter-date').exists()).toBe(true)
    expect(wrapper.find('.op-filter-reset').exists()).toBe(true)
    expect(wrapper.find('.op-export-btn').text()).toContain('导出')
  })

  it('按项目名称与招标主体模糊筛选只显示匹配行', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({
      success: true,
      data: [
        baseRow({ projectId: 1, projectName: '智慧水务一期', customer: '北京四季青' }),
        baseRow({ projectId: 2, projectName: '现代农业项目', customer: '现代牧业集团' }),
      ],
    })

    const wrapper = mountCard()
    await flushPromises()

    wrapper.vm.projectNameFilter = '水务'
    await flushPromises()
    expect(wrapper.text()).toContain('智慧水务一期')
    expect(wrapper.text()).not.toContain('现代农业项目')

    wrapper.vm.projectNameFilter = ''
    wrapper.vm.customerFilter = '牧业'
    await flushPromises()
    expect(wrapper.text()).toContain('现代农业项目')
    expect(wrapper.text()).not.toContain('智慧水务一期')
  })

  it('按客户类型/项目类型/时间范围筛选', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({
      success: true,
      data: [
        baseRow({ projectId: 1, customerType: 'CENTRAL_SOE', projectType: 'INDUSTRIAL', predictedBidDate: '2026-09-14' }),
        baseRow({ projectId: 2, customerType: 'PRIVATE', projectType: 'OFFICE', predictedBidDate: '2026-12-20' }),
      ],
    })

    const wrapper = mountCard()
    await flushPromises()

    wrapper.vm.customerTypeFilter = 'CENTRAL_SOE'
    await flushPromises()
    expect(wrapper.vm.filteredRows).toHaveLength(1)

    wrapper.vm.customerTypeFilter = ''
    wrapper.vm.projectTypeFilter = 'OFFICE'
    await flushPromises()
    expect(wrapper.vm.filteredRows).toHaveLength(1)

    wrapper.vm.projectTypeFilter = ''
    wrapper.vm.predictedDateRange = ['2026-09-01', '2026-09-30']
    await flushPromises()
    expect(wrapper.vm.filteredRows).toHaveLength(1)
  })

  it('重置按钮清空所有筛选条件（含项目名称和招标主体）', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({ success: true, data: [baseRow()] })
    const wrapper = mountCard()
    await flushPromises()

    const resetBtn = wrapper.find('.op-filter-reset')
    expect(resetBtn.attributes('disabled')).toBeDefined()

    wrapper.vm.projectNameFilter = '水务'
    wrapper.vm.customerFilter = '四季青'
    wrapper.vm.customerTypeFilter = 'CENTRAL_SOE'
    await flushPromises()
    expect(wrapper.vm.hasActiveFilters).toBe(true)
    expect(resetBtn.attributes('disabled')).toBeUndefined()

    await resetBtn.trigger('click')
    expect(wrapper.vm.projectNameFilter).toBe('')
    expect(wrapper.vm.customerFilter).toBe('')
    expect(wrapper.vm.customerTypeFilter).toBe('')
    expect(wrapper.vm.hasActiveFilters).toBe(false)
  })

  it('导出功能：有数据调用 exportExcel，无数据提示 warning', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({ success: true, data: [baseRow()] })
    const wrapper = mountCard()
    await flushPromises()

    wrapper.vm.handleExport()
    expect(apiMock.exportExcel).toHaveBeenCalledTimes(1)
    expect(apiMock.notifyExportSuccess).toHaveBeenCalledTimes(1)

    wrapper.vm.projectNameFilter = '不存在项目'
    await flushPromises()
    wrapper.vm.handleExport()
    expect(apiMock.messageWarning).toHaveBeenCalledWith('暂无可导出数据')
  })

  it('筛选无结果时展示无匹配文案而非暂无数据', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({ success: true, data: [baseRow()] })

    const wrapper = mountCard()
    await flushPromises()

    wrapper.vm.customerTypeFilter = 'FOREIGN'
    await flushPromises()

    expect(wrapper.find('.el-empty-stub').text()).toContain('无匹配')
  })
})
