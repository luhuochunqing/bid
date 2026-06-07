// Input: Operation log panel with mocked auditApi responses
// Output: loading, search/reset, error state, and operation log field normalization coverage
// Pos: src/views/System/settings/ - component tests

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  getAuditLogs: vi.fn(),
  getOperationLogs: vi.fn(),
}))

vi.mock('@/api', () => ({
  auditApi: {
    getAuditLogs: mocks.getAuditLogs,
    getOperationLogs: mocks.getOperationLogs,
  },
}))

import AuditLogPanel from './AuditLogPanel.vue'

const stubs = {
  ElAlert: {
    props: ['title'],
    template: '<div role="alert">{{ title }}</div>',
  },
  ElButton: {
    props: ['disabled', 'loading'],
    emits: ['click'],
    template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>',
  },
  ElCard: {
    template: '<section><header><slot name="header" /></header><slot /></section>',
  },
  ElInput: {
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue', 'keyup'],
    template: `
      <input
        :placeholder="placeholder"
        :value="modelValue"
        @input="$emit('update:modelValue', $event.target.value)"
        @keyup.enter="$emit('keyup', $event)"
      />
    `,
  },
  ElTable: {
    props: ['data'],
    template: `
      <div class="audit-table">
        <div v-for="row in data" :key="row.id" class="audit-row">
          {{ row.time }} {{ row.operator }} {{ row.action }} {{ row.target }}
        </div>
        <slot />
      </div>
    `,
  },
  ElTableColumn: {
    template: '<div />',
  },
  ElTag: {
    template: '<span><slot /></span>',
  },
}

function mountPanel() {
  return mount(AuditLogPanel, {
    global: {
      stubs,
      directives: {
        loading: {},
      },
    },
  })
}

function mountAuditPanel() {
  return mount(AuditLogPanel, {
    props: {
      mode: 'audit',
    },
    global: {
      stubs,
      directives: {
        loading: {},
      },
    },
  })
}

function buttonByText(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text() === text)
}

describe('AuditLogPanel', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-04-30T10:20:30+08:00'))
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('loads operation rows and prefers detail plus summary.totalCount when normalizing fields', async () => {
    mocks.getOperationLogs.mockResolvedValue({
      success: true,
      data: {
        items: [
          {
            id: 7,
            time: '2026-04-30 09:15:00',
            operator: '李总',
            actionType: 'create',
            detail: '创建资质',
            target: '88',
          },
          {
            timestamp: '2026-04-29 16:00:00',
            createdByName: '张经理',
            description: '描述兜底',
            entityName: '标讯',
          },
        ],
        total: 99,
        summary: {
          totalCount: 42,
        },
      },
    })

    const wrapper = mountPanel()
    await flushPromises()

    expect(mocks.getOperationLogs).toHaveBeenCalledWith({})
    expect(mocks.getAuditLogs).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('操作日志')
    expect(wrapper.text()).toContain('共 42 条记录')
    expect(wrapper.text()).toContain('创建资质')
    expect(wrapper.text()).toContain('李总')
    expect(wrapper.text()).toContain('88')
    expect(wrapper.text()).toContain('描述兜底')
    expect(wrapper.text()).toContain('张经理')
    expect(wrapper.text()).toContain('标讯')
  })

  it('searches by keyword and reset clears the query before reloading', async () => {
    mocks.getOperationLogs.mockResolvedValue({
      success: true,
      data: {
        items: [],
        summary: { totalCount: 0 },
      },
    })

    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.find('input[placeholder="搜索操作内容/对象"]').setValue('创建资质')
    await buttonByText(wrapper, '搜索').trigger('click')
    await flushPromises()

    expect(mocks.getOperationLogs).toHaveBeenLastCalledWith({ keyword: '创建资质' })

    await buttonByText(wrapper, '重置').trigger('click')
    await flushPromises()

    expect(wrapper.find('input[placeholder="搜索操作内容/对象"]').element.value).toBe('')
    expect(mocks.getOperationLogs).toHaveBeenLastCalledWith({})
  })

  it('shows an error state and clears rows when loading fails', async () => {
    mocks.getOperationLogs.mockRejectedValue(new Error('操作日志服务不可用'))

    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.text()).toContain('操作日志服务不可用')
    expect(wrapper.text()).toContain('共 0 条记录')
    expect(wrapper.findAll('.audit-row')).toHaveLength(0)
  })

  it('loads full audit rows in audit mode', async () => {
    mocks.getAuditLogs.mockResolvedValue({
      success: true,
      data: {
        items: [],
        summary: { totalCount: 0 },
      },
    })

    const wrapper = mountAuditPanel()
    await flushPromises()

    expect(wrapper.text()).toContain('审计日志')
    expect(mocks.getAuditLogs).toHaveBeenCalledWith({})
    expect(mocks.getOperationLogs).not.toHaveBeenCalled()
  })
})
