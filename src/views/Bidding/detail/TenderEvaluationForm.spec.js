// Instance-level permission matrix spec for TenderEvaluationForm (V130 3-section + V1026 field rename).
//
// Replaces the legacy role-string contract: instead of asking "is the user
// MANAGER / ADMIN?" the form is driven entirely by two booleans
// (`canFill`, `canDecide`) computed by the backend on the evaluation DTO.
//
// canFill   → user is the tender's latest assignee (PM of this tender)
// canDecide → user is the latest assigned-by (the one who assigned the tender)
//
// Decision matrix (canFill × canDecide × evaluationStatus):
//
//   canFill | canDecide | status     | form editable | save/submit | bid/abandon
//   --------|-----------|------------|---------------|-------------|------------
//   true    | *         | null/DRAFT | yes           | submit only | no
//   true    | *         | SUBMITTED  | no (RO)       | no          | (see canDecide)
//   false   | *         | any        | no (RO)       | no          | (see canDecide)
//   *       | true      | SUBMITTED  | (see canFill) | -           | yes
//   *       | true      | null/DRAFT | -             | -           | no  (eval first)
//   *       | false     | any        | -             | -           | no

import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const { elMessage, elMessageBox } = vi.hoisted(() => ({
  elMessage: {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  },
  elMessageBox: {
    confirm: vi.fn(),
    prompt: vi.fn(),
  },
}))

vi.mock('@/stores/user.js', () => ({
  useUserStore: () => ({ token: 'test-token' }),
}))

vi.mock('element-plus', () => ({
  ElMessage: elMessage,
  ElMessageBox: elMessageBox,
}))

import TenderEvaluationForm from './TenderEvaluationForm.vue'

