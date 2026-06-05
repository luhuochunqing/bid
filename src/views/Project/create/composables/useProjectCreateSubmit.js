// Input: project create dependencies and reactive submit flags
// Output: real API submit handlers for manual task save and automatic task breakdown
// Pos: src/views/Project/create/composables/ - Project create imperative shell
// 一旦我被更新，务必更新所属文件夹文档。

import { ElMessage } from 'element-plus'
import { projectsApi } from '@/api'
import customerOpportunityApi from '@/api/modules/customerOpportunity'

export function useProjectCreateSubmit({
  projectStore,
  router,
  sourceInfo,
  submitting,
  decomposing,
  buildApiProjectPayload,
  buildTaskCreatePayloads,
  hasGlobalHttpErrorMessage,
}) {
  async function createProjectBase() {
    const createdProject = await projectStore.createProject(buildApiProjectPayload())
    if (createdProject?.id && sourceInfo.opportunityId) {
      await syncOpportunityConversion(createdProject.id)
    }
    return createdProject
  }

  async function syncOpportunityConversion(projectId) {
    try {
      await customerOpportunityApi.convertToProject(sourceInfo.opportunityId, projectId)
    } catch (convertError) {
      ElMessage.warning(
        convertError?.response?.data?.msg || '项目已创建，但商机转化状态回写失败'
      )
    }
  }

  async function persistManualTasks(projectId) {
    const taskPayloads = buildTaskCreatePayloads()
    if (!projectId || taskPayloads.length === 0) return 0
    const results = await Promise.all(taskPayloads.map((payload) => projectsApi.createTask(projectId, payload)))
    return results.filter((result) => result?.success !== false).length
  }

  async function handleSubmit() {
    if (submitting.value) return
    submitting.value = true
    try {
      const createdProject = await createProjectBase()
      if (!createdProject?.id) return router.push('/project')

      const taskCount = await persistManualTasks(createdProject.id)
      ElMessage.success(taskCount > 0 ? `项目创建成功，已同步 ${taskCount} 个任务` : '项目创建成功')
      router.push(`/project/${createdProject.id}`)
    } catch (error) {
      if (!hasGlobalHttpErrorMessage(error)) ElMessage.error(error?.message || '项目创建失败')
    } finally {
      submitting.value = false
    }
  }

  async function handleCreateAndDecompose() {
    if (decomposing.value) return
    decomposing.value = true
    try {
      const createdProject = await createProjectBase()
      if (!createdProject?.id) throw new Error('项目创建失败')

      const result = await projectsApi.decomposeTasks(createdProject.id)
      const tasks = Array.isArray(result?.data) ? result.data : result?.data?.tasks
      if (!result?.success || !Array.isArray(tasks)) throw new Error(result?.msg || '自动拆解任务失败')

      ElMessage.success(`项目已创建，并自动拆解生成 ${tasks.length} 个任务`)
      router.push(`/project/${createdProject.id}`)
    } catch (error) {
      ElMessage.warning(
        error?.response?.data?.msg
          || error?.message
          || '项目已创建，但自动拆解需要先解析真实招标文件'
      )
    } finally {
      decomposing.value = false
    }
  }

  return { handleSubmit, handleCreateAndDecompose }
}
