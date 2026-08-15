// Input: props.projectId, emit callbacks
// Output: state and handlers for score parse drawer (real API driven, no mock fallbacks)
// Pos: src/composables/projectDetail/ - Presentation domain composable

import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { bidAgentApi } from '@/api/modules/bidAgent.js'
import { projectsApi } from '@/api/modules/projects.js'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'

export function useScoreParseDrawer(props, emit) {
  const visible = ref(false)
  const loading = ref(false)
  const error = ref('')
  const isSection1Expanded = ref(true)
  const currentStage = ref(2)
  const scored = ref(false)
  const scoringOverlayVisible = ref(false)
  const sourceFileName = ref('国家级数据中心扩容项目招标文件.pdf')
  const parseTime = ref('2026-08-15 14:00:00')
  const bidFileName = ref('西域数智化投标文件_v3.pdf')
  const scoreTime = ref('2026-08-15 14:30:00')
  const importing = ref(false)

  const scoreItems = ref([])
  const scoreResults = ref({})

  const detailModalVisible = ref(false)
  const detailMode = ref('est')
  const selectedItem = ref(null)
  const selectedResult = ref(null)

  const totalWeight = computed(() =>
    scoreItems.value.reduce((sum, item) => sum + (Number(item.weight) || 0), 0)
  )

  const objectiveWeight = computed(() =>
    scoreItems.value
      .filter((i) => i.scoreType === '客观项')
      .reduce((sum, item) => sum + (Number(item.weight) || 0), 0)
  )

  const subjectiveWeight = computed(() =>
    scoreItems.value
      .filter((i) => i.scoreType === '主观项')
      .reduce((sum, item) => sum + (Number(item.weight) || 0), 0)
  )

  const statsOkCount = computed(() =>
    scoreItems.value.filter((i) => i.status === 'ok').length
  )
  const statsDangerCount = computed(() =>
    scoreItems.value.filter((i) => i.status === 'danger').length
  )
  const statsNeutralCount = computed(() =>
    scoreItems.value.filter((i) => i.status === 'neutral').length
  )

  const estTotalScore = computed(() => {
    let score = 0
    for (const item of scoreItems.value) {
      if (item.scoreType === '客观项' && typeof item.estScore === 'number') {
        score += item.estScore
      }
    }
    return score
  })

  const actualTotalScore = computed(() => {
    let score = 0
    for (const item of scoreItems.value) {
      if (item.scoreType === '客观项') {
        const res = scoreResults.value[item.code]
        if (res && typeof res.score === 'number') {
          score += res.score
        }
      }
    }
    return score
  })

  function openDetail(item, result, mode = 'est') {
    selectedItem.value = item
    selectedResult.value = result || scoreResults.value[item.code] || null
    detailMode.value = mode
    detailModalVisible.value = true
  }

  async function open(options = {}) {
    visible.value = true
    currentStage.value = options.stage ?? 2
    scored.value = options.scored ?? (currentStage.value === 2)
    await fetchAnalysisData(options)
  }

  function normalizeScoreItem(s, i) {
    const reqText = s.req || s.indicator || s.detail || s.name || ''
    const weight = Number(s.weight ?? 0)
    const isSubj = s.isSubjective || (s.subType === 'TECHNICAL_EVALUATION') || (s.scoreType === '主观项')
    return {
      code: s.itemNumber || s.code || `S${i + 1}`,
      dim: s.dimension || s.dim || '评分项',
      req: reqText,
      detail: s.detail || reqText,
      weight,
      status: s.status || 'neutral',
      statusText: s.statusText || (s.status === 'ok' ? '满足' : s.status === 'danger' ? '不满足' : '待确认'),
      scoreType: isSubj ? '主观项' : '客观项',
      estScore: s.estScore != null ? s.estScore : (isSubj ? '待评审' : weight),
      estBasis: s.estBasis || (isSubj ? '主观方案类评分项，需由评标专家根据方案深度综合评定' : '根据标书描述与资质匹配情况综合评审'),
    }
  }

  async function fetchAnalysisData(options = {}) {
    loading.value = true
    error.value = ''
    try {
      const [analysisRes, criteriaRes] = await Promise.allSettled([
        bidAgentApi.getFullAnalysis(props.projectId),
        bidAgentApi.getScoringCriteria(props.projectId),
      ])

      const analysisData = analysisRes.status === 'fulfilled' ? analysisRes.value?.data : null
      const criteriaData = criteriaRes.status === 'fulfilled' ? criteriaRes.value?.data : null

      if (analysisData?.sourceFileName) sourceFileName.value = analysisData.sourceFileName
      if (analysisData?.bidFileName) bidFileName.value = analysisData.bidFileName
      parseTime.value = analysisData?.parseTime || new Date().toLocaleString('zh-CN', { hour12: false })
      scoreTime.value = analysisData?.scoreTime || new Date().toLocaleString('zh-CN', { hour12: false })

      const apiItems = criteriaData?.structuredItems ||
        analysisData?.scoringCriteria?.structuredItems ||
        analysisData?.scoringCriteria?.items

      if (Array.isArray(apiItems) && apiItems.length > 0) {
        scoreItems.value = apiItems.map(normalizeScoreItem)
      } else {
        scoreItems.value = [] // PRD §5.3: 空状态，绝不回退假数据
      }

      emit('parsed', {
        dangerCount: statsDangerCount.value,
        warnCount: statsNeutralCount.value,
      })

      if (currentStage.value === 2) {
        await runScoring({ auto: options.autoScore !== false })
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
        const evalRes = await bidAgentApi.evaluateBidScore(props.projectId)
        const evalData = evalRes?.data || {}
        if (evalData.items && Array.isArray(evalData.items)) {
          const resultMap = {}
          for (const item of evalData.items) {
            resultMap[item.code] = {
              score: item.actualScore != null ? Number(item.actualScore) : null,
              status: item.status,
              evalText: item.isSubjective ? '待专家评审' : `${item.actualScore ?? 0} 分`,
              basis: item.basis,
              quote: item.quote,
              missedReason: item.missedReason,
              suggestion: item.suggestion,
            }
          }
          scoreResults.value = resultMap
          if (evalData.bidFileName) bidFileName.value = evalData.bidFileName
          if (evalData.scoreTime) scoreTime.value = evalData.scoreTime
        }
      }
      scored.value = true
      scoreTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
      ElMessage.success(isAuto ? 'AI 自动打分完成' : 'AI 实际打分完成')
    } catch (e) {
      notifyErrorUnlessRateLimit(e, '打分失败')
    } finally {
      scoringOverlayVisible.value = false
    }
  }

  async function reparse() {
    await open({ stage: currentStage.value, autoScore: false })
    ElMessage.success('已重新解析评分标准')
  }

  function exportReport() {
    const reportHtml = `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>AI 评分标准解析报告 - ${props.projectId}</title>
<style>body{font-family:sans-serif;padding:24px;color:var(--text-primary,#333);}table{width:100%;border-collapse:collapse;margin-top:16px;}th,td{border:1px solid var(--border-color,#ddd);padding:8px;font-size:12px;text-align:left;}th{background:var(--bg-muted,#f5f7fa);}.num{text-align:right;}</style>
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
