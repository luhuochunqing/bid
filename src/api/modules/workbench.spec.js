// Input: workbenchApi and mocked http client
// Output: workbench schedule overview request/normalization coverage
// Pos: src/api/modules/测试 - workbench API spec
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
}))

vi.mock('../client.js', () => ({
  default: {
    get: getMock,
  },
}))

import { workbenchApi } from './workbench.js'

describe('workbenchApi', () => {
  beforeEach(() => {
    getMock.mockReset()
  })

  it('getScheduleOverview should send date params and normalize payload', async () => {
    getMock.mockResolvedValue({
      success: true,
      data: {
        start: '2026-04-01',
        end: '2026-04-30',
        total: 3,
        urgent: 1,
        events: [{ id: 1 }],
      },
    })

    const result = await workbenchApi.getScheduleOverview({
      start: new Date('2026-04-01T08:00:00'),
      end: '2026-04-30',
      assigneeId: 9,
    })

    expect(getMock).toHaveBeenCalledWith('/api/workbench/schedule-overview', {
      params: {
        start: '2026-04-01',
        end: '2026-04-30',
        assigneeId: 9,
      },
    })
    expect(result.data).toEqual({
      start: '2026-04-01',
      end: '2026-04-30',
      assigneeId: null,
      total: 3,
      urgent: 1,
      events: [{ id: 1 }],
    })
  })

  it('getScheduleOverview should fall back to empty normalized payload', async () => {
    getMock.mockResolvedValue({ success: true, data: null })

    const result = await workbenchApi.getScheduleOverview({})

    expect(result.data).toEqual({
      start: '',
      end: '',
      assigneeId: null,
      total: 0,
      urgent: 0,
      events: [],
    })
  })
})
