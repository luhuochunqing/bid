// Input: estimate refresh API outcomes and project identity changes
// Output: isolated refresh state, reload invalidation and receipt/rule/result race assertions
// Pos: projectDetail composable unit test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive, ref } from 'vue'
import { useScoreEstimateRefresh } from './useScoreEstimateRefresh.js'
import { scoreParseApi } from '@/api/modules/scoreParse.js'

vi.mock('@/api/modules/scoreParse.js', () => ({
  scoreParseApi: { refreshEstimates: vi.fn(), getItems: vi.fn() },
}))

const ITEM = {
  id: 2, code: 'D1', dim: '企业荣誉', detail: '未知证书条件', weight: 6,
  scoreType: 'OBJECTIVE', status: 'PENDING', estScore: null, estimateStatus: 'STALE',
}

describe('useScoreEstimateRefresh', () => {
  beforeEach(() => vi.clearAllMocks())

  it('clears the summary and pending reasons when active items reload in the same project', async () => {
    const items = ref([ITEM])
    const refresh = { processedCount: 1, scoredCount: 0, pendingCount: 1,
      pendingItems: [{ id: 2, dim: '企业荣誉', reason: '缺少荣誉证据' }] }
    scoreParseApi.refreshEstimates.mockResolvedValue({ data: { items: [ITEM], refresh } })
    scoreParseApi.getItems.mockResolvedValue({ data: { items: [ITEM] } })
    const state = useScoreEstimateRefresh({ projectId: 1 }, items, ref(false), ref(false))
    await state.refreshEstimates()
    expect(state.estimateRefreshResult.value).toEqual(refresh)

    items.value = [{ ...ITEM, id: 3, dim: '财务信用' }]

    expect(state.estimateRefreshResult.value).toBeNull()
    expect(items.value[0].dim).toBe('财务信用')
  })

  it('keeps POST counts and pending reasons while displaying GET readback, including zero scores', async () => {
    const items = ref([ITEM])
    const refresh = { processedCount: 2, scoredCount: 1, pendingCount: 1,
      pendingItems: [{ id: 3, dim: '财务信用', reason: '缺少财务证据' }] }
    const pendingItem = { ...ITEM, id: 3, dim: '财务信用' }
    const scoredItem = { ...ITEM, estScore: 0, estBasis: '未匹配业绩' }
    scoreParseApi.refreshEstimates.mockResolvedValue({ data: { items: [scoredItem, pendingItem], summary: null, meta: {}, refresh } })
    scoreParseApi.getItems.mockResolvedValue({ data: { items: [{ ...ITEM, estScore: 0, estBasis: '未匹配业绩' }, pendingItem] } })
    const state = useScoreEstimateRefresh({ projectId: 1 }, items, ref(false), ref(false))

    await state.refreshEstimates()

    expect(state.estimateRefreshResult.value).toEqual(refresh)
    expect(items.value[0].estScore).toBe(0)
    expect(items.value[0].estBasis).toBe('未匹配业绩')
    expect(scoreParseApi.getItems).toHaveBeenCalledWith(1)
  })

  it.each([
    { field: 'estScore', value: 6, displayed: 6 },
    { field: 'status', value: 'OK', displayed: 'ok' },
    { field: 'estBasis', value: '更新后的知识库依据', displayed: '更新后的知识库依据' },
    { field: 'kbHit', value: true, displayed: true },
    { field: 'estimatedAt', value: '2026-11-12T12:00:01', displayed: '2026-11-12T12:00:01' },
    { field: 'estimateStatus', value: 'STALE', displayed: 'STALE' },
  ])('rejects a result-only $field race but keeps GET data visible', async ({ field, value, displayed }) => {
    const saved = { ...ITEM, estScore: 0, status: 'DANGER', estBasis: '未匹配', kbHit: false,
      estimatedAt: '2026-11-12T12:00:00', estimateStatus: 'FRESH' }
    const changed = { ...saved, [field]: value }
    const refresh = { processedCount: 1, scoredCount: 1, pendingCount: 0, pendingItems: [] }
    scoreParseApi.refreshEstimates.mockResolvedValue({ data: { items: [saved], refresh } })
    scoreParseApi.getItems.mockResolvedValueOnce({ data: { items: [saved] } })
      .mockResolvedValueOnce({ data: { items: [changed] } })
    const items = ref([ITEM])
    const state = useScoreEstimateRefresh({ projectId: 1 }, items, ref(false), ref(false))
    await state.refreshEstimates()
    expect(state.estimateRefreshResult.value).toEqual(refresh)

    await state.refreshEstimates()

    expect(state.estimateRefreshResult.value).toBeNull()
    expect(state.estimateRefreshError.value).toContain('预判结果在刷新后发生变化')
    expect(items.value[0][field]).toBe(displayed)
    expect(state.estimatesRefreshing.value).toBe(false)
  })

  it.each([
    { processedCount: 0, scoredCount: 0, pendingCount: 0, pendingItems: [] },
    undefined,
  ])('never reports success for empty or missing refresh statistics: %j', async refresh => {
    scoreParseApi.refreshEstimates.mockResolvedValue({ data: { refresh } })
    scoreParseApi.getItems.mockResolvedValue({ data: { items: [] } })
    const state = useScoreEstimateRefresh({ projectId: 1 }, ref([ITEM]), ref(false), ref(false))
    await state.refreshEstimates()
    expect(state.estimateRefreshResult.value).toBeNull()
    expect(state.estimateRefreshError.value).not.toBe('')
  })

  it.each([
    { processedCount: -1, scoredCount: 0, pendingCount: -1, pendingItems: [] },
    { processedCount: 1.5, scoredCount: 1.5, pendingCount: 0, pendingItems: [] },
    { processedCount: 1, scoredCount: '1', pendingCount: 0, pendingItems: [] },
    { processedCount: 1, scoredCount: 0, pendingCount: 0, pendingItems: [] },
    { processedCount: 1, scoredCount: 0, pendingCount: 1, pendingItems: [] },
    { processedCount: 1, scoredCount: 0, pendingCount: 1, pendingItems: [{ id: 99, dim: '信用', reason: '缺证据' }] },
    { processedCount: 1, scoredCount: 0, pendingCount: 1, pendingItems: [{ id: 2, dim: '信用', reason: '' }] },
    { processedCount: 2, scoredCount: 2, pendingCount: 0, pendingItems: [] },
  ])('rejects malformed receipt statistics: %j', async refresh => {
    scoreParseApi.refreshEstimates.mockResolvedValue({ data: { items: [ITEM], refresh } })
    scoreParseApi.getItems.mockResolvedValue({ data: { items: [ITEM] } })
    const state = useScoreEstimateRefresh({ projectId: 1 }, ref([ITEM]), ref(false), ref(false))
    await state.refreshEstimates()
    expect(state.estimateRefreshResult.value).toBeNull()
    expect(state.estimateRefreshError.value).not.toBe('')
  })

  it.each([
    { receiptItems: undefined },
    { receiptItems: [{ ...ITEM, id: 99 }] },
    { receiptItems: [{ ...ITEM, detail: '已修改的规则' }] },
  ])('rejects missing receipt items or a changed active rule set: %j', async ({ receiptItems }) => {
      scoreParseApi.refreshEstimates.mockResolvedValue({ data: { items: receiptItems, refresh: {
        processedCount: 1, scoredCount: 1, pendingCount: 0, pendingItems: [],
      } } })
      scoreParseApi.getItems.mockResolvedValue({ data: { items: [ITEM] } })
      const state = useScoreEstimateRefresh({ projectId: 1 }, ref([ITEM]), ref(false), ref(false))
      await state.refreshEstimates()
      expect(state.estimateRefreshResult.value).toBeNull()
      expect(state.estimateRefreshError.value).not.toBe('')
    })

  it('clears the previous summary and exposes a readback failure', async () => {
    scoreParseApi.refreshEstimates.mockResolvedValue({ data: { items: [ITEM], refresh: {
      processedCount: 1, scoredCount: 1, pendingCount: 0, pendingItems: [],
    } } })
    scoreParseApi.getItems.mockResolvedValueOnce({ data: { items: [ITEM] } })
      .mockRejectedValueOnce(new Error('回读失败'))
    const items = ref([ITEM])
    const state = useScoreEstimateRefresh({ projectId: 1 }, items, ref(false), ref(false))
    await state.refreshEstimates()
    expect(state.estimateRefreshResult.value.processedCount).toBe(1)
    await state.refreshEstimates()
    expect(state.estimateRefreshResult.value).toBeNull()
    expect(state.estimateRefreshError.value).toContain('回读失败')
    expect(items.value).toHaveLength(1)
  })

  it('lets the new project refresh while ignoring an old project completion', async () => {
    const props = reactive({ projectId: 1 })
    const items = ref([ITEM])
    const state = useScoreEstimateRefresh(props, items, ref(false), ref(false))
    let finishOld
    scoreParseApi.refreshEstimates.mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve }))
    const oldRequest = state.refreshEstimates()
    props.projectId = 2
    expect(state.estimatesRefreshing.value).toBe(false)
    scoreParseApi.refreshEstimates.mockResolvedValueOnce({ data: { items: [{ ...ITEM, id: 22, estScore: 0 }], refresh: {
      processedCount: 1, scoredCount: 1, pendingCount: 0, pendingItems: [],
    } } })
    let finishNew
    scoreParseApi.getItems.mockImplementationOnce(() => new Promise(resolve => { finishNew = resolve }))
    const newRequest = state.refreshEstimates()
    await vi.waitFor(() => expect(finishNew).toBeTypeOf('function'))
    finishOld({ data: {} })
    await oldRequest
    expect(state.estimatesRefreshing.value).toBe(true)
    finishNew({ data: { items: [{ ...ITEM, id: 22, estScore: 0 }] } })
    await newRequest
    expect(items.value[0].id).toBe(22)
    expect(state.estimateRefreshResult.value.scoredCount).toBe(1)
    props.projectId = 3
    expect(state.estimateRefreshResult.value).toBeNull()
  })

  it('preserves items and exposes the API error when refresh fails', async () => {
    const items = ref([ITEM])
    scoreParseApi.refreshEstimates.mockRejectedValue(new Error('无权限更新'))
    const state = useScoreEstimateRefresh({ projectId: 1 }, items, ref(false), ref(false))

    await state.refreshEstimates()

    expect(items.value).toEqual([ITEM])
    expect(state.estimateRefreshError.value).toContain('无权限更新')
    expect(state.estimatesRefreshing.value).toBe(false)
  })

  it('discards an old readback after an A-B-A project switch', async () => {
    const props = reactive({ projectId: 1 })
    const items = ref([ITEM])
    const state = useScoreEstimateRefresh(props, items, ref(false), ref(false))
    scoreParseApi.refreshEstimates.mockResolvedValue({ data: {} })
    let finish
    scoreParseApi.getItems.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))

    const pending = state.refreshEstimates()
    await vi.waitFor(() => expect(finish).toBeTypeOf('function'))
    props.projectId = 2
    props.projectId = 1
    finish({ data: { items: [{ ...ITEM, estScore: 999, estimateStatus: 'FRESH' }] } })
    await pending

    expect(items.value).toEqual([ITEM])
  })
})
