<!-- Input: Workbench WorkCalendar props and user actions
Output: presentational Workbench WorkCalendar section with simplified empty states
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="section-card calendar-card calendar-card--hero">
    <div class="section-header">
      <h3 class="section-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="section-icon">
          <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
          <line x1="16" y1="2" x2="16" y2="6"/>
          <line x1="8" y1="2" x2="8" y2="6"/>
          <line x1="3" y1="10" x2="21" y2="10"/>
        </svg>
        {{ title }}
      </h3>
      <el-tag size="small" type="primary">{{ visibleEvents.length }} 个节点</el-tag>
    </div>
    <EmptyState
      v-if="error"
      state="error"
      icon="!"
      title="日程加载失败"
      :description="error"
      action-label="重试"
      @action="emit('retry')"
    />
    <div v-else class="calendar-topbar">
      <div class="calendar-summary-strip">
        <div class="summary-pill"><span class="summary-label">本月节点</span><strong>{{ monthSummary.total }}</strong></div>
        <div class="summary-pill risk"><span class="summary-label">高风险</span><strong>{{ monthSummary.urgent }}</strong></div>
        <div class="summary-pill accent"><span class="summary-label">最近截止</span><strong>{{ monthSummary.nextDeadlineLabel }}</strong></div>
      </div>
      <div class="calendar-filter-bar">
        <div class="calendar-filter-copy">
          <span class="filter-eyebrow">节点筛选</span>
          <span class="filter-hint">按类型或风险切换主舞台视图</span>
        </div>
        <div class="calendar-filter-row">
          <button
            v-for="filter in filters"
            :key="filter.value"
            type="button"
            class="calendar-filter-chip"
            :class="{ active: activeFilter === filter.value }"
            @click="emit('update:activeFilter', filter.value)"
          >
            <span class="chip-dot" :class="`dot-${filter.value}`"></span>
            {{ filter.label }}
          </button>
        </div>
      </div>
    </div>
    <div v-if="!error" class="calendar-hero-grid">
      <div class="calendar-hero-main">
        <div class="calendar-wrapper">
          <el-calendar v-model="calendarValue">
            <template #date-cell="{ data }">
              <div
                class="calendar-day-cell"
                :class="resolveCalendarCellClass(data)"
                role="button"
                tabindex="0"
                :aria-label="`${data.day}，${resolveEventsForDate(data.date).length} 个投标节点`"
                @click="selectDate(data.date)"
                @keydown.enter.prevent="selectDate(data.date)"
                @keydown.space.prevent="selectDate(data.date)"
              >
                <span class="calendar-day-number">{{ data.day.split('-')[2] }}</span>
                <div v-if="resolveEventsForDate(data.date).length > 0" class="calendar-day-marker">
                  <div class="calendar-day-dots">
                    <span
                      v-for="event in resolveEventsForDate(data.date).slice(0, 3)"
                      :key="event.id"
                      class="calendar-event-dot"
                      :class="`event-${event.type}`"
                    ></span>
                  </div>
                  <span class="calendar-day-count">{{ resolveEventsForDate(data.date).length }}</span>
                  <span v-if="resolveEventsForDate(data.date).some((event) => event.urgent)" class="calendar-day-alert">!</span>
                </div>
              </div>
            </template>
          </el-calendar>
        </div>
        <div class="calendar-legend">
          <span class="legend-item"><span class="legend-dot event-deadline"></span>截止</span>
          <span class="legend-item"><span class="legend-dot event-bid"></span>投标</span>
          <span class="legend-item"><span class="legend-dot event-opening"></span>开标</span>
          <span class="legend-item"><span class="legend-dot event-review"></span>评审</span>
        </div>
      </div>
      <div class="calendar-hero-side">
        <div class="calendar-panel">
          <div class="calendar-panel-header">
            <div>
              <div class="calendar-panel-eyebrow">选中日期</div>
              <h4 class="calendar-panel-title">{{ selectedDateLabel }}</h4>
            </div>
            <el-tag size="small" :type="selectedDateEvents.length > 0 ? 'danger' : 'info'">
              {{ selectedDateEvents.length > 0 ? `${selectedDateEvents.length} 个事项` : '无事项' }}
            </el-tag>
          </div>
          <div v-if="selectedDateEvents.length > 0" class="selected-events-list">
            <div v-for="event in selectedDateEvents" :key="event.id" class="selected-event-card" :class="[`event-${event.type}`, event.priorityLevel]">
              <div class="selected-event-main">
                <div class="selected-event-topline">
                  <span class="selected-event-type">{{ resolveEventTypeTag(event.type).label }}</span>
                  <span class="selected-event-countdown">{{ event.countdownLabel }}</span>
                </div>
                <h5 class="selected-event-title">{{ event.shortTitle || event.title }}</h5>
                <p class="selected-event-project">{{ event.project }}</p>
                <div class="selected-event-meta">
                  <span>{{ event.fieldSummary?.owner }}</span>
                  <span>{{ event.fieldSummary?.stage }}</span>
                  <span>{{ event.fieldSummary?.blocker }}</span>
                </div>
              </div>
              <div class="selected-event-actions">
                <el-tag size="small" :type="event.riskTagType" :class="['risk-tag', event.priorityLevel]">{{ event.riskLabel }}</el-tag>
                <el-button size="small" text type="primary" @click="emit('event-action', event)">{{ event.actionLabel }}</el-button>
              </div>
            </div>
          </div>
          <EmptyState
            v-else
            class="calendar-empty-state"
            icon=""
            title="这一天没有投标节点"
            description="当前筛选条件下没有事项，可以切换筛选或查看未来 7 天。"
          />
        </div>
        <div class="upcoming-panel">
          <div class="calendar-panel-header">
            <div>
              <div class="calendar-panel-eyebrow">未来 7 天</div>
              <h4 class="calendar-panel-title">关键执行清单</h4>
            </div>
            <el-link type="primary" underline="hover" @click="emit('update:activeFilter', 'all')">清除筛选</el-link>
          </div>
          <div class="upcoming-events-list">
            <div
              v-for="event in upcomingEvents"
              :key="event.id"
              class="upcoming-event-item"
              :class="[`event-${event.type}`, event.priorityLevel]"
              role="button"
              tabindex="0"
              @click="selectEventDate(event)"
              @keydown.enter.prevent="selectEventDate(event)"
              @keydown.space.prevent="selectEventDate(event)"
            >
              <div class="upcoming-event-rail">
                <span class="upcoming-rail-countdown">{{ event.countdownLabel }}</span>
                <span class="upcoming-rail-type">{{ resolveEventTypeTag(event.type).label }}</span>
              </div>
              <div class="upcoming-event-body">
                <div class="upcoming-event-title-row">
                  <span class="upcoming-event-title">{{ event.project }}</span>
                  <span class="upcoming-event-date-label">{{ event.dayLabel }} {{ event.weekdayLabel }}</span>
                </div>
                <div class="upcoming-event-subline execution-meta">
                  <span class="execution-chip">{{ event.shortTitle || event.title }}</span>
                  <span class="execution-chip muted">{{ event.fieldSummary?.stage }}</span>
                  <span class="execution-chip blocker">{{ event.fieldSummary?.blocker }}</span>
                </div>
              </div>
              <div class="upcoming-event-side">
                <span class="upcoming-event-owner">{{ event.fieldSummary?.owner }}</span>
                <el-button size="small" text type="primary" @click.stop="emit('event-action', event)">{{ event.actionLabel }}</el-button>
              </div>
            </div>
          </div>
          <EmptyState
            v-if="upcomingEvents.length === 0"
            class="calendar-empty-state compact"
            icon=""
            title="未来 7 天没有待执行节点"
            description="当前筛选下没有临近节点，可以清除筛选查看全部日程。"
            action-label="清除筛选"
            @action="emit('update:activeFilter', 'all')"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import EmptyState from './EmptyState.vue'

