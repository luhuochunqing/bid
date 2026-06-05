// Input: dynamic widget components, state refs, and action callbacks
// Output: registry, props, and listeners for DynamicLayoutRenderer
// Pos: src/views/Dashboard/ - Workbench dynamic layout composition
// 一旦我被更新，务必更新所属的文件夹的 README。
import { computed } from 'vue'
import { getPriorityLabel, getPriorityType } from '@/views/Dashboard/workbench-core.js'
import ActivityList from '@/views/Dashboard/components/ActivityList.vue'
import ApprovalList from '@/views/Dashboard/components/ApprovalList.vue'
import CustomerFollowUpList from '@/views/Dashboard/components/CustomerFollowUpList.vue'
import MetricCards from '@/views/Dashboard/components/MetricCards.vue'
import PriorityTodos from '@/views/Dashboard/components/PriorityTodos.vue'
import ProcessTimeline from '@/views/Dashboard/components/ProcessTimeline.vue'
import ProjectList from '@/views/Dashboard/components/ProjectList.vue'
import ReviewList from '@/views/Dashboard/components/ReviewList.vue'
import TeamPerformance from '@/views/Dashboard/components/TeamPerformance.vue'
import TeamTaskList from '@/views/Dashboard/components/TeamTaskList.vue'
import TechnicalTaskList from '@/views/Dashboard/components/TechnicalTaskList.vue'
import TenderList from '@/views/Dashboard/components/TenderList.vue'
import WorkbenchQuickStart from '@/views/Dashboard/components/WorkbenchQuickStart.vue'
import WorkCalendar from '@/views/Dashboard/components/WorkCalendar.vue'

export function useWorkbenchDynamicWidgets({ state, actions }) {
  const widgetRegistry = {
    ActivityList,
    ApprovalList,
    CustomerFollowUpList,
    MetricCards,
    PriorityTodos,
    ProcessTimeline,
    ProjectList,
    ReviewList,
    TeamPerformance,
    TeamTaskList,
    TechnicalTaskList,
    TenderList,
    WorkbenchQuickStart,
    WorkCalendar,
  }

  const widgetProps = computed(() => ({
    TenderList: { tenders: state.hotTenders.value },
    TechnicalTaskList: { tasks: state.myTechnicalTasks.value },
    ReviewList: { reviews: state.pendingReviews.value },
    CustomerFollowUpList: { customers: state.followUpCustomers.value },
    ProjectList: {
      projects: state.activeProjects.value,
      progressColorResolver: actions.getProgressColor,
      statusTypeResolver: actions.getProjectStatusType,
      metaFields: state.currentUserRole.value === 'admin'
        ? ['manager', 'deadline']
        : ['deadline', 'manager'],
    },
    TeamTaskList: { members: state.teamMembers.value },
    TeamPerformance: { teams: state.teamPerformance.value },
    ApprovalList: {
      approvals: state.pendingApprovals.value,
      count: state.pendingApprovals.value.length,
      error: state.approvalsError.value,
    },
    ProcessTimeline: {
      processes: state.myProcesses.value,
      timeFormatter: actions.formatRelativeTime,
      error: state.processesError.value,
    },
    ActivityList: { activities: state.activities.value },
    PriorityTodos: {
      todos: state.priorityTodos.value,
      error: state.todosError.value,
      priorityTypeResolver: getPriorityType,
      priorityLabelResolver: getPriorityLabel,
    },
    WorkbenchQuickStart: {},
    WorkCalendar: {
      modelValue: state.calendarDate.value,
      activeFilter: state.activeCalendarFilter.value,
      filters: state.calendarFilters.value,
      visibleEvents: state.visibleCalendarEvents.value,
      selectedDateEvents: state.selectedDateEvents.value,
      selectedDateLabel: state.selectedDateLabel.value,
      monthSummary: state.monthCalendarSummary.value,
      upcomingEvents: state.upcomingCalendarEvents.value,
      getEventsForDate: actions.getEventsForDate,
      calendarCellClass: actions.calendarCellClass,
      getEventTypeTag: actions.getEventTypeTag,
      error: state.calendarError.value,
    },
  }))

  const widgetListeners = computed(() => ({
    TenderList: { 'view-all': actions.viewBidding, 'tender-click': actions.handleTenderClick },
    TechnicalTaskList: { 'task-change': actions.handleTaskComplete },
    ReviewList: { review: actions.handleReview },
    ProjectList: { 'view-all': actions.viewProject, 'project-click': actions.handleProjectClick },
    ApprovalList: {
      approve: actions.handleApprove,
      reject: actions.handleReject,
      retry: actions.loadPendingApprovals,
    },
    ProcessTimeline: { retry: actions.loadMyProcesses },
    PriorityTodos: { 'todo-toggle': actions.handleTaskComplete, retry: actions.loadTodos },
    WorkbenchQuickStart: { submitted: actions.handleApprovalSuccess },
    WorkCalendar: {
      'update:modelValue': actions.updateCalendarDate,
      'update:activeFilter': actions.updateActiveCalendarFilter,
      'date-click': actions.handleDateClick,
      'event-date-select': actions.selectCalendarEventDate,
      'event-action': actions.handleCalendarAction,
      retry: actions.reloadSchedule,
    },
  }))

  return { widgetRegistry, widgetProps, widgetListeners }
}
