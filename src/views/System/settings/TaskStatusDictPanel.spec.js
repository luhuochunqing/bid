// Input: mount TaskStatusDictPanel with mocked taskStatusDictAdminApi + project store
// Output: coverage for load / render / create / edit-prefill / save / disable / enable flows
// Pos: src/views/System/settings/ - component tests

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('@/api/modules/taskStatusDictAdmin.js', () => ({
  taskStatusDictAdminApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    disable: vi.fn(),
    enable: vi.fn(),
    reorder: vi.fn(),
  },
}))

const invalidateTaskStatuses = vi.fn()
vi.mock('@/stores/project', () => ({
  useProjectStore: () => ({ invalidateTaskStatuses }),
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual('element-plus')
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
    },
    ElMessageBox: {
      confirm: vi.fn().mockResolvedValue('confirm'),
    },
  }
})

import { taskStatusDictAdminApi } from '@/api/modules/taskStatusDictAdmin.js'
import TaskStatusDictPanel from './TaskStatusDictPanel.vue'

const stubs = {
  ElCard: { template: '<div class="el-card"><slot/></div>' },
  ElAlert: { template: '<div class="el-alert"><slot/></div>' },
  ElButton: {
    template: '<button class="el-button" :disabled="loading || disabled" @click="$emit(\'click\', $event)"><slot/></button>',
    props: ['loading', 'type', 'link', 'disabled', 'icon', 'size'],
    emits: ['click'],
  },
  ElTable: {
    template: '<table><tbody><tr v-for="(r,i) in data" :key="i" class="el-table-row" :data-code="r.code"><slot name="default" v-bind="{row:r, $index:i}"/></tr></tbody></table>',
    props: ['data', 'rowKey', 'loading', 'rowClassName'],
  },
  ElTableColumn: {
    template: '<slot name="default" v-bind="{row:{},$index:0}"/>',
    props: ['prop', 'label', 'width', 'align'],
  },
  ElTag: {
    template: '<span class="el-tag"><slot/></span>',
    props: ['type', 'size'],
  },
  ElDialog: {
    template: '<div v-if="modelValue" class="el-dialog"><slot/><slot name="footer"/></div>',
    props: ['modelValue', 'title', 'width', 'closeOnClickModal'],
  },
  DynamicFormRenderer: {
    name: 'DynamicFormRenderer',
    template: '<div class="dynamic-form-renderer"></div>',
    props: ['fields', 'modelValue'],
    methods: {
      submit() { return { valid: true, data: this.modelValue } },
    },
  },
}

function mountPanel() {
  return mount(TaskStatusDictPanel, {
    global: {
      stubs,
      directives: {
        loading: {},
      },
    },
  })
}

describe('TaskStatusDictPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    taskStatusDictAdminApi.list.mockResolvedValue({
      success: true,
      data: [
        { code: 'TODO', name: '待办', category: 'OPEN', color: '#909399', sortOrder: 10, initial: true, terminal: false, enabled: true },
        { code: 'COMPLETED', name: '已完成', category: 'CLOSED', color: '#67c23a', sortOrder: 40, initial: false, terminal: true, enabled: true },
        { code: 'LEGACY', name: '历史', category: 'OPEN', color: '#cccccc', sortOrder: 50, initial: false, terminal: false, enabled: false },
      ],
    })
  })

  it('loads list on mount', async () => {
    mountPanel()
    await flushPromises()
    expect(taskStatusDictAdminApi.list).toHaveBeenCalled()
  })

  it('renders disabled items distinctly (LEGACY row present)', async () => {
    const w = mountPanel()
    await flushPromises()
    const html = w.html()
    expect(html).toContain('LEGACY')
  })

  it('clicking 新增 opens dialog in create mode', async () => {
    const w = mountPanel()
    await flushPromises()
    await w.find('[data-test="new-status-btn"]').trigger('click')
    await flushPromises()
    expect(w.find('.el-dialog').exists()).toBe(true)
    expect(w.vm.editingCode).toBe(null)
  })

  it('saving create form calls api.create then invalidate', async () => {
    const w = mountPanel()
    await flushPromises()
    taskStatusDictAdminApi.create.mockResolvedValue({
      success: true,
      data: { code: 'NEW', name: 'x', category: 'OPEN', color: '#aaa', sortOrder: 60, initial: false, terminal: false, enabled: true },
    })
    await w.find('[data-test="new-status-btn"]').trigger('click')
    await flushPromises()
    w.vm.editingForm = { code: 'NEW', name: 'new', category: 'OPEN', color: '#aaa' }
    await w.vm.onSave()
    await flushPromises()
    expect(taskStatusDictAdminApi.create).toHaveBeenCalled()
    expect(invalidateTaskStatuses).toHaveBeenCalled()
  })

  it('disable row calls api.disable and invalidate', async () => {
    const w = mountPanel()
    await flushPromises()
    taskStatusDictAdminApi.disable.mockResolvedValue({ success: true, data: { code: 'TODO', enabled: false } })
    await w.vm.onDisable({ code: 'TODO', name: '待办' })
    await flushPromises()
    expect(taskStatusDictAdminApi.disable).toHaveBeenCalledWith('TODO')
    expect(invalidateTaskStatuses).toHaveBeenCalled()
  })

  it('enable row calls api.enable and invalidate', async () => {
    const w = mountPanel()
    await flushPromises()
    taskStatusDictAdminApi.enable.mockResolvedValue({ success: true, data: { code: 'LEGACY', enabled: true } })
    await w.vm.onEnable({ code: 'LEGACY', name: '历史' })
    await flushPromises()
    expect(taskStatusDictAdminApi.enable).toHaveBeenCalledWith('LEGACY')
    expect(invalidateTaskStatuses).toHaveBeenCalled()
  })

  it('edit copies row fields into form (covers @Valid full dto requirement)', async () => {
    const w = mountPanel()
    await flushPromises()
    const row = { code: 'TODO', name: '待办', category: 'OPEN', color: '#909399', sortOrder: 10, initial: true, terminal: false, enabled: true }
    await w.vm.openEdit(row)
    expect(w.vm.editingForm.code).toBe('TODO')
    expect(w.vm.editingForm.name).toBe('待办')
    expect(w.vm.editingForm.category).toBe('OPEN')
    expect(w.vm.editingCode).toBe('TODO')
  })
})
