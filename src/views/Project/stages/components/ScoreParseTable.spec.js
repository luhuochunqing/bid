import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ScoreParseTable from './ScoreParseTable.vue'
import { mapScoreResults } from '@/composables/projectDetail/scoreParseTask.js'

describe('ScoreParseTable.vue', () => {
  it('shows continuous row sequence numbers without changing lookup codes', () => {
    const items = [{ id: 1, code: 'business_financial', dim: '经营能力', detail: '完整规则', weight: 5 },
      { id: 2, code: 'tech_platform', dim: '平台能力', detail: '完整规则', weight: 10 },
      { id: 3, code: '2.2.1', dim: '其他', detail: '完整规则', weight: 1 }]
    const wrapper = mount(ScoreParseTable, { props: { items } })
    expect(wrapper.findAll('.code-cell').map(cell => cell.text())).toEqual(['1', '2', '3'])
    expect(items[0].code).toBe('business_financial')
  })
  it('distinguishes subjective, unsupported estimates and genuine zero without affecting actual mode', () => {
    const items = [
      { id: 1, scoreType: '主观项', estScore: '待确认', status: 'neutral' },
      { id: 2, scoreType: '客观项', estScore: null, status: 'neutral', estBasis: '未识别到知识库匹配类别，待人工确认预计得分' },
      { id: 3, scoreType: '客观项', estScore: 0, status: 'danger' },
      { id: 4, scoreType: '客观项', estScore: null, status: 'neutral' },
    ]
    const wrapper = mount(ScoreParseTable, { props: { items } })
    expect(wrapper.findAll('tbody .score-cell').map(c => c.text())).toEqual(['待专家评审', '需人工评估', '0', '待确认'])
    const actual = mount(ScoreParseTable, { props: { items, mode: 'actual' } })
    expect(actual.findAll('tbody .score-cell').every(c => c.text() === '待确认')).toBe(true)
  })

  const mockItems = [
    {
      code: '1',
      dim: '办公通用类物资',
      detail: '办公通用类物资 10% 35% 50% / 5% / 单边法',
      weight: 29,
      status: 'neutral',
      scoreType: '客观项',
      estScore: '待确认',
    },
    {
      code: '2.2.1',
      dim: '自营商城订单销量',
      detail: '横向比较各供应商自营商城近三年销售金额情况',
      weight: 6,
      status: 'neutral',
      scoreType: '主观项',
      estScore: '待确认',
    },
  ]

  it('renders exact 8 columns in correct order: 序号, 标书原文, 评分项, 权重, 满足状态, 评分类别, 预计得分, 得分依据', () => {
    const wrapper = mount(ScoreParseTable, {
      props: {
        items: mockItems,
        mode: 'est',
        totalWeight: 35,
        statsOkCount: 0,
        statsDangerCount: 0,
        statsNeutralCount: 2,
        objectiveWeight: 29,
        subjectiveWeight: 6,
        highlightScore: '待确认',
      },
    })

    const headers = wrapper.findAll('thead th').map((th) => th.text())
    expect(headers).toEqual([
      '序号',
      '标书原文',
      '评分项',
      '权重',
      '满足状态',
      '评分类别',
      '预计得分',
      '得分依据',
    ])

    const firstRowCells = wrapper.findAll('tbody tr:first-child td')
    expect(firstRowCells[0].text()).toBe('1')
    expect(firstRowCells[1].text()).toBe('办公通用类物资 10% 35% 50% / 5% / 单边法')
    expect(firstRowCells[2].text()).toBe('办公通用类物资')
    expect(firstRowCells[3].text()).toBe('29')
    expect(firstRowCells[4].text()).toContain('待确认')
    expect(firstRowCells[5].text()).toBe('客观项')
    expect(firstRowCells[6].text()).toBe('待确认')
    expect(firstRowCells[7].text()).toBe('详情')
  })

  it('duplicate local codes keep independent rows when item.id differs', () => {
    const wrapper = mount(ScoreParseTable, {
      props: {
        items: [
          {
            id: 101,
            code: '1',
            dim: '财务状况',
            detail: '现金流量净额连续三年为正得1.5分',
            weight: 3,
            status: 'neutral',
            scoreType: '客观项',
            estScore: null,
          },
          {
            id: 102,
            code: '1',
            dim: '服务团队方案',
            detail: '人员配置方面：项目经理花名册',
            weight: 10,
            status: 'ok',
            scoreType: '客观项',
            estScore: 10,
          },
        ],
        mode: 'est',
        totalWeight: 13,
        statsOkCount: 1,
        statsDangerCount: 0,
        statsNeutralCount: 1,
        objectiveWeight: 13,
        subjectiveWeight: 0,
        highlightScore: 10,
      },
    })

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('财务状况')
    expect(rows[0].text()).toContain('待确认')
    expect(rows[1].text()).toContain('服务团队方案')
    expect(rows[1].text()).toContain('满足')
    expect(rows[0].attributes('data-item-id')).toBe('101')
    expect(rows[1].attributes('data-item-id')).toBe('102')
  })

  it('actual mode: duplicate codes resolve results by item.id only', () => {
    const wrapper = mount(ScoreParseTable, {
      props: {
        items: [
          { id: 101, code: '1', dim: '财务状况', detail: '现金流', weight: 3, scoreType: '客观项' },
          { id: 102, code: '1', dim: '服务团队方案', detail: '人员配置', weight: 10, scoreType: '客观项' },
        ],
        results: mapScoreResults([
          { scoreItemId: 101, code: '1', actualScore: null, status: 'PENDING' },
          { scoreItemId: 102, code: '1', actualScore: 10, status: 'OK' },
        ]),
        mode: 'actual',
        totalWeight: 13,
        objectiveWeight: 13,
        subjectiveWeight: 0,
      },
    })
    const rows = wrapper.findAll('tbody tr')
    expect(rows[0].text()).toContain('财务状况')
    expect(rows[0].text()).toContain('待确认')
    expect(rows[1].text()).toContain('服务团队方案')
    expect(rows[1].text()).toContain('满足')
    expect(rows[1].text()).toContain('10')
  })

  it('renders actual mode headers with 实际得分 and total weight footer', () => {
    const wrapper = mount(ScoreParseTable, {
      props: {
        items: mockItems,
        results: {
          '1': { actualScore: 29, status: 'ok' },
        },
        mode: 'actual',
        totalWeight: 35,
        objectiveWeight: 29,
        subjectiveWeight: 6,
      },
    })

    const headers = wrapper.findAll('thead th').map((th) => th.text())
    expect(headers[6]).toBe('实际得分')

    const firstRowCells = wrapper.findAll('tbody tr:first-child td')
    expect(firstRowCells[4].text()).toContain('满足')
    expect(firstRowCells[6].text()).toBe('29')
  })

  it('rounds floating-point objective and subjective weights to integers and ensures sum is 100 when totalWeight is 100', () => {
    const wrapper = mount(ScoreParseTable, {
      props: {
        items: mockItems,
        mode: 'est',
        totalWeight: 100,
        objectiveWeight: 43.4,
        subjectiveWeight: 56.6,
      },
    })
    expect(wrapper.find('.obj-text').text()).toBe('客观项权重 43')
    expect(wrapper.find('.subj-text').text()).toBe('主观项权重 57')
  })

  it('handles half rounding so objective and subjective weights sum strictly to 100', () => {
    const wrapper = mount(ScoreParseTable, {
      props: {
        items: mockItems,
        mode: 'est',
        totalWeight: 100,
        objectiveWeight: 43.5,
        subjectiveWeight: 56.5,
      },
    })
    expect(wrapper.find('.obj-text').text()).toBe('客观项权重 44')
    expect(wrapper.find('.subj-text').text()).toBe('主观项权重 56')
  })

  it('applies is-text class to highlight-score-box when score is string text and removes it when score is number', () => {
    const textWrapper = mount(ScoreParseTable, {
      props: {
        items: [{ id: 1, scoreType: '主观项', weight: 100 }],
        mode: 'est',
      },
    })
    const textBox = textWrapper.find('.highlight-score-box')
    expect(textBox.text()).toBe('待确认')
    expect(textBox.classes()).toContain('is-text')

    const numWrapper = mount(ScoreParseTable, {
      props: {
        items: [{ id: 1, scoreType: '客观项', weight: 100, estScore: 88 }],
        mode: 'est',
      },
    })
    const numBox = numWrapper.find('.highlight-score-box')
    expect(numBox.text()).toBe('88')
    expect(numBox.classes()).not.toContain('is-text')
  })
})

