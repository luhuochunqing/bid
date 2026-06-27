import { onMounted } from 'vue'
import { buildProjectCreatedActivity } from './useProjectDetailActivities.js'
import { taskBackendToCard } from '@/views/Project/project-utils.js'

export function useProjectDetailInit(context) {
  const { route, projectStore, knowledgeApi, barStore, approvalApi, projectsApi } = context

  const loadProjectWorkflowData = async (projectId) => {
    if (!context.project.value || !context.isApiProject.value) return
    const [taskResult, documentResult] = await Promise.all([projectsApi.getTasks(projectId), projectsApi.getDocuments(projectId)])
    // Regression for IJSVX7 问题二：必须经 taskBackendToCard 走 normalizeTaskStatusFromApi，
    // 把后端 mapStatus 输出的小写字符串（'todo' / 'done' / 'review'）
    // 规范到 TODO / COMPLETED / REVIEW 大写。
    // CO-361 三态模型收口后 IN_PROGRESS 已废弃，小写 'doing' 不再产生，
    // 但保留 normalize 层以兼容历史小写返回值。
    context.project.value.tasks = taskResult?.success && Array.isArray(taskResult.data)
      ? taskResult.data
          .filter((task) => !task.title?.startsWith('【待立项】'))
          .map((task) => taskBackendToCard({ ...task, deliverables: task.deliverables || [] }))
      : []
    context.project.value.documents = documentResult?.success && Array.isArray(documentResult.data) ? documentResult.data : []
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
