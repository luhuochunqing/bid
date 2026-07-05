import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { ref, reactive } from 'vue'
import ProjectDetailReviewerDialog from './ProjectDetailReviewerDialog.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'
import { ElDialogStub } from './__test-utils__/ElDialogStub.js'

// 用 reactive 包装 ctx 贴近生产：ProjectDetailShell.vue 的 projectDetailContext = reactive({...})。
// 历史 bug：模板里写 v-model="detail.xxx.value" 在 reactive unwrap 后失效，dialog 永不弹出。
// 之前 spec 用普通对象 ctx，没覆盖到 reactive unwrap 行为，所以 bug 没被发现。

function mountDialog(contextOverrides = {}) {
  // 优先使用 overrides 中传入的 ref，让测试可以拿到同一个 ref 引用进行切换
  const {
    reviewerDialogVisible = ref(true),
    reviewerForm = ref({ userId: '', role: '' }),
    ...restOverrides
  } = contextOverrides
  const context = reactive({
    reviewerDialogVisible,
    reviewerForm,
    handleReviewerSelect: vi.fn(),
    handleConfirmAddReviewer: vi.fn(),
    closeReviewerDialog: vi.fn(),
    ...restOverrides,
  })
  const wrapper = mount(ProjectDetailReviewerDialog, {
    global: {
      provide: {
        [projectDetailKey]: context,
      },
      stubs: {
        ElDialog: ElDialogStub,
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { props: ['label', 'required'], template: '<label>{{ label }}<slot /></label>' },
        'el-select': {
          name: 'ElSelect',
          props: ['modelValue'],
          template: '<select><slot /></select>',
        },
        'el-option': { template: '<option><slot /></option>' },
        'el-button': {
          name: 'ElButton',
          template: '<button><slot /></button>',
        },
        UserPicker: {
          name: 'UserPicker',
          props: ['modelValue', 'mode', 'placeholder', 'excludeIds'],
          emits: ['update:modelValue', 'select'],
          template: '<div class="user-picker-stub" />',
        },
      },
    },
  })
  return { wrapper, context, reviewerDialogVisible, reviewerForm, closeReviewerDialog: context.closeReviewerDialog }
}

describe('ProjectDetailReviewerDialog', () => {
  it('renders UserPicker for reviewer selection', () => {
    const { wrapper } = mountDialog()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    expect(picker.exists()).toBe(true)
    expect(picker.props('mode')).toBe('search')
    expect(picker.props('placeholder')).toBe('请选择评审人')
  })

  it('binds UserPicker v-model to reviewerForm.userId', async () => {
    const { wrapper, reviewerForm } = mountDialog()

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    await picker.vm.$emit('update:modelValue', 'U001')
    expect(reviewerForm.value.userId).toBe('U001')
  })

  it('calls handleReviewerSelect on UserPicker @select', async () => {
    const { wrapper, context } = mountDialog()
    const user = { id: 'U001', name: '王评审' }

    const picker = wrapper.findComponent({ name: 'UserPicker' })
    await picker.vm.$emit('select', user)

    expect(context.handleReviewerSelect).toHaveBeenCalledWith(user)
  })

  // 回归测试：v-model 必须直接绑 ref 字段，不能写 .value
  // （reactive unwrap 后 .value 是 undefined，dialog 永不弹出）
  it('dialog v-model reflects reviewerDialogVisible ref state', async () => {
    const { wrapper, reviewerDialogVisible } = mountDialog({ reviewerDialogVisible: ref(false) })
    const dialogEl = wrapper.find('[data-title="添加评审人"]')
    expect(dialogEl.attributes('data-model-value')).toBe('false')

    reviewerDialogVisible.value = true
    await wrapper.vm.$nextTick()
    expect(dialogEl.attributes('data-model-value')).toBe('true')
  })

  // 回归测试：取消按钮必须调用 closeReviewerDialog（含 reset 逻辑），不能只关闭 dialog
  // 历史 bug：模板写 @click="detail.reviewerDialogVisible = false"，selectedReviewerUser 残留
  it('clicking 取消 button calls closeReviewerDialog (not just closes dialog)', async () => {
    const { wrapper, closeReviewerDialog } = mountDialog()
    const btnComp = wrapper.findAllComponents({ name: 'ElButton' }).find(c => c.text().includes('取消'))
    expect(btnComp).toBeTruthy()
    await btnComp.trigger('click')
    expect(closeReviewerDialog).toHaveBeenCalled()
  })
})
