// Input: tasksApi/tendersApi/projectsApi/dashboardApi + 角色码判断 + 当前用户角色/ID ref
// Output: 工作台角色化待办 4 个独立数据源 ref + 加载函数（spec §4.4 API 调用表）
// Pos: src/views/Dashboard/ - Dashboard feature composables
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref } from 'vue'
import { tasksApi } from '@/api/modules/dashboard.js'
import { projectsApi, tendersApi } from '@/api'
import { dashboardApi } from '@/api'
import { isBidAdminOrLeadRole, isSalesRole } from '@/constants/roleCodes.js'

/**
 * 工作台角色化待办 composable（spec §4.4）：
 * - taskTodos：标书制作阶段任务（所有角色相同）→ GET /api/tasks/my?projectStage=DRAFTING
 * - tenderTodos：按角色过滤标讯 → admin_lead: PENDING_ASSIGNMENT+EVALUATED; sales: TRACKING; 其他不加载
 * - projectTodos：按角色过滤项目 → GET /api/projects/workbench-todos（后端按角色返回）
 * - resourceTodos：待审批申请 → GET /api/dashboard/resource-pending-approvals（所有角色相同）
 *
 * @param {object} opts
 * @param {import('vue').Ref<string>} opts.roleRef 当前用户角色码 ref
 * @param {import('vue').Ref<*>} opts.userIdRef 当前用户 ID ref
 * @returns {{taskTodos, tenderTodos, projectTodos, resourceTodos, loadAll, loadTaskTodos, loadTenderTodos, loadProjectTodos, loadResourceTodos}}
 */
export function useWorkbenchRoleTodos({ roleRef, userIdRef }) {
  const taskTodos = ref([])
  const tenderTodos = ref([])
  const projectTodos = ref([])
  const resourceTodos = ref([])

  async function loadTaskTodos() {
    const assigneeId = userIdRef?.value
    if (!assigneeId) { taskTodos.value = []; return }
    try {
      const result = await tasksApi.getMine(assigneeId, 'DRAFTING')
      taskTodos.value = Array.isArray(result?.data) ? result.data : []
    } catch {
      taskTodos.value = []
    }
  }

  async function loadTenderTodos() {
    const role = roleRef?.value
    let statusParam = null
    if (isBidAdminOrLeadRole(role)) {
      statusParam = 'PENDING_ASSIGNMENT,EVALUATED'
    } else if (isSalesRole(role)) {
      statusParam = 'TRACKING'
    } else {
      tenderTodos.value = []
      return
    }
    try {
      const response = await tendersApi.getList({ status: statusParam })
      const tenders = Array.isArray(response?.data) ? response.data : []
      tenderTodos.value = tenders.slice(0, 8).map((item) => ({
        id: item.id,
        title: item.title || '未命名标讯',
        registrationDeadline: item.registrationDeadline,
        projectId: item.projectId ?? null,
      }))
    } catch {
      tenderTodos.value = []
    }
  }

  async function loadProjectTodos() {
    try {
      const response = await projectsApi.getWorkbenchTodos()
      projectTodos.value = Array.isArray(response?.data) ? response.data : []
    } catch {
      projectTodos.value = []
    }
  }

  async function loadResourceTodos() {
    try {
      const response = await dashboardApi.getResourcePendingApprovals()
      resourceTodos.value = Array.isArray(response?.data) ? response.data : []
    } catch {
      resourceTodos.value = []
    }
  }

  async function loadAll() {
    await Promise.allSettled([
      loadTaskTodos(),
      loadTenderTodos(),
      loadProjectTodos(),
      loadResourceTodos(),
    ])
  }

  return {
    taskTodos,
    tenderTodos,
    projectTodos,
    resourceTodos,
    loadAll,
    loadTaskTodos,
    loadTenderTodos,
    loadProjectTodos,
    loadResourceTodos,
  }
}
