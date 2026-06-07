// Input: workbench project, todo, approval, role, and user refs
// Output: derived list data for dashboard workbench widgets
// Pos: src/views/Dashboard/ - Workbench page state composition
// 一旦我被更新，务必更新所属的文件夹的 README。
import { computed } from 'vue'
import { extractCustomersFromProjects } from '@/views/Dashboard/workbench-utils.js'
import { filterProjectsByRole } from '@/views/Dashboard/workbench-core.js'

export function useWorkbenchDerivedLists({
  workbenchProjects,
  priorityTodos,
  pendingApprovals,
  currentUserRole,
  currentUserName,
}) {
  const activeProjects = computed(() => filterProjectsByRole(workbenchProjects.value, {
    role: currentUserRole.value,
    userName: currentUserName.value,
  }))

  const followUpCustomers = computed(() => extractCustomersFromProjects(workbenchProjects.value))

  const teamMembers = computed(() => {
    const byManager = new Map()
    for (const project of workbenchProjects.value) {
      if (!project?.manager) continue
      const existing = byManager.get(project.manager) || {
        id: project.manager,
        name: project.manager,
        tasks: [],
        workload: '0%',
        workloadLevel: 'low',
      }
      existing.tasks.push({
        id: `${project.id}-task`,
        title: project.name,
        priority: project.priority === 'high' ? 'high' : 'medium',
      })
      byManager.set(project.manager, existing)
    }

    return Array.from(byManager.values()).map((item) => {
      const taskCount = item.tasks.length
      const workload = Math.min(95, 20 + taskCount * 20)
      return {
        ...item,
        workload: `${workload}%`,
        workloadLevel: workload >= 80 ? 'high' : workload >= 50 ? 'medium' : 'low',
      }
    })
  })

  const myTechnicalTasks = computed(() => priorityTodos.value
    .filter((todo) => todo.sourceType === 'task')
    .slice(0, 6)
    .map((todo) => ({
      id: todo.id,
      title: todo.title,
      project: '项目任务',
      deadline: todo.deadline || '待定',
      priority: todo.priority === 'urgent' || todo.priority === 'high' ? 'high' : 'medium',
      done: todo.done,
    })))

  const pendingReviews = computed(() => pendingApprovals.value.slice(0, 6).map((item) => ({
    id: item.id,
    title: item.title,
    author: item.applicantName || '待确认',
    time: item.submitTime || item.time || '刚刚',
  })))

  const teamPerformance = computed(() => teamMembers.value.map((member) => {
    const projectCount = member.tasks.length
    const wins = member.tasks.filter((task) => task.priority === 'high').length
    const active = Math.max(projectCount - wins, 0)
    return {
      dept: member.name,
      size: Math.max(1, Math.min(12, projectCount * 2)),
      progress: Number.parseInt(member.workload, 10) || 0,
      color: '#3B82F6',
      wins,
      active,
    }
  }))

  return {
    activeProjects,
    followUpCustomers,
    teamMembers,
    myTechnicalTasks,
    pendingReviews,
    teamPerformance,
  }
}
