<!-- Input: Workbench MetricCards props and user actions
Output: responsive presentational Workbench MetricCards section
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="metrics-grid" v-loading="loading">
    <EmptyState
      v-if="error"
      class="metrics-state"
      state="error"
      icon="!"
      title="指标加载失败"
      :description="error"
      action-label="重试"
      @action="emit('retry')"
    />
    <EmptyState
      v-else-if="!loading && metrics.length === 0"
      class="metrics-state"
      icon="0"
      title="暂无指标数据"
      description="当前角色还没有可展示的工作台指标。"
      action-label="刷新"
      @action="emit('retry')"
    />
    <template v-else>
      <div
        v-for="metric in metrics"
        :key="metric.key"
        class="metric-card"
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
          <span class="metric-change" :class="metric.changeClass">{{ metric.change }}</span>
          <span class="metric-compare">{{ compareLabel }}</span>
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
  compareLabel: { type: String, default: '较上月' },
})

const emit = defineEmits(['metric-click', 'retry'])
const selectMetric = (metric) => emit('metric-click', metric)
</script>
