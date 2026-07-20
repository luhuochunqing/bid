// Input: workbenchApi schedule overview endpoint and router
// Output: workbench schedule state/actions composable for Workbench.vue
// Pos: src/views/Dashboard/ - dashboard feature composables
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, ref } from 'vue'
import { workbenchApi } from '@/api/modules/workbench.js'
import { isRealTenderId, normalizeCalendarEvent } from '@/views/Dashboard/workbench-utils.js'
import { navigateToProject } from '@/utils/projectNavigation.js'
import {
  calendarFilters,
  decorateCalendarEvent,
  filterCalendarEvents,
  formatDateKey,
  formatSelectedDateLabel,
  getCalendarEventsForDate,
  getCalendarMonthKey,
  getEventTypeTag,
  getMonthCalendarSummary,
  getUpcomingCalendarEvents,
  parseDate,
  resolveCalendarCellClass,
} from '@/views/Dashboard/workbench-calendar-core.js'

export { formatDateKey }

export function useWorkbenchSchedule({ router, assigneeIdRef, onEventsLoaded } = {}) {
  const calendarDate = ref(new Date())
  const activeCalendarFilter = ref('all')
  const selectedDateKey = ref('')
  const calendarEvents = ref([])
  const calendarError = ref('')

  const normalizedCalendarEvents = computed(() => calendarEvents.value.map((event) => decorateCalendarEvent(event)))

  const visibleCalendarEvents = computed(() => filterCalendarEvents(normalizedCalendarEvents.value, activeCalendarFilter.value))

  const getEventsForDate = (date) => {
    return getCalendarEventsForDate(visibleCalendarEvents.value, date)
  }

  const calendarCellClass = ({ date, viewType }) => {
    return resolveCalendarCellClass(visibleCalendarEvents.value, { date, viewType })
  }

  const handleDateClick = (date) => {
    selectedDateKey.value = formatDateKey(date)
    calendarDate.value = date
  }

  const selectedDateEvents = computed(() =>
    visibleCalendarEvents.value.filter((event) => event.date === selectedDateKey.value)
  )

  const selectedDateLabel = computed(() => formatSelectedDateLabel(selectedDateKey.value))

  const monthCalendarSummary = computed(() => {
    return getMonthCalendarSummary(visibleCalendarEvents.value, calendarDate.value)
  })

  const upcomingCalendarEvents = computed(() => getUpcomingCalendarEvents(visibleCalendarEvents.value))

  const selectCalendarEventDate = (event) => {
    selectedDateKey.value = event.date
    calendarDate.value = parseDate(event.date)
  }

  const handleCalendarAction = (event) => {
    // Tender 派生事件（开标/报名截止）跳转标讯详情，与 handleTenderClick 行为对齐
    const tenderEventTypes = ['opening', 'deadline', 'bid']
    const eventType = event?.eventType || event?.type
    const tenderId = event?.id
    if (tenderEventTypes.includes(eventType) && tenderId) {
      // 真实标讯 → 标讯详情；demo 标讯 → 标讯列表页
      if (isRealTenderId(tenderId)) {
        router.push(`/bidding/${tenderId}`)
      } else {
        router.push('/bidding')
      }
      return
    }

    if (event?.projectId) {
      navigateToProject(router, event.projectId)
      return
    }

    router.push({
      path: '/project',
      query: {
        calendarDate: event?.date || '',
        calendarType: event?.eventType || event?.type || '',
      },
    })
  }

  const loadScheduleOverview = async () => {
    calendarError.value = ''
    const rangeStart = new Date(calendarDate.value)
    rangeStart.setDate(1)
    const rangeEnd = new Date(calendarDate.value)
    rangeEnd.setMonth(rangeEnd.getMonth() + 1, 0)

    try {
      const response = await workbenchApi.getScheduleOverview({
        start: rangeStart,
        end: rangeEnd,
        assigneeId: assigneeIdRef?.value || undefined,
      })
      const normalizedEvents = (response?.data?.events || []).map(normalizeCalendarEvent)
      calendarEvents.value = normalizedEvents
      onEventsLoaded?.(normalizedEvents)
      return normalizedEvents
    } catch {
      calendarEvents.value = []
      onEventsLoaded?.([])
      calendarError.value = '日程节点加载失败，请稍后重试'
      return []
    }
  }

  const syncSelectedDate = (options = {}) => {
    const { keepCalendarDate = false } = options

    // 翻月场景：保持 calendarDate，selectedDateKey 优先指向当前月份的事件
    if (keepCalendarDate) {
      const currentMonthKey = getCalendarMonthKey(calendarDate.value)
      const monthEvents = normalizedCalendarEvents.value
        .filter((event) => getCalendarMonthKey(parseDate(event.date)) === currentMonthKey)
        .sort((a, b) => a.diffDays - b.diffDays)

      if (monthEvents[0]) {
        selectedDateKey.value = monthEvents[0].date
        return
      }

      // 当前月份无事件：选当前月第一天，仅影响日历格子高亮
      selectedDateKey.value = formatDateKey(calendarDate.value)
      return
    }

    // 初始加载场景：跳转到最近未来事件所在月份
    selectedDateKey.value = formatDateKey(new Date())
    const firstUpcomingEvent = normalizedCalendarEvents.value
      .filter((event) => event.diffDays >= 0)
      .sort((a, b) => a.diffDays - b.diffDays)[0]

    if (firstUpcomingEvent) {
      selectedDateKey.value = firstUpcomingEvent.date
      calendarDate.value = parseDate(firstUpcomingEvent.date)
    }
  }

  const calendarMonthKey = computed(() => getCalendarMonthKey(calendarDate.value))

  return {
    calendarDate,
    activeCalendarFilter,
    selectedDateKey,
    calendarError,
    calendarFilters,
    visibleCalendarEvents,
    selectedDateEvents,
    selectedDateLabel,
    monthCalendarSummary,
    upcomingCalendarEvents,
    getEventsForDate,
    calendarCellClass,
    handleDateClick,
    getEventTypeTag,
    selectCalendarEventDate,
    handleCalendarAction,
    loadScheduleOverview,
    syncSelectedDate,
    calendarMonthKey,
  }
}
