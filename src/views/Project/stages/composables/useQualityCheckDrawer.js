import { ref, reactive, computed, nextTick, onUnmounted, getCurrentInstance } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { complianceApi } from '@/api/modules/ai.js'
import { scoreParseApi } from '@/api/modules/scoreParse.js'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import { getAiDocumentErrorPresentation } from '@/utils/aiDocumentError.js'
import { normalizeScoreItem, mapScoreResults } from '@/composables/projectDetail/scoreParseTask.js'
import { sumObjectiveActualScore } from './qualityCheckDetail.js'

export function useQualityCheckDrawer(props, emit) {
  const visible = ref(false)
  const polling = ref(false)
  const progressPercent = ref(0)
  const currentStepText = ref('')
  const progressStatus = ref('')
  const report = ref(null)
  const activeStatusFilter = ref('ALL')
  const errorMessage = ref('')
  const errorPresentation = computed(() => getAiDocumentErrorPresentation(errorMessage.value, 'AI 标书质量核查失败，请重试'))
  // 前端等待窗口到顶后任务仍在后端执行（大文件核查），此态不得误报失败（spec 066 FR-008）
  const backgroundRunning = ref(false)
  // 客观项实际得分合计（来自 AI 评分标准解析阶段 2），null = 尚未打分，得分链接不展示
  const objectiveScore = ref(null)
  // 详情弹窗当前选中项
  const detailItem = ref(null)
  const detailVisible = ref(false)

  const collapsedCategories = reactive({
    BASIC_INFO: false, SUB_ITEM_COMPLIANCE: false, ATTACHMENT_CHECKLIST: false
  })

  let pollTimer = null
  let pollStartTime = 0
  const POLL_INTERVAL_MS = 2000
  // 30 分钟等待窗口：大文件核查 = 解析 3~20 分钟 + 招标要求提取 + LLM 语义裁决，
  // 到顶仅转入"后台执行中"提示态，不再误报失败（spec 066 FR-008）
  const MAX_POLL_TIMEOUT_MS = 1800000

  const categoryDefinitions = [
    { category: 'BASIC_INFO', title: '基本信息检查', label: '基本信息' },
    { category: 'SUB_ITEM_COMPLIANCE', title: '分项合规检查', label: '分项合规' },
    { category: 'ATTACHMENT_CHECKLIST', title: '附件清单检查', label: '附件清单' }
  ]

  // 分类定位 tab：恒显 3 类，计数取全量（不受状态筛选影响）
  const categoryTabs = computed(() => categoryDefinitions.map(def => ({
    category: def.category, label: def.label,
    count: (report.value?.items || []).filter(i => i.category === def.category).length
  })))

  const filteredCategories = computed(() => {
    if (!report.value?.items) return []
    const items = report.value.items

    return categoryDefinitions.map(def => {
      const allCatItems = items.filter(i => i.category === def.category)
      const catItems = activeStatusFilter.value === 'ALL'
        ? allCatItems
        : allCatItems.filter(i => i.status === activeStatusFilter.value)
      return {
        category: def.category, title: def.title, items: catItems,
        failCount: allCatItems.filter(i => i.status === 'FAIL').length,
        manualCount: allCatItems.filter(i => i.status === 'MANUAL_REVIEW').length,
        passCount: allCatItems.filter(i => i.status === 'PASS').length
      }
    }).filter(group => group.items.length > 0 || activeStatusFilter.value === 'ALL')
  })

  function toggleCategory(cat) {
    collapsedCategories[cat] = !collapsedCategories[cat]
  }

  // ===== 分类定位（tab 点击 → 展开目标分组并平滑滚动） =====
  const categoryAnchors = new Map()

  function registerCategoryAnchor(category, el) {
    if (el) categoryAnchors.set(category, el)
    else categoryAnchors.delete(category)
  }

  function scrollToCategory(category) {
    collapsedCategories[category] = false
    nextTick(() => {
      categoryAnchors.get(category)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }

  // ===== 详情弹窗 =====
  function openDetail(item) {
    detailItem.value = item
    detailVisible.value = true
  }

  function closeDetail() {
    detailVisible.value = false
  }

  async function open() {
    visible.value = true
    await loadLatestReport()
  }

  async function loadLatestReport() {
    try {
      const res = await complianceApi.getBidQualityReport(props.projectId)
      if (res?.data) {
        // 仅在拿到确定状态后才退出后台态：刷新等待期间保持提示，避免闪回"尚未执行"空白态
        backgroundRunning.value = false
        if (res.data.status && res.data.status !== 'COMPLETED' && res.data.status !== 'FAILED') {
          errorMessage.value = ''
          startPolling(res.data.taskId)
        } else if (res.data.status === 'COMPLETED') {
          errorMessage.value = ''
          report.value = res.data
          await loadObjectiveScore()
        } else if (res.data.status === 'FAILED') {
          report.value = null
          errorMessage.value = res.data.errorMessage || 'AI 标书质量核查失败，请重试'
        } else {
          errorMessage.value = ''
          report.value = res.data
          await loadObjectiveScore()
        }
      } else {
        backgroundRunning.value = false
        errorMessage.value = ''
        await triggerCheck(false)
      }
    } catch (e) {
      console.error('加载质量核查报告异常:', e)
    }
  }

  // 客观项实际得分合计：复用评分标准解析的 items + results，静默降级（失败/未打分 → 不展示得分链接）
  async function loadObjectiveScore() {
    objectiveScore.value = null
    try {
      const itemsRes = await scoreParseApi.getItems(props.projectId)
      const apiItems = itemsRes?.data?.items
      if (!Array.isArray(apiItems) || apiItems.length === 0) return
      const items = apiItems.map(normalizeScoreItem)

      const resultsRes = await scoreParseApi.getResults(props.projectId)
      const results = resultsRes?.data?.results
      if (!Array.isArray(results) || results.length === 0) return
      objectiveScore.value = sumObjectiveActualScore(items, mapScoreResults(results))
    } catch {
      // 未解析/未打分/接口异常均静默降级，得分链接不展示
    }
  }

  async function handleRecheckConfirm() {
    try {
      await ElMessageBox.confirm(
        '重新核查将跳过缓存，基于最新的标书与招标文件重新执行 25 项深度比对，是否继续？',
        '重新核查确认',
        { confirmButtonText: '确定核查', cancelButtonText: '取消', type: 'warning' }
      )
      await triggerCheck(true)
    } catch {
      // user cancelled
    }
  }

  function goToScoreParse() {
    visible.value = false
    closeDetail()
    emit('go-to-score-parse', { stage: 2, autoScore: true })
  }

  function markTriggerRejected(e) {
    polling.value = false
    backgroundRunning.value = false
    progressPercent.value = 0
    progressStatus.value = 'exception'
    const msg = e.response?.data?.msg || e.response?.data?.message
    errorMessage.value = msg || '无法执行核查，请先上传投标文件'
    ElMessage.error(errorMessage.value)
  }

  function isClientRejection(e) {
    const status = e?.response?.status
    return status >= 400 && status < 500 && status !== 429
  }

  async function triggerCheck(force = false) {
    stopPolling()
    backgroundRunning.value = false
    errorMessage.value = ''
    if (force) report.value = null
    polling.value = true
    progressPercent.value = 10
    currentStepText.value = '正在初始化核查任务...'
    progressStatus.value = ''

    try {
      const res = await complianceApi.triggerBidQualityCheck(props.projectId, force)
      const task = res?.data
      if (!task) {
        polling.value = false
        errorMessage.value = res?.message || '触发质量核查失败'
        ElMessage.error(errorMessage.value)
        return
      }

      if (task.status === 'COMPLETED') {
        progressPercent.value = 100
        polling.value = false
        await loadLatestReport()
        ElMessage.success('已加载最新核查结果')
        return
      }

      startPolling(task.taskId)
    } catch (e) {
      polling.value = false
      if (isClientRejection(e)) {
        markTriggerRejected(e)
        return
      }
      errorMessage.value = e?.message || '触发质量核查异常'
      notifyErrorUnlessRateLimit(e, '触发质量核查异常')
    }
  }

  function startPolling(taskId) {
    stopPolling()
    polling.value = true
    pollStartTime = Date.now()

    pollTimer = setInterval(async () => {
      if (Date.now() - pollStartTime > MAX_POLL_TIMEOUT_MS) {
        // 窗口到顶：任务仍在后端执行，转入后台执行态（可刷新查看），不得误报失败
        stopPolling()
        polling.value = false
        progressStatus.value = ''
        backgroundRunning.value = true
        return
      }

      try {
        const res = await complianceApi.getBidQualityProgress(taskId)
        const task = res?.data
        if (!task) return

        progressPercent.value = task.progress || 20
        currentStepText.value = task.currentStep || '正在处理中...'

        if (task.status === 'COMPLETED') {
          stopPolling()
          polling.value = false
          backgroundRunning.value = false
          progressPercent.value = 100
          errorMessage.value = ''
          await loadLatestReport()
          ElMessage.success('AI 标书质量核查已完成！')
        } else if (task.status === 'FAILED') {
          stopPolling()
          polling.value = false
          backgroundRunning.value = false
          progressStatus.value = 'exception'
          errorMessage.value = task.errorMessage || '核查任务执行失败'
          ElMessage.error(errorMessage.value)
        }
      } catch (e) {
        console.warn('轮询任务进度异常:', e)
      }
    }, POLL_INTERVAL_MS)
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  if (getCurrentInstance()) {
    onUnmounted(() => {
      stopPolling()
    })
  }

  return {
    visible, polling, errorMessage, errorPresentation, backgroundRunning, progressPercent, currentStepText, progressStatus,
    report, activeStatusFilter, collapsedCategories, categoryTabs, filteredCategories,
    objectiveScore, detailItem, detailVisible, toggleCategory, registerCategoryAnchor,
    scrollToCategory, openDetail, closeDetail, open, loadLatestReport,
    handleRecheckConfirm, goToScoreParse, triggerCheck
  }
}
