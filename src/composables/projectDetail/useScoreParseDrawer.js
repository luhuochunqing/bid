// Input: props.projectId, emit callbacks
// Output: coherent extraction/active state, independent knowledge estimates, explicit CAS selection and historical scoring
// Pos: src/composables/projectDetail/ - Presentation domain composable
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { scoreParseApi } from '@/api/modules/scoreParse.js'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import { getAiDocumentErrorPresentation } from '@/utils/aiDocumentError.js'
import { formatTime, pollTask, mapScoreResults, lookupScoreResult, getDecisionBadgeType, getDecisionBadgeText, shouldHideFailureBadge } from './scoreParseTask.js'
import { parseTriggerSource, scoringBody, scoringSkipHint, hasMeaningfulResults, circuitHintFromMeta } from './scoreParseSpendGuard.js'
import { normalizeEstimatedItem, useScoreEstimateRefresh } from './useScoreEstimateRefresh.js'
import { useScoreExtraction } from './useScoreExtraction.js'
import { useScoreCandidateAdoption } from './useScoreCandidateAdoption.js'
import { sourceDiagnosticsFromSummary } from './scoreExtractionPresentation.js'

export function useScoreParseDrawer(props, emit) {
  const visible = ref(false)
  const loading = ref(false)
  const error = ref(''), errorPresentation = computed(() => getAiDocumentErrorPresentation(error.value, '评分标准解析失败，请重新解析'))
  const isSection1Expanded = ref(true)
  const currentStage = ref(2)
  const scored = ref(false)
  const scoringOverlayVisible = ref(false)
  const sourceFileName = ref('—'), parseTime = ref('—'), bidFileName = ref('—'), scoreTime = ref('—')
  const scoringHint = ref(''), circuitHint = ref(''), normalizationWarning = ref('')
  const decisionStatus = ref(''), reviewReason = ref(''), fallbackReason = ref(''), pipelineVersion = ref(''), artifactSha256 = ref('')
  const sourceDiagnostics = ref([])
  const reviewSnapshot = ref(null), reviewLoading = ref(false), reviewSubmitting = ref(false), reviewError = ref(''), reviewSubmitted = ref(false)
  const decisionBadgeType = computed(() => getDecisionBadgeType(decisionStatus.value, reviewSubmitted.value))
  const decisionBadgeText = computed(() => getDecisionBadgeText(decisionStatus.value, reviewSubmitted.value))

  const decisionReviewRequired = computed(() => decisionStatus.value === 'REVIEW_REQUIRED' || decisionStatus.value === 'STRUCTURE_INCOMPLETE')
  const reviewRequired = computed(() => decisionReviewRequired.value && !reviewSubmitted.value)
  const reviewFacts = computed(() => Array.isArray(reviewSnapshot.value?.facts) ? reviewSnapshot.value.facts : [])
  const reviewAudits = computed(() => Array.isArray(reviewSnapshot.value?.audits) ? reviewSnapshot.value.audits : [])

  const extraction = useScoreExtraction(props, fetchAnalysisData)
  const hasHistoricalResults = ref(false), hasScoreResults = ref(false)
  const scoreItems = ref([]), scoreResults = ref({}), detailModalVisible = ref(false), detailMode = ref('est')
  const estimates = useScoreEstimateRefresh(props, scoreItems, loading, scoringOverlayVisible)
  // 本次解析失败但仍有历史评分项在生效时不展示失败徽章，避免"评分项正确 + 结构异常"并存误导用户
  const decisionBadgeVisible = computed(() => Boolean(decisionStatus.value) && !extraction.latestExtraction.value
    && !shouldHideFailureBadge(decisionStatus.value, scoreItems.value.length))
  const activeParseTime = computed(() => extraction.latestExtraction.value?.active?.taskId && scoreItems.value.length && parseTime.value !== '—' ? parseTime.value : '未知')
  const selectedItem = ref(null), selectedResult = ref(null)
  const totalWeight = computed(() => scoreItems.value.length && scoreItems.value.every(i => i.weight != null) ? scoreItems.value.reduce((sum, item) => sum + (Number(item.weight) || 0), 0) : null)
  const isTotalWeight100 = computed(() => totalWeight.value != null && Math.round(totalWeight.value) === 100)
  const objectiveWeight = computed(() => {
    const raw = scoreItems.value.filter((i) => i.scoreType === '客观项').reduce((sum, item) => sum + (Number(item.weight) || 0), 0)
    const rounded = Math.round(raw)
    return isTotalWeight100.value ? Math.max(0, Math.min(100, rounded)) : rounded
  })
  const subjectiveWeight = computed(() => {
    if (isTotalWeight100.value) return Math.max(0, 100 - objectiveWeight.value)
    const raw = scoreItems.value.filter((i) => i.scoreType === '主观项').reduce((sum, item) => sum + (Number(item.weight) || 0), 0)
    return Math.round(raw)
  })
  const statsOkCount = computed(() => scoreItems.value.filter((i) => i.status === 'ok').length)
  const statsDangerCount = computed(() => scoreItems.value.filter((i) => i.status === 'danger').length)
  const statsNeutralCount = computed(() => scoreItems.value.filter((i) => i.status === 'neutral').length)

  const estTotalScore = computed(() => {
    const known = scoreItems.value.filter(i => i.scoreType === '客观项' && typeof i.estScore === 'number')
    return known.length ? known.reduce((sum, i) => sum + i.estScore, 0) : null
  })
  const actualTotalScore = computed(() => {
    const known = scoreItems.value.filter(i => i.scoreType === '客观项').map(i => lookupScoreResult(scoreResults.value, i))
      .filter(r => r?.reuseKind !== 'LEGACY_UNVERIFIED' && typeof r?.actualScore === 'number')
    return known.length ? known.reduce((sum, r) => sum + r.actualScore, 0) : null
  })

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
      const { itemsResponse: res, resultsData, readError } = await extraction.readBundle()
      if (readError) error.value = readError
      const data = res?.data
      const apiItems = data?.items

      sourceFileName.value = data?.sourceFileName || '—'
      parseTime.value = formatTime(data?.parseTime) || '—'
      scoreItems.value = Array.isArray(apiItems) && apiItems.length > 0 ? apiItems.map(normalizeEstimatedItem) : []
      normalizationWarning.value = data?.summary?.normalizationWarning || ''
      const meta = res?.data?.meta || {}
      sourceDiagnostics.value = sourceDiagnosticsFromSummary(meta.runSummary)
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
      } else if (!extraction.latestExtraction.value || extraction.latestExtraction.value.payloadVersion === 0) await loadReviewSnapshotIfNeeded()
      emit('parsed', { dangerCount: statsDangerCount.value, warnCount: statsNeutralCount.value })
      const results = resultsData?.results || []
      scoreResults.value = mapScoreResults(results)
      hasHistoricalResults.value = results.some(r => r.reuseKind === 'LEGACY_UNVERIFIED')
      hasScoreResults.value = hasMeaningfulResults(results)
      const hasResults = results.some(r => r.reuseKind !== 'LEGACY_UNVERIFIED' && r.status !== 'PENDING' && hasMeaningfulResults([r]))
      if (resultsData?.bidFileName && !meta.bidFileName) bidFileName.value = resultsData.bidFileName
      if (resultsData?.scoreTime && !meta.scoreTime) scoreTime.value = formatTime(resultsData.scoreTime)
      const hasBidDoc = Boolean(meta.bidFileName || (bidFileName.value && bidFileName.value !== '—') || props.hasBidDocument)
      currentStage.value = options.stage !== undefined ? options.stage : ((hasScoreResults.value || hasBidDoc) ? 2 : 1)
      scored.value = hasHistoricalResults.value && !hasResults ? false : options.scored !== undefined ? options.scored : hasResults
      const lastParseStatus = meta.lastParseStatus ?? null
      const inFlight = lastParseStatus === 'PENDING' || lastParseStatus === 'PROCESSING'
      if (!readError && !extraction.latestExtraction.value && scoreItems.value.length === 0 && (lastParseStatus == null || inFlight) && options.autoParse !== false) {
        await startParse({ silent: true })
      } else if (!readError && lastParseStatus === 'FAILED') {
        error.value = meta.lastParseError || '评分标准解析失败，请重新解析'
      }
      if (!readError && currentStage.value === 2 && !hasScoreResults.value && options.autoScore === true) {
        await runScoring({ auto: true })
      }
    } catch (e) {
      error.value = e?.response?.data?.msg || e?.message || '评分标准解析加载失败'
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
          await fetchAnalysisData({ stage: currentStage.value, autoScore: false, autoParse: false })
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

        await fetchAnalysisData({ stage: currentStage.value, autoScore: false, autoParse: false })

        if (pollError) {
          notifyErrorUnlessRateLimit(pollError, '打分失败')
        } else if (!error.value) {
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
      await pollTask(() => scoreParseApi.getParseStatus(props.projectId), '解析')
      await fetchAnalysisData({ stage: currentStage.value, autoScore: false, autoParse: false })
      if (!silent) {
        ElMessage.success('已重新解析评分标准')
      }
    } catch (e) {
      const msg = e?.response?.data?.msg || e?.message || '重新解析失败'
      await fetchAnalysisData({ stage: currentStage.value, autoScore: false, autoParse: false })
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
    schemes, selectedSchemeId, schemeSelectionRequired,
    candidateTotalWeight,
    declaredTotalWeight,
    adoptionStatus,
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
    ...extraction, ...estimates, activeParseTime, hasHistoricalResults, hasScoreResults,
    visible, loading, error, errorPresentation, isSection1Expanded, currentStage, scored, scoringOverlayVisible,
    sourceFileName, parseTime, bidFileName, scoreTime, scoringHint, circuitHint, normalizationWarning,
    scoreItems, scoreResults, detailModalVisible, detailMode, selectedItem, selectedResult,
    totalWeight, objectiveWeight, subjectiveWeight, statsOkCount, statsDangerCount, statsNeutralCount,
    estTotalScore, actualTotalScore, openDetail, open, runScoring, reparse,
    decisionStatus, decisionBadgeType, decisionBadgeText, decisionBadgeVisible, reviewReason, fallbackReason, pipelineVersion, artifactSha256, sourceDiagnostics,
    reviewRequired, reviewSnapshot, reviewFacts, reviewAudits, reviewLoading, reviewSubmitting, reviewSubmitted, reviewError,
    loadReviewSnapshotIfNeeded, submitReviewRetention,
    candidateFacts, schemes, selectedSchemeId, schemeSelectionRequired, candidateTotalWeight, declaredTotalWeight, adoptionStatus, isAdoptingCandidates, adoptCandidateFacts,
  }
}