describe('same-source totals and historical scores', () => {
  it('does not assume 100 or warn for a genuine 200 point scheme', () => {
    const w = mount(ScoreParseTable, { props: { items: [], totalWeight: 200, declaredTotal: 200 } })
    expect(w.find('.weight-warning').exists()).toBe(false)
  })
  it('shows unknown total without inventing 100', () => {
    const w = mount(ScoreParseTable, { props: { items: [] } })
    expect(w.find('.tfoot-weight').text()).toBe('待确认')
  })
  it('retains historical value but excludes it from current total', () => {
    const w = mount(ScoreParseTable, { props: { mode: 'actual', items: [{ id: 1, scoreType: '客观项', weight: 100 }], results: mapScoreResults([{ scoreItemId: 1, actualScore: 80, status: 'PENDING', reuseKind: 'LEGACY_UNVERIFIED' }]) } })
    expect(w.text()).toContain('历史评分，需重新评分')
    expect(w.find('.highlight-score-box').text()).toBe('待确认')
    expect(w.find('.score-cell').text()).toContain('80')
  })
})

it('does not label unverified active candidate text as original source', () => {
  const w = mount(ScoreParseTable, { props: { items: [{ id: 1, detail: '模型规则', validationStatus: 'REVIEW_REQUIRED' }], versioned: true } })
  expect(w.findAll('thead th')[1].text()).toBe('当前规则文本')
  expect(w.text()).toContain('来源未核验')
})
