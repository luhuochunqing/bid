<!-- Input: Workbench ProcessTimeline props and user actions
Output: presentational Workbench ProcessTimeline section
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="section-card process-card">
    <div class="section-header">
      <h3 class="section-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="section-icon">
          <circle cx="12" cy="12" r="10"/>
          <polyline points="12 6 12 12 16 14"/>
        </svg>
        {{ title }}
      </h3>
    </div>
    <EmptyState
      v-if="error"
      state="error"
      icon="!"
      title="流程加载失败"
      :description="error"
      action-label="重试"
      @action="emit('retry')"
    />
    <EmptyState
      v-else-if="processes.length === 0"
      icon="流"
      title="暂无流程记录"
      description="你发起或参与的审批流程会显示在这里。"
    />
    <div v-else class="process-timeline">
      <div v-for="process in processes" :key="process.id" class="process-item">
        <div class="process-dot" :class="`status-${process.status}`"></div>
        <div class="process-content">
          <div class="process-header">
            <span class="process-title">{{ process.title }}</span>
            <span class="process-time">{{ resolveTime(process.time) }}</span>
          </div>
          <p class="process-desc">{{ process.description }}</p>
          <div v-if="process.progress" class="process-progress">
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: `${process.progress}%` }"></div>
            </div>
            <span class="progress-label">{{ process.progress }}%</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import EmptyState from './EmptyState.vue'

const props = defineProps({
  title: { type: String, default: '我的流程' },
  processes: { type: Array, default: () => [] },
  timeFormatter: { type: Function, default: null },
  error: { type: String, default: '' },
})

const emit = defineEmits(['retry'])
const defaultTimeFormatter = (time) => {
  if (!time) return ''
  const date = new Date(time)
  if (Number.isNaN(date.getTime())) return time
  const diff = Date.now() - date.getTime()
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return `${Math.floor(diff / 86400000)}天前`
}

const resolveTime = (time) => (props.timeFormatter ? props.timeFormatter(time) : defaultTimeFormatter(time))
</script>
