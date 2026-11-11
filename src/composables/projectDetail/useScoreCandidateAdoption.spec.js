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
      { factId: 'f1', code: '1', dimension: '资质要求', weight: 10, sourceText: '具备ISO9001', candidateEligible: true },
      { factId: 'f2', code: '2', dimension: '技术方案', weight: 30, sourceText: '实施方案完整', candidateEligible: true },
      { factId: 'f3', code: '3', dimension: '占位模板', weight: 0, sourceText: 'XX~XX分', candidateEligible: true },
    ])
    reviewSnapshot = ref({ parseTaskId: 9, facts: reviewFacts.value, audits: [] })
    reviewSubmitted = ref(false)
    currentStage = ref(2)
    fetchAnalysisData = vi.fn().mockResolvedValue()
  })

  it('counts corrected detail facts once against the source maximum total', () => {
    reviewSnapshot.value = { schemes: [{ schemeId: 'scheme-summary', declaredTotal: 100,
      totalEvidenceStatus: 'VERIFIED', tableIds: ['qualification', 'score'] }] }
    reviewFacts.value = [
      { factId: 'a', code: '1', dimension: '实施方案', weight: 40, candidateEligible: true, schemeId: 'scheme-summary' },
      { factId: 'b', code: '2', dimension: '履约能力', weight: 60, candidateEligible: true, schemeId: 'scheme-summary' },
    ]
    const drawer = useScoreCandidateAdoption({
      props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData,
    })
    expect(drawer.candidateTotalWeight.value).toBe(100)
    expect(drawer.declaredTotalWeight.value).toBe(100)
    expect(drawer.adoptionStatus.value).toBe('DECLARED_TOTAL')
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
      { factId: 'p1', code: '', dimension: '商务部分', weight: 30, sourceText: '商务部分汇总', candidateEligible: true },
      { factId: 'c1', code: '1', dimension: '企业业绩', weight: 6, sourceText: '业绩细则', parentFactId: 'p1', candidateEligible: true },
      { factId: 'c2', code: '2', dimension: '财务状况', weight: 3, sourceText: '财务细则', parentFactId: 'p1', candidateEligible: true },
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

  it('互斥档位按父项最高分计一次，累计子项仍按叶子求和', () => {
    reviewFacts.value = [
      { factId: 'tier-parent', weight: 5, childAggregation: 'MUTUALLY_EXCLUSIVE', candidateEligible: true },
      { factId: 'tier-5', weight: 5, parentFactId: 'tier-parent', candidateEligible: true },
      { factId: 'tier-4', weight: 4, parentFactId: 'tier-parent', candidateEligible: true },
      { factId: 'tier-3', weight: 3, parentFactId: 'tier-parent', candidateEligible: true },
      { factId: 'tier-2', weight: 2, parentFactId: 'tier-parent', candidateEligible: true },
      { factId: 'sum-parent', weight: 20, childAggregation: 'SUM', candidateEligible: true },
      { factId: 'sum-5a', weight: 5, parentFactId: 'sum-parent', candidateEligible: true },
      { factId: 'sum-5b', weight: 5, parentFactId: 'sum-parent', candidateEligible: true },
      { factId: 'sum-10', weight: 10, parentFactId: 'sum-parent', candidateEligible: true },
    ]
    const { candidateTotalWeight } = useScoreCandidateAdoption({
      props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData,
    })

    expect(candidateTotalWeight.value).toBe(25)
  })

  it('人工核对未知父子关系时按明细计算候选总分', () => {
    reviewFacts.value = [
      { factId: 'unknown-parent', weight: 5, childAggregation: 'UNKNOWN', candidateEligible: true },
      { factId: 'unknown-child', weight: 5, parentFactId: 'unknown-parent', candidateEligible: true },
    ]
    const { candidateTotalWeight } = useScoreCandidateAdoption({
      props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData,
    })

    expect(candidateTotalWeight.value).toBe(5)
  })

  it('allows a scored source-unverified item into manual review', async () => {
    reviewFacts.value = [{ factId: 'u', code: '1', dimension: '待核对', weight: 5,
      candidateEligible: false, scopeStatus: 'SOURCE_UNVERIFIED', schemeId: null }]
    reviewSnapshot.value = { parseTaskId: 9, facts: reviewFacts.value, schemes: [] }
    scoreParseApi.submitReview.mockResolvedValue({ data: { facts: reviewFacts.value, audits: [] } })
    const { candidateTotalWeight, adoptCandidateFacts } = useScoreCandidateAdoption({ props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData })
    expect(candidateTotalWeight.value).toBe(5)
    await adoptCandidateFacts()
    expect(scoreParseApi.submitReview).toHaveBeenCalledWith(49, expect.objectContaining({ actions: [{ factId: 'u', action: 'ADOPT_CANDIDATES' }] }))
  })

  it('方案外事实保留审计展示，来源未验证事实可供人工采纳', async () => {
    reviewFacts.value = [
      { factId: 'in-scheme', code: '1', dimension: '评分办法内', weight: 100, candidateEligible: true, scopeStatus: 'IN_SCHEME' },
      { factId: 'out-of-scheme', code: '2', dimension: '履约考核', weight: 40, candidateEligible: false, scopeStatus: 'OUT_OF_SCHEME' },
      { factId: 'legacy', code: '3', dimension: '未验证来源', weight: 25 },
    ]
    reviewSnapshot.value = { parseTaskId: 9, facts: reviewFacts.value, audits: [] }
    scoreParseApi.submitReview.mockResolvedValue({ data: { facts: reviewFacts.value, audits: [] } })
    const { candidateFacts, candidateTotalWeight, adoptCandidateFacts } = useScoreCandidateAdoption({
      props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData,
    })

    expect(candidateFacts.value.map((fact) => fact.factId)).toEqual(['in-scheme', 'out-of-scheme', 'legacy'])
    expect(candidateTotalWeight.value).toBe(125)
    await adoptCandidateFacts()
    expect(scoreParseApi.submitReview).toHaveBeenCalledWith(49, {
      parseTaskId: 9,
      reason: '人工确认采纳候选评分标准',
      actions: [{ factId: 'in-scheme', action: 'ADOPT_CANDIDATES' }, { factId: 'legacy', action: 'ADOPT_CANDIDATES' }],
    })
  })

  it('keeps the server uncovered-row status when a scheme has a verified total', () => {
    reviewSnapshot.value = { adoptionStatus: 'SOURCE_ROWS_UNCOVERED', schemes: [
      { schemeId: 'a', totalEvidenceStatus: 'VERIFIED', declaredTotal: 100, tableIds: ['t'] },
    ] }
    const { adoptionStatus } = useScoreCandidateAdoption({ props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData })
    expect(adoptionStatus.value).toBe('SOURCE_ROWS_UNCOVERED')
  })

  it('多个方案必须明确选择，当前合计和采纳 payload 只用所选方案', async () => {
    reviewFacts.value = [
      { factId: 'a', schemeId: 'scheme-a', code: '1', dimension: '方案甲', weight: 100, candidateEligible: true },
      { factId: 'b', schemeId: 'scheme-b', code: '1', dimension: '方案乙', weight: 150, candidateEligible: true },
      { factId: 'audit', schemeId: 'scheme-a', code: '2', dimension: '履约考核', weight: 35,
        candidateEligible: false, scopeStatus: 'OUT_OF_SCHEME' },
    ]
    reviewSnapshot.value = {
      parseTaskId: 9,
      facts: reviewFacts.value,
      schemes: [
        { schemeId: 'scheme-a', title: '方案甲', declaredTotal: 100, totalEvidenceStatus: 'VERIFIED', tableIds: ['a1', 'a2'] },
        { schemeId: 'scheme-b', title: '方案乙', declaredTotal: 150, totalEvidenceStatus: 'VERIFIED', tableIds: ['b1'] },
      ],
      schemeSelectionRequired: true,
      selectedSchemeId: null,
      declaredTotal: null,
    }
    scoreParseApi.submitReview.mockResolvedValue({ data: { facts: reviewFacts.value, audits: [] } })
    const { selectedSchemeId, candidateTotalWeight, declaredTotalWeight, adoptionStatus, adoptCandidateFacts } = useScoreCandidateAdoption({
      props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData,
    })

    expect(selectedSchemeId.value).toBeNull()
    expect(candidateTotalWeight.value).toBe(0)
    expect(adoptionStatus.value).toBe('SCHEME_SELECTION_REQUIRED')
    selectedSchemeId.value = 'scheme-a'
    expect(candidateTotalWeight.value).toBe(100)
    expect(declaredTotalWeight.value).toBe(100)
    await adoptCandidateFacts()
    expect(scoreParseApi.submitReview).toHaveBeenCalledWith(49, expect.objectContaining({
      schemeId: 'scheme-a',
      actions: [{ factId: 'a', action: 'ADOPT_CANDIDATES' }],
    }))
  })

  it('does not add an unselected alternative to the displayed total', () => {
    reviewFacts.value = [
      { factId: 'selected', weight: 100, candidateEligible: true, schemeId: 'a' },
      { factId: 'alternative', weight: 20, candidateEligible: true, schemeId: 'a',
        evidences: [{ kind: 'ALTERNATIVE_EXTRACTION_SET_NOT_SELECTED' }] },
    ]
    reviewSnapshot.value = { parseTaskId: 9, selectedSchemeId: 'a', schemes: [{ schemeId: 'a' }] }
    const { candidateTotalWeight } = useScoreCandidateAdoption({ props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData })
    expect(candidateTotalWeight.value).toBe(100)
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
      parseTaskId: 9,
      reason: '人工确认采纳候选评分标准',
      actions: [
        { factId: 'f1', action: 'ADOPT_CANDIDATES' },
        { factId: 'f2', action: 'ADOPT_CANDIDATES' },
      ],
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
      factId: 'f1', code: '1', dimension: '资质要求', weight: 10, candidateEligible: true,
      sourceText: '具备ISO9001', parentFactId: '', scoreType: 'OBJECTIVE',
    }])

    expect(scoreParseApi.submitReview).toHaveBeenCalledWith(49, {
      parseTaskId: 9,
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

  it('刷新失败也保留原始采纳错误提示并清除旧快照', async () => {
    scoreParseApi.submitReview.mockRejectedValue(new Error('解析任务已变化'))
    fetchAnalysisData.mockRejectedValue(new Error('刷新失败'))
    const { adoptCandidateFacts } = useScoreCandidateAdoption({
      props, reviewFacts, reviewSnapshot, reviewSubmitted, currentStage, fetchAnalysisData,
    })

    await adoptCandidateFacts()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(reviewSnapshot.value).toBeNull()
  })

  describe('版式继承与业务父子关系分离（三场景一致性校验）', () => {
    it('场景1：普通未合并标签，3个末级项，合计100分', () => {
      reviewFacts.value = [
        { factId: 'f1', code: '1', dimension: '商务', weight: 30, sourceText: '财务状况', parentFactId: '', candidateEligible: true },
        { factId: 'f2', code: '2', dimension: '商务', weight: 20, sourceText: '履约能力', parentFactId: '', candidateEligible: true },
        { factId: 'f3', code: '3', dimension: '价格', weight: 50, sourceText: '投标报价', parentFactId: '', candidateEligible: true },
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
        { factId: 'f1', code: '1', dimension: '商务', weight: 30, sourceText: '财务状况', parentFactId: '', candidateEligible: true },
        { factId: 'f2', code: '2', dimension: '商务', weight: 20, sourceText: '履约能力', parentFactId: '', candidateEligible: true },
        { factId: 'f3', code: '3', dimension: '价格', weight: 50, sourceText: '投标报价', parentFactId: '', candidateEligible: true },
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
        { factId: 'p1', code: '', dimension: '商务', weight: 50, sourceText: '商务（50分）', parentFactId: '', candidateEligible: true },
        { factId: 'f1', code: '1', dimension: '商务', weight: 30, sourceText: '财务状况', parentFactId: 'p1', candidateEligible: true },
        { factId: 'f2', code: '2', dimension: '商务', weight: 20, sourceText: '履约能力', parentFactId: 'p1', candidateEligible: true },
        { factId: 'f3', code: '3', dimension: '价格', weight: 50, sourceText: '投标报价', parentFactId: '', candidateEligible: true },
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
        { factId: 'p_standalone', code: '一', dimension: '商务部分', weight: 50, sourceText: '商务部分（50分）', parentFactId: '', candidateEligible: true },
        { factId: 'f1', code: '1', dimension: '商务部分', weight: 30, sourceText: '财务状况', parentFactId: 'p_standalone', candidateEligible: true },
        { factId: 'f2', code: '2', dimension: '商务部分', weight: 20, sourceText: '履约能力', parentFactId: 'p_standalone', candidateEligible: true },
        { factId: 'f3', code: '3', dimension: '价格', weight: 50, sourceText: '投标报价', parentFactId: '', candidateEligible: true },
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
