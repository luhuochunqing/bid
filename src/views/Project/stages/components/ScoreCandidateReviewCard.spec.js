import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import ScoreCandidateReviewCard from './ScoreCandidateReviewCard.vue'

describe('ScoreCandidateReviewCard.vue', () => {
  const candidateFacts = [
    { factId: 'f1', code: '1', dimension: '商务部分', weight: 6, scoreType: 'OBJECTIVE', sourceText: '业绩要求', candidateEligible: true },
    { factId: 'f2', code: '2', dimension: '财务状况', weight: 3, scoreType: 'SUBJECTIVE', sourceText: '财务指标', candidateEligible: true },
  ]

  it('shows source-unverified scored facts for manual review and permits adoption', () => {
    const facts = [{ factId: 'u', code: '1', dimension: '待核对', weight: 5, sourceText: '规则',
      candidateEligible: false, scopeStatus: 'SOURCE_UNVERIFIED', schemeId: null }]
    const wrapper = mount(ScoreCandidateReviewCard, { props: { candidateFacts: facts, candidateTotalWeight: 5 },
      global: { stubs: { 'el-button': { template: '<button v-bind="$attrs"><slot /></button>' } } } })
    expect(wrapper.findAll('.candidate-table tbody tr')).toHaveLength(1)
    expect(wrapper.text()).toContain('来源未核验')
    expect(wrapper.get('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeUndefined()
  })

  it('allows adoption with a warning when original rows are missing', () => {
    const wrapper = mount(ScoreCandidateReviewCard, { props: { candidateFacts, candidateTotalWeight: 9,
      declaredTotalWeight: 9, adoptionStatus: 'SOURCE_ROWS_UNCOVERED' },
      global: { stubs: { 'el-button': { template: '<button v-bind="$attrs"><slot /></button>' },
        'el-alert': { props: ['title'], template: '<div>{{ title }}</div>' } } },
    })
    expect(wrapper.get('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).toContain('原文评分表仍有未识别行')
  })

  it('allows adoption with a warning when parent-child relationship is unresolved', () => {
    const facts = [
      { factId: 'p', code: '1', dimension: '商务', weight: 15, candidateEligible: true, childAggregation: 'UNKNOWN' },
      { factId: 'c', code: '1.1', parentFactId: 'p', dimension: '商务', weight: 15, candidateEligible: true },
    ]
    const wrapper = mount(ScoreCandidateReviewCard, { props: { candidateFacts: facts, candidateTotalWeight: 85 },
      global: { stubs: { 'el-button': { template: '<button v-bind="$attrs"><slot /></button>' }, 'el-alert': { props: ['title'], template: '<div>{{ title }}</div>' } } },
    })
    expect(wrapper.get('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).toContain('父子评分关系未确认')
  })

  it('keeps identified rows readable but disables edits and adoption for a read-only viewer', () => {
    const wrapper = mount(ScoreCandidateReviewCard, { props: { candidateFacts, candidateTotalWeight: 9, readonly: true },
      global: { stubs: {
        'el-input': { props: ['disabled'], template: '<input :disabled="disabled" />' },
        'el-input-number': { props: ['disabled'], template: '<input :disabled="disabled" />' },
        'el-select': { props: ['disabled'], template: '<select :disabled="disabled"><slot /></select>' },
        'el-button': { template: '<button v-bind="$attrs"><slot /></button>' },
      } },
    })
    expect(wrapper.findAll('input').length).toBeGreaterThan(0)
    expect(wrapper.findAll('input').every(input => input.attributes('disabled') !== undefined)).toBe(true)
    expect(wrapper.get('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('检测到待人工复核')
  })

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

  it('allows manual review of a candidate total above the declared total', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: { candidateFacts, candidateTotalWeight: 101, declaredTotalWeight: 100 },
      global: { stubs: { 'el-alert': true, 'el-button': { template: '<button v-bind="$attrs"><slot /></button>' } } },
    })

    expect(wrapper.find('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeUndefined()
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

  it('shows unverified facts for review while separating out-of-scheme facts', () => {
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

    expect(wrapper.findAll('.candidate-table tbody tr')).toHaveLength(2)
    expect(wrapper.find('.audit-facts').text()).toContain('履约考核')
    expect(wrapper.find('.audit-facts').text()).toContain('方案外')
    expect(wrapper.text()).toContain('来源未核验')
    expect(wrapper.text()).toContain('当前方案候选 2 项，有效合计 6 分')
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

  it('shows both omitted rows and total mismatch without blocking manual adoption', () => {
    const wrapper = mount(ScoreCandidateReviewCard, { props: { candidateFacts, candidateTotalWeight: 100,
      declaredTotalWeight: 5, adoptionStatus: 'SOURCE_ROWS_UNCOVERED' }, global: { stubs: {
        'el-alert': { props: ['title'], template: '<div>{{ title }}</div>' },
        'el-button': { template: '<button v-bind="$attrs"><slot /></button>' },
      } } })
    expect(wrapper.text()).toContain('原文评分表仍有未识别行')
    expect(wrapper.text()).toContain('超过原文声明的 5 分')
    expect(wrapper.get('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeUndefined()
  })

  it('warns after a reviewer explicitly includes an alternative extraction', async () => {
    const alternative = { factId: 'alt', code: '1', dimension: '备用项', weight: 5,
      candidateEligible: true, scopeStatus: 'IN_SCHEME', schemeId: 's', sourceText: '完整规则',
      evidences: [{ kind: 'ALTERNATIVE_EXTRACTION_SET_NOT_SELECTED' }] }
    const wrapper = mount(ScoreCandidateReviewCard, { props: { candidateFacts: [alternative],
      candidateTotalWeight: 5, selectedSchemeId: 's', schemes: [{ schemeId: 's' }] }, global: { stubs: {
        'el-alert': { props: ['title'], template: '<div>{{ title }}</div>' },
        'el-button': { template: `<button v-bind="$attrs" @click="$emit('click')"><slot /></button>` },
      } } })
    expect(wrapper.text()).not.toContain('已选择备用提取结果')
    await wrapper.get('[data-test="btn-include-fact"]').trigger('click')
    expect(wrapper.text()).toContain('已选择备用提取结果')
    expect(alternative.manualIncluded).toBe(true)
  })

  it('allows manual adoption of 100 points despite a reported total of 5', () => {
    const wrapper = mount(ScoreCandidateReviewCard, { props: { candidateFacts, candidateTotalWeight: 100, declaredTotalWeight: 5 },
      global: { stubs: { 'el-alert': { props: ['title'], template: '<div>{{ title }}</div>' },
        'el-button': { template: '<button v-bind="$attrs"><slot /></button>' } } } })
    expect(wrapper.get('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).toContain('超过')
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

    expect(wrapper.find('.el-alert-mock').text()).toContain('请核对候选项后决定是否采纳')
    expect(wrapper.find('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeUndefined()
  })

  it('allows manual adoption when multiple table totals conflict', () => {
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
    expect(wrapper.find('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeUndefined()
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

  it('allows manual adoption when multi-table declared total is unknown (UNKNOWN_TOTAL)', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts,
        candidateTotalWeight: 69,
        declaredTotalWeight: null,
        adoptionStatus: 'UNKNOWN_TOTAL',
      },
      global: {
        stubs: {
          'el-alert': { template: '<div class="el-alert-mock">{{ title }}</div>', props: ['title'] },
          'el-button': { template: '<button v-bind="$attrs"><slot /></button>' },
        },
      },
    })

    expect(wrapper.find('.el-alert-mock').text()).toContain('该方案包含多个物理表格，原文未声明统一总分')
    expect(wrapper.find('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeUndefined()
  })

  it('supports excluding a candidate fact and including an audit fact into current scheme', async () => {
    const mutableFacts = [
      { factId: 'f1', schemeId: 'scheme-main', code: '1', dimension: '商务部分', weight: 6, scoreType: 'OBJECTIVE', sourceText: '业绩要求', candidateEligible: true },
      { factId: 'f2', code: '2', dimension: '服务保障', weight: 10, scoreType: 'SUBJECTIVE', sourceText: '保障方案', candidateEligible: false, scopeStatus: 'OUT_OF_SCHEME' },
    ]
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts: mutableFacts,
        candidateTotalWeight: 6,
        selectedSchemeId: 'scheme-main',
      },
      global: {
        stubs: {
          'el-button': { template: '<button v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>' },
        },
      },
    })

    // 移出 f1
    const excludeBtn = wrapper.find('[data-test="btn-exclude-fact"]')
    expect(excludeBtn.exists()).toBe(true)
    await excludeBtn.trigger('click')
    expect(mutableFacts[0].candidateEligible).toBe(false)
    expect(wrapper.emitted('fact-change')).toBeTruthy()

    // 纳入 f2
    const includeBtns = wrapper.findAll('[data-test="btn-include-fact"]')
    expect(includeBtns.length).toBeGreaterThan(0)
    await includeBtns[includeBtns.length - 1].trigger('click')
    expect(mutableFacts[1].candidateEligible).toBe(true)
    expect(mutableFacts[1].schemeId).toBe('scheme-main')
  })
})

it('distinguishes extracted records from zero candidates in the selected scheme', () => {
  const facts = Array.from({ length: 4 }, (_, index) => ({ factId: `outside-${index}`, code: String(index + 1),
    dimension: `原文记录${index + 1}`, weight: 10, sourceText: '完整原文', schemeId: 'other',
    candidateEligible: false, scopeStatus: 'OUT_OF_SCHEME' }))
  const wrapper = mount(ScoreCandidateReviewCard, { props: { candidateFacts: facts, selectedSchemeId: 'current',
    candidateTotalWeight: 0 }, global: { stubs: { 'el-alert': true, 'el-button': true } } })
  expect(wrapper.find('.candidate-review-title').text()).toContain('本次提取 4 条记录')
  expect(wrapper.find('.candidate-review-title').text()).toContain('当前方案候选 0 项，有效合计 0 分')
  expect(wrapper.findAll('.candidate-table tbody tr')).toHaveLength(0)
  expect(wrapper.findAll('.audit-fact-row')).toHaveLength(4)
  expect(wrapper.get('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeDefined()
})

it('warns and refuses silent mutation when clicking include while schemeSelectionRequired and no scheme is selected', async ({ onTestFinished }) => {
  const warning = vi.spyOn(ElMessage, 'warning')
  onTestFinished(() => warning.mockRestore())
  const facts = [
    { factId: 'f1', code: '1', dimension: '商务部分', weight: 20, sourceText: '商务规则', schemeId: 'scheme-pseudo', candidateEligible: false, scopeStatus: 'OUT_OF_SCHEME' },
  ]
  const wrapper = mount(ScoreCandidateReviewCard, {
    props: {
      candidateFacts: facts,
      schemes: [
        { schemeId: 'scheme-1', title: '第四章：谈判及评审方法' },
        { schemeId: 'scheme-2', title: '第二评分方案' },
      ],
      selectedSchemeId: null,
      schemeSelectionRequired: true,
      candidateTotalWeight: 0,
    },
    global: {
      stubs: {
        'el-alert': true,
        'el-select': true,
        'el-option': true,
        'el-button': { template: '<button v-bind="$attrs"><slot /></button>' },
      },
    },
  })

  await wrapper.get('[data-test="btn-include-fact"]').trigger('click')
  expect(warning).toHaveBeenCalledTimes(1)
  expect(warning).toHaveBeenCalledWith('请先在上方选择一个评分方案，再纳入评分项')
  expect(wrapper.emitted('scheme-change')).toBeUndefined()
  expect(wrapper.emitted('fact-change')).toBeUndefined()
  expect(facts[0].manualIncluded).toBeUndefined()
  expect(facts[0].candidateEligible).toBe(false)
})

it('blocks adoption when eligible candidates have empty, pure-numeric, placeholder, pipe, or unclosed-bracket names', () => {
  for (const badDim of ['', '1', '一', '评分项 21', '商务评分（20', '3 | 营业总收入']) {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts: [
          { factId: 'f1', code: '1', dimension: badDim, weight: 10, scoreType: 'OBJECTIVE', sourceText: '规则原文', candidateEligible: true },
        ],
        candidateTotalWeight: 10,
      },
      global: { stubs: { 'el-alert': true, 'el-select': true, 'el-option': true, 'el-button': { template: '<button v-bind="$attrs"><slot /></button>' } } },
    })
    expect(wrapper.find('[data-test="invalid-dimension-hint"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="invalid-dimension-alert"]').exists()).toBe(true)
    expect(wrapper.get('[data-test="btn-adopt-candidates"]').attributes('disabled')).toBeDefined()
  }
})

it.each(['SUM', 'UNKNOWN', undefined])('ignores the name of a %s rollup only while it has selected children', async (childAggregation) => {
  const parent = { factId: 'parent', dimension: '', weight: 10, sourceText: '分组规则', candidateEligible: true, childAggregation }
  const child = { factId: 'child', parentFactId: 'parent', dimension: '营业总收入', weight: 10, sourceText: '收入规则', candidateEligible: true }
  const wrapper = mount(ScoreCandidateReviewCard, {
    props: { candidateFacts: [parent, child], candidateTotalWeight: 10 },
    global: { stubs: { 'el-alert': true, 'el-button': { template: '<button v-bind="$attrs"><slot /></button>' } } },
  })
  const button = wrapper.get('[data-test="btn-adopt-candidates"]')
  expect(button.attributes('disabled')).toBeUndefined()
  expect(wrapper.find('[data-test="invalid-dimension-alert"]').exists()).toBe(false)
  expect(wrapper.find('[data-test="invalid-dimension-hint"]').exists()).toBe(false)
  await wrapper.setProps({ candidateFacts: [parent, { ...child, manualExcluded: true }] })
  expect(button.attributes('disabled')).toBeDefined()
})

it('checks the name of the effective shared cap and ignores its nested detail names', async () => {
  const parent = { factId: 'parent', dimension: '企业资质', weight: 10, sourceText: '共享上限', candidateEligible: true, childAggregation: 'SHARED_CAP' }
  const subgroup = { factId: 'group', parentFactId: 'parent', dimension: '', weight: 6, sourceText: '分组规则', candidateEligible: true, childAggregation: 'SUM' }
  const child = { factId: 'child', parentFactId: 'group', dimension: '', weight: 6, sourceText: '细则', candidateEligible: true }
  const wrapper = mount(ScoreCandidateReviewCard, {
    props: { candidateFacts: [parent, subgroup, child], candidateTotalWeight: 10 },
    global: { stubs: { 'el-alert': true, 'el-button': { template: '<button v-bind="$attrs"><slot /></button>' } } },
  })
  const button = wrapper.get('[data-test="btn-adopt-candidates"]')
  expect(button.attributes('disabled')).toBeUndefined()
  await wrapper.setProps({ candidateFacts: [{ ...parent, dimension: '' }, subgroup, child] })
  expect(button.attributes('disabled')).toBeDefined()
})

it('requires a name for a zero-point deduction only when its source is verified', async () => {
  const revenue = { factId: 'revenue', dimension: '营业总收入', weight: 10, sourceText: '收入规则', candidateEligible: true }
  const deduction = { factId: 'deduction', dimension: '', weight: 0, sourceText: '缺少材料扣分',
    candidateEligible: true, manualIncluded: true, scopeStatus: 'IN_SCHEME' }
  const wrapper = mount(ScoreCandidateReviewCard, {
    props: { candidateFacts: [revenue, deduction], candidateTotalWeight: 10 },
    global: { stubs: { 'el-alert': true, 'el-button': { template: '<button v-bind="$attrs"><slot /></button>' } } },
  })
  const button = wrapper.get('[data-test="btn-adopt-candidates"]')
  expect(button.attributes('disabled')).toBeUndefined()
  await wrapper.setProps({ candidateFacts: [revenue, { ...deduction,
    evidences: [{ kind: 'SOURCE_VERIFIED_ZERO_DEDUCTION', quotedText: deduction.sourceText }] }] })
  expect(button.attributes('disabled')).toBeDefined()
})
