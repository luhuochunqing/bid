import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ScoreCandidateReviewCard from './ScoreCandidateReviewCard.vue'

describe('ScoreCandidateReviewCard.vue', () => {
  const candidateFacts = [
    { factId: 'f1', code: '1', dimension: '商务部分', weight: 6, scoreType: 'OBJECTIVE', sourceText: '业绩要求', candidateEligible: true },
    { factId: 'f2', code: '2', dimension: '财务状况', weight: 3, scoreType: 'SUBJECTIVE', sourceText: '财务指标', candidateEligible: true },
  ]

  it('renders candidate table with facts and displays dimension and weight', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts,
        candidateTotalWeight: 9,
        isAdoptingCandidates: false,
      },
      global: {
        stubs: {
          'el-alert': {
            template: '<div class="el-alert-mock"><slot name="title" />{{ title }}</div>',
            props: ['title'],
          },
          'el-button': {
            template: '<button class="el-button-mock"><slot /></button>',
          },
        },
      },
    })

    const rows = wrapper.findAll('.candidate-table tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].find('.col-dim').html()).toContain('商务部分')
    expect(rows[0].find('.col-weight').html()).toContain('6')
    expect(rows[1].find('.col-dim').html()).toContain('财务状况')
    expect(rows[1].find('.col-weight').html()).toContain('3')
  })

  it('shows score type and original detail as explicit review fields', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: { candidateFacts, candidateTotalWeight: 9 },
      global: {
        stubs: {
          'el-alert': true,
          'el-button': { template: '<button v-bind="$attrs"><slot /></button>' },
          'el-input': { template: '<span>{{ modelValue }}</span>', props: ['modelValue'] },
          'el-input-number': { template: '<span>{{ modelValue }}</span>', props: ['modelValue'] },
          'el-select': { template: '<span class="select-stub">{{ modelValue }}</span>', props: ['modelValue'] },
          'el-option': true,
        },
      },
    })

    expect(wrapper.find('thead').text()).toContain('评分项')
    expect(wrapper.find('thead').text()).toContain('权重')
    expect(wrapper.find('thead').text()).toContain('评分类别')
    expect(wrapper.find('thead').text()).toContain('标书原文与详情')
    expect(wrapper.findAll('.col-score-type')[0].text()).toContain('客观项')
    expect(wrapper.findAll('.col-score-type')[1].text()).toContain('主观项')
    expect(wrapper.findAll('.col-detail')[0].text()).toContain('业绩要求')
  })

  it('explains a partial candidate total and still allows explicit adoption', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts,
        candidateTotalWeight: 91,
        isAdoptingCandidates: false,
      },
      global: {
        stubs: {
          'el-alert': {
            template: '<div class="el-alert-mock">{{ title }}</div>',
            props: ['title'],
          },
          'el-button': { template: '<button v-bind="$attrs"><slot /></button>' },
        },
      },
    })

    const alert = wrapper.find('.el-alert-mock')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('候选合计为 91 分，原文声明 100 分，尚差 9 分')
    expect(wrapper.find('[data-test="btn-adopt-candidates"]').element.disabled).toBe(false)
  })

  it('blocks a candidate total above the declared total', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: { candidateFacts, candidateTotalWeight: 101, declaredTotalWeight: 100 },
      global: { stubs: { 'el-alert': true, 'el-button': { template: '<button v-bind="$attrs"><slot /></button>' } } },
    })

    expect(wrapper.find('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeDefined()
  })

  it('shows Chinese explanations instead of raw validator codes and JSON', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts: [{ ...candidateFacts[0], validationStatus: 'REVIEW_REQUIRED', validationMessage:
          '{"validationDiff":"ITEM_SOURCE_SCOPE_MISMATCH[field=dimension, expected=技术]; ITEM_WEIGHT_SOURCE_UNKNOWN[field=weight, expected=[6, 0], actual=6]; WEIGHT_SUM_MISMATCH[field=weight, expected=100, actual=91]"}',
          evidences: [{ kind: 'SCHEME_QUALIFICATION', quotedText: '', explanation: '{"schemeId":"internal-id","candidateEligible":true}' }] }],
        candidateTotalWeight: 6,
      },
      global: { stubs: { 'el-alert': true, 'el-button': true } },
    })

    const message = wrapper.find('.validation-diff').text()
    expect(message).toContain('评分项类别与引用的原文行暂时无法对应')
    expect(message).toContain('原文暂时无法单独确认该分值')
    expect(message).toContain('评分项合计与原文声明总分存在差额')
    expect(message).not.toContain('ITEM_SOURCE_SCOPE_MISMATCH')
    expect(message).not.toContain('ITEM_WEIGHT_SOURCE_UNKNOWN')
    expect(message).not.toContain('WEIGHT_SUM_MISMATCH')
    expect(message).not.toContain('{"')
    expect(wrapper.text()).not.toContain('internal-id')
  })

  it('separates out-of-scheme and unverified facts into an audit-only section', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts: [
          candidateFacts[0],
          { factId: 'contract', dimension: '履约考核', weight: 40, candidateEligible: false,
            scopeStatus: 'OUT_OF_SCHEME', exclusionReason: 'ITEM_SCHEME_SCOPE_MISMATCH', sourceText: '第98页履约考核' },
          { factId: 'legacy', dimension: '来源待核实', weight: 25, scopeStatus: 'SOURCE_UNVERIFIED' },
        ],
        candidateTotalWeight: 6,
      },
      global: { stubs: { 'el-alert': true, 'el-button': true, 'el-input': true, 'el-input-number': true,
        'el-select': true, 'el-option': true } },
    })

    expect(wrapper.findAll('.candidate-table tbody tr')).toHaveLength(1)
    expect(wrapper.find('.audit-facts').text()).toContain('履约考核')
    expect(wrapper.find('.audit-facts').text()).toContain('方案外')
    expect(wrapper.find('.audit-facts').text()).toContain('来源未验证')
    expect(wrapper.text()).toContain('本方案内 1 项，合计 6 分')
    expect(wrapper.find('.audit-facts').text()).toContain('不计当前候选、不计当前总分')
  })

  it('shows an explicit scheme selector and only the selected scheme in the candidate table', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts: [
          { factId: 'a', schemeId: 'scheme-a', code: '1', dimension: '方案甲', weight: 100, candidateEligible: true },
          { factId: 'b', schemeId: 'scheme-b', code: '1', dimension: '方案乙', weight: 150, candidateEligible: true },
          { factId: 'audit', schemeId: 'scheme-a', dimension: '履约考核', weight: 35, candidateEligible: false,
            scopeStatus: 'OUT_OF_SCHEME', sourceText: '第98页履约考核' },
        ],
        schemes: [
          { schemeId: 'scheme-a', title: '方案甲', declaredTotal: 100 },
          { schemeId: 'scheme-b', title: '方案乙', declaredTotal: 150 },
        ],
        selectedSchemeId: 'scheme-a',
        schemeSelectionRequired: true,
        candidateTotalWeight: 100,
        declaredTotalWeight: 100,
      },
      global: { stubs: { 'el-alert': true, 'el-button': true, 'el-input': true, 'el-input-number': true,
        'el-select': true, 'el-option': true } },
    })

    expect(wrapper.find('.scheme-selection').text()).toContain('评分方案')
    expect(wrapper.findAll('.candidate-table tbody tr')).toHaveLength(1)
    expect(wrapper.find('.candidate-review-title').text()).toContain('方案甲')
    expect(wrapper.find('.audit-facts').text()).toContain('方案乙')
    expect(wrapper.find('.audit-facts').text()).toContain('履约考核')
  })

  it('uses a Chinese fallback label when a scheme title is unavailable', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts: [{ factId: 'a', schemeId: 'scheme-d9e11f7d-private', dimension: '技术', weight: 91, candidateEligible: true }],
        schemes: [
          { schemeId: 'scheme-d9e11f7d-private', title: '', declaredTotal: 100 },
          { schemeId: 'scheme-other-private', title: '第二方案', declaredTotal: 50 },
        ],
        selectedSchemeId: 'scheme-d9e11f7d-private',
        schemeSelectionRequired: true,
        candidateTotalWeight: 91,
        declaredTotalWeight: 100,
      },
      global: { stubs: { 'el-alert': true, 'el-button': true, 'el-input': true, 'el-input-number': true,
        'el-select': { template: '<div class="select-stub"><slot /></div>' },
        'el-option': { template: '<div>{{ label }}<slot /></div>', props: ['label', 'value'] } } },
    })

    expect(wrapper.find('.scheme-selection').text()).toContain('评分方案1')
    expect(wrapper.find('.scheme-selection').text()).not.toContain('scheme-d9e11f7d-private')
  })

  it('does not render warning alert when candidateTotalWeight is 100', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts,
        candidateTotalWeight: 100,
        isAdoptingCandidates: false,
      },
      global: {
        stubs: {
          'el-alert': {
            template: '<div class="el-alert-mock">{{ title }}</div>',
            props: ['title'],
          },
          'el-button': true,
        },
      },
    })

    expect(wrapper.find('.el-alert-mock').exists()).toBe(false)
  })

  it('allows an 80-point candidate set when the document declares 80 points', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: { candidateFacts, candidateTotalWeight: 80, declaredTotalWeight: 80 },
      global: { stubs: { 'el-alert': true, 'el-button': { template: '<button v-bind="$attrs"><slot /></button>' } } },
    })

    expect(wrapper.find('.el-alert-mock').exists()).toBe(false)
    expect(wrapper.find('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeUndefined()
  })

  it('allows manual adoption when a single-table declared total is unknown', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: { candidateFacts, candidateTotalWeight: 100, declaredTotalWeight: null },
      global: {
        stubs: {
          'el-alert': { template: '<div class="el-alert-mock">{{ title }}</div>', props: ['title'] },
          'el-button': { template: '<button v-bind="$attrs"><slot /></button>' },
        },
      },
    })

    expect(wrapper.find('.el-alert-mock').text()).toContain('单表方案且候选项核验通过')
    expect(wrapper.find('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeUndefined()
  })

  it('keeps adoption disabled when multiple table totals conflict', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: { candidateFacts, candidateTotalWeight: 100, declaredTotalWeight: null, adoptionStatus: 'CONFLICTING_TOTAL' },
      global: {
        stubs: {
          'el-alert': { template: '<div class="el-alert-mock">{{ title }}</div>', props: ['title'] },
          'el-button': { template: '<button v-bind="$attrs"><slot /></button>' },
        },
      },
    })

    expect(wrapper.find('.el-alert-mock').text()).toContain('所选评分方案的原文总分存在冲突')
    expect(wrapper.find('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeDefined()
  })

  it('does not show internal evidence locators or qualification JSON', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: { candidateFacts: [{ ...candidateFacts[0], validationStatus: 'AUTO_APPROVED',
        validationMessage: '{"schemeId":"internal"}', sourceCellIds: '["doc0-t-0009-r3-c2"]',
        evidences: [{ kind: 'SCHEME_QUALIFICATION', explanation: '{"schemeId":"internal"}' }] }], candidateTotalWeight: 6 },
      global: { stubs: { 'el-alert': true, 'el-button': true } },
    })
    expect(wrapper.text()).not.toContain('doc0-t-0009-r3-c2')
    expect(wrapper.text()).not.toContain('schemeId')
    expect(wrapper.text()).not.toContain('internal')
    expect(wrapper.find('.validation-diff').exists()).toBe(false)
  })

  it('emits adopt when clicking adopt button', async () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts,
        candidateTotalWeight: 100,
        isAdoptingCandidates: false,
      },
      global: {
        stubs: {
          'el-button': {
            template: '<button class="el-button-mock"><slot /></button>',
          },
        },
      },
    })

    const adoptBtn = wrapper.find('[data-test="btn-adopt-candidates"]')
    await adoptBtn.trigger('click')
    expect(wrapper.emitted('adopt')).toHaveLength(1)
  })
})