const props = defineProps({
  title: { type: String, default: '投标日历' },
  modelValue: { type: [Date, String], default: () => new Date() },
  activeFilter: { type: String, default: 'all' },
  filters: { type: Array, default: () => [] },
  visibleEvents: { type: Array, default: () => [] },
  selectedDateEvents: { type: Array, default: () => [] },
  selectedDateLabel: { type: String, default: '' },
  monthSummary: { type: Object, default: () => ({ total: 0, urgent: 0, nextDeadlineLabel: '--' }) },
  upcomingEvents: { type: Array, default: () => [] },
  getEventsForDate: { type: Function, default: null },
  calendarCellClass: { type: Function, default: null },
  getEventTypeTag: { type: Function, default: null },
  error: { type: String, default: '' },
})

const emit = defineEmits([
  'update:modelValue',
  'update:activeFilter',
  'date-click',
  'event-date-select',
  'event-action',
  'retry',
])

const calendarValue = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const resolveEventsForDate = (date) => (props.getEventsForDate ? props.getEventsForDate(date) : [])
const resolveCalendarCellClass = (data) => (props.calendarCellClass ? props.calendarCellClass(data) : '')
const resolveEventTypeTag = (type) => (
  props.getEventTypeTag ? props.getEventTypeTag(type) : { label: type || '节点' }
)
const selectDate = (date) => emit('date-click', date)
const selectEventDate = (event) => emit('event-date-select', event)
</script>
