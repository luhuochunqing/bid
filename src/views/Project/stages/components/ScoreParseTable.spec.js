import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ScoreParseTable from './ScoreParseTable.vue'
import { mapScoreResults } from '@/composables/projectDetail/scoreParseTask.js'

describe('ScoreParseTable.vue', () => {
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

  it('renders exact 8 columns in correct order: 编号, 标书原文, 评分项, 权重, 满足状态, 评分类别, 预计得分, 得分依据', () => {
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
      '编号',
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
})
