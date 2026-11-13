import { effectScope, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { scoreParseApi } from '@/api/modules/scoreParse.js'
import { useActualScoring } from './useActualScoring.js'
import { ElMessage } from 'element-plus'

vi.mock('@/api/modules/scoreParse.js', () => ({
  scoreParseApi: {
    triggerScoring: vi.fn(),
    getScoringStatus: vi.fn(),
  },
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, ElMessage: { success: vi.fn(), warning: vi.fn(), info: vi.fn() } }
})

describe('useActualScoring', () => {
  beforeEach(() => vi.clearAllMocks())

  it('submits one explicit request, observes its task by GET, then refreshes results', async () => {
    scoreParseApi.triggerScoring.mockResolvedValue({ data: { taskId: 'score-1', status: 'PENDING' } })
    scoreParseApi.getScoringStatus.mockResolvedValue({ data: {
      taskId: 'score-1', status: 'COMPLETED', completedAt: '2026-11-13T10:00:00',
    } })
    const visible = ref(true)
    const overlay = ref(false)
    const scored = ref(false)
    const scoreTime = ref(null)
    const scoringHint = ref('')
    const scoringQuality = ref({ success: true })
    const refresh = vi.fn().mockResolvedValue(undefined)
    const scope = effectScope()
    const scoring = scope.run(() => useActualScoring({
      props: { projectId: 1123 }, visible, loading: ref(false), currentStage: ref(2),
      scored, scoringOverlayVisible: overlay, scoringHint, scoreTime, scoringQuality,
      error: ref(null), refresh,
    }))

    await scoring.runScoring()

    expect(scoreParseApi.triggerScoring).toHaveBeenCalledTimes(1)
    expect(scoreParseApi.triggerScoring).toHaveBeenCalledWith(1123, {
      source: 'MANUAL', scope: 'ALL', itemIds: [],
    })
    expect(scoreParseApi.getScoringStatus).toHaveBeenCalledTimes(1)
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(scoreTime.value).not.toBeNull()
    expect(overlay.value).toBe(false)
    expect(ElMessage.success).toHaveBeenCalledWith('AI 实际打分完成')
    scope.stop()
  })
})
