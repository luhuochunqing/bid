<!-- Input: deadline metrics array from selectDeadlineMetrics()
Output: responsive deadline metric cards with loading/error/empty states
Pos: src/views/Dashboard/components/ - Dashboard display components -->
<template>
  <div class="deadline-metrics-grid" v-loading="loading">
    <EmptyState
      v-if="error"
      class="metrics-state"
      state="error"
      icon="!"
      title="截止节点加载失败"
      :description="error"
      action-label="重试"
      @action="emit('retry')"
    />
    <EmptyState
      v-else-if="!loading && metrics.length === 0"
      class="metrics-state"
      icon="0"
      title="暂无截止节点数据"
      description="当前时间窗口内没有关键截止节点。"
      action-label="刷新"
      @action="emit('retry')"
    />
    <template v-else>
      <div
        v-for="metric in metrics"
        :key="metric.key"
        class="metric-card deadline-metric"
        :class="`metric-${metric.variant}`"
        role="button"
        tabindex="0"
        @click="selectMetric(metric)"
        @keydown.enter.prevent="selectMetric(metric)"
        @keydown.space.prevent="selectMetric(metric)"
      >
        <div class="metric-header">
          <span class="metric-label">{{ metric.label }}</span>
          <div class="metric-icon" :style="{ background: metric.iconBg, color: metric.iconColor }">
            <el-icon :size="20">
              <component :is="metric.icon" />
            </el-icon>
          </div>
        </div>
        <div class="metric-value">{{ metric.value }}</div>
        <div class="metric-footer">
          <span class="metric-period">{{ compareLabel }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import EmptyState from './EmptyState.vue'

defineProps({
  metrics: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  compareLabel: { type: String, default: '个' },
})

const emit = defineEmits(['metric-click', 'retry'])
const selectMetric = (metric) => emit('metric-click', metric)
</script>

<style scoped>
.deadline-metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

@media (max-width: 768px) {
  .deadline-metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .deadline-metrics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
