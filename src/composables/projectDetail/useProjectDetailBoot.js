import { onMounted } from 'vue'
import { approvalApi, knowledgeApi } from '@/api'
import { buildProjectBaselineActivities } from './useProjectDetailActivities.js'
import { auditApi } from '@/api/modules/audit.js'

export function useProjectDetailBoot(context) {
  const { route, userStore, projectStore, barStore, state, workflow, expenseAggregation, loadProjectWorkflowData } = context

  const ensureProjectCollections = () => {
    const currentProject = projectStore.currentProject
    if (!currentProject) {
      return null
    }

    if (!Array.isArray(currentProject.tasks)) {
      currentProject.tasks = []
    }
    if (!Array.isArray(currentProject.documents)) {
      currentProject.documents = []
    }

    return currentProject
  }

  const loadApprovalHistory = async (projectId) => {
    try {
      const result = await approvalApi.getProjectApprovals(projectId)
      state.approvalHistory.value = Array.isArray(result?.data) ? result.data : []
    } catch (error) {
      console.error('加载审批历史失败:', error)
      state.approvalHistory.value = []
    }
  }

  const loadProjectDetailDependencies = async (projectId) => {
    const templatePromise = knowledgeApi.templates.getList()
      .then((templateResult) => {
        workflow.templates.value = templateResult?.success && Array.isArray(templateResult.data) ? templateResult.data : []
      })
      .catch((error) => {
        console.error('加载模板列表失败:', error)
        workflow.templates.value = []
      })

    const expensePromise = expenseAggregation.loadProjectExpenseAggregation(projectId)
      .catch((error) => {
        console.error('加载项目费用聚合失败:', error)
      })

    const workflowPromise = loadProjectWorkflowData(projectId)
      .catch((error) => {
        console.error('加载项目工作流数据失败:', error)
      })

    await Promise.all([templatePromise, expensePromise, workflowPromise])
  }

  // CO-324: 项目动态改读后端 audit_logs（按 projectId）；接口失败回退伪造基线
  const initializeProjectActivities = async () => {
    const currentProject = ensureProjectCollections()
    const baseline = buildProjectBaselineActivities(currentProject, userStore?.userName)
    try {
      const resp = await auditApi.getProjectActivityLogs(currentProject.id)
      const logs = resp?.data || resp || []
      const mapped = (Array.isArray(logs) ? logs : []).map((log, idx) => ({
        id: log.id != null ? String(log.id) : `${log.time}-${log.actionType}-${idx}`,
        user: log.operator || '系统',
        action: (log.detail && String(log.detail).trim()) || log.actionType || '操作',
        time: log.time || '',
      }))
      // CO-324: 接口数据无"创建了项目"时 prepend 基线，保证首条可见
      state.activities.value = (mapped.some(a => a.action === '创建了项目'))
        ? mapped : [...baseline, ...mapped]
    } catch (e) {
      console.warn('加载项目动态失败，回退基线:', e)
      state.activities.value = baseline
    }
  }

  onMounted(async () => {
    state.loading.value = true
    const projectId = route.params.id
    try {
      await projectStore.getProjectById(projectId)
      await projectStore.loadTaskStatuses()
      ensureProjectCollections()
      await initializeProjectActivities()
      state.loading.value = false

      void loadProjectDetailDependencies(projectId).then(() => {
        ensureProjectCollections()
      })

      void (async () => {
        try {
          await barStore.getSites()
          const currentProject = ensureProjectCollections()
          if (currentProject) {
            const matchedSite = barStore.sites.find((site) => site.region === currentProject.region || currentProject.customer?.includes(site.name?.substring(0, 4)))
            if (matchedSite) {
              const result = await barStore.checkSiteCapability(matchedSite.name)
              if (result?.found) state.assetCheckResult.value = result
            }
          }
        } catch (error) {
          console.error('加载 BAR 资产检查失败:', error)
        }
      })()

      void loadApprovalHistory(projectId)
    } finally {
      state.loading.value = false
    }
  })

  return { loadApprovalHistory }
}
