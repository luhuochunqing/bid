// Input: props.projectId, emit callbacks
// Output: state and handlers for score parse drawer (real API driven, no mock fallbacks)
// Pos: src/composables/projectDetail/ - Presentation domain composable

import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { scoreParseApi } from '@/api/modules/scoreParse.js'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import { getAiDocumentErrorPresentation } from '@/utils/aiDocumentError.js'
import { formatTime, pollTask, normalizeScoreItem, mapScoreResults, lookupScoreResult, getDecisionBadgeType, getDecisionBadgeText } from './scoreParseTask.js'
import { parseTriggerSource, scoringBody, scoringSkipHint, hasMeaningfulResults, circuitHintFromMeta } from './scoreParseSpendGuard.js'
import { useScoreCandidateAdoption } from './useScoreCandidateAdoption.js'

export function useScoreParseDrawer(props, emit) {
  const visible = ref(false)
  const loading = ref(false)
  const error = ref(''), errorPresentation = computed(() => getAiDocumentErrorPresentation(error.value, '评分标准解析失败，请重新解析'))
  const isSection1Expanded = ref(true)
  const currentStage = ref(2)
  const scored = ref(false)
  const scoringOverlayVisible = ref(false)
  const sourceFileName = ref('—')
  const parseTime = ref('—')
  const bidFileName = ref('—')
  const scoreTime = ref('—')
  const scoringHint = ref('')
  const circuitHint = ref('')
  const normalizationWarning = ref('')
  const decisionStatus = ref('')
  const reviewReason = ref('')
  const fallbackReason = ref('')
  const pipelineVersion = ref('')
  const artifactSha256 = ref('')
  const reviewSnapshot = ref(null)
  const reviewLoading = ref(false)
  const reviewSubmitting = ref(false)
  const reviewError = ref('')
  const reviewSubmitted = ref(false)
  const decisionBadgeType = computed(() => getDecisionBadgeType(decisionStatus.value, reviewSubmitted.value))
  const decisionBadgeText = computed(() => getDecisionBadgeText(decisionStatus.value, reviewSubmitted.value))

  const decisionReviewRequired = computed(() => decisionStatus.value === 'REVIEW_REQUIRED' || decisionStatus.value === 'STRUCTURE_INCOMPLETE')
  const reviewRequired = computed(() => decisionReviewRequired.value && !reviewSubmitted.value)
  const reviewFacts = computed(() => Array.isArray(reviewSnapshot.value?.facts) ? reviewSnapshot.value.facts : [])
  const reviewAudits = computed(() => Array.isArray(reviewSnapshot.value?.audits) ? reviewSnapshot.value.audits : [])

  const scoreItems = ref([])
  const scoreResults = ref({})
  const detailModalVisible = ref(false)
  const detailMode = ref('est')
  const selectedItem = ref(null)
  const selectedResult = ref(null)
  const totalWeight = computed(() => scoreItems.value.reduce((sum, item) => sum + (Number(item.weight) || 0), 0))
  const objectiveWeight = computed(() => scoreItems.value.filter((i) => i.scoreType === '客观项').reduce((sum, item) => sum + (Number(item.weight) || 0), 0))
  const subjectiveWeight = computed(() => scoreItems.value.filter((i) => i.scoreType === '主观项').reduce((sum, item) => sum + (Number(item.weight) || 0), 0))
  const statsOkCount = computed(() => scoreItems.value.filter((i) => i.status === 'ok').length)
  const statsDangerCount = computed(() => scoreItems.value.filter((i) => i.status === 'danger').length)
  const statsNeutralCount = computed(() => scoreItems.value.filter((i) => i.status === 'neutral').length)

  const estTotalScore = computed(() => scoreItems.value.reduce((sum, i) => sum + (i.scoreType === '客观项' && typeof i.estScore === 'number' ? i.estScore : 0), 0))
  const actualTotalScore = computed(() => scoreItems.value.reduce((sum, i) => {
    if (i.scoreType !== '客观项') return sum
    const res = lookupScoreResult(scoreResults.value, i)
    const val = res?.actualScore ?? res?.score
    return sum + (typeof val === 'number' ? val : 0)
  }, 0))

  function openDetail(item, result, mode = 'est') {
    selectedItem.value = item
    selectedResult.value = result || lookupScoreResult(scoreResults.value, item)
    detailMode.value = mode
    detailModalVisible.value = true
  }

  async function open(options = {}) {
    visible.value = true
    await fetchAnalysisData(options)
  }

  async function fetchAnalysisData(options = {}) {
    loading.value = true
    error.value = ''
    try {
      const res = await scoreParseApi.getItems(props.projectId)
      const data = res?.data
      const apiItems = data?.items

      if (data?.sourceFileName) sourceFileName.value = data.sourceFileName
      if (data?.parseTime) parseTime.value = formatTime(data.parseTime) || data.parseTime
      scoreItems.value = Array.isArray(apiItems) && apiItems.length > 0 ? apiItems.map(normalizeScoreItem) : []
      normalizationWarning.value = data?.summary?.normalizationWarning || ''
      const meta = res?.data?.meta || {}
      if (meta.sourceFileName) sourceFileName.value = meta.sourceFileName
      if (meta.parseTime) parseTime.value = formatTime(meta.parseTime)
      if (meta.bidFileName) bidFileName.value = meta.bidFileName
      if (meta.scoreTime) scoreTime.value = formatTime(meta.scoreTime)
      if (meta.lastScoringHint) scoringHint.value = meta.lastScoringHint
      decisionStatus.value = meta.decisionStatus || ''
      reviewSubmitted.value = false
      reviewReason.value = meta.reviewReason || ''
      fallbackReason.value = meta.fallbackReason || ''
      pipelineVersion.value = meta.pipelineVersion || ''
      artifactSha256.value = meta.artifactSha256 || ''
      circuitHint.value = circuitHintFromMeta(meta)
      if (meta.lastParseStatus && meta.lastParseStatus !== 'COMPLETED') {
        reviewSnapshot.value = null
        decisionStatus.value = meta.lastParseStatus === 'FAILED' ? 'PARSE_FAILED' : ''
      } else await loadReviewSnapshotIfNeeded()
      emit('parsed', { dangerCount: statsDangerCount.value, warnCount: statsNeutralCount.value })
      let hasResults = false
      try {
        const resultsData = (await scoreParseApi.getResults(props.projectId))?.data
        const results = resultsData?.results
        if (resultsData?.bidFileName && !meta.bidFileName) bidFileName.value = resultsData.bidFileName
        if (resultsData?.scoreTime && !meta.scoreTime) scoreTime.value = formatTime(resultsData.scoreTime) || resultsData.scoreTime
        if (Array.isArray(results) && results.length > 0) {
          scoreResults.value = mapScoreResults(results)
          hasResults = hasMeaningfulResults(results)
        }
      } catch { /* results 不存在时忽略 */ }
      const hasBidDoc = Boolean(meta.bidFileName || (bidFileName.value && bidFileName.value !== '—') || props.hasBidDocument)
      currentStage.value = options.stage !== undefined ? options.stage : ((hasResults || hasBidDoc) ? 2 : 1)
      scored.value = options.scored !== undefined ? options.scored : hasResults
      const lastParseStatus = meta.lastParseStatus ?? null
      const inFlight = lastParseStatus === 'PENDING' || lastParseStatus === 'PROCESSING'
      if (scoreItems.value.length === 0 && (lastParseStatus == null || inFlight) && options.autoParse !== false) {
        await startParse({ silent: true })
      } else if (lastParseStatus === 'FAILED') {
        error.value = meta.lastParseError || '评分标准解析失败，请重新解析'
      }
      if (currentStage.value === 2 && !hasResults && options.autoScore === true) {
        await runScoring({ auto: true })
      }
    } catch (e) {
      error.value = e?.response?.data?.msg || '评分标准解析加载失败'
    } finally {
      loading.value = false
    }
  }

  async function runScoring(options = {}) {
    const isAuto = typeof options === 'boolean' ? options : !!options?.auto
    const customRunner = typeof options === 'object' && typeof options.runner === 'function' ? options.runner : null
    if ((loading.value && !isAuto) || scoringOverlayVisible.value) return

    if (currentStage.value !== 2) {
      ElMessage.warning('请先进入阶段 2（上传投标文件后）')
      return
    }
    scoringOverlayVisible.value = true
    try {
      if (customRunner) {
        await customRunner()
        scored.value = true
        ElMessage.success(isAuto ? 'AI 自动打分完成' : 'AI 实际打分完成')
      } else {
        const body = scoringBody({
          source: isAuto ? 'AUTO' : 'MANUAL',
          scope: options.scope || 'ALL',
          itemIds: options.itemIds || [],
        })
        const triggered = await scoreParseApi.triggerScoring(props.projectId, body)
        const skipHint = scoringSkipHint(triggered?.data)
        if (skipHint) scoringHint.value = skipHint
        if (triggered?.data?.outcome === 'SKIPPED') {
          scored.value = true
          ElMessage.info(skipHint || '文件未变化')
          return
        }
        let done = null
        let pollError = null
        try {
          done = await pollTask(() => scoreParseApi.getScoringStatus(props.projectId), '打分')
          if (done?.completedAt) scoreTime.value = formatTime(done.completedAt)
        } catch (err) {
          pollError = err
        }

        try {
          const resultsData = (await scoreParseApi.getResults(props.projectId))?.data
          const results = resultsData?.results
          if (resultsData?.bidFileName) bidFileName.value = resultsData.bidFileName
          if (resultsData?.scoreTime) scoreTime.value = formatTime(resultsData.scoreTime) || resultsData.scoreTime
          if (Array.isArray(results) && results.length > 0) {
            scoreResults.value = mapScoreResults(results)
            if (hasMeaningfulResults(results)) scored.value = true
          }
        } catch { /* 降级忽略 */ }

        if (pollError) {
          notifyErrorUnlessRateLimit(pollError, '打分失败')
        } else {
          scored.value = true
          ElMessage.success(isAuto ? 'AI 自动打分完成' : 'AI 实际打分完成')
        }
      }
    } catch (e) {
      notifyErrorUnlessRateLimit(e, '打分失败')
    } finally {
      scoringOverlayVisible.value = false
    }
  }

  async function startParse(options = {}) {
    const silent = !!options.silent
    try {
      scoringOverlayVisible.value = true
      const started = await scoreParseApi.triggerParse(props.projectId, { source: parseTriggerSource(silent) })
      if (!started?.data?.taskId || started?.data?.status === 'SKIPPED') {
        await fetchAnalysisData({ stage: currentStage.value, autoScore: false, autoParse: false })
        return
      }
      reviewSnapshot.value = null
      decisionStatus.value = ''
      const done = await pollTask(() => scoreParseApi.getParseStatus(props.projectId), '解析')
      if (done?.completedAt) parseTime.value = formatTime(done.completedAt)
      await fetchAnalysisData({ stage: currentStage.value, autoScore: false, autoParse: false })
      if (!silent) {
        ElMessage.success('已重新解析评分标准')
      }
    } catch (e) {
      const msg = e?.response?.data?.msg || e?.message || '重新解析失败'
      reviewSnapshot.value = null
      decisionStatus.value = 'PARSE_FAILED'
      if (silent) error.value = msg
      else notifyErrorUnlessRateLimit(e, '重新解析失败')
    } finally {
      scoringOverlayVisible.value = false
    }
  }
  async function reparse() {
    if (loading.value || scoringOverlayVisible.value) return
    await startParse({ silent: false })
  }

  async function loadReviewSnapshotIfNeeded() {
    reviewError.value = ''
    if (!decisionReviewRequired.value) {
      reviewSnapshot.value = null
      reviewSubmitted.value = decisionStatus.value === 'MANUAL_APPROVED'
      return
    }
    reviewLoading.value = true
    try {
      reviewSnapshot.value = (await scoreParseApi.getReview(props.projectId))?.data || null
      // 已提交过保留动作或采纳动作则恢复复核完成态，防止刷新/重开抽屉后重复提交（2026-09-18）
      reviewSubmitted.value = (reviewSnapshot.value?.audits ?? []).some(
        (a) => a.action === 'RETAIN_ORIGINAL' || a.action === 'ADOPT_CANDIDATES'
      )
    } catch (e) {
      reviewError.value = e?.response?.data?.msg || '复核证据加载失败'
    } finally {
      reviewLoading.value = false
    }
  }

  async function submitReviewRetention() {
    if (!reviewRequired.value || reviewSubmitting.value || reviewSubmitted.value) return
    reviewSubmitting.value = true
    try {
      reviewSnapshot.value = (await scoreParseApi.submitReview(props.projectId, {
        reason: '人工确认本次待复核结果暂不覆盖正式评分项，保留现有评分标准',
        actions: [{ action: 'RETAIN_ORIGINAL', reason: '保留现有正式评分项，待后续重新解析或人工调整' }],
      }))?.data || reviewSnapshot.value
      reviewSubmitted.value = true
      ElMessage.success('复核记录已保存')
    } catch (e) {
      notifyErrorUnlessRateLimit(e, '复核记录保存失败')
    } finally {
      reviewSubmitting.value = false
    }
  }

  const {
    candidateFacts,
    candidateTotalWeight,
    declaredTotalWeight,
    isAdoptingCandidates,
    adoptCandidateFacts,
  } = useScoreCandidateAdoption({
    props,
    reviewFacts,
    reviewSnapshot,
    reviewSubmitted,
    currentStage,
    fetchAnalysisData,
  })

  return {
    visible, loading, error, errorPresentation, isSection1Expanded, currentStage, scored, scoringOverlayVisible,
    sourceFileName, parseTime, bidFileName, scoreTime, scoringHint, circuitHint, normalizationWarning,
    scoreItems, scoreResults, detailModalVisible, detailMode, selectedItem, selectedResult,
    totalWeight, objectiveWeight, subjectiveWeight, statsOkCount, statsDangerCount, statsNeutralCount,
    estTotalScore, actualTotalScore, openDetail, open, runScoring, reparse,
    decisionStatus, decisionBadgeType, decisionBadgeText, reviewReason, fallbackReason, pipelineVersion, artifactSha256,
    reviewRequired, reviewSnapshot, reviewFacts, reviewAudits, reviewLoading, reviewSubmitting, reviewSubmitted, reviewError,
    loadReviewSnapshotIfNeeded, submitReviewRetention,
    candidateFacts, candidateTotalWeight, declaredTotalWeight, isAdoptingCandidates, adoptCandidateFacts,
  }
}
