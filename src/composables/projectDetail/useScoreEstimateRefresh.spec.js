// Input: estimate refresh API outcomes and project identity changes
// Output: isolated refresh state, readback and stale response assertions
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
