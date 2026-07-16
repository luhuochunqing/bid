// Input: priorityTodos/hotTenders/activeProjects/pendingApprovals/deadlineStats/visibleCalendarEvents/menuPermissions/currentUser
// Output: 工作台改造区块的状态与派生数据（greeting/permissions/welcomeStats/todoCategoryCards/deadlinePanels/calendarPermissions/handlers）
// Pos: src/views/Dashboard/ - Dashboard feature composables
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, ref } from 'vue'
import { buildTodoCategoryCards, buildDeadlinePanels, buildWelcomeStats } from '@/views/Dashboard/workbench-rebuild-core.js'
import { getTimeGreeting } from '@/views/Dashboard/workbench-utils.js'
import { navigateToProject } from '@/utils/projectNavigation.js'
import { hasAnyPermission } from '@/utils/permission'
import { hasQuickStartPermission } from '@/views/Dashboard/workbench-core.js'

export function useWorkbenchRebuild({
  priorityTodosRef,
  hotTendersRef,
  activeProjectsRef,
  pendingApprovalsRef,
  deadlineStatsRef,
  visibleCalendarEventsRef,
  menuPermissionsRef,
  currentUserRef,
  router,
  handleTenderClick,
  handleProjectClick,
  handleApprove,
}) {
  const greeting = computed(() => getTimeGreeting())
  const deadlinePeriod = ref('week')

  const permissions = computed(() => {
    const perms = menuPermissionsRef?.value
    return {
      WelcomeBanner: hasAnyPermission(perms, ['dashboard:view_welcome_banner']),
      MetricCards: hasAnyPermission(perms, ['dashboard:view_metric_cards']),
      WorkCalendar: hasAnyPermission(perms, ['dashboard:view_calendar']),
      TenderList: hasAnyPermission(perms, ['dashboard:view_tender_list']),
      TechnicalTaskList: hasAnyPermission(perms, ['dashboard:view_technical_task']),
      ReviewList: hasAnyPermission(perms, ['dashboard:view_review_list']),
      CustomerFollowUpList: hasAnyPermission(perms, ['dashboard:view_customer_followup']),
      ProjectList: hasAnyPermission(perms, ['dashboard:view_active_projects']),
      TeamTaskList: hasAnyPermission(perms, ['dashboard:view_team_task']),
      TeamPerformance: hasAnyPermission(perms, ['dashboard:view_team_performance']),
      ApprovalList: hasAnyPermission(perms, ['dashboard:view_approval_list']),
      ProcessTimeline: hasAnyPermission(perms, ['dashboard:view_process_timeline']),
      ActivityList: hasAnyPermission(perms, ['dashboard:view_activity_list']),
      PriorityTodos: hasAnyPermission(perms, ['dashboard:view_priority_todos']),
      WorkbenchQuickStart: hasQuickStartPermission(currentUserRef?.value),
      canViewProjectList: hasAnyPermission(perms, ['dashboard:view_project_list']),
      canViewGlobalProjects: hasAnyPermission(perms, ['dashboard:view_global_projects']),
      canCreateProject: hasAnyPermission(perms, ['project.create', 'project']),
    }
  })

  const welcomeStats = computed(() => buildWelcomeStats({
    pendingCount: priorityTodosRef?.value?.filter((t) => !t?.done).length || 0,
    myProjectCount: activeProjectsRef?.value?.length || 0,
    deadlineStats: deadlineStatsRef?.value,
  }))

  const todoCategoryCards = computed(() => buildTodoCategoryCards({
    priorityTodos: priorityTodosRef?.value || [],
    hotTenders: hotTendersRef?.value || [],
    activeProjects: activeProjectsRef?.value || [],
    pendingApprovals: pendingApprovalsRef?.value || [],
  }))

  const deadlinePanels = computed(() => buildDeadlinePanels(
    visibleCalendarEventsRef?.value || [],
    deadlinePeriod.value,
  ))

  function handleWelcomeStatClick(stat) {
    if (stat.label === '待办任务') router.push('/project?tab=todo')
    else if (stat.label === '待办项目') router.push('/project')
    else if (stat.label === '报名截止' || stat.label === '今日开标') deadlinePeriod.value = 'today'
  }

  function handleTodoCardClick({ cardKey, item }) {
    if (cardKey === 'task') router.push('/project?tab=todo')
    else if (cardKey === 'tender') handleTenderClick?.({ id: item.id })
    else if (cardKey === 'project') handleProjectClick?.({ id: item.id })
    else if (cardKey === 'resource') handleApprove?.({ id: item.id, title: item.name })
  }

  function handleDeadlineRowClick(row) {
    if (row.projectId) {
      navigateToProject(router, String(row.projectId))
    }
  }

  return {
    greeting,
    deadlinePeriod,
    permissions,
    welcomeStats,
    todoCategoryCards,
    deadlinePanels,
    handleWelcomeStatClick,
    handleTodoCardClick,
    handleDeadlineRowClick,
  }
}
