<template>
  <div class="trend-chart-card">
    <div v-if="loading" class="chart-state state-loading">
      <div class="loading-overlay">
        <el-icon class="is-loading" :size="28"><Loading /></el-icon>
      </div>
      <div ref="chartRef" class="chart-container"></div>
    </div>

    <div v-else-if="error" class="chart-state state-error">
      <el-result icon="error" title="数据加载失败" :sub-title="error">
        <template #extra>
          <el-button type="primary" size="small" @click="$emit('retry')">重试</el-button>
        </template>
      </el-result>
    </div>

    <div v-else-if="isEmpty" class="chart-state state-empty">
      <el-empty description="暂无数据" :image-size="80" />
    </div>

    <div v-else ref="chartRef" class="chart-container"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, markRaw, nextTick, computed } from 'vue'
import * as echarts from 'echarts'
import { Loading } from '@element-plus/icons-vue'
import { buildChartOption } from './chartOptions.js'

const props = defineProps({
  data: { type: Array, default: () => [] },
  xAxisType: { type: String, default: 'time' },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

const emit = defineEmits(['bar-click', 'retry'])
const chartRef = ref(null)
let chartInstance = null

const isEmpty = computed(() => {
  return !props.loading && !props.error && (!props.data || props.data.length === 0)
})

const initChart = () => {
  if (!chartRef.value) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = markRaw(echarts.init(chartRef.value, null, { renderer: 'canvas' }))
  chartInstance.setOption(buildChartOption(props.data, props.xAxisType), true)
  chartInstance.on('click', (params) => {
    if (params.componentType === 'series' && (params.seriesType === 'bar' || params.seriesType === 'line')) {
      const dataItem = props.data[params.dataIndex]
      if (dataItem) {
        emit('bar-click', {
          seriesName: params.seriesName,
          dataIndex: params.dataIndex,
          data: dataItem,
          value: params.value
        })
      }
    }
  })
}

const updateChart = () => {
  if (!chartInstance) { initChart(); return }
  chartInstance.setOption(buildChartOption(props.data, props.xAxisType), true)
}

const resizeChart = () => { if (chartInstance) chartInstance.resize() }

watch(() => props.data, () => { nextTick(() => updateChart()) }, { deep: true })
watch(() => props.xAxisType, () => { nextTick(() => updateChart()) })

onMounted(() => {
  nextTick(() => {
    if (!props.loading && !props.error && !isEmpty.value) initChart()
    window.addEventListener('resize', resizeChart)
  })
})

onUnmounted(() => {
  if (chartInstance) { chartInstance.dispose(); chartInstance = null }
  window.removeEventListener('resize', resizeChart)
})

defineExpose({ resize: resizeChart, getInstance: () => chartInstance })
</script>

<style scoped>
.trend-chart-card {
  background: var(--bg-card);
  border-radius: 12px;
  padding: 20px;
  position: relative;
}

.chart-container {
  width: 100%;
  height: 400px;
  min-height: 300px;
}

.chart-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.state-loading { position: relative; }
.state-loading .chart-container { opacity: 0.3; pointer-events: none; }

.loading-overlay {
  position: absolute;
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--brand-primary);
}

.state-error { padding: 40px 0; }
.state-empty { padding: 40px 0; }
</style>