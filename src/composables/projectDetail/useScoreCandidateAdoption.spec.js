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

  it('candidateFacts 应展示包括零分项在内的完整候选', () => {
    const { candidateFacts, candidateTotalWeight } = useScoreCandidateAdoption({
      props,
      reviewFacts,
      reviewSnapshot,
      reviewSubmitted,
      currentStage,
      fetchAnalysisData,
    })

    expect(candidateFacts.value).toHaveLength(3)
    expect(candidateFacts.value.map((f) => f.factId)).toEqual(['f1', 'f2', 'f3'])
    expect(candidateTotalWeight.value).toBe(40)
  })

  it('declaredTotal 缺失时保留待确认状态，不伪装成100分', () => {
    const { declaredTotalWeight } = useScoreCandidateAdoption({
      props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData,
    })

    expect(declaredTotalWeight.value).toBeNull()
    reviewSnapshot.value = { ...reviewSnapshot.value, declaredTotal: 80 }
    expect(declaredTotalWeight.value).toBe(80)
  })

  it('candidateFacts 展示父子全量事实，但总分只统计叶子项', () => {
    reviewFacts.value = [
      { factId: 'p1', code: '', dimension: '商务部分', weight: 30, sourceText: '商务部分汇总' },
      { factId: 'c1', code: '1', dimension: '企业业绩', weight: 6, sourceText: '业绩细则', parentFactId: 'p1' },
      { factId: 'c2', code: '2', dimension: '财务状况', weight: 3, sourceText: '财务细则', parentFactId: 'p1' },
    ]
    const { candidateFacts, candidateTotalWeight } = useScoreCandidateAdoption({
      props,
      reviewFacts,
      reviewSnapshot,
      reviewSubmitted,
      currentStage,
      fetchAnalysisData,
    })

    expect(candidateFacts.value).toHaveLength(3)
    expect(candidateFacts.value.map((f) => f.factId)).toEqual(['p1', 'c1', 'c2'])
    expect(candidateTotalWeight.value).toBe(9)
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

  it('adoptCandidateFacts 提交人工修正后的完整候选字段', async () => {
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

    await adoptCandidateFacts([{
      factId: 'f1', code: '1', dimension: '资质要求', weight: 10,
      sourceText: '具备ISO9001', parentFactId: '', scoreType: 'OBJECTIVE',
    }])

    expect(scoreParseApi.submitReview).toHaveBeenCalledWith(49, {
      reason: '人工确认采纳候选评分标准',
      actions: [{
        factId: 'f1', action: 'ADOPT_CANDIDATES', adjustedWeight: 10,
        adjustedCode: '1', adjustedDimension: '资质要求',
        adjustedSourceText: '具备ISO9001', adjustedParentFactId: '',
        adjustedScoreType: 'OBJECTIVE',
      }],
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

  describe('版式继承与业务父子关系分离（三场景一致性校验）', () => {
    it('场景1：普通未合并标签，3个末级项，合计100分', () => {
      reviewFacts.value = [
        { factId: 'f1', code: '1', dimension: '商务', weight: 30, sourceText: '财务状况', parentFactId: '' },
        { factId: 'f2', code: '2', dimension: '商务', weight: 20, sourceText: '履约能力', parentFactId: '' },
        { factId: 'f3', code: '3', dimension: '价格', weight: 50, sourceText: '投标报价', parentFactId: '' },
      ]
      const { candidateFacts, candidateTotalWeight } = useScoreCandidateAdoption({
        props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData,
      })

      expect(candidateFacts.value).toHaveLength(3)
      expect(candidateTotalWeight.value).toBe(100)
      expect(candidateFacts.value.map((f) => ({ code: f.code, weight: f.weight, dim: f.dimension }))).toEqual([
        { code: '1', weight: 30, dim: '商务' },
        { code: '2', weight: 20, dim: '商务' },
        { code: '3', weight: 50, dim: '价格' },
      ])
    })

    it('场景2：合并标签“商务”无汇总分值，正常计分明细不作为父项，仍为3个末级项，合计100分', () => {
      reviewFacts.value = [
        { factId: 'f1', code: '1', dimension: '商务', weight: 30, sourceText: '财务状况', parentFactId: '' },
        { factId: 'f2', code: '2', dimension: '商务', weight: 20, sourceText: '履约能力', parentFactId: '' },
        { factId: 'f3', code: '3', dimension: '价格', weight: 50, sourceText: '投标报价', parentFactId: '' },
      ]
      const { candidateFacts, candidateTotalWeight } = useScoreCandidateAdoption({
        props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData,
      })

      expect(candidateFacts.value).toHaveLength(3)
      expect(candidateTotalWeight.value).toBe(100)
      expect(candidateFacts.value.map((f) => ({ code: f.code, weight: f.weight, dim: f.dimension, parentFactId: f.parentFactId }))).toEqual([
        { code: '1', weight: 30, dim: '商务', parentFactId: '' },
        { code: '2', weight: 20, dim: '商务', parentFactId: '' },
        { code: '3', weight: 50, dim: '价格', parentFactId: '' },
      ])
    })

    it('场景3：合并标签“商务（50分）”存在独立汇总父事实，排除父项保留3个末级项，合计100分', () => {
      reviewFacts.value = [
        { factId: 'p1', code: '', dimension: '商务', weight: 50, sourceText: '商务（50分）', parentFactId: '' },
        { factId: 'f1', code: '1', dimension: '商务', weight: 30, sourceText: '财务状况', parentFactId: 'p1' },
        { factId: 'f2', code: '2', dimension: '商务', weight: 20, sourceText: '履约能力', parentFactId: 'p1' },
        { factId: 'f3', code: '3', dimension: '价格', weight: 50, sourceText: '投标报价', parentFactId: '' },
      ]
      const { candidateFacts, candidateTotalWeight } = useScoreCandidateAdoption({
        props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData,
      })

      expect(candidateFacts.value).toHaveLength(4)
      expect(candidateTotalWeight.value).toBe(100)
      expect(candidateFacts.value.map((f) => ({ code: f.code, weight: f.weight, dim: f.dimension, parentFactId: f.parentFactId }))).toEqual([
        { code: '', weight: 50, dim: '商务', parentFactId: '' },
        { code: '1', weight: 30, dim: '商务', parentFactId: 'p1' },
        { code: '2', weight: 20, dim: '商务', parentFactId: 'p1' },
        { code: '3', weight: 50, dim: '价格', parentFactId: '' },
      ])
    })

    it('场景4：独立商务汇总行50分，下属明细30分和20分，另有价格50分，采纳精准排除独立汇总父项，为3个末级项100分', () => {
      reviewFacts.value = [
        { factId: 'p_standalone', code: '一', dimension: '商务部分', weight: 50, sourceText: '商务部分（50分）', parentFactId: '' },
        { factId: 'f1', code: '1', dimension: '商务部分', weight: 30, sourceText: '财务状况', parentFactId: 'p_standalone' },
        { factId: 'f2', code: '2', dimension: '商务部分', weight: 20, sourceText: '履约能力', parentFactId: 'p_standalone' },
        { factId: 'f3', code: '3', dimension: '价格', weight: 50, sourceText: '投标报价', parentFactId: '' },
      ]
      const { candidateFacts, candidateTotalWeight } = useScoreCandidateAdoption({
        props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData,
      })

      expect(candidateFacts.value).toHaveLength(4)
      expect(candidateTotalWeight.value).toBe(100)
      expect(candidateFacts.value.map((f) => ({ code: f.code, weight: f.weight, dim: f.dimension, parentFactId: f.parentFactId }))).toEqual([
        { code: '一', weight: 50, dim: '商务部分', parentFactId: '' },
        { code: '1', weight: 30, dim: '商务部分', parentFactId: 'p_standalone' },
        { code: '2', weight: 20, dim: '商务部分', parentFactId: 'p_standalone' },
        { code: '3', weight: 50, dim: '价格', parentFactId: '' },
      ])
    })
  })
})
