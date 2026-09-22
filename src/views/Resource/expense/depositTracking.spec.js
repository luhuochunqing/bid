// Input: deposit return tracking helpers
// Output: deposit tracking derivation coverage without fake date arithmetic
// Pos: src/views/Resource/expense/ - Expense page helper unit tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { describe, expect, it } from 'vitest'

import { buildDepositTrackingList } from './depositTracking.js'

describe('buildDepositTrackingList', () => {
  it('uses backend expectedReturnDate and reminder fields directly', () => {
    const rows = buildDepositTrackingList([
      {
        id: 7,
        type: '保证金',
        project: '西域数据港',
        amount: 20,
        status: 'paid',
        date: '2026-01-01',
        expectedReturnDate: '2026-04-10',
        lastRemindedAt: '2026-04-12 10:20:00',
        overdue: true
      }
    ])

    expect(rows).toEqual([
      expect.objectContaining({
        id: 7,
        expectedReturnDate: '2026-04-10',
        lastRemindedAt: '2026-04-12 10:20:00',
        overdue: true
      })
    ])
  })

  it('does not fabricate expectedReturnDate from payment date', () => {
    const [row] = buildDepositTrackingList([
      {
        id: 8,
        type: '保证金',
        project: '西域云枢纽',
        amount: 5,
        status: 'paid',
        date: '2026-02-01',
        expectedReturnDate: '',
        lastRemindedAt: '',
        overdue: false
      }
    ])

    expect(row.expectedReturnDate).toBe('')
    expect(row.overdue).toBe(false)
  })
})