const globalStubs = {
  ElForm: {
    name: 'ElForm',
    props: ['model', 'rules', 'labelWidth', 'disabled'],
    template: '<form class="el-form-stub" :data-disabled="disabled ? \'true\' : \'false\'"><slot /></form>',
  },
  ElFormItem: {
    name: 'ElFormItem',
    props: ['label', 'prop', 'required'],
    template: '<div class="el-form-item-stub" :data-prop="prop"><label>{{ label }}</label><slot /></div>',
  },
  ElInput: {
    name: 'ElInput',
    props: ['modelValue', 'type', 'rows', 'placeholder', 'disabled', 'readonly'],
    emits: ['update:modelValue'],
    template:
      '<textarea v-if="type === \'textarea\'" class="el-input-stub" :data-disabled="disabled ? \'true\' : \'false\'" :data-readonly="readonly ? \'true\' : \'false\'" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' +
      '<input v-else class="el-input-stub" :data-disabled="disabled ? \'true\' : \'false\'" :data-readonly="readonly ? \'true\' : \'false\'" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElInputNumber: {
    name: 'ElInputNumber',
    props: ['modelValue', 'min', 'max', 'precision', 'disabled'],
    emits: ['update:modelValue'],
    template:
      '<input class="el-input-number-stub" type="number" :data-disabled="disabled ? \'true\' : \'false\'" :data-min="min" :data-precision="precision" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
  },
  ElSelect: {
    name: 'ElSelect',
    props: ['modelValue', 'placeholder', 'disabled'],
    emits: ['update:modelValue', 'change'],
    template:
      '<select class="el-select-stub" :data-disabled="disabled ? \'true\' : \'false\'" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>',
  },
  ElOption: {
    name: 'ElOption',
    props: ['label', 'value'],
    template: '<option :value="value">{{ label }}</option>',
  },
  ElButton: {
    name: 'ElButton',
    props: ['type', 'disabled'],
    emits: ['click'],
    template:
      '<button type="button" :data-button-type="type" :disabled="disabled" @click="$emit(\'click\', $event)"><slot /></button>',
  },
  ElCard: {
    name: 'ElCard',
    template: '<div class="el-card-stub"><slot name="header" /><slot /></div>',
  },
  ElCollapse: {
    name: 'ElCollapse',
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div class="el-collapse-stub"><slot /></div>',
  },
  ElCollapseItem: {
    name: 'ElCollapseItem',
    props: ['title', 'name'],
    template: '<div class="el-collapse-item-stub"><slot /></div>',
  },
  CustomerInfoMatrix: {
    name: 'CustomerInfoMatrix',
    template: '<div class="customer-info-matrix-stub"><slot /></div>',
  },
  ElDatePicker: {
    name: 'ElDatePicker',
    props: ['modelValue', 'type', 'placeholder', 'disabled', 'format', 'valueFormat'],
    emits: ['update:modelValue'],
    template:
      '<input class="el-date-picker-stub" type="date" :data-disabled="disabled ? String(true) : String(false)" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
  ElUpload: {
    name: 'ElUpload',
    props: ['file-list', 'action', 'headers', 'before-upload', 'on-success', 'on-remove', 'multiple', 'drag', 'accept', 'limit'],
    emits: ['update:file-list'],
    template: '<div class="el-upload-stub" />',
  },
  ElIcon: {
    name: 'ElIcon',
    props: [],
    template: '<span class="el-icon-stub"><slot /></span>',
  },
  Document: {
    name: 'Document',
    template: '<span class="document-icon-stub" />',
  },
  Upload: {
    name: 'Upload',
    template: '<span class="upload-icon-stub" />',
  },
}

function makeWrapper(props = {}, extra = {}) {
  setActivePinia(createPinia())
  return mount(TenderEvaluationForm, {
    props: {
      tenderId: 9001,
      evaluation: null,
      canFill: false,
      canDecide: false,
      ...props,
    },
    global: {
      stubs: globalStubs,
      plugins: [createPinia()],
      ...(extra.global || {}),
    },
  })
}

function findButtonByText(wrapper, label) {
  const buttons = wrapper.findAll('button')
  return buttons.find((b) => b.text().trim() === label) || null
}

function makeFullPayload(overrides = {}) {
  return {
    basic: {
      plannedShortlistedCount: 3,
      mroOfficeFlowAmount: 500000,
      customerRevenue: 120000,
      unfavorableItems: '有较大技术偏离',
      riskAssessment: '竞争对手强势',
      contingencyPlan: '已有备选方案',
      processKnowledge: '是',
      supportNotes: '需要法务支持',
      projectPlanGap: '时间紧张',
    },
    customerInfo: [],
    recommendation: {
      shouldBid: true,
      reason: '市场机会大',
    },
    ...overrides,
  }
}

function makeEvaluation(status, overrides = {}) {
  if (status === null) return null
  return {
    evaluationStatus: status,
    ...makeFullPayload(),
    ...overrides,
  }
}

async function fillRequiredBasicFields(wrapper) {
  // Find plannedShortlistedCount by its form-item label
  const plannedItem = wrapper.findAll('.el-form-item-stub').find(el => el.find('label').text() === '计划入围供应商数量')
  if (plannedItem) {
    const input = plannedItem.find('input.el-input-number-stub')
    if (input.exists()) await input.setValue('3')
  }
  // Find mroOfficeFlowAmount
  const mroItem = wrapper.findAll('.el-form-item-stub').find(el => el.find('label').text() === '电商MRO+办公流水金额（亿）')
  if (mroItem) {
    const input = mroItem.find('input.el-input-number-stub')
    if (input.exists()) await input.setValue('500000')
  }
  // Find unfavorableItems
  const unfavorableItem = wrapper.findAll('.el-form-item-stub').find(el => el.find('label').text() === '招标文件不利项')
  if (unfavorableItem) {
    const input = unfavorableItem.find('textarea.el-input-stub')
    if (input.exists()) await input.setValue('有较大技术偏离')
  }
  // Find riskAssessment
  const riskItem = wrapper.findAll('.el-form-item-stub').find(el => el.find('label').text() === '风险预判')
  if (riskItem) {
    const input = riskItem.find('textarea.el-input-stub')
    if (input.exists()) await input.setValue('竞争对手强势')
  }
  await flushPromises()
}

async function fillRequiredRecommendation(wrapper) {
  const shouldBidItem = wrapper.findAll('.el-form-item-stub').find(el => el.find('label').text() === '是否投标')
  if (shouldBidItem) {
    const select = shouldBidItem.find('select.el-select-stub')
    if (select.exists()) await select.setValue('true')
  }
  await flushPromises()
}

function formIsDisabled(wrapper) {
  const form = wrapper.findComponent({ name: 'ElForm' })
  return form.exists() && form.props('disabled') === true
}

describe('TenderEvaluationForm — instance-level permission matrix (V130 3-section)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ---------- rendering / fields presence ----------

  it('renders section 1: 基础信息 fields regardless of permissions', () => {
    const wrapper = makeWrapper({ canFill: true, canDecide: false, evaluation: null })
    const text = wrapper.text()
    expect(text).toContain('计划入围供应商数量')
    expect(text).toContain('电商MRO+办公流水金额')
    expect(text).toContain('客户营收')
    expect(text).toContain('招标文件不利项')
    expect(text).toContain('风险预判')
    expect(text).toContain('项目经理综合评估是否有兜底方案')
    expect(text).toContain('项目经理是否了解评标全流程')
    expect(text).toContain('需要的支持及其他关键信息备注')
    expect(text).toContain('项目计划 GAP')
  })

  it('renders section 2: 客户信息', () => {
    const wrapper = makeWrapper({ canFill: true, canDecide: false, evaluation: null })
    expect(wrapper.findComponent({ name: 'CustomerInfoMatrix' }).exists()).toBe(true)
  })

  it('renders section 3: 投标负责人建议 fields', () => {
    const wrapper = makeWrapper({ canFill: true, canDecide: false, evaluation: null })
    const text = wrapper.text()
    expect(text).toContain('是否投标')
    // 理由字段默认隐藏（仅 shouldBid=false 时显示）
    expect(text).not.toContain('理由')
  })

  it('renders section 3: 理由字段 shouldBid=false 时显示', () => {
    const wrapper = makeWrapper({
      canFill: true,
      canDecide: false,
      evaluation: makeEvaluation('DRAFT', { recommendation: { shouldBid: false, reason: '测试理由' } }),
    })
    const text = wrapper.text()
    expect(text).toContain('理由')
  })

  it('renders section 3: 理由字段 shouldBid=true 时隐藏', () => {
    const wrapper = makeWrapper({
      canFill: true,
      canDecide: false,
      evaluation: makeEvaluation('DRAFT', { recommendation: { shouldBid: true, reason: '' } }),
    })
    const text = wrapper.text()
    expect(text).not.toContain('理由')
  })

  // ---------- matrix: form-editable axis (canFill × status) ----------
  //
  // Note: Action buttons (保存草稿/提交/投标/弃标) were moved to BottomActionBar
  // in DetailPage.vue. TenderEvaluationForm only controls form disabled state and
  // exposes { handleSubmit, handleSaveDraft, handleBid, handleAbandon }.

  it('canFill=true + status=null → editable', () => {
    const wrapper = makeWrapper({ canFill: true, canDecide: false, evaluation: null })
    expect(formIsDisabled(wrapper)).toBe(false)
  })

  it('canFill=true + status=DRAFT → editable', () => {
    const wrapper = makeWrapper({
      canFill: true, canDecide: false, evaluation: makeEvaluation('DRAFT'),
    })
    expect(formIsDisabled(wrapper)).toBe(false)
  })

  it('canFill=true + status=SUBMITTED → read-only', () => {
    const wrapper = makeWrapper({
      canFill: true, canDecide: false, evaluation: makeEvaluation('SUBMITTED'),
    })
    expect(formIsDisabled(wrapper)).toBe(true)
  })

  it('canFill=false + status=DRAFT → read-only', () => {
    const wrapper = makeWrapper({
      canFill: false, canDecide: false, evaluation: makeEvaluation('DRAFT'),
    })
    expect(formIsDisabled(wrapper)).toBe(true)
  })

  it('canFill=false + status=null → read-only (un-assigned tender)', () => {
    const wrapper = makeWrapper({
      canFill: false, canDecide: false, evaluation: null,
    })
    expect(formIsDisabled(wrapper)).toBe(true)
  })

  // ---------- matrix: decision axis (canDecide × status) ----------
  // Decision buttons are shown by BottomActionBar; TenderEvaluationForm
  // exposes handleBid/handleAbandon for the parent to call.

  it('canDecide=true + status=SUBMITTED → form is read-only (bid/decide done by parent)', () => {
    const wrapper = makeWrapper({
      canFill: false, canDecide: true, evaluation: makeEvaluation('SUBMITTED'),
    })
    expect(formIsDisabled(wrapper)).toBe(true)
  })

  it('canDecide=false + status=SUBMITTED → read-only', () => {
    const wrapper = makeWrapper({
      canFill: false, canDecide: false, evaluation: makeEvaluation('SUBMITTED'),
    })
    expect(formIsDisabled(wrapper)).toBe(true)
  })

  it('canFill=true + canDecide=true + status=SUBMITTED → read-only (assignee == assigner edge case)', () => {
    const wrapper = makeWrapper({
      canFill: true, canDecide: true, evaluation: makeEvaluation('SUBMITTED'),
    })
    expect(formIsDisabled(wrapper)).toBe(true)
  })

  // ---------- emits / behaviour (via exposed methods) ----------

  it('submit event fires with full payload when canFill=true and recommendation filled', async () => {
    const wrapper = makeWrapper({ canFill: true, canDecide: false, evaluation: null })
    await fillRequiredBasicFields(wrapper)
    await fillRequiredRecommendation(wrapper)
    wrapper.vm.handleSubmit()
    await flushPromises()

    const emitted = wrapper.emitted('submit')
    expect(emitted).toBeTruthy()
    expect(emitted.length).toBe(1)
    expect(emitted[0][0]).toMatchObject({
      evaluationBasic: {
        plannedShortlistedCount: 3,
        mroOfficeFlowAmount: 500000,
        unfavorableItems: '有较大技术偏离',
        riskAssessment: '竞争对手强势',
      },
    })
    expect(emitted[0][0].evaluationRecommendation.shouldBid).toBeTruthy()
  })

  it('submit does NOT fire when recommendation missing', async () => {
    const wrapper = makeWrapper({ canFill: true, canDecide: false, evaluation: null })
    await fillRequiredBasicFields(wrapper)
    wrapper.vm.handleSubmit()
    await flushPromises()
    expect(wrapper.emitted('submit')).toBeFalsy()
  })

  it('submit does NOT fire when plannedShortlistedCount = 0 (policy requires >= 1)', async () => {
    const wrapper = makeWrapper({ canFill: true, canDecide: false, evaluation: null })
    await fillRequiredBasicFields(wrapper)
    const numbers = wrapper.findAll('input.el-input-number-stub')
    await numbers[0].setValue('0')
    await fillRequiredRecommendation(wrapper)
    wrapper.vm.handleSubmit()
    await flushPromises()
    expect(wrapper.emitted('submit')).toBeFalsy()
  })

  it('bid event fires via exposed handleBid', async () => {
    const wrapper = makeWrapper({
      canFill: false, canDecide: true, evaluation: makeEvaluation('SUBMITTED'),
    })
    wrapper.vm.handleBid()
    await flushPromises()
    const emitted = wrapper.emitted('bid')
    expect(emitted).toBeTruthy()
    expect(emitted.length).toBe(1)
  })

  it('abandon fires with { reason } when canDecide=true + SUBMITTED + confirm dialog', async () => {
    elMessageBox.prompt.mockResolvedValueOnce({ value: '客户预算不足' })
    const wrapper = makeWrapper({
      canFill: false, canDecide: true, evaluation: makeEvaluation('SUBMITTED'),
    })
    await wrapper.vm.handleAbandon()
    await flushPromises()
    expect(elMessageBox.prompt).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('abandon')[0][0]).toEqual({ reason: '客户预算不足' })
  })

  it('abandon does NOT emit when dialog cancelled', async () => {
    elMessageBox.prompt.mockRejectedValueOnce(new Error('cancel'))
    const wrapper = makeWrapper({
      canFill: false, canDecide: true, evaluation: makeEvaluation('SUBMITTED'),
    })
    await wrapper.vm.handleAbandon()
    await flushPromises()
    expect(wrapper.emitted('abandon')).toBeFalsy()
  })
})
