// Input: props.projectId, emit callbacks
// Output: state and handlers for score parse drawer (real API driven, no mock fallbacks)
// Pos: src/composables/projectDetail/ - Presentation domain composable

import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { scoreParseApi } from '@/api/modules/scoreParse.js'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import { formatTime, pollTask, normalizeScoreItem } from './scoreParseTask.js'
import { parseTriggerSource, scoringBody, scoringSkipHint, mapScoreResults, hasMeaningfulResults, circuitHintFromMeta } from './scoreParseSpendGuard.js'

export function useScoreParseDrawer(props, emit) {
  const visible = ref(false)
  const loading = ref(false)
  const error = ref('')
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
    const res = scoreResults.value[i.code]
    const val = res?.actualScore ?? res?.score
    return sum + (typeof val === 'number' ? val : 0)
  }, 0))

  function openDetail(item, result, mode = 'est') {
    selectedItem.value = item
    selectedResult.value = result || scoreResults.value[item.code] || null
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
      if (data?.summary?.normalizationWarning) normalizationWarning.value = data.summary.normalizationWarning
      const meta = res?.data?.meta || {}
      if (meta.sourceFileName) sourceFileName.value = meta.sourceFileName
      if (meta.parseTime) parseTime.value = formatTime(meta.parseTime)
      if (meta.bidFileName) bidFileName.value = meta.bidFileName
      if (meta.scoreTime) scoreTime.value = formatTime(meta.scoreTime)
      if (meta.lastScoringHint) scoringHint.value = meta.lastScoringHint
      circuitHint.value = circuitHintFromMeta(meta)
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
      const done = await pollTask(() => scoreParseApi.getParseStatus(props.projectId), '解析')
      if (done?.completedAt) parseTime.value = formatTime(done.completedAt)
      await fetchAnalysisData({ stage: currentStage.value, autoScore: false, autoParse: false })
      if (!silent) {
        ElMessage.success('已重新解析评分标准')
      }
    } catch (e) {
      const msg = e?.response?.data?.msg || e?.message || '重新解析失败'
      if (silent) {
        error.value = msg
      } else {
        notifyErrorUnlessRateLimit(e, '重新解析失败')
      }
    } finally {
      scoringOverlayVisible.value = false
    }
  }

  async function reparse() {
    await startParse({ silent: false })
  }

  return {
    visible, loading, error, isSection1Expanded, currentStage, scored, scoringOverlayVisible,
    sourceFileName, parseTime, bidFileName, scoreTime, scoringHint, circuitHint, normalizationWarning,
    scoreItems, scoreResults, detailModalVisible, detailMode, selectedItem, selectedResult,
    totalWeight, objectiveWeight, subjectiveWeight, statsOkCount, statsDangerCount, statsNeutralCount,
    estTotalScore, actualTotalScore, openDetail, open, runScoring, reparse,
  }
}
