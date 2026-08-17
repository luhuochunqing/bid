<template>
  <div class="trend-chart-card">
    <!-- 图表容器始终在 DOM 中，避免 v-if 切换导致 ECharts 实例指向废弃 DOM 元素 -->
    <div ref="chartRef" class="chart-container" :class="{ 'chart-dimmed': loading }"></div>

    <div v-if="loading" class="chart-overlay">
      <el-icon class="is-loading" :size="28"><Loading /></el-icon>
    </div>

    <div v-else-if="error" class="chart-overlay state-error">
      <el-result icon="error" title="数据加载失败" :sub-title="error">
        <template #extra>
          <el-button type="primary" size="small" @click="$emit('retry')">重试</el-button>
        </template>
      </el-result>
    </div>

    <div v-else-if="isEmpty" class="chart-overlay state-empty">
      <el-empty description="暂无数据" :image-size="80" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, markRaw, nextTick, computed } from 'vue'
import echarts from '@/utils/echarts'
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

  // 使用 zrender 的 click 事件，增大柱子点击容错区域
  // 根因：窄图表下 bar 宽度可能只有 4px，点击容易落在间隙中导致 ECharts on('click') 不触发
  chartInstance.getZr().on('click', (params) => {
    const offsetX = params.offsetX
    const offsetY = params.offsetY

    // 反向计算 category index
    const xIndex = chartInstance.convertFromPixel({ xAxisIndex: 0 }, offsetX)
    const categoryIndex = Math.round(xIndex)
    if (categoryIndex < 0 || categoryIndex >= props.data.length) return

    const dataItem = props.data[categoryIndex]
    if (!dataItem) return

    // 用 getItemLayout 获取 bar 矩形，判断点击位置命中哪个 series
    const model = chartInstance.getModel()
    const isStatusAxis = props.xAxisType === 'projectStatus'
    const seriesConfigs = isStatusAxis
      ? [{ index: 0, name: '数量', valueKey: 'bidCount' }]
      : [
          { index: 0, name: '投标数', valueKey: 'bidCount' },
          { index: 1, name: '中标数', valueKey: 'winCount' }
        ]

    // 辅助函数：获取 bar 矩形
    const getBarRect = (cfg) => {
      const sm = model.getSeriesByIndex(cfg.index)
      if (!sm) return null
      const layout = sm.getData().getItemLayout(categoryIndex)
      if (!layout) return null
      return {
        left: layout.x,
        right: layout.x + layout.width,
        top: Math.min(layout.y, layout.y + layout.height),
        bottom: Math.max(layout.y, layout.y + layout.height),
        center: layout.x + layout.width / 2
      }
    }

    // 第一轮：精确命中（无容错），命中即返回
    for (const cfg of seriesConfigs) {
      const rect = getBarRect(cfg)
      if (!rect) continue
      if (offsetX >= rect.left && offsetX <= rect.right && offsetY >= rect.top && offsetY <= rect.bottom) {
        const value = dataItem[cfg.valueKey] ?? 0
        if (value > 0) {
          emit('bar-click', {
            seriesName: cfg.name,
            dataIndex: categoryIndex,
            data: dataItem,
            value
          })
        }
        return
      }
    }

    // 第二轮：容错命中（左右各加 5px），选择 x 坐标距离最近的 bar
    // 根因：窄图表下 bar 宽度可能仅 4px，点击容易落在间隙中
    const tolerance = 5
    let bestMatch = null
    let bestDist = Infinity
    for (const cfg of seriesConfigs) {
      const rect = getBarRect(cfg)
      if (!rect) continue
      if (
        offsetX >= rect.left - tolerance &&
        offsetX <= rect.right + tolerance &&
        offsetY >= rect.top &&
        offsetY <= rect.bottom
      ) {
        const value = dataItem[cfg.valueKey] ?? 0
        // 0 值柱子不触发下钻（用户需求：0 不需要点击）
        if (value > 0) {
          const dist = Math.abs(offsetX - rect.center)
          if (dist < bestDist) {
            bestDist = dist
            bestMatch = { seriesName: cfg.name, value }
          }
        }
      }
    }
    if (bestMatch) {
      emit('bar-click', {
        seriesName: bestMatch.seriesName,
        dataIndex: categoryIndex,
        data: dataItem,
        value: bestMatch.value
      })
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
    // 图表容器始终在 DOM 中，直接初始化
    initChart()
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
  min-height: 400px;
}

.chart-container {
  width: 100%;
  height: 400px;
  min-height: 300px;
}

.chart-container.chart-dimmed {
  opacity: 0.3;
  pointer-events: none;
}

.chart-overlay {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 10;
}

.state-error { padding: 40px 0; }
.state-empty { padding: 40px 0; }
</style>