// Input: expense list rows already normalized by the resources API
// Output: expense page derived deposit tracking keeps backend return dates authoritative
// Pos: src/composables/ - expense page derived state regression tests

import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import { useExpensePageDerived } from './useExpensePageDerived'

function buildDerived(expenses) {
  return useExpensePageDerived({
    expenses: ref(expenses),
    approvalRecords: ref([]),
    searchForm: ref({ project: '', type: '', status: '' })
  })
}

describe('useExpensePageDerived', () => {
  it('prefers backend expectedReturnDate over fabricated fallback dates', () => {
    const { depositList } = buildDerived([
      {
        id: 9,
        type: '保证金',
        status: 'paid',
        paidAt: '2026-04-01 09:00:00',
        expectedReturnDate: '2026-06-30'
      }
    ])

    expect(depositList.value).toEqual([
      expect.objectContaining({
        id: 9,
        expectedReturn: '2026-06-30'
      })
    ])
  })

  it('keeps expectedReturn empty when backend does not provide one', () => {
    const { depositList } = buildDerived([
      {
        id: 10,
        type: '保证金',
        status: 'paid',
        paidAt: '2026-04-01 09:00:00',
        expectedReturnDate: ''
      }
    ])

    expect(depositList.value[0].expectedReturn).toBe('')
  })
})
