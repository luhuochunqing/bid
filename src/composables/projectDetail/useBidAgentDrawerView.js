import { computed } from 'vue'
import { buildBidAgentEditorHref } from './useProjectDetailBidAgent.js'

const READY_STATUSES = ['DRAFTED', 'COMPLETED', 'READY', 'DONE', 'APPLIED', 'READY_FOR_WRITER']
const FAILED_STATUSES = ['FAILED', 'ERROR']

const STATUS_TEXT = {
  QUEUED: '排队中',
  PENDING: '待处理',
  RUNNING: '生成中',
  DRAFTED: '已生成',
  COMPLETED: '已完成',
  READY: '可写入',
  READY_FOR_WRITER: '已写入',
  APPLIED: '已写入',
  FAILED: '失败',
  ERROR: '失败',
}

const STATUS_TYPE = {
  COMPLETED: 'success',
  DRAFTED: 'success',
  READY: 'success',
  READY_FOR_WRITER: 'success',
  APPLIED: 'success',
  RUNNING: 'primary',
  QUEUED: 'info',
  PENDING: 'info',
  FAILED: 'danger',
  ERROR: 'danger',
}

const STAGE_TEXT = {
  QUEUED: '等待后端调度',
  PENDING: '等待处理',
  RUNNING: '正在生成',
  DRAFTED: '初稿已生成，可审查或写入',
  COMPLETED: '已完成',
  READY_FOR_WRITER: '已写入文档编辑器',
  FAILED: '处理失败',
}

export function useBidAgentDrawerView(detail, agent) {
  const run = computed(() => agent.currentRun.value)
  const status = computed(() => String(run.value?.status || '').toUpperCase())
  const draftSections = computed(() => run.value?.draft?.sections || [])
  const isReady = computed(() => READY_STATUSES.includes(status.value))

  const displayStages = computed(() => {
    if (run.value?.stages?.length) return run.value.stages
    return status.value ? [{ key: 'current', title: '当前状态', status: status.value }] : []
  })

  const warnings = computed(() => [
    ...formatWarnings(run.value?.risks),
    ...formatWarnings(run.value?.gaps),
    ...formatWarnings(run.value?.manualConfirmations),
  ])

  const applyResultText = computed(() => {
    const result = agent.applyResult.value
    if (!result) return ''
    if (result.msg) return result.msg
    if (result.documentName) return `已写入 ${result.documentName}`
    if (result.documentId) return `已写入文档 #${result.documentId}`
    if (result.structureId) return `已写入章节树 #${result.structureId}`
    return '后端已确认写入结果'
  })

  const editorHref = computed(() => buildBidAgentEditorHref({
    target: agent.applyResult.value || {},
    projectId: agent.projectId?.value ?? detail.project?.value?.id ?? '',
    runId: agent.currentRunId.value,
  }))

  const canApply = computed(() => Boolean(agent.currentRunId.value) && (isReady.value || draftSections.value.length > 0))
  const canReview = computed(() => Boolean(agent.currentRunId.value) && !FAILED_STATUSES.includes(status.value))
  const statusText = computed(() => STATUS_TEXT[status.value] || '未启动')
  const statusType = computed(() => STATUS_TYPE[status.value] || 'info')

  function openEditor(event) {
    event?.preventDefault?.()
    const before = window.location.href
    const navigation = agent.goToEditor(agent.applyResult.value)
    if (!navigation || typeof navigation.finally !== 'function') return

    Promise.resolve(navigation).finally(() => {
      window.setTimeout(() => {
        if (window.location.href === before && editorHref.value) {
          window.location.assign(editorHref.value)
        }
      }, 100)
    })
  }

  return {
    run,
    draftSections,
    displayStages,
    warnings,
    applyResultText,
    editorHref,
    canApply,
    canReview,
    statusText,
    statusType,
    openEditor,
    getStageText,
    stageClass,
  }
}

function formatWarnings(items = []) {
  return items.map((item) => {
    if (typeof item === 'string') return item
    return item.title || item.message || item.description || item.name || ''
  }).filter(Boolean)
}

function getStageText(stageStatus = '') {
  return STAGE_TEXT[String(stageStatus).toUpperCase()] || '等待后端状态'
}

function stageClass(stageStatus = '') {
  return `stage-${String(stageStatus).toLowerCase() || 'pending'}`
}
