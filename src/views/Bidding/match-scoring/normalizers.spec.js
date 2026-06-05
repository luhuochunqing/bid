import { describe, expect, it } from 'vitest'
import {
  buildScoreFromAnalysis,
  getScoreTone,
  normalizeMatchScoreForView,
  summarizeScoreState,
} from './normalizers.js'

describe('match scoring view normalizers', () => {
  it('keeps arbitrary enabled dimensions instead of relying on fixed labels', () => {
    const score = normalizeMatchScoreForView({
      totalScore: '83',
      modelVersion: '2026.04',
      dimensions: [
        { key: 'customA', name: '客户预算弹性', score: '90', weight: '55', enabled: true },
        { key: 'customB', name: '现场交付窗口', score: '70', weight: '45', enabled: true },
        { key: 'disabled', name: '停用维度', score: '100', weight: '0', enabled: false },
      ],
    })

    expect(score.totalScore).toBe(83)
    expect(score.dimensionSummaries.map((dimension) => dimension.name)).toEqual([
      '客户预算弹性',
      '现场交付窗口',
    ])
    expect(score.dimensionSummaries[0]).toMatchObject({
      key: 'customA',
      score: 90,
      percentage: 90,
      weightText: '55%',
    })
  })

  it('normalizes evidence from strings and objects', () => {
    const score = normalizeMatchScoreForView({
      dimensions: [
        {
          key: 'evidence',
          name: '证据维度',
          evidence: ['公告预算充足', { title: '历史项目', content: '同类交付记录' }],
        },
      ],
    })

    expect(score.dimensionSummaries[0].evidence).toEqual([
      { title: '公告预算充足', content: '', source: '' },
      { title: '历史项目', content: '同类交付记录', source: '' },
    ])
  })

  it('summarizes empty, failed, not-configured and ready states', () => {
    expect(summarizeScoreState({ loading: true }).text).toBe('正在加载评分')
    expect(summarizeScoreState({ error: '网络异常' }).text).toBe('评分加载失败')
    expect(summarizeScoreState({ score: { status: 'NOT_CONFIGURED' } }).text).toBe('配置缺失')
    expect(summarizeScoreState({ score: { status: 'FAILED', failureReason: '生成失败' } }).description).toBe('生成失败')
    expect(summarizeScoreState({ score: { status: 'READY', totalScore: 80 } }).text).toBe('真实评分已生成')
    expect(summarizeScoreState({ score: null }).actionText).toBe('生成匹配评分')
  })

  it('builds reusable score display data from AI analysis dimensions', () => {
    const score = buildScoreFromAnalysis({
      winScore: 76,
      suggestion: '建议继续跟进',
      dimensionScores: [{ name: '动态维度', score: 66, description: '来自分析结果' }],
    })

    expect(score.status).toBe('READY')
    expect(score.summary).toBe('建议继续跟进')
    expect(score.dimensionSummaries[0]).toMatchObject({
      name: '动态维度',
      score: 66,
      evidence: [{ title: '评估说明', content: '来自分析结果', source: '' }],
    })
  })

  it('returns stable tone for score bands', () => {
    expect(getScoreTone(92)).toBe('excellent')
    expect(getScoreTone(76)).toBe('good')
    expect(getScoreTone(52)).toBe('warning')
    expect(getScoreTone(20)).toBe('danger')
  })
})
