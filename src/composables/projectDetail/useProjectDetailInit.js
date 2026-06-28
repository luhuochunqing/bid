import { onMounted } from 'vue'
import { buildProjectCreatedActivity } from './useProjectDetailActivities.js'
import { taskBackendToCard } from '@/views/Project/project-utils.js'

export function useProjectDetailInit(context) {
  const { route, projectStore, knowledgeApi, barStore, approvalApi, projectsApi } = context

  const loadProjectWorkflowData = async (projectId) => {
    if (!context.project.value || !context.isApiProject.value) return
    // CO-361: 用 allSettled 替代 Promise.all，避免任一请求失败（如文档 403）拖垮另一个请求的渲染。
    // 典型场景：bid-projectLeader 非主负责人时 getDocuments 返回 403，原 Promise.all fail-fast 会让
    // 已成功的 getTasks 数据被一并丢弃，任务看板整页空白。改为独立处理，失败方给空数组兜底。
    const [taskResult, documentResult] = await Promise.allSettled([
      projectsApi.getTasks(projectId),
      projectsApi.getDocuments(projectId),
    ])
    const taskData = taskResult.status === 'fulfilled' ? taskResult.value : null
    const documentData = documentResult.status === 'fulfilled' ? documentResult.value : null
    // Regression for IJSVX7 问题二：必须经 taskBackendToCard 走 normalizeTaskStatusFromApi，
    // 把后端 mapStatus 输出的小写字符串（'todo' / 'done' / 'review'）
    // 规范到 TODO / COMPLETED / REVIEW 大写。
    // CO-361 三态模型收口后 IN_PROGRESS 已废弃，后端 mapStatus 不再输出 'doing'。
    // normalize 映射表已同步移除 doing→IN_PROGRESS；历史遗留 doing 值（如有）将走 fallback 原样返回。
    context.project.value.tasks = taskData?.success && Array.isArray(taskData.data)
      ? taskData.data
          .filter((task) => !task.title?.startsWith('【待立项】'))
          .map((task) => taskBackendToCard({ ...task, deliverables: task.deliverables || [] }))
      : []
    context.project.value.documents = documentData?.success && Array.isArray(documentData.data) ? documentData.data : []
  }

  const loadApprovalHistory = async (projectId) => {
    try {
      const result = await approvalApi.getProjectApprovals(projectId)
      context.approvalHistory.value = Array.isArray(result?.data) ? result.data : []
    } catch {
      context.approvalHistory.value = []
    }
  }

  onMounted(async () => {
    context.loading.value = true
    const projectId = route.params.id
    await projectStore.getProjectById(projectId)
    await projectStore.loadTaskStatuses()
    context.activities.value = buildProjectCreatedActivity(projectStore.currentProject)
    const templateResult = await knowledgeApi.templates.getList()
    context.templates.value = templateResult?.success && Array.isArray(templateResult.data) ? templateResult.data : []
    if (!projectStore.currentProject) projectStore.currentProject = null
    await context.loadProjectExpenseAggregation(projectId)
    await loadProjectWorkflowData(projectId)
    await barStore.getSites()
    const currentProject = projectStore.currentProject
    if (currentProject) {
      const matchedSite = barStore.sites.find((site) => site.region === currentProject.region || currentProject.customer?.includes(site.name?.substring(0, 4)))
      if (matchedSite) {
        const result = await barStore.checkSiteCapability(matchedSite.name)
        if (result.found) context.assetCheckResult.value = result
      }
    }
    await loadApprovalHistory(projectId)
    context.loading.value = false
  })

  return { loadProjectWorkflowData, loadApprovalHistory }
}
