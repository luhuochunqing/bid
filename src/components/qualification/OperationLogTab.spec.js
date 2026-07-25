import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, h, provide, inject, computed } from 'vue'

const mockHttp = vi.hoisted(() => ({
  get: vi.fn()
}))

vi.mock('@/api/client', () => ({
  default: mockHttp
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

vi.mock('@element-plus/icons-vue', () => ({
  Refresh: { template: '<i />' }
}))

import OperationLogTab from './OperationLogTab.vue'

// 简化的 provide/inject：el-table 把 data 传给子级 el-table-column
const ROWS_KEY = Symbol('op-log-rows')

// el-table-column stub：遍历父级 data，给每个 row 渲染一次 slot
// 这样能模拟 el-table 的 #default="scope" 行为，让 data-testid 元素能被断言
const TableColumnStub = defineComponent({
  name: 'ElTableColumn',
  props: ['prop', 'label', 'width'],
  setup(_, { slots }) {
    const rows = inject(ROWS_KEY, () => [])
    return () => {
      const list = rows.value || rows
      if (!list || list.length === 0) return h('div')
      return h('div', list.map(row => slots.default?.({ row, $index: 0 })))
    }
  }
})

// el-table stub：把 data 通过 provide 传给子级 column
const TableStub = defineComponent({
  name: 'ElTable',
  props: ['data', 'size', 'border'],
  setup(props, { slots }) {
    const rowsRef = computed(() => props.data || [])
    provide(ROWS_KEY, rowsRef)
    return () => h('div', { class: 'op-log-table' }, slots.default?.())
  }
})

const stubs = {
  'el-table': TableStub,
  'el-table-column': TableColumnStub,
  'el-date-picker': { template: '<input />', props: ['modelValue', 'type', 'size'] },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] },
  'el-tag': { template: '<span><slot /></span>', props: ['type', 'size'] },
  'el-icon': { template: '<i><slot /></i>' }
}

describe('OperationLogTab', () => {
  let wrapper

  beforeEach(() => {
    mockHttp.get.mockReset()
  })

  afterEach(() => {
    if (wrapper) wrapper.unmount()
  })

  function createWrapper(props = {}) {
    return mount(OperationLogTab, {
      props,
      global: { stubs }
    })
  }

  it('renders empty state when no logs', async () => {
    mockHttp.get.mockResolvedValue({ success: true, data: [] })
    wrapper = createWrapper({ qualificationId: 1 })
    await flushPromises()
    expect(wrapper.find('[data-testid="qd-op-log-tab"]').exists()).toBe(true)
    // 无日志时表格不渲染（v-if="filteredLogs.length"）
    expect(wrapper.find('.op-log-table').exists()).toBe(false)
  })

  it('renders log rows with data-testid attributes when logs exist', async () => {
    mockHttp.get.mockResolvedValue({
      success: true,
      data: [
        {
          time: '2026-07-26 10:00:00',
          operator: '管理员（admin）',
          actionType: 'CREATE',
          detail: '创建资质',
          target: '证书A'
        }
      ]
    })
    wrapper = createWrapper({ qualificationId: 1 })
    await flushPromises()
    // 表格渲染（v-if="filteredLogs.length"）
    expect(wrapper.find('.op-log-table').exists()).toBe(true)
    expect(wrapper.find('[data-testid="qd-op-log-time"]').text()).toBe('2026-07-26 10:00:00')
    expect(wrapper.find('[data-testid="qd-op-log-operator"]').text()).toBe('管理员（admin）')
  })
})


