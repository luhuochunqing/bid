<!-- Input: calendarDate / visibleCalendarEvents / selectedDateKey / selectedDateEvents / selectedDateLabel
Output: 改造版投标日历（左日历网格 + 右事件列表，对齐 workbench-preview.html）
Pos: src/views/Dashboard/components/ - 工作台改造组件
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <section class="cal-rebuild">
    <!-- 左侧：日历网格 -->
    <div class="cal-rebuild-main">
      <div class="cal-rebuild-head">
        <div class="cal-rebuild-title">投标日历</div>
        <div class="cal-rebuild-nav">
          <button class="cal-nav-btn" @click="goPrev" aria-label="上一月">‹</button>
          <span class="cal-nav-month">{{ monthLabel }}</span>
          <button class="cal-nav-btn" @click="goNext" aria-label="下一月">›</button>
        </div>
        <div class="cal-rebuild-legend">
          <span class="legend-item"><i class="dot dot-red"></i>报名截止</span>
          <span class="legend-item"><i class="dot dot-green"></i>开标时间</span>
        </div>
      </div>
      <div class="cal-rebuild-grid">
        <div v-for="w in weekHeaders" :key="w" class="cal-rebuild-hdr">{{ w }}</div>
        <div
          v-for="cell in calendarCells"
          :key="cell.key"
          class="cal-rebuild-day"
          :class="cell.cls"
          @click="onDayClick(cell)"
        >
          <span class="day-num">{{ cell.day }}</span>
          <span v-if="cell.dots.length" class="day-dots">
            <i v-for="(d, i) in cell.dots" :key="i" class="dot" :class="d"></i>
          </span>
        </div>
      </div>
    </div>
    <!-- 右侧：事件列表 -->
    <div class="cal-rebuild-list">
      <div class="cal-list-title">📅 {{ selectedDateLabel }} · 共 {{ selectedDateEvents.length }} 项</div>
      <div class="cal-list-body">
        <div v-if="selectedDateEvents.length === 0" class="cal-list-empty">该日期暂无关键节点</div>
        <div
          v-for="(event, idx) in selectedDateEvents"
          :key="event.id || idx"
          class="cal-event"
          :class="eventTagClass(event)"
          @click="emit('event-click', event)"
        >
          <div class="cal-event-date">
            <div class="d">{{ dayOfDate(event.date) }}</div>
            <div class="m">{{ monthOfDate(event.date) }}月</div>
          </div>
          <div class="cal-event-info">
            <div class="cal-event-name">{{ event.title }}</div>
            <div class="cal-event-meta">{{ eventMetaText(event) }}</div>
          </div>
          <span class="cal-event-tag">{{ eventTagText(event) }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  calendarDate: { type: Object, required: true },
  visibleCalendarEvents: { type: Array, default: () => [] },
  selectedDateKey: { type: String, default: '' },
  selectedDateEvents: { type: Array, default: () => [] },
  selectedDateLabel: { type: String, default: '' },
})
const emit = defineEmits(['prev-month', 'next-month', 'date-click', 'event-click'])

const weekHeaders = ['日', '一', '二', '三', '四', '五', '六']

const monthLabel = computed(() => {
  const d = props.calendarDate
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月`
})

function fmtKey(year, month, day) {
  return `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

function eventDotsForDate(dateKey) {
  const dayEvents = props.visibleCalendarEvents.filter((e) => e.date === dateKey)
  const dots = []
  dayEvents.forEach((e) => {
    if (e.type === 'deadline' || e.type === 'bid') dots.push('dot-red')
    if (e.type === 'opening') dots.push('dot-green')
  })
  return [...new Set(dots)]
}

const calendarCells = computed(() => {
  const d = props.calendarDate
  const year = d.getFullYear()
  const month = d.getMonth()
  const firstDay = new Date(year, month, 1)
  const startWeekday = firstDay.getDay()
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const prevDays = new Date(year, month, 0).getDate()
  const today = new Date()
  const todayKey = fmtKey(today.getFullYear(), today.getMonth(), today.getDate())
  const cells = []

  for (let i = startWeekday - 1; i >= 0; i--) {
    const day = prevDays - i
    const prevMonth = month === 0 ? 11 : month - 1
    const prevYear = month === 0 ? year - 1 : year
    cells.push({ key: `p-${day}`, day, cls: 'muted', dots: [] })
  }
  for (let day = 1; day <= daysInMonth; day++) {
    const dateKey = fmtKey(year, month, day)
    const dots = eventDotsForDate(dateKey)
    let cls = ''
    if (dateKey === todayKey) cls = 'today'
    if (dateKey === props.selectedDateKey) cls = cls ? `${cls} selected` : 'selected'
    cells.push({ key: dateKey, day, dateKey, cls, dots })
  }
  const remaining = 42 - cells.length
  for (let i = 1; i <= remaining; i++) {
    cells.push({ key: `n-${i}`, day: i, cls: 'muted', dots: [] })
  }
  return cells
})

function onDayClick(cell) {
  if (cell.dateKey) emit('date-click', cell.dateKey)
}

function goPrev() { emit('prev-month') }
function goNext() { emit('next-month') }

function dayOfDate(dateStr) {
  return dateStr ? Number(dateStr.slice(8)) : ''
}
function monthOfDate(dateStr) {
  return dateStr ? Number(dateStr.slice(5, 7)) : ''
}
function eventTagClass(event) {
  if (event.type === 'deadline' || event.type === 'bid') return 'signup'
  if (event.type === 'opening') return 'opening'
  return ''
}
function eventTagText(event) {
  const tagMap = { deadline: '报名', bid: '报名', opening: '开标', review: '评审', milestone: '里程碑', reminder: '提醒' }
  return tagMap[event.type] || '其他'
}
function eventMetaText(event) {
  const tagMap = { deadline: '报名截止', bid: '投标截止', opening: '开标时间', review: '评审时间', milestone: '里程碑', reminder: '提醒' }
  const label = tagMap[event.type] || '事件'
  return event.urgent ? `${label} · 紧急` : label
}
</script>
