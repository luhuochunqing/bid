// Input: props.projectId, emit callbacks
// Output: state and handlers for score parse drawer (real API driven, no mock fallbacks)
// Pos: src/composables/projectDetail/ - Presentation domain composable

import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { scoreParseApi } from '@/api/modules/scoreParse.js'
import { projectsApi } from '@/api/modules/projects.js'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import { STATUS_MAP, formatTime, pollTask, normalizeScoreItem, normalizeScoreResult } from './scoreParseTask.js'

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
  const importing = ref(false)

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

      if (Array.isArray(apiItems) && apiItems.length > 0) {
        scoreItems.value = apiItems.map(normalizeScoreItem)
      } else {
        scoreItems.value = []
      }

      // 来源信息栏元数据（R007/R022：无则保持空态占位）
      const meta = res?.data?.meta || {}
      if (meta.sourceFileName) sourceFileName.value = meta.sourceFileName
      if (meta.parseTime) parseTime.value = formatTime(meta.parseTime)
      if (meta.bidFileName) bidFileName.value = meta.bidFileName
      if (meta.scoreTime) scoreTime.value = formatTime(meta.scoreTime)

      emit('parsed', {
        dangerCount: statsDangerCount.value,
        warnCount: statsNeutralCount.value,
      })

      let hasResults = false
      try {
        const resultsRes = await scoreParseApi.getResults(props.projectId)
        const resultsData = resultsRes?.data
        const results = resultsData?.results
        if (resultsData?.bidFileName && !meta.bidFileName) bidFileName.value = resultsData.bidFileName
        if (resultsData?.scoreTime && !meta.scoreTime) scoreTime.value = formatTime(resultsData.scoreTime) || resultsData.scoreTime

        if (Array.isArray(results) && results.length > 0) {
          const resultMap = {}
          for (const r of results) {
            resultMap[r.code] = normalizeScoreResult(r)
          }
          scoreResults.value = resultMap
          hasResults = results.some((r) =>
            r.actualScore != null ||
            r.score != null ||
            (r.status && r.status !== 'neutral' && r.status !== 'PENDING' && r.status !== 'PENDING_EXPERT') ||
            Boolean(r.evidence) ||
            Boolean(r.quote) ||
            Boolean(r.suggestion)
          )
        }
      } catch {
        // results 不存在时正常忽略
      }

      // 阶段与打分状态判定（无投标文件必须为阶段1，有文件未打分为阶段2且scored=false）
      const hasBidDoc = Boolean(
        meta.bidFileName ||
        (bidFileName.value && bidFileName.value !== '—') ||
        props.hasBidDocument
      )
      if (options.stage !== undefined) {
        currentStage.value = options.stage
      } else {
        currentStage.value = (hasResults || hasBidDoc) ? 2 : 1
      }
      scored.value = options.scored !== undefined ? options.scored : hasResults

      // 仅当阶段 2 且从未打分且显式指定 autoScore 时才自动打分
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
      } else {
        // spec 041 真接口：POST /scoring（异步触发）→ 轮询 status → GET /results
        await scoreParseApi.triggerScoring(props.projectId)
        const done = await pollTask(() => scoreParseApi.getScoringStatus(props.projectId), '打分')
        if (done?.completedAt) scoreTime.value = formatTime(done.completedAt)

        const resultsRes = await scoreParseApi.getResults(props.projectId)
        const resultsData = resultsRes?.data
        const results = resultsData?.results

        // P1 修复：从打分结果响应读取投标文件元数据
        if (resultsData?.bidFileName) bidFileName.value = resultsData.bidFileName
        if (resultsData?.scoreTime) scoreTime.value = formatTime(resultsData.scoreTime) || resultsData.scoreTime

        if (Array.isArray(results)) {
          const resultMap = {}
          for (const r of results) {
            const isSubj = r.scoreType === 'SUBJECTIVE' || r.scoreType === '主观项'
            const actualVal = r.actualScore != null ? Number(r.actualScore) : null
            resultMap[r.code] = {
              actualScore: actualVal,
              score: actualVal,
              status: STATUS_MAP[r.status] || 'neutral',
              evalText: isSubj ? '待确认' : (actualVal != null ? `${actualVal} 分` : '—'),
              basis: r.evidence,
              quote: r.quote,
              missedReason: r.missedReason,
              suggestion: r.suggestion,
            }
          }
          scoreResults.value = resultMap
        }
      }
      scored.value = true
      ElMessage.success(isAuto ? 'AI 自动打分完成' : 'AI 实际打分完成')
    } catch (e) {
      notifyErrorUnlessRateLimit(e, '打分失败')
    } finally {
      scoringOverlayVisible.value = false
    }
  }

  async function reparse() {
    try {
      scoringOverlayVisible.value = true
      // spec 041 真接口：POST /parse（FR-021 覆盖旧解析结果）→ 轮询 → 重拉 items
      await scoreParseApi.triggerParse(props.projectId)
      const done = await pollTask(() => scoreParseApi.getParseStatus(props.projectId), '解析')
      if (done?.completedAt) parseTime.value = formatTime(done.completedAt)
      await fetchAnalysisData({ stage: currentStage.value, autoScore: false })
      ElMessage.success('已重新解析评分标准')
    } catch (e) {
      notifyErrorUnlessRateLimit(e, '重新解析失败')
    } finally {
      scoringOverlayVisible.value = false
    }
  }

  function exportReport() {
    const reportHtml = `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>AI 评分标准解析报告 - ${props.projectId}</title>
<style>body{font-family:sans-serif;padding:24px;color:var(--text-primary-ui);}table{width:100%;border-collapse:collapse;margin-top:16px;}th,td{border:1px solid var(--border-color);padding:8px;font-size:12px;text-align:left;}th{background:var(--fill-lightest);}.num{text-align:right;}</style>
</head><body>
<h2>AI 评分标准解析报告（项目 ID: ${props.projectId}）</h2>
<p>招标文件：${sourceFileName.value} | 解析时间：${parseTime.value}</p>
<p>投标文件：${bidFileName.value} | 评分时间：${scoreTime.value}</p>
<hr/>
<h3>评分项明细（共 ${scoreItems.value.length} 项，总权重 ${totalWeight.value} 分）</h3>
<table>
<thead><tr><th>编号</th><th>维度</th><th>评分要求</th><th>权重</th><th>满足预判</th><th>实际得分</th><th>引用说明</th></tr></thead>
<tbody>
${scoreItems.value
  .map((item) => {
    const res = scoreResults.value[item.code] || {}
    return `<tr><td>${item.code}</td><td>${item.dim}</td><td>${item.req}</td><td class="num">${item.weight}</td><td>${item.statusText}</td><td class="num">${res.evalText || item.estScore || '-'}</td><td>${res.quote || item.estBasis || '-'}</td></tr>`
  })
  .join('')}
</tbody>
</table>
</body></html>`

    try {
      const win = typeof window !== 'undefined' ? window.open('', '_blank') : null
      if (win && win.document) {
        win.document.write(reportHtml)
        win.document.close()
        win.print()
        ElMessage.success('已生成打印预览')
        return
      }
    } catch {
      // 弹窗被拦截，降级为 Blob 下载
    }

    if (typeof Blob !== 'undefined' && typeof document !== 'undefined') {
      const blob = new Blob([reportHtml], { type: 'text/html;charset=utf-8' })
      const url = typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function' ? URL.createObjectURL(blob) : null
      if (url) {
        const a = document.createElement('a')
        a.href = url
        a.download = `AI评分标准解析报告_${props.projectId}.html`
        a.click()
        URL.revokeObjectURL(url)
      }
      ElMessage.success('已导出报告文件')
    }
  }

  async function importToDrafts() {
    if (scoreItems.value.length === 0) {
      ElMessage.warning('暂无评分项可导入')
      return
    }
    try {
      await ElMessageBox.confirm(
        `确定将解析出的 ${scoreItems.value.length} 个评分项规则导入到项目评分草稿库中吗？`,
        '导入到评分草稿',
        { confirmButtonText: '确定导入', cancelButtonText: '取消', type: 'info' },
      )
      importing.value = true
      const res = await projectsApi.importScoreDraftsFromAnalysis(props.projectId)
      const count = res?.data?.importedCount ?? scoreItems.value.length
      ElMessage.success(`成功导入 ${count} 条评分项到草稿库`)
      emit('imported', { count })
    } catch (e) {
      if (e !== 'cancel') {
        notifyErrorUnlessRateLimit(e, '导入评分草稿失败')
      }
    } finally {
      importing.value = false
    }
  }

  return {
    visible, loading, error, isSection1Expanded, currentStage, scored, scoringOverlayVisible,
    sourceFileName, parseTime, bidFileName, scoreTime, importing, scoreItems, scoreResults,
    detailModalVisible, detailMode, selectedItem, selectedResult, totalWeight, objectiveWeight,
    subjectiveWeight, statsOkCount, statsDangerCount, statsNeutralCount, estTotalScore, actualTotalScore,
    openDetail, open, runScoring, reparse, exportReport, importToDrafts,
  }
}
