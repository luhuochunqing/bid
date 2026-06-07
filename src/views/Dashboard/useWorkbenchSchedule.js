// Input: workbenchApi schedule overview endpoint and router
// Output: workbench schedule state/actions composable for Workbench.vue
// Pos: src/views/Dashboard/ - dashboard feature composables
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, ref } from 'vue'
import { workbenchApi } from '@/api/modules/workbench.js'
import { normalizeCalendarEvent } from '@/views/Dashboard/workbench-utils.js'
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
    if (event?.projectId) {
      router.push(`/project/${event.projectId}`)
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

  const syncSelectedDate = () => {
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
