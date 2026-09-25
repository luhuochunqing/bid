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
          'el-button': true,
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

  it('renders warning alert when candidateTotalWeight is not 100', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts,
        candidateTotalWeight: 9,
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

    const alert = wrapper.find('.el-alert-mock')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('9 分，应与原文声明的 100 分闭合')
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

  it('renders JSON encoded source cell ids without throwing', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: { candidateFacts: [{ ...candidateFacts[0], sourceCellIds: '["p1-r1-c1"]' }], candidateTotalWeight: 6 },
      global: { stubs: { 'el-alert': true, 'el-button': true } },
    })
    expect(wrapper.find('.evidence-location').text()).toContain('p1-r1-c1')
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
