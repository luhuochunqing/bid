import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import BasicInfoReadOnly from './BasicInfoReadOnly.vue'
import BasicFieldsSection from './BasicFieldsSection.vue'
import ProjectPlanGapUpload from './ProjectPlanGapUpload.vue'
import TenderEvaluationForm from '../TenderEvaluationForm.vue'

function mountWithElementPlus(component, options = {}) {
  return mount(component, {
    ...options,
    global: {
      ...(options.global || {}),
      plugins: [ElementPlus, ...(options.global?.plugins || [])],
    },
  })
}

vi.mock('@/stores/user.js', () => ({
  useUserStore: () => ({ token: 'test-token' }),
}))

function expectTextareaAutosize(textarea, expected) {
  expect(textarea.props('type')).toBe('textarea')
  expect(textarea.props('autosize')).toEqual(expected)
}

describe('BasicInfoReadOnly — 只读详情多行字段', () => {
  it('标讯描述和标讯信息应使用 readonly-textarea 原生 textarea', async () => {
    const wrapper = mountWithElementPlus(BasicInfoReadOnly, {
      props: {
        tender: {
          description: '第一行\n第二行\n第三行\n第四行\n第五行',
          tenderInfo: '信息第一行\n信息第二行\n信息第三行\n信息第四行',
        },
      },
    })
    await wrapper.vm.$nextTick()

    const textareas = wrapper.findAll('textarea.readonly-textarea')
    expect(textareas).toHaveLength(2)
    expect(textareas[0].attributes('readonly')).toBeDefined()
    expect(textareas[1].attributes('readonly')).toBeDefined()
    expect(textareas[0].element.value).toContain('第一行')
    expect(textareas[1].element.value).toContain('信息第一行')

    expect(wrapper.findAllComponents({ name: 'ElInput' }).filter(c => c.props('type') === 'textarea')).toHaveLength(0)
  })
})

describe('BasicFieldsSection — 评估表基础段多行字段启用 autosize', () => {
  it('招标文件不利项、风险预判、了解流程、支持备注应启用 autosize', async () => {
    const wrapper = mountWithElementPlus(BasicFieldsSection, {
      props: {
        modelValue: {
          plannedShortlistedCount: null,
          mroOfficeFlowAmount: null,
          customerRevenue: null,
          unfavorableItems: '不利项1\n不利项2',
          riskAssessment: '风险1\n风险2',
          contingencyPlan: '否',
          processKnowledge: '了解',
          supportNotes: '备注1\n备注2',
        },
        disabled: false,
        errors: {},
      },
    })
    await wrapper.vm.$nextTick()

    const textareas = wrapper.findAllComponents({ name: 'ElInput' }).filter(c => c.props('type') === 'textarea')
    expect(textareas).toHaveLength(4)
    textareas.forEach(t => expectTextareaAutosize(t, { minRows: 3, maxRows: 10 }))
  })
})

describe('ProjectPlanGapUpload — GAP 多行字段启用 autosize', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('项目计划差距应启用 autosize', async () => {
    const wrapper = mountWithElementPlus(ProjectPlanGapUpload, {
      props: {
        modelValue: {
          projectPlanGap: '差距1\n差距2',
          projectPlanGapFiles: [],
        },
        tenderId: 9001,
        disabled: false,
      },
    })
    await wrapper.vm.$nextTick()

    const textareas = wrapper.findAllComponents({ name: 'ElInput' }).filter(c => c.props('type') === 'textarea')
    expect(textareas).toHaveLength(1)
    expectTextareaAutosize(textareas[0], { minRows: 3, maxRows: 10 })
  })
})

describe('TenderEvaluationForm — 建议段理由字段启用 autosize', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('选择不投标时理由字段应启用 autosize', async () => {
    const wrapper = mountWithElementPlus(TenderEvaluationForm, {
      props: {
        tenderId: 9001,
        evaluation: {
          evaluationStatus: 'DRAFT',
          basic: {
            plannedShortlistedCount: 3,
            mroOfficeFlowAmount: 500000,
            customerRevenue: 120000,
            unfavorableItems: '不利项',
            riskAssessment: '风险',
            contingencyPlan: '否',
            processKnowledge: '了解',
            supportNotes: '备注',
            projectPlanGap: '',
            projectPlanGapFiles: [],
          },
          customerInfo: [],
          recommendation: { shouldBid: false, reason: '预算不足\n周期紧张\n资源不够' },
        },
        canFill: true,
        canDecide: false,
        canFillRecommendation: true,
      },
    })
    await wrapper.vm.$nextTick()

    const textareas = wrapper.findAllComponents({ name: 'ElInput' }).filter(c => c.props('type') === 'textarea')
    const reasonTextarea = textareas.find(t => t.props('placeholder')?.includes('理由'))
    expect(reasonTextarea).toBeTruthy()
    expectTextareaAutosize(reasonTextarea, { minRows: 4, maxRows: 10 })
  })
})
