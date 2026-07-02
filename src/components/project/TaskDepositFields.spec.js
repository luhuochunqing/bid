import { mount } from '@vue/test-utils'
import { describe, it, expect, beforeEach } from 'vitest'
import TaskDepositFields from './TaskDepositFields.vue'

const globalStubs = {
  ElFormItem: {
    props: ['label', 'required', 'error'],
    template: '<div class="form-item"><label>{{ label }}</label><span v-if="required" class="required-flag">*</span><slot /><div v-if="error" class="form-item-error">{{ error }}</div></div>',
  },
  ElInput: {
    props: ['modelValue', 'type', 'rows', 'placeholder', 'disabled'],
    emits: ['update:modelValue'],
    template: '<input class="el-input-stub" :value="modelValue" :disabled="disabled" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElDatePicker: {
    props: ['modelValue', 'type', 'valueFormat', 'disabled'],
    emits: ['update:modelValue'],
    template: '<input class="el-date-stub" :value="modelValue" :disabled="disabled" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
}

function mountComponent(props = {}) {
  return mount(TaskDepositFields, {
    props: {
      extendedFields: { depositAmount: 100, depositDeadline: '2026-08-15', payee: '', payeeAccount: '', actualPaymentDate: '', expectedRefundDate: '' },
      isAssigneeSubmitting: false,
      ...props,
    },
    global: { stubs: globalStubs },
  })
}

describe('TaskDepositFields', () => {
  beforeEach(() => {
    vi.clearAllTimers()
  })

  it('renders all 6 deposit field labels', () => {
    const wrapper = mountComponent()
    const text = wrapper.text()
    expect(text).toContain('保证金金额（万）')
    expect(text).toContain('保证金缴纳截止日期')
    expect(text).toContain('收款方')
    expect(text).toContain('收款账号')
    expect(text).toContain('实际缴纳日期')
    expect(text).toContain('预计归还日期')
  })

  // 保证金金额和保证金缴纳截止日期始终只读（disabled），不论是否执行人提交
  it('always disables depositAmount and depositDeadline fields, even when assignee submitting', () => {
    const wrapper = mountComponent({ isAssigneeSubmitting: true })
    const inputs = wrapper.findAll('input.el-input-stub, input.el-date-stub')
    // 第 1 个是保证金金额（el-input disabled），第 2 个是保证金缴纳截止日期（el-date-picker disabled）
    expect(inputs[0].attributes('disabled')).toBeDefined()
    expect(inputs[1].attributes('disabled')).toBeDefined()
  })

  // 4 个执行人填写字段：isAssigneeSubmitting=true 时可编辑
  it('enables 4 assignee-filled fields when isAssigneeSubmitting=true', () => {
    const wrapper = mountComponent({ isAssigneeSubmitting: true })
    const inputs = wrapper.findAll('input.el-input-stub, input.el-date-stub')
    // inputs[0]=金额, [1]=截止日期, [2]=收款方, [3]=收款账号, [4]=实际缴纳日期, [5]=预计归还日期
    expect(inputs[2].attributes('disabled')).toBeUndefined()
    expect(inputs[3].attributes('disabled')).toBeUndefined()
    expect(inputs[4].attributes('disabled')).toBeUndefined()
    expect(inputs[5].attributes('disabled')).toBeUndefined()
  })

  // 4 个执行人填写字段：非执行人提交时 disabled
  it('disables 4 assignee-filled fields when isAssigneeSubmitting=false (non-assignee viewing)', () => {
    const wrapper = mountComponent({ isAssigneeSubmitting: false })
    const inputs = wrapper.findAll('input.el-input-stub, input.el-date-stub')
    expect(inputs[2].attributes('disabled')).toBeDefined()
    expect(inputs[3].attributes('disabled')).toBeDefined()
    expect(inputs[4].attributes('disabled')).toBeDefined()
    expect(inputs[5].attributes('disabled')).toBeDefined()
  })

  // 回归测试（CO-448 bug fix）：任务详情页 mode='view' 但执行人提交时，4 字段必须可编辑。
  // 之前的 bug 是 disabled 表达式误用 `readonly || !isAssigneeSubmitting`，导致 view 模式下永远 disabled。
  // 修复后 disabled 仅取决于 isAssigneeSubmitting，与父组件 mode/readonly 无关。
  it('regression: enables 4 assignee-filled fields when isAssigneeSubmitting=true (covers view-mode assignee submit)', () => {
    // 仅传 isAssigneeSubmitting=true，不传任何 readonly 相关 prop（组件已不再接收）
    const wrapper = mountComponent({ isAssigneeSubmitting: true })
    const inputs = wrapper.findAll('input.el-input-stub, input.el-date-stub')
    expect(inputs[2].attributes('disabled')).toBeUndefined()
    expect(inputs[3].attributes('disabled')).toBeUndefined()
    expect(inputs[4].attributes('disabled')).toBeUndefined()
    expect(inputs[5].attributes('disabled')).toBeUndefined()
  })

  // 4 字段必填标识：仅执行人提交时显示
  it('shows required flag on 4 assignee-filled fields when isAssigneeSubmitting=true', () => {
    const wrapper = mountComponent({ isAssigneeSubmitting: true })
    const requiredFlags = wrapper.findAll('.required-flag')
    expect(requiredFlags).toHaveLength(4)
  })

  it('does not show required flag on 4 assignee-filled fields when isAssigneeSubmitting=false', () => {
    const wrapper = mountComponent({ isAssigneeSubmitting: false })
    const requiredFlags = wrapper.findAll('.required-flag')
    expect(requiredFlags).toHaveLength(0)
  })

  // 必填标识也不应出现在保证金金额和截止日期上（它们只读，不要求用户填）
  it('never shows required flag on depositAmount and depositDeadline fields', () => {
    const wrapper = mountComponent({ isAssigneeSubmitting: true })
    const formItems = wrapper.findAll('.form-item')
    // 第 1、2 个 form-item 是金额和截止日期，不应有 required-flag
    expect(formItems[0].find('.required-flag').exists()).toBe(false)
    expect(formItems[1].find('.required-flag').exists()).toBe(false)
  })

  // validate()：执行人提交且 4 字段为空 → invalid
  it('validate() returns invalid when isAssigneeSubmitting=true and 4 fields empty', () => {
    const wrapper = mountComponent({
      extendedFields: { depositAmount: 100, depositDeadline: '2026-08-15', payee: '', payeeAccount: '', actualPaymentDate: '', expectedRefundDate: '' },
      isAssigneeSubmitting: true,
    })
    const r = wrapper.vm.validate()
    expect(r.valid).toBe(false)
    expect(r.message).toBeTruthy()
  })

  // validate()：执行人提交且 4 字段填好 → valid
  it('validate() returns valid when isAssigneeSubmitting=true and 4 fields filled', () => {
    const wrapper = mountComponent({
      extendedFields: { depositAmount: 100, depositDeadline: '2026-08-15', payee: 'XX公司', payeeAccount: '1234', actualPaymentDate: '2026-07-15', expectedRefundDate: '2026-09-15' },
      isAssigneeSubmitting: true,
    })
    const r = wrapper.vm.validate()
    expect(r.valid).toBe(true)
  })

  // validate()：非执行人提交时直接 valid（不需要校验 4 字段）
  it('validate() returns valid when isAssigneeSubmitting=false (no validation needed)', () => {
    const wrapper = mountComponent({
      extendedFields: { depositAmount: 100, depositDeadline: '2026-08-15', payee: '', payeeAccount: '', actualPaymentDate: '', expectedRefundDate: '' },
      isAssigneeSubmitting: false,
    })
    const r = wrapper.vm.validate()
    expect(r.valid).toBe(true)
  })

  // validate() 显示字段级错误
  it('validate() shows field-level errors for 4 empty assignee fields', async () => {
    const wrapper = mountComponent({
      extendedFields: { depositAmount: 100, depositDeadline: '2026-08-15', payee: '', payeeAccount: '', actualPaymentDate: '', expectedRefundDate: '' },
      isAssigneeSubmitting: true,
    })
    wrapper.vm.validate()
    await wrapper.vm.$nextTick()
    const errors = wrapper.findAll('.form-item-error').map((el) => el.text())
    expect(errors.length).toBeGreaterThanOrEqual(4)
  })

  // 字段编辑后清错（最小行为校验）
  it('clears payee error when user fills payee field', async () => {
    const wrapper = mountComponent({
      extendedFields: { depositAmount: 100, depositDeadline: '2026-08-15', payee: '', payeeAccount: '', actualPaymentDate: '', expectedRefundDate: '' },
      isAssigneeSubmitting: true,
    })
    wrapper.vm.validate()
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('.form-item-error').length).toBeGreaterThanOrEqual(4)

    // 用户填写收款方
    const payeeInput = wrapper.findAll('input.el-input-stub')[1] // 第 2 个 el-input-stub（第 1 个是金额）
    await payeeInput.setValue('XX公司')
    await wrapper.vm.$nextTick()
    const errorsAfter = wrapper.findAll('.form-item-error').map((el) => el.text())
    expect(errorsAfter).not.toContain(expect.stringContaining('收款方'))
  })

  // emit update:extendedFields
  it('emits update:extendedFields when user edits payee field', async () => {
    const wrapper = mountComponent({
      extendedFields: { depositAmount: 100, depositDeadline: '2026-08-15', payee: '', payeeAccount: '', actualPaymentDate: '', expectedRefundDate: '' },
      isAssigneeSubmitting: true,
    })
    const payeeInput = wrapper.findAll('input.el-input-stub')[1]
    await payeeInput.setValue('XX公司')
    await wrapper.vm.$nextTick()
    const emitted = wrapper.emitted('update:extendedFields')
    expect(emitted).toBeTruthy()
    const lastPayload = emitted[emitted.length - 1][0]
    expect(lastPayload.payee).toBe('XX公司')
    // 其他字段保持不变
    expect(lastPayload.depositAmount).toBe(100)
    expect(lastPayload.depositDeadline).toBe('2026-08-15')
  })
})
