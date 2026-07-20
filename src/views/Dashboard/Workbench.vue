<template>
  <div class="workbench">
    <!-- 第 1 部分：欢迎横幅（改造版） -->
    <WelcomeBannerRebuild
      v-if="permissions.WelcomeBanner"
      :greeting="greeting"
      :user-name="currentUserName"
      :subtitle="bannerSubtitle"
      :stats="[]"
      @stat-click="handleWelcomeStatClick"
    />

    <!-- 第 2 部分：待办模块（4 分类卡片） -->
    <TodoCategoryCards
      :cards="todoCategoryCards"
      @item-click="handleTodoCardClick"
    />

    <!-- 第 3 部分：截止时间（Tab + 3 列列表，按 period 拉真实数据） -->
    <DeadlinePanels
      v-model:active-period="deadlinePeriod"
      :panels="deadlinePanels"
      :loading="deadlineItemsLoading"
      @row-click="handleDeadlineRowClick"
    />

    <!-- 第 4 部分：投标日历（改造版） -->
    <WorkbenchCalendarRebuild
      :calendar-date="calendarDate"
      :visible-calendar-events="visibleCalendarEvents"
      :selected-date-key="selectedDateKey"
      :selected-date-events="selectedDateEvents"
      :selected-date-label="selectedDateLabel"
      @prev-month="goPrevMonth"
      @next-month="goNextMonth"
      @date-click="handleCalendarDateClick"
      @event-click="handleCalendarAction"
    />

    <!-- 动态扩展区（后端配置才显示） -->
    <DynamicLayoutRenderer
      v-if="dynamicLayout"
      :layout="dynamicLayout"
      :registry="widgetRegistry"
      :widget-props="widgetProps"
      :widget-listeners="widgetListeners"
      :permissions="permissions"
    />

    <!-- 第 5 部分：AI 商机预测 + 消息通知 -->
    <div class="rebuild-ai-notif">
      <div class="rebuild-ai-card">
        <div class="rebuild-card-header">
          <div class="rebuild-card-title"><span class="icon"></span>AI 商机预测</div>
          <span class="rebuild-ai-tag">敬请期待</span>
        </div>
        <div class="rebuild-ai-placeholder">
          <div class="rebuild-ai-icon">🤖</div>
          <div class="rebuild-ai-title">AI 商机预测模块 · 规划中</div>
          <div class="rebuild-ai-sub">基于历史中标数据 + 公开标讯<br>预测潜在高胜率商机 · 后续版本上线</div>
        </div>
      </div>
      <WorkbenchNotifications />
    </div>

    <ApprovalDialog
      v-model:visible="approvalDialogVisible"
      :mode="approvalMode"
      :approval-info="currentApprovalItem"
      @success="handleApprovalSuccess"
    />
  </div>
</template>

