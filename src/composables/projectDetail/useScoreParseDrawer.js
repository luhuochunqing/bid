/**
 * AI 评分标准解析抽屉业务逻辑 Composable
 * Pos: src/composables/projectDetail/useScoreParseDrawer.js
 */
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { bidAgentApi } from '@/api/modules/bidAgent.js'
import { projectsApi } from '@/api/modules/projects.js'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import { defaultScoreTemplate, defaultScoreResults } from './scoreParseDefaults.js'

export function useScoreParseDrawer(props, emit) {
  const visible = ref(false)
  const loading = ref(false)
  const error = ref('')
  const isSection1Expanded = ref(true)
  const currentStage = ref(2)
  const scored = ref(true)
  const scoringOverlayVisible = ref(false)

  const sourceFileName = ref('测试招标文件.pdf')
  const parseTime = ref('')
  const bidFileName = ref('xxx 投标文件_v3.pdf')
  const scoreTime = ref('')
  const importing = ref(false)

  const scoreItems = ref([])
  const scoreResults = ref({})

  const detailModalVisible = ref(false)
  const detailMode = ref('est')
  const selectedItem = ref(null)
  const selectedResult = ref(null)

  const totalWeight = computed(() => {
    return scoreItems.value.reduce((acc, item) => acc + (Number(item.weight) || 0), 0) || 100
  })

  const objectiveWeight = computed(() => {
    return scoreItems.value
      .filter((s) => (s.scoreType || '').includes('客观') || (scoreResults.value[s.code]?.scoreType === 'objective'))
      .reduce((acc, item) => acc + (Number(item.weight) || 0), 0)
  })

  const subjectiveWeight = computed(() => totalWeight.value - objectiveWeight.value)

  const statsOkCount = computed(() => scoreItems.value.filter((s) => s.status === 'ok').length)
  const statsDangerCount = computed(() => scoreItems.value.filter((s) => s.status === 'danger').length)
  const statsNeutralCount = computed(() => scoreItems.value.filter((s) => s.status === 'neutral' || s.status === 'warn').length)

  const estTotalScore = computed(() => {
    return scoreItems.value.reduce((acc, s) => {
      return typeof s.estScore === 'number' ? acc + s.estScore : acc
    }, 0)
  })

  const actualTotalScore = computed(() => {
    return Object.values(scoreResults.value).reduce((acc, r) => {
      return r?.scoreType === 'objective' && typeof r?.actualScore === 'number' ? acc + r.actualScore : acc
    }, 0)
  })

  function openDetail(item, result = null, mode = 'est') {
    selectedItem.value = item
    selectedResult.value = result || scoreResults.value[item.code] || null
    detailMode.value = mode
    detailModalVisible.value = true
  }

  async function open(options = {}) {
    visible.value = true
    currentStage.value = options.stage || 2
    if (options.file) {
      bidFileName.value = options.file
    }
    await fetchStage1Data(options)
  }

  async function fetchStage1Data(options = {}) {
    loading.value = true
    error.value = ''
    try {
      const [analysisRes, criteriaRes] = await Promise.allSettled([
        bidAgentApi.getFullAnalysis(props.projectId),
        bidAgentApi.getScoringCriteria(props.projectId),
      ])

      const analysisData = analysisRes.status === 'fulfilled' ? analysisRes.value?.data : null
      const criteriaData = criteriaRes.status === 'fulfilled' ? criteriaRes.value?.data : null

      if (analysisData?.sourceFileName) {
        sourceFileName.value = analysisData.sourceFileName
      }
      if (analysisData?.bidFileName) {
        bidFileName.value = analysisData.bidFileName
      }
      parseTime.value = analysisData?.parseTime || new Date().toLocaleString('zh-CN', { hour12: false })
      scoreTime.value = analysisData?.scoreTime || new Date().toLocaleString('zh-CN', { hour12: false })

      const apiItems = criteriaData?.structuredItems || analysisData?.scoringCriteria?.items
      if (Array.isArray(apiItems) && apiItems.length > 0) {
        scoreItems.value = apiItems.map((s, i) => {
          const fallback = defaultScoreTemplate[i] || {}
          const reqText = s.req || s.indicator || s.detail || fallback.req || fallback.detail || ''
          return {
            code: s.itemNumber || s.code || fallback.code || `S${i + 1}`,
            dim: s.dimension || s.dim || fallback.dim || '评分项',
            req: reqText,
            detail: s.detail || reqText,
            weight: Number(s.weight ?? fallback.weight ?? 5),
            status: s.status || fallback.status || 'neutral',
            statusText: s.statusText || fallback.statusText || '待确认',
            scoreType: s.scoreType || fallback.scoreType || '客观项',
            estScore: s.estScore ?? fallback.estScore ?? '待评审',
            estBasis: s.estBasis || fallback.estBasis || '根据标书描述与资质匹配情况综合评审',
          }
        })
      } else {
        scoreItems.value = JSON.parse(JSON.stringify(defaultScoreTemplate))
      }

      scoreResults.value = JSON.parse(JSON.stringify(defaultScoreResults))

      emit('parsed', {
        dangerCount: statsDangerCount.value,
        warnCount: statsNeutralCount.value,
      })

      if (options.autoScore) {
        setTimeout(() => runScoring({ auto: true }), 400)
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
        await new Promise((resolve) => setTimeout(resolve, 1600))
      }
      scored.value = true
      scoreTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
      ElMessage.success(isAuto ? 'AI 自动打分完成' : 'AI 实际打分完成')
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
<style>body{font-family:sans-serif;padding:24px;color:#333;}table{width:100%;border-collapse:collapse;margin-top:16px;}th,td{border:1px solid #ddd;padding:8px;font-size:12px;text-align:left;}th{background:#f5f7fa;}.num{text-align:right;}</style>
</head><body>
<h2>AI 评分标准解析与对标报告</h2>
<p>项目编号：${props.projectId} | 招标文件：${sourceFileName.value} | 投标文件：${bidFileName.value}</p>
<p>客观项得分合计：${actualTotalScore.value} 分 / ${objectiveWeight.value} 分 (总权重: ${totalWeight.value} 分)</p>
<table><thead><tr><th>编号</th><th>维度</th><th>细则</th><th>权重</th><th>类别</th><th>满足状态</th><th>实际得分</th></tr></thead>
<tbody>${scoreItems.value.map((s) => `<tr><td>${s.code}</td><td>${s.dim}</td><td>${s.req}</td><td class="num">${s.weight}</td><td>${s.scoreType}</td><td>${s.statusText}</td><td class="num">${scoreResults.value[s.code]?.actualScore ?? '待评审'}</td></tr>`).join('')}</tbody>
</table></body></html>`

    const printWin = window.open('', '_blank')
    if (printWin) {
      printWin.document.write(reportHtml)
      printWin.document.close()
      printWin.focus()
      setTimeout(() => printWin.print(), 250)
    } else {
      // 降级使用 Blob 本地下载
      if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
        const blob = new Blob([reportHtml], { type: 'text/html;charset=utf-8' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `AI评分标准解析报告_项目${props.projectId}.html`
        document.body.appendChild(a)
        a.click()
        document.body.removeChild(a)
        if (typeof URL.revokeObjectURL === 'function') {
          URL.revokeObjectURL(url)
        }
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
