// Input: mount TaskExtendedFieldPanel with mocked taskExtendedFieldAdminApi + project store
// Output: coverage for load / render / create / edit-prefill / save / disable / enable flows
// Pos: src/views/System/settings/ - component tests

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('@/api/modules/taskExtendedFieldAdmin.js', () => ({
  taskExtendedFieldAdminApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    disable: vi.fn(),
    enable: vi.fn(),
    reorder: vi.fn(),
  },
}))

const invalidateTaskExtendedFields = vi.fn()
vi.mock('@/stores/project', () => ({
  useProjectStore: () => ({ invalidateTaskExtendedFields }),
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

import { taskExtendedFieldAdminApi } from '@/api/modules/taskExtendedFieldAdmin.js'
import TaskExtendedFieldPanel from './TaskExtendedFieldPanel.vue'

const stubs = {
  ElCard: { template: '<div class="el-card"><slot/></div>' },
  ElAlert: { template: '<div class="el-alert"><slot/></div>' },
  ElButton: {
    template: '<button class="el-button" :disabled="loading || disabled" @click="$emit(\'click\', $event)"><slot/></button>',
    props: ['loading', 'type', 'link', 'disabled', 'icon', 'size'],
    emits: ['click'],
  },
  ElTable: {
    template: '<table><tbody><tr v-for="(r,i) in data" :key="i" class="el-table-row" :data-key="r.key"><slot name="default" v-bind="{row:r, $index:i}"/></tr></tbody></table>',
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
  return mount(TaskExtendedFieldPanel, {
    global: {
      stubs,
      directives: {
        loading: {},
      },
    },
  })
}

describe('TaskExtendedFieldPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    taskExtendedFieldAdminApi.list.mockResolvedValue({
      success: true,
      data: [
        {
          key: 'tender_chapter',
          label: '招标章节',
          fieldType: 'text',
          required: true,
          placeholder: '',
          options: null,
          sortOrder: 10,
          enabled: true,
        },
        {
          key: 'priority_level',
          label: '优先级',
          fieldType: 'select',
          required: false,
          placeholder: '',
          options: [
            { label: '高', value: 'high' },
            { label: '中', value: 'medium' },
            { label: '低', value: 'low' },
          ],
          sortOrder: 20,
          enabled: true,
        },
        {
          key: 'legacy_note',
          label: '历史备注',
          fieldType: 'textarea',
          required: false,
          placeholder: '',
          options: null,
          sortOrder: 30,
          enabled: false,
        },
      ],
    })
  })

  it('loads list on mount', async () => {
    mountPanel()
    await flushPromises()
    expect(taskExtendedFieldAdminApi.list).toHaveBeenCalled()
  })

  it('renders disabled items distinctly (legacy_note row present)', async () => {
    const w = mountPanel()
    await flushPromises()
    const html = w.html()
    expect(html).toContain('legacy_note')
  })

  it('clicking 新增 opens dialog in create mode', async () => {
    const w = mountPanel()
    await flushPromises()
    await w.find('[data-test="new-field-btn"]').trigger('click')
    await flushPromises()
    expect(w.find('.el-dialog').exists()).toBe(true)
    expect(w.vm.editingKey).toBe(null)
  })

  it('saving create form calls api.create then invalidate', async () => {
    const w = mountPanel()
    await flushPromises()
    taskExtendedFieldAdminApi.create.mockResolvedValue({
      success: true,
      data: {
        key: 'new_field',
        label: '新字段',
        fieldType: 'text',
        required: false,
        options: null,
        sortOrder: 40,
        enabled: true,
      },
    })
    await w.find('[data-test="new-field-btn"]').trigger('click')
    await flushPromises()
    w.vm.editingForm = {
      key: 'new_field',
      label: '新字段',
      fieldType: 'text',
      required: false,
      placeholder: '',
      optionsJson: '',
    }
    await w.vm.onSave()
    await flushPromises()
    expect(taskExtendedFieldAdminApi.create).toHaveBeenCalled()
    const createArg = taskExtendedFieldAdminApi.create.mock.calls[0][0]
    expect(createArg.key).toBe('new_field')
    expect(createArg.options).toBe(null)
    expect(invalidateTaskExtendedFields).toHaveBeenCalled()
  })

  it('disable row calls api.disable and invalidate', async () => {
    const w = mountPanel()
    await flushPromises()
    taskExtendedFieldAdminApi.disable.mockResolvedValue({
      success: true,
      data: { key: 'tender_chapter', enabled: false },
    })
    await w.vm.onDisable({ key: 'tender_chapter', label: '招标章节' })
    await flushPromises()
    expect(taskExtendedFieldAdminApi.disable).toHaveBeenCalledWith('tender_chapter')
    expect(invalidateTaskExtendedFields).toHaveBeenCalled()
  })

  it('enable row calls api.enable and invalidate', async () => {
    const w = mountPanel()
    await flushPromises()
    taskExtendedFieldAdminApi.enable.mockResolvedValue({
      success: true,
      data: { key: 'legacy_note', enabled: true },
    })
    await w.vm.onEnable({ key: 'legacy_note', label: '历史备注' })
    await flushPromises()
    expect(taskExtendedFieldAdminApi.enable).toHaveBeenCalledWith('legacy_note')
    expect(invalidateTaskExtendedFields).toHaveBeenCalled()
  })

  it('edit copies row fields into form (including optionsJson serialized from row.options)', async () => {
    const w = mountPanel()
    await flushPromises()
    const row = {
      key: 'priority_level',
      label: '优先级',
      fieldType: 'select',
      required: false,
      placeholder: '',
      options: [
        { label: '高', value: 'high' },
        { label: '低', value: 'low' },
      ],
      sortOrder: 20,
      enabled: true,
    }
    await w.vm.openEdit(row)
    expect(w.vm.editingForm.key).toBe('priority_level')
    expect(w.vm.editingForm.label).toBe('优先级')
    expect(w.vm.editingForm.fieldType).toBe('select')
    expect(w.vm.editingKey).toBe('priority_level')
    // optionsJson should be a JSON string that parses back to the same array
    const parsed = JSON.parse(w.vm.editingForm.optionsJson)
    expect(Array.isArray(parsed)).toBe(true)
    expect(parsed).toHaveLength(2)
    expect(parsed[0]).toEqual({ label: '高', value: 'high' })
  })
})
