// Input: project detail route/router context and bid-agent API module
// Output: drawer state and lifecycle actions for project bid writing agent runs
// Pos: src/composables/projectDetail/ - Project Detail feature composables

import { computed, ref } from 'vue'
import { bidAgentApi as defaultBidAgentApi } from '@/api/modules/bidAgent.js'
import { useScoringCriteria } from './useScoringCriteria.js'
import { useFullAnalysis } from './useFullAnalysis.js'

function getResponseData(response) {
  return response?.data ?? null
}

function isFailedResponse(response) {
  return response?.success === false
}

function getMessage(error, fallback) {
  return error?.response?.data?.msg
    || error?.response?.data?.error
    || error?.message
    || fallback
}

function resolveRunId(run) {
  return run?.runId ?? run?.id ?? null
}

export function buildBidAgentEditorRoute({ target = {}, projectId = '', runId = '' } = {}) {
  const targetProjectId = target?.projectId ?? projectId
  const query = {}
  if (runId) query.bidAgentRunId = String(runId)
  if (target?.documentId) query.documentId = String(target.documentId)
  if (target?.structureId) query.structureId = String(target.structureId)
  if (target?.jobId) query.jobId = String(target.jobId)

  return {
    name: 'DocumentEditor',
    params: { id: String(targetProjectId) },
    query,
  }
}

export function buildBidAgentEditorHref({ target = {}, projectId = '', runId = '' } = {}) {
  const editorRoute = buildBidAgentEditorRoute({ target, projectId, runId })
  const queryText = new URLSearchParams(editorRoute.query).toString()
  return `/document/editor/${editorRoute.params.id}${queryText ? `?${queryText}` : ''}`
}