<script setup>
import { computed, markRaw, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { dashboardApi, projectsApi, tendersApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useBiddingStore } from '@/stores/bidding'
import { useWorkbenchRoleTodos } from '@/views/Dashboard/useWorkbenchRoleTodos.js'
import { useWorkbenchSchedule } from '@/views/Dashboard/useWorkbenchSchedule.js'
import { useWorkbenchMetrics } from '@/views/Dashboard/useWorkbenchMetrics.js'
import { useWorkbenchTodos } from '@/views/Dashboard/useWorkbenchTodos.js'
import { useWorkbenchApprovals } from '@/views/Dashboard/useWorkbenchApprovals.js'
import { useWorkbenchDeadline } from '@/views/Dashboard/useWorkbenchDeadline.js'; import { useWorkbenchDerivedLists } from '@/views/Dashboard/useWorkbenchDerivedLists.js'
import { useWorkbenchDynamicWidgets } from '@/views/Dashboard/useWorkbenchDynamicWidgets.js'
import { useWorkbenchRebuild } from '@/views/Dashboard/useWorkbenchRebuild.js'
import { hasAnyPermission } from '@/utils/permission'; import { navigateToProject } from '@/utils/projectNavigation.js'
import {
  formatCurrentDate, formatRelativeTime, getBannerSubtitle,
  getPriorityLabel, getPriorityType, getProgressColor,
  getProjectStatusType,
} from '@/views/Dashboard/workbench-core.js'
import { normalizeProjectForWorkbench } from '@/views/Dashboard/workbench-utils.js'
import ApprovalDialog from '@/components/common/ApprovalDialog.vue'
import DynamicLayoutRenderer from '@/views/Dashboard/components/DynamicLayoutRenderer.vue'
import WelcomeBannerRebuild from '@/views/Dashboard/components/WelcomeBannerRebuild.vue'
import TodoCategoryCards from '@/views/Dashboard/components/TodoCategoryCards.vue'
import DeadlinePanels from '@/views/Dashboard/components/DeadlinePanels.vue'
import WorkbenchCalendarRebuild from '@/views/Dashboard/components/WorkbenchCalendarRebuild.vue'
import WorkbenchNotifications from '@/views/Dashboard/components/WorkbenchNotifications.vue'
import {
  Briefcase, Calendar, Check, DataAnalysis, Document, Flag, TrendCharts, User,
} from '@element-plus/icons-vue'
import '@/views/Dashboard/styles/workbench-styles.js'

const Icons = markRaw({ Briefcase, Calendar, Check, DataAnalysis, Document, Flag, TrendCharts, User })
const router = useRouter()
const userStore = useUserStore()
const biddingStore = useBiddingStore()
const currentUserRole = computed(() => userStore.currentUser?.roleCode || userStore.currentUser?.role || 'bid-Team')
const currentUserName = computed(() => userStore.currentUser?.name || '用户')
const currentUserId = computed(() => userStore.currentUser?.id || null)
const currentDate = computed(() => formatCurrentDate())
const workbenchProjects = ref([])
const hotTenders = ref([])
const dynamicLayout = ref(null)

// 工作台角色化待办：4 个独立数据源（专供待办卡片，按角色差异化加载）
const { taskTodos: workbenchTaskTodos, tenderTodos: workbenchTenderTodos, projectTodos: workbenchProjectTodos, resourceTodos: workbenchResourceTodos, loadAll: loadWorkbenchRoleTodos } = useWorkbenchRoleTodos({ roleRef: currentUserRole, userIdRef: currentUserId })

const {
  pendingApprovals, pendingApprovalsTotalCount, approvalDialogVisible, approvalMode,
  currentApprovalItem, myProcesses, approvalsError, processesError,
  handleApprove, handleReject, handleApprovalSuccess,
  loadPendingApprovals, loadMyProcesses,
} = useWorkbenchApprovals()

const {
  priorityTodos, pendingCount, completedTodoCount, todosError, loadTodos,
  handleTaskComplete,
} = useWorkbenchTodos({ assigneeIdRef: currentUserId, canLoadAlertTodosRef: computed(() => hasAnyPermission(userStore.menuPermissions, ['settings-alerts'])), message: ElMessage })

const myProjectCount = computed(() => workbenchProjects.value.length)

const {
  summaryStats, metricsLoading, metricsError, metrics, loadWorkbenchSummary, handleMetricClick,
} = useWorkbenchMetrics({
  router, message: ElMessage, currentUserRoleRef: currentUserRole,
  pendingCountRef: pendingCount, pendingApprovalsTotalCountRef: pendingApprovalsTotalCount,
  myProjectCountRef: myProjectCount, completedTodoCountRef: completedTodoCount,
  icons: Icons, menuPermissionsRef: computed(() => userStore.menuPermissions),
})

const { deadlineStats, deadlineMetrics, deadlineMetricsError, loadDeadlineStats, deadlineItemsLoading, deadlinePanels, loadDeadlineItems } = useWorkbenchDeadline({ menuPermissionsRef: computed(() => userStore.menuPermissions) })
const bannerSubtitle = computed(() => `今天是${currentDate.value}，欢迎回到工作台`)

const {
  activeProjects, followUpCustomers, teamMembers, myTechnicalTasks,
  pendingReviews, teamPerformance,
} = useWorkbenchDerivedLists({
  workbenchProjects,
  priorityTodos,
  pendingApprovals,
  currentUserRole,
  currentUserName,
})

const {
  calendarDate, visibleCalendarEvents, selectedDateKey, selectedDateEvents,
  selectedDateLabel, handleCalendarAction, loadScheduleOverview, syncSelectedDate,
  calendarMonthKey,
} = useWorkbenchSchedule({
  router,
  assigneeIdRef: currentUserId,
  onEventsLoaded: (events) => biddingStore.setCalendar(events),
})

const {
  greeting, deadlinePeriod, permissions, welcomeStats, todoCategoryCards,
  handleWelcomeStatClick, handleTodoCardClick, handleDeadlineRowClick,
} = useWorkbenchRebuild({
  taskTodosRef: workbenchTaskTodos,
  tenderTodosRef: workbenchTenderTodos,
  projectTodosRef: workbenchProjectTodos,
  resourceTodosRef: workbenchResourceTodos,
  deadlineStatsRef: deadlineStats,
  menuPermissionsRef: computed(() => userStore.menuPermissions),
  currentUserRef: computed(() => userStore.currentUser),
  roleRef: currentUserRole,
  userIdRef: currentUserId,
  router,
  handleTenderClick,
  handleProjectClick,
  handleApprove,
})

const activities = computed(() => {
  const processActivities = myProcesses.value.slice(0, 4).map((process) => ({
    id: `process-${process.id}`,
    type: process.status === 'urgent' ? 'warning' : process.status === 'in-progress' ? 'success' : 'info',
    text: process.title,
    time: process.time || '刚刚',
  }))
  if (processActivities.length > 0) {
    return processActivities
  }
  return priorityTodos.value.slice(0, 4).map((todo) => ({
    id: `todo-${todo.id}`,
    type: todo.done ? 'success' : 'warning',
    text: todo.title,
    time: todo.deadline || '待处理',
  }))
})

const { widgetRegistry, widgetProps, widgetListeners } = useWorkbenchDynamicWidgets({
  state: {
    hotTenders,
    myTechnicalTasks,
    pendingReviews,
    followUpCustomers,
    activeProjects,
    currentUserRole,
    teamMembers,
    teamPerformance,
    pendingApprovals,
    approvalsError,
    myProcesses,
    processesError,
    activities: computed(() => activities.value),
    priorityTodos,
    todosError,
    calendarDate,
    visibleCalendarEvents,
    selectedDateEvents,
    selectedDateLabel,
  },
  actions: {
    getProgressColor,
    getProjectStatusType,
    formatRelativeTime,
    getPriorityType,
    getPriorityLabel,
    viewBidding: () => router.push('/bidding'),
    viewProject: () => router.push('/project'),
    handleTenderClick,
    handleTaskComplete,
    handleReview,
    handleProjectClick,
    handleShareClick,
    handleApprove,
    handleReject,
    loadPendingApprovals,
    loadMyProcesses,
    loadTodos,
    handleApprovalSuccess,
    handleCalendarAction,
  },
})

function goPrevMonth() {
  const d = new Date(calendarDate.value)
  d.setMonth(d.getMonth() - 1)
  calendarDate.value = d
}
function goNextMonth() {
  const d = new Date(calendarDate.value)
  d.setMonth(d.getMonth() + 1)
  calendarDate.value = d
}
function handleCalendarDateClick(dateKey) {
  selectedDateKey.value = dateKey
  calendarDate.value = new Date(`${dateKey}T00:00:00`)
}

function handleTenderClick(tender) {
  if (String(tender.id || '').startsWith('-')) { router.push('/bidding'); return }
  router.push(`/bidding/${tender.id}`)
}

function handleProjectClick(project) {
  const projectId = String(project?.id || '')
  if (/^\d+$/.test(projectId)) { navigateToProject(router, projectId); return }
  router.push({ path: '/project', query: { demoProjectId: projectId } })
}

function handleShareClick() { ElMessage.info('协作功能开发中') }

function handleReview(review) { ElMessage.info(`打开评审: ${review.title}`) }

async function loadWorkbenchProjects() {
  try {
    const response = await projectsApi.getList()
    workbenchProjects.value = Array.isArray(response?.data) ? response.data.map(normalizeProjectForWorkbench) : []
  } catch {
    workbenchProjects.value = []
  }
}

async function loadWorkbenchTenders() {
  if (!userStore.hasPermission('bidding')) { hotTenders.value = []; return }
  try {
    const response = await tendersApi.getList()
    const tenders = Array.isArray(response?.data) ? response.data : []
    hotTenders.value = tenders.slice(0, 6).map((item) => {
      const score = Number(item.aiScore || 0)
      return {
        id: item.id, title: item.title || '未命名标讯', budget: Number(item.budget || 0),
        region: item.region || '-', aiScore: score,
        scoreLevel: score >= 85 ? 'high' : score >= 70 ? 'medium' : 'low',
        probability: score >= 85 ? 'high' : 'medium', probibilityText: score >= 85 ? '高概率' : '中等概率'
      }
    })
  } catch {
    hotTenders.value = []
  }
}

async function reloadSchedule() {
  await loadScheduleOverview(); syncSelectedDate()
}

async function loadDynamicLayout() {
  const res = await dashboardApi.getLayout().catch(() => null)
  const layoutJson = res?.data?.layoutJson
  dynamicLayout.value = res?.success && layoutJson && layoutJson !== '[]' ? JSON.parse(layoutJson) : null
}

onMounted(async () => {
  metricsLoading.value = true
  await Promise.allSettled([
    loadDynamicLayout(),
    loadWorkbenchProjects(),
    loadWorkbenchTenders(),
    loadScheduleOverview(), loadTodos(), loadPendingApprovals(), loadMyProcesses(),
    loadWorkbenchSummary(), loadDeadlineStats(),
    loadWorkbenchRoleTodos(), // 工作台角色化待办（4 个独立数据源）
  ])
  metricsLoading.value = false
  syncSelectedDate()
})

watch(calendarMonthKey, async (current, previous) => {
  if (!previous || current === previous) return
  await loadScheduleOverview()
  syncSelectedDate({ keepCalendarDate: true })
})

watch(deadlinePeriod, (p) => loadDeadlineItems(p || 'week'), { immediate: true }) // CO-593: 截止时间 Tab 切换 + 初始加载
</script>

<script>
export default { name: 'DashboardWorkbench' }
</script>
