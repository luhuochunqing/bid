import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const stubs = {
  'el-dialog': {
    template: '<div class="el-dialog-stub"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'destroyOnClose', 'top']
  },
  'el-form': {
    template: '<div class="el-form-stub"><slot /></div>',
    props: ['model', 'rules', 'labelWidth', 'size'],
    methods: {
      validate() {
        return Promise.resolve(true)
      },
      resetFields() {}
    }
  },
  'el-form-item': {
    template: '<div class="el-form-item-stub" :data-label="label"><slot /></div>',
    props: ['label', 'prop']
  },
  'el-input': {
    template: '<input class="el-input-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
    props: ['modelValue', 'type', 'rows', 'placeholder', 'maxlength', 'showWordLimit', 'disabled']
  },
  'el-select': {
    template: '<div class="el-select-stub"><slot /></div>',
    props: ['modelValue', 'filterable', 'clearable', 'placeholder']
  },
  'el-option': {
    template: '<div class="el-option-stub" :data-value="value">{{ label }}</div>',
    props: ['label', 'value']
  },
  'el-radio-group': {
    template: '<div class="el-radio-group-stub"><slot /></div>',
    props: ['modelValue']
  },
  'el-radio': {
    template: '<span class="el-radio-stub" :data-value="value"><slot /></span>',
    props: ['value']
  },
  'el-date-picker': {
    template: '<div class="el-date-picker-stub" />',
    props: ['modelValue', 'type', 'placeholder', 'valueFormat', 'disabledDate']
  },
  'el-upload': {
    template: '<div class="el-upload-stub"><slot /></div>',
    props: ['action', 'headers', 'showFileList', 'accept', 'withCredentials']
  },
  'el-button': {
    template: '<button class="el-button-stub" :class="{ \'is-loading\': loading }" @click="$emit(\'click\')"><slot /></button>',
    props: ['type', 'loading', 'plain', 'size']
  },
  'el-icon': { template: '<i class="el-icon-stub"><slot /></i>' }
}

const mockCa = {
  id: 1,
  platformIds: ['政采云'],
  caType: 'ENTITY_CA',
  sealTypeLabel: '公章',
  holderName: '西域科技',
  borrowStatus: 'IN_STOCK',
  status: 'ACTIVE'
}

const mockProjects = [
  { id: 'proj-001', name: '项目A' },
  { id: 'proj-002', name: '项目B' }
]

async function importComponent() {
  const mod = await import('../CABorrowDialog.vue')
  return mod.default
}

function mountComponent(props = {}) {
  return importComponent().then((component) =>
    mount(component, {
      props: { modelValue: true, ca: mockCa, projects: mockProjects, ...props },
      global: { plugins: [createPinia()], stubs }
    })
  )
}

describe('CABorrowDialog.vue — 瘦组件（props 驱动 + emit 事件）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('通过 props 接收项目列表，不再自己调用 API', async () => {
    const wrapper = await mountComponent()
    await flushPromises()

    const options = wrapper.findAll('.el-option-stub')
    expect(options.length).toBe(2)
    expect(options[0].text()).toBe('项目A')
    expect(options[1].text()).toBe('项目B')
  })

  it('通过 props 接收 uploadUrl 和 uploadHeaders', async () => {
    const wrapper = await mountComponent({
      uploadUrl: '/api/custom-upload',
      uploadHeaders: { Authorization: 'Bearer test-token' }
    })
    await flushPromises()

    expect(wrapper.props('uploadUrl')).toBe('/api/custom-upload')
    expect(wrapper.props('uploadHeaders')).toEqual({ Authorization: 'Bearer test-token' })
  })

  it('提交时 emit submit 事件，携带表单数据', async () => {
    const wrapper = await mountComponent()
    await flushPromises()

    const buttons = wrapper.findAll('button.el-button-stub')
    const submitBtn = buttons.find(b => b.text().includes('提交申请'))
    await submitBtn.trigger('click')
    await flushPromises()

    expect(wrapper.emitted('submit')).toBeTruthy()
    const submitData = wrapper.emitted('submit')[0][0]
    expect(submitData).toHaveProperty('purpose')
    expect(submitData).toHaveProperty('borrowDurationType')
  })

  it('关闭时 emit update:modelValue', async () => {
    const wrapper = await mountComponent()
    await flushPromises()

    const buttons = wrapper.findAll('button.el-button-stub')
    const cancelBtn = buttons.find(b => b.text().includes('取消'))
    await cancelBtn.trigger('click')

    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
  })
})
