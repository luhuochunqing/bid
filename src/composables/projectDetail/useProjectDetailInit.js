import { onMounted } from 'vue'
import { buildProjectBaselineActivities } from './useProjectDetailActivities.js'
import { taskBackendToCard } from '@/views/Project/project-utils.js'

export function useProjectDetailInit(context) {
  const { route, projectStore, knowledgeApi, barStore, approvalApi, projectsApi } = context

  const loadProjectWorkflowData = async (projectId) => {
    if (!context.project.value || !context.isApiProject.value) return
    const [taskResult, documentResult] = await Promise.all([projectsApi.getTasks(projectId), projectsApi.getDocuments(projectId)])
    // Regression for IJSVX7 问题二：必须经 taskBackendToCard 走 normalizeTaskStatusFromApi，
    // 把后端 mapStatus 输出的小写字符串（'todo' / 'doing' / 'done' / 'review'）
    // 规范到 TODO / IN_PROGRESS / COMPLETED / REVIEW 大写。
    // 否则 TaskBoard.columns.filter((s) => s.code !== 'IN_PROGRESS') 会把小写 'doing'
    // upcase 后的 IN_PROGRESS 任务全部过滤掉，造成"任务消失"。
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
    context.activities.value = buildProjectBaselineActivities(projectStore.currentProject, context.userStore?.userName)
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