export function useProjectDetailBidAgent(context) {
  const { route, router, project, message, bidAgentApi = defaultBidAgentApi } = context

  const drawerVisible = ref(false)
  const showWorkbench = ref(false)
  const currentRun = ref(null)
  const applyResult = ref(null)
  const reviewResult = ref(null)
  const importResult = ref(null)
  const tenderFile = ref(null)
  const error = ref('')
  const importing = ref(false)
  const creating = ref(false)
  const fetching = ref(false)
  const applying = ref(false)
  const reviewing = ref(false)
  const qualificationMatch = ref(null)
  const qualificationMatchLoading = ref(false)
  const technicalRequirements = ref(null)
  const technicalRequirementsLoading = ref(false)
  const commercialRequirements = ref(null)
  const commercialRequirementsLoading = ref(false)
  const riskClassification = ref(null)
  const riskClassificationLoading = ref(false)

  const projectId = computed(() => project?.value?.id ?? route.params.id)
  const currentRunId = computed(() => resolveRunId(currentRun.value))
  const selectedTenderFileName = computed(() => tenderFile.value?.name || tenderFile.value?.fileName || '')
  const reportError = (err, fallback) => {
    const text = getMessage(err, fallback)
    error.value = text
    if (!err?.response) {
      message?.error?.(text)
    }
  }

  // Extract scoring criteria logic to separate composable to keep file under 300 lines
  const scoringCriteriaComposable = useScoringCriteria({
    projectId,
    bidAgentApi,
    reportError,
  })
  const { scoringCriteria, scoringCriteriaLoading, fetchScoringCriteria } = scoringCriteriaComposable

  // Extract full analysis + KB match to separate composable
  const fullAnalysisComposable = useFullAnalysis({
    projectId, bidAgentApi, reportError,
    panelRefs: { qualificationMatch, technicalRequirements, commercialRequirements, riskClassification, scoringCriteria },
  })
  const { knowledgeBaseMatch, knowledgeBaseMatchLoading, fullAnalysisLoading, riskSummary, fetchFullAnalysis, fetchKnowledgeBaseMatch } = fullAnalysisComposable

  const isBusy = computed(() =>
    importing.value || creating.value || fetching.value || applying.value
    || reviewing.value || qualificationMatchLoading.value
    || technicalRequirementsLoading.value || commercialRequirementsLoading.value
    || riskClassificationLoading.value || scoringCriteriaLoading.value || fullAnalysisLoading.value,
  )

  const openDrawer = () => {
    drawerVisible.value = true
  }

  const ensureRunId = () => {
    if (currentRunId.value) return currentRunId.value
    const text = '请先启动 AI 生成初稿任务'
    error.value = text
    message?.warning?.(text)
    return null
  }

  const selectTenderFile = (file) => {
    tenderFile.value = file?.raw || file || null
    return false
  }

  const clearTenderFile = () => { tenderFile.value = null }
  const importTenderDocument = async () => {
    if (!tenderFile.value) { error.value = '请先选择招标文件'; message?.warning?.(error.value); return null }
    importing.value = true; error.value = ''; applyResult.value = null; reviewResult.value = null; importResult.value = null; currentRun.value = null

    try {
      const formData = new FormData()
      formData.set('file', tenderFile.value, selectedTenderFileName.value || '招标文件')
      const response = await bidAgentApi.importTenderDocument(projectId.value, formData)
      if (isFailedResponse(response)) throw new Error(response.msg || '解析招标文件失败')
      const parsedResult = getResponseData(response)
      importResult.value = parsedResult
      
      // Instead of auto-running AI, we show the workbench for human verification
      showWorkbench.value = true
      
      return parsedResult
    } catch (err) {
      reportError(err, '解析招标文件失败')
      return null
    } finally {
      importing.value = false
    }
  }

  const createRun = async (payload = {}, options = {}) => {
    const { silentSuccess = false } = options
    drawerVisible.value = true
    creating.value = true
    error.value = ''
    applyResult.value = null
    reviewResult.value = null

    try {
      const response = await bidAgentApi.createRun(projectId.value, payload)
      if (isFailedResponse(response)) throw new Error(response.msg || '启动 AI 生成初稿失败')
      currentRun.value = getResponseData(response)
      if (!silentSuccess) {
        message?.success?.('AI 初稿生成任务已启动')
      }
      return currentRun.value
    } catch (err) {
      reportError(err, '启动 AI 生成初稿失败')
      return null
    } finally {
      creating.value = false
    }
  }

  const confirmWorkbench = async (_confirmedProfile) => {
    showWorkbench.value = false
    drawerVisible.value = true

    const snapshotId = importResult.value?.document?.snapshotId
    if (!snapshotId) return

    await createRun({ snapshotId })
  }

  const fetchQualificationMatch = async () => {
    qualificationMatchLoading.value = true; error.value = ''
    try {
      const response = await bidAgentApi.getQualificationMatch(projectId.value)
      if (isFailedResponse(response)) throw new Error(response.msg || '获取匹配结果失败')
      qualificationMatch.value = getResponseData(response)
    } catch (err) { reportError(err, '获取资质匹配结果失败')
    } finally { qualificationMatchLoading.value = false }
  }
  const fetchTechnicalRequirements = async () => {
    technicalRequirementsLoading.value = true; error.value = ''
    try {
      const response = await bidAgentApi.getTechnicalRequirements(projectId.value)
      if (isFailedResponse(response)) throw new Error(response.msg || '获取分类失败')
      technicalRequirements.value = getResponseData(response)
    } catch (err) { reportError(err, '获取技术要点分类失败')
    } finally { technicalRequirementsLoading.value = false }
  }
  const fetchCommercialRequirements = async () => {
    commercialRequirementsLoading.value = true; error.value = ''
    try {
      const response = await bidAgentApi.getCommercialRequirements(projectId.value)
      if (isFailedResponse(response)) throw new Error(response.msg || '获取分类失败')
      commercialRequirements.value = getResponseData(response)
    } catch (err) { reportError(err, '获取商务条款分类失败')
    } finally { commercialRequirementsLoading.value = false }
  }
  const fetchRiskClassification = async () => {
    riskClassificationLoading.value = true; error.value = ''
    try {
      const response = await bidAgentApi.getRiskClassification(projectId.value)
      if (isFailedResponse(response)) throw new Error(response.msg || '获取分类失败')
      riskClassification.value = getResponseData(response)
    } catch (err) { reportError(err, '获取风险分类失败')
    } finally { riskClassificationLoading.value = false }
  }



  const fetchRun = async (runId = currentRunId.value) => {
    if (!runId) return null
    fetching.value = true
    error.value = ''

    try {
      const response = await bidAgentApi.getRun(projectId.value, runId)
      if (isFailedResponse(response)) throw new Error(response.msg || '获取 AI 生成状态失败')
      currentRun.value = getResponseData(response)
      return currentRun.value
    } catch (err) {
      reportError(err, '获取 AI 生成状态失败')
      return null
    } finally {
      fetching.value = false
    }
  }

  const applyBidAgentResult = async (options = {}) => {
    const runId = ensureRunId()
    if (!runId) return null

    const { navigate = false, silentSuccess = false, ...payload } = options
    applying.value = true
    error.value = ''

    try {
      const response = await bidAgentApi.applyRun(projectId.value, runId, payload)
      if (isFailedResponse(response)) throw new Error(response.msg || '写入文档编辑器失败')
      applyResult.value = getResponseData(response) || response
      if (!silentSuccess) {
        message?.success?.('AI 初稿已写入文档编辑器')
      }
      if (navigate) goToEditor(applyResult.value)
      return applyResult.value
    } catch (err) { reportError(err, '写入文档编辑器失败'); return null
    } finally { applying.value = false }
  }
  const createReview = async (payload = {}) => {
    const runId = ensureRunId()
    if (!runId) return null

    reviewing.value = true
    error.value = ''

    try {
      const response = await bidAgentApi.createReview(projectId.value, { runId, ...payload })
      if (isFailedResponse(response)) throw new Error(response.msg || '发起 AI 初稿审查失败')
      reviewResult.value = getResponseData(response) || response
      message?.success?.('AI 初稿审查已发起')
      return reviewResult.value
    } catch (err) { reportError(err, '发起 AI 初稿审查失败'); return null
    } finally { reviewing.value = false }
  }
  function goToEditor(target = applyResult.value) {
    return router.push(buildBidAgentEditorRoute({ target, projectId: projectId.value, runId: currentRunId.value }))
  }

  return {
    drawerVisible, showWorkbench, currentRun, applyResult, reviewResult,
    importResult, tenderFile, error, importing, creating, fetching,
    applying, reviewing, qualificationMatch, qualificationMatchLoading,
    technicalRequirements, technicalRequirementsLoading, commercialRequirements,
    commercialRequirementsLoading, riskClassification, riskClassificationLoading,
    ...scoringCriteriaComposable, ...fullAnalysisComposable,
    projectId, currentRunId, selectedTenderFileName, isBusy,
    openDrawer, selectTenderFile, clearTenderFile, importTenderDocument,
    confirmWorkbench, fetchQualificationMatch, fetchTechnicalRequirements,
    fetchCommercialRequirements, fetchRiskClassification,
    createRun, fetchRun, applyBidAgentResult, createReview, goToEditor,
  }
}
