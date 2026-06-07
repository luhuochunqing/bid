const missingTenderBreakdownSourceMessage = '未找到可用于拆解任务的标书拆解结果'

export const normalizeProjectTaskList = (tasks = []) => tasks.map((task) => ({
  ...task,
  deliverables: Array.isArray(task.deliverables) ? task.deliverables : [],
  hasDeliverable: Boolean(task.hasDeliverable),
}))

export async function openScoreDraftDialogWhenTenderSourceMissing({
  error,
  projectsApi,
  projectId,
  state,
  message,
  resolveErrorMessage,
}) {
  if (!resolveErrorMessage(error, '').includes(missingTenderBreakdownSourceMessage)) {
    return false
  }
  if (typeof projectsApi.getScoreDrafts !== 'function') {
    return false
  }
  try {
    const result = await projectsApi.getScoreDrafts(projectId)
    const drafts = Array.isArray(result?.data) ? result.data : []
    if (drafts.length === 0) {
      return false
    }
    state.scoreDraftDialogVisible.value = true
    message.warning('已找到评分草稿，请在评分标准拆解中确认后生成正式任务')
    return true
  } catch {
    return false
  }
}
