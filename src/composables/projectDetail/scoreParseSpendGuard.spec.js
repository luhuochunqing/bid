import { describe, it, expect } from 'vitest'
import {
  parseTriggerSource,
  scoringBody,
  CIRCUIT_MESSAGE,
  circuitHintFromMeta,
  scoringSkipHint,
  mapScoreResults,
  hasMeaningfulResults,
} from './scoreParseSpendGuard.js'

describe('scoreParseSpendGuard — spec 044 花费守卫纯函数', () => {
  it('silent parse 走 AUTO，手点走 MANUAL', () => {
    expect(parseTriggerSource(true)).toBe('AUTO')
    expect(parseTriggerSource(false)).toBe('MANUAL')
  })

  it('ITEMS 只提交非空 id，其它范围清空 itemIds', () => {
    expect(scoringBody({ scope: 'ITEMS', itemIds: [1, null, 2] })).toEqual({
      source: 'MANUAL',
      scope: 'ITEMS',
      itemIds: [1, 2],
    })
    expect(scoringBody({ scope: 'ALL', itemIds: [1] }).itemIds).toEqual([])
  })

  it('circuitOpen 才展示熔断说明', () => {
    expect(circuitHintFromMeta({ circuitOpen: true })).toBe(CIRCUIT_MESSAGE)
    expect(circuitHintFromMeta({ circuitOpen: false })).toBe('')
    expect(circuitHintFromMeta({})).toBe('')
  })

  it('SKIPPED 优先用 hint，否则默认文件未变化', () => {
    expect(scoringSkipHint({ outcome: 'SKIPPED', hint: '文件未变化' })).toBe('文件未变化')
    expect(scoringSkipHint({ outcome: 'SKIPPED' })).toBe('文件未变化')
    expect(scoringSkipHint({ hint: '全量打分' })).toBe('全量打分')
  })

  it('有实际得分或引用才算有效打分结果', () => {
    expect(hasMeaningfulResults([{ status: 'PENDING', actualScore: null }])).toBe(false)
    expect(hasMeaningfulResults([{ actualScore: 6 }])).toBe(true)
    expect(hasMeaningfulResults([{ quote: '第 3 章' }])).toBe(true)
  })

  it('mapScoreResults 按 code 建表', () => {
    const map = mapScoreResults([
      { code: 'D1', scoreType: 'OBJECTIVE', actualScore: 6, status: 'OK' },
    ])
    expect(map.D1.actualScore).toBe(6)
  })
})
