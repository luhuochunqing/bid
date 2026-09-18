import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { useScoreCandidateAdoption } from './useScoreCandidateAdoption.js'
import { scoreParseApi } from '@/api/modules/scoreParse.js'
import { ElMessage } from 'element-plus'

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
      error: vi.fn(),
    },
  }
})

vi.mock('@/api/modules/scoreParse.js', () => ({
  scoreParseApi: {
    submitReview: vi.fn(),
  },
}))

describe('useScoreCandidateAdoption.js', () => {
  let props
  let reviewFacts
  let reviewSnapshot
  let reviewSubmitted
  let currentStage
  let fetchAnalysisData

  beforeEach(() => {
    vi.clearAllMocks()
    props = { projectId: 49 }
    reviewFacts = ref([
      { factId: 'f1', code: '1', dimension: '资质要求', weight: 10, sourceText: '具备ISO9001' },
      { factId: 'f2', code: '2', dimension: '技术方案', weight: 30, sourceText: '实施方案完整' },
      { factId: 'f3', code: '3', dimension: '占位模板', weight: 0, sourceText: 'XX~XX分' },
    ])
    reviewSnapshot = ref({ facts: reviewFacts.value, audits: [] })
    reviewSubmitted = ref(false)
    currentStage = ref(2)
    fetchAnalysisData = vi.fn().mockResolvedValue()
  })

  it('candidateFacts 应正确过滤出分值大于0的有效项', () => {
    const { candidateFacts, candidateTotalWeight } = useScoreCandidateAdoption({
      props,
      reviewFacts,
      reviewSnapshot,
      reviewSubmitted,
      currentStage,
      fetchAnalysisData,
    })

    expect(candidateFacts.value).toHaveLength(2)
    expect(candidateFacts.value.map((f) => f.factId)).toEqual(['f1', 'f2'])
    expect(candidateTotalWeight.value).toBe(40)
  })

  it('adoptCandidateFacts 默认调用 submitReview 一键采纳所有候选并刷新数据', async () => {
    scoreParseApi.submitReview.mockResolvedValue({
      data: {
        facts: reviewFacts.value,
        audits: [{ action: 'ADOPT_CANDIDATES', reason: '一键采纳所有候选有效评分项' }],
      },
    })

    const { isAdoptingCandidates, adoptCandidateFacts } = useScoreCandidateAdoption({
      props,
      reviewFacts,
      reviewSnapshot,
      reviewSubmitted,
      currentStage,
      fetchAnalysisData,
    })

    await adoptCandidateFacts()

    expect(scoreParseApi.submitReview).toHaveBeenCalledWith(49, {
      reason: '人工确认采纳候选评分标准',
      actions: [{ action: 'ADOPT_CANDIDATES', reason: '一键采纳所有候选有效评分项' }],
    })
    expect(reviewSubmitted.value).toBe(true)
    expect(ElMessage.success).toHaveBeenCalledWith('已成功采纳候选评分标准！')
    expect(fetchAnalysisData).toHaveBeenCalledWith({ stage: 2, autoScore: false, autoParse: false })
    expect(isAdoptingCandidates.value).toBe(false)
  })

  it('adoptCandidateFacts 支持按指定 factId 列表提交细粒度采纳动作', async () => {
    scoreParseApi.submitReview.mockResolvedValue({
      data: {
        facts: reviewFacts.value,
        audits: [{ action: 'ADOPT_CANDIDATES' }],
      },
    })

    const { adoptCandidateFacts } = useScoreCandidateAdoption({
      props,
      reviewFacts,
      reviewSnapshot,
      reviewSubmitted,
      currentStage,
      fetchAnalysisData,
    })

    await adoptCandidateFacts(['f1'])

    expect(scoreParseApi.submitReview).toHaveBeenCalledWith(49, {
      reason: '人工确认采纳候选评分标准',
      actions: [{ factId: 'f1', action: 'ADOPT_CANDIDATES' }],
    })
  })

  it('adoptCandidateFacts 接口报错时能优雅处理并恢复 loading 状态', async () => {
    scoreParseApi.submitReview.mockRejectedValue(new Error('网络超时'))

    const { isAdoptingCandidates, adoptCandidateFacts } = useScoreCandidateAdoption({
      props,
      reviewFacts,
      reviewSnapshot,
      reviewSubmitted,
      currentStage,
      fetchAnalysisData,
    })

    await adoptCandidateFacts()

    expect(isAdoptingCandidates.value).toBe(false)
  })
})
