import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref, reactive, h, defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import ProjectTransferDialog from './ProjectTransferDialog.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

// 捕获 v-model 传入的 modelValue，用于回归测试 v-model 是否正确绑定 ref 字段
const ElDialogStub = defineComponent({
  name: 'ElDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, default: '' },
  },
  setup(props, { slots }) {
    return () => h('div', {
      'data-title': props.title,
      'data-model-value': String(props.modelValue),
    }, [
      slots.default?.(),
      slots.footer?.(),
    ])
  },
})

function mountDialog(overrides = {}) {
  const transferDialogVisible = overrides.transferDialogVisible || ref(false)
  const transferring = overrides.transferring || ref(false)
  const transferForm = reactive({ newOwnerUserId: null, reason: '' })
  const excludeOwnerIds = overrides.excludeOwnerIds || ref([7246])
  const openTransfer = vi.fn(() => {
    transferForm.newOwnerUserId = null
    transferForm.reason = ''
    transferDialogVisible.value = true
  })
  const closeTransfer = vi.fn(() => {
    transferDialogVisible.value = false
    transferForm.newOwnerUserId = null
    transferForm.reason = ''
  })
  const handleTransferConfirm = vi.fn()
  const project = overrides.project || { id: 135, name: '测试项目', managerId: 7246, projectLeaderName: '陈梦瑶' }
  const context = reactive({
    transferDialogVisible,
    transferring,
    transferForm,
    excludeOwnerIds,
    project,
    openTransfer,
    closeTransfer,
    handleTransferConfirm,
  })
  const wrapper = mount(ProjectTransferDialog, {
    global: {
      provide: { [projectDetailKey]: context },
      plugins: [createPinia(), createRouter({ history: createMemoryHistory(), routes: [] })],
      stubs: {
        ElDialog: ElDialogStub,
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { props: ['label', 'required'], template: '<label>{{ label }}<slot /></label>' },
        'el-input': { template: '<input />' },
        ElButton: {
          name: 'ElButton',
          props: ['loading', 'type'],
          template: '<button><slot /></button>',
        },
        UserPicker: {
          name: 'UserPicker',
          props: ['modelValue', 'mode', 'placeholder', 'excludeIds'],
          emits: ['update:modelValue'],
          template: '<div class="user-picker-stub" />',
        },
      },
    },
  })
  return { wrapper, context, transferDialogVisible, transferring, transferForm, excludeOwnerIds, openTransfer, closeTransfer, handleTransferConfirm }
}

describe('ProjectTransferDialog', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders project name and current owner', () => {
    const { wrapper } = mountDialog()
    const html = wrapper.html()
    expect(html).toContain('测试项目')
    expect(html).toContain('陈梦瑶')
  })

  // 回归测试：v-model 必须直接绑 ref 字段，不能写 .value
  // 历史 bug：v-model="ctx.transferDialogVisible.value" 在 reactive unwrap 后失效，
  // dialog 永不弹出，用户报"项目转移功能没法点击"
  it('dialog v-model reflects transferDialogVisible ref state', async () => {
    const { wrapper, transferDialogVisible } = mountDialog()
    const dialogEl = wrapper.find('[data-title="项目转移"]')
    expect(dialogEl.exists()).toBe(true)
    expect(dialogEl.attributes('data-model-value')).toBe('false')

    transferDialogVisible.value = true
    await wrapper.vm.$nextTick()
    expect(dialogEl.attributes('data-model-value')).toBe('true')
  })

  it('openTransfer sets transferDialogVisible to true and dialog reflects it', async () => {
    const { wrapper, openTransfer, transferDialogVisible } = mountDialog()
    const dialogEl = wrapper.find('[data-title="项目转移"]')

    openTransfer()
    await wrapper.vm.$nextTick()
    expect(transferDialogVisible.value).toBe(true)
    expect(dialogEl.attributes('data-model-value')).toBe('true')
  })

  // 回归测试：excludeOwnerIds 必须 unwrap 后传入（旧 bug：写 .value 永远 undefined）
  it('passes excludeOwnerIds (unwrapped) to UserPicker', () => {
    const { wrapper } = mountDialog()
    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.props('excludeIds')).toEqual([7246])
  })

  // 回归测试：transferring 必须 unwrap 后传入（旧 bug：写 .value 永远 undefined）
  it('passes transferring (unwrapped) as button loading state', async () => {
    const { wrapper, transferring } = mountDialog()
    // ElDialogStub 现在渲染 footer slot，里面包含 ElButton
    const btnComp = wrapper.findAllComponents({ name: 'ElButton' }).find(c => c.text().includes('确认转移'))
    expect(btnComp).toBeTruthy()
    expect(btnComp.props('loading')).toBe(false)

    transferring.value = true
    await wrapper.vm.$nextTick()
    expect(btnComp.props('loading')).toBe(true)
  })

  it('clicking 确认转移 button calls handleTransferConfirm', async () => {
    const { wrapper, handleTransferConfirm } = mountDialog()
    const btnComp = wrapper.findAllComponents({ name: 'ElButton' }).find(c => c.text().includes('确认转移'))
    await btnComp.trigger('click')
    expect(handleTransferConfirm).toHaveBeenCalled()
  })

  it('clicking 取消 button calls closeTransfer', async () => {
    const { wrapper, closeTransfer } = mountDialog()
    const btnComp = wrapper.findAllComponents({ name: 'ElButton' }).find(c => c.text().includes('取消'))
    await btnComp.trigger('click')
    expect(closeTransfer).toHaveBeenCalled()
  })
})
