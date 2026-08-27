// Input: OpportunityPredictionCard + mocked workbenchApi
// Output: 表格渲染 / 分页 / 空态 / 标签映射 / 临近红点 / 类型与时间筛选覆盖
// Pos: src/views/Dashboard/__tests__/ - 工作台商机预测卡片组件测试

import { flushPromises, mount } from '@vue/test-utils'
import { computed } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = vi.hoisted(() => ({
  getOpportunityPredictions: vi.fn(),
  routerPush: vi.fn(),
}))

vi.mock('@/api', () => ({ workbenchApi: { getOpportunityPredictions: apiMock.getOpportunityPredictions } }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: apiMock.routerPush }) }))

import OpportunityPredictionCard from '@/views/Dashboard/components/OpportunityPredictionCard.vue'

// el-table stub：provide 响应式行数据给列 stub（provide 非响应式，必须包
// computed 让列 stub 在数据异步加载后仍能读到最新值），列 stub 逐行渲染
// default 插槽（保留 row/$index 作用域）
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
  'el-select': {
    props: ['modelValue', 'clearable', 'placeholder', 'size'],
    emits: ['update:modelValue'],
    template: '<div class="el-select-stub" :data-value="modelValue || \'\'"><slot /></div>',
  },
  'el-option': { props: ['label', 'value'], template: '<div class="el-option-stub" />' },
  'el-date-picker': {
    props: ['modelValue', 'type', 'valueFormat', 'rangeSeparator', 'startPlaceholder', 'endPlaceholder', 'size'],
    emits: ['update:modelValue'],
    template: '<div class="el-date-picker-stub" />',
  },
  'el-button': {
    props: ['link', 'type', 'size'],
    template: '<button class="el-button-stub" @click="$emit(\'click\')"><slot /></button>',
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
  })

  it('挂载时调用商机预测接口并渲染表格字段', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({ success: true, data: [baseRow()] })

    const wrapper = mountCard()
    await flushPromises()

    expect(apiMock.getOpportunityPredictions).toHaveBeenCalledTimes(1)
    const text = wrapper.text()
    expect(text).toContain('AI 商机预测')
    expect(text).toContain('只显示近 6 个月商机')
    expect(text).toContain('智慧水务一期工程')
    expect(text).toContain('XX市水务局')
    expect(text).toContain('张明')
    expect(text).toContain('央企') // customerType CENTRAL_SOE 翻译
    expect(text).toContain('工业品') // projectType INDUSTRIAL 翻译
    expect(text).toContain('已中标')
    // 日期统一两行排版：第一行年-月，第二行 —日（月和日之间有破折号）
    expect(text).toContain('2021-08')
    expect(text).toContain('2026-08')
    expect(text).toContain('2026-09')
    const dayTexts = wrapper.findAll('.op-date-d').map((n) => n.text())
    expect(dayTexts.length).toBeGreaterThanOrEqual(3)
    for (const day of dayTexts) {
      expect(day.startsWith('—')).toBe(true)
    }
    expect(wrapper.find('.op-near-dot').exists()).toBe(true) // 3 个月内红点
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

  it('接口返回空数据时展示空态', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({ success: true, data: [] })

    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.find('.el-empty-stub').text()).toContain('暂无近 6 个月商机预测')
  })

  it('接口失败时降级为空列表，不抛错', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({ success: false, data: [] })

    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.find('.el-empty-stub').exists()).toBe(true)
  })

  it('渲染客户类型/项目类型/时间范围三个筛选控件', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({ success: true, data: [baseRow()] })

    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.findAll('.op-filter-select').length).toBe(2) // 客户类型 + 项目类型
    expect(wrapper.find('.op-filter-date').exists()).toBe(true)
  })

  it('按客户类型筛选只显示匹配行', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({
      success: true,
      data: [
        baseRow({ projectId: 1, projectName: '央企项目', customerType: 'CENTRAL_SOE' }),
        baseRow({ projectId: 2, projectName: '民企项目', customerType: 'PRIVATE' }),
      ],
    })

    const wrapper = mountCard()
    await flushPromises()

    wrapper.vm.customerTypeFilter = 'CENTRAL_SOE'
    await flushPromises()

    expect(wrapper.text()).toContain('央企项目')
    expect(wrapper.text()).not.toContain('民企项目')
  })

  it('按项目类型筛选只显示匹配行', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({
      success: true,
      data: [
        baseRow({ projectId: 1, projectName: '工业品项目', projectType: 'INDUSTRIAL' }),
        baseRow({ projectId: 2, projectName: '办公项目', projectType: 'OFFICE' }),
      ],
    })

    const wrapper = mountCard()
    await flushPromises()

    wrapper.vm.projectTypeFilter = 'OFFICE'
    await flushPromises()

    expect(wrapper.text()).toContain('办公项目')
    expect(wrapper.text()).not.toContain('工业品项目')
  })

  it('时间范围筛选按预测招标时间过滤', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({
      success: true,
      data: [
        baseRow({ projectId: 1, projectName: '范围内项目', predictedBidDate: '2026-09-14' }),
        baseRow({ projectId: 2, projectName: '范围外项目', predictedBidDate: '2026-12-20' }),
      ],
    })

    const wrapper = mountCard()
    await flushPromises()

    wrapper.vm.predictedDateRange = ['2026-09-01', '2026-09-30']
    await flushPromises()

    expect(wrapper.text()).toContain('范围内项目')
    expect(wrapper.text()).not.toContain('范围外项目')
    expect(wrapper.vm.filteredRows).toHaveLength(1)
  })

  it('筛选变化时重置回第 1 页', async () => {
    const rows = Array.from({ length: 7 }, (_, i) =>
      baseRow({ projectId: i + 1, projectName: `项目${i + 1}`, predictedBidDate: `2026-09-${String(i + 1).padStart(2, '0')}` }),
    )
    apiMock.getOpportunityPredictions.mockResolvedValue({ success: true, data: rows })

    const wrapper = mountCard()
    await flushPromises()

    wrapper.vm.currentPage = 2
    await flushPromises()
    wrapper.vm.customerTypeFilter = 'CENTRAL_SOE'
    await flushPromises()

    expect(wrapper.vm.currentPage).toBe(1)
    expect(wrapper.text()).toContain('项目5') // 回到第一页可见区间
  })

  it('筛选无结果时展示无匹配文案而非暂无数据', async () => {
    apiMock.getOpportunityPredictions.mockResolvedValue({ success: true, data: [baseRow()] })

    const wrapper = mountCard()
    await flushPromises()

    wrapper.vm.customerTypeFilter = 'FOREIGN'
    await flushPromises()

    const emptyText = wrapper.find('.el-empty-stub').text()
    expect(emptyText).toContain('无匹配')
  })
})
