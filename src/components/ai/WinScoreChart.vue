<template>
  <div class="win-score-chart">
    <div ref="chartRef" class="radar-chart-container"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  scores: {
    type: Array,
    default: () => []
  },
  dimensionScores: {
    type: Array,
    default: () => []
  }
})

const chartRef = ref(null)
let chartInstance = null

// 获取图表配置
const getChartOption = () => {
  const dimensions = props.dimensionScores.length > 0
    ? props.dimensionScores.map(d => d.name)
    : ['客户关系', '需求匹配', '资质满足', '交付能力', '竞争态势']

  const values = props.dimensionScores.length > 0
    ? props.dimensionScores.map(d => d.score)
    : [70, 70, 70, 70, 70]

  return {
    radar: {
      indicator: dimensions.map(name => ({
        name,
        max: 100
      })),
      center: ['50%', '55%'],
      radius: '65%',
      axisName: {
        color: 'var(--text-secondary-ui)',
        fontSize: 13,
        fontWeight: 500
      },
      splitArea: {
        areaStyle: {
          color: ['var(--bg-subtle)', 'var(--gray-250)', '#d4d7dc', '#c4c7cc', '#b4b7bc']
        }
      },
      axisLine: {
        lineStyle: {
          color: '#dcdfe6'
        }
      },
      splitLine: {
        lineStyle: {
          color: '#dcdfe6'
        }
      }
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: values,
            name: '赢面分析',
            areaStyle: {
              color: {
                type: 'linear',
                x: 0,
                y: 0,
                x2: 1,
                y2: 1,
                colorStops: [
                  { offset: 0, color: 'rgba(0, 102, 204, 0.3)' },
                  { offset: 1, color: 'rgba(0, 102, 204, 0.1)' }
                ]
              }
            },
            lineStyle: {
              color: 'var(--brand-primary)',
              width: 2
            },
            itemStyle: {
              color: 'var(--brand-primary)'
            }
          }
        ]
      }
    ]
  }
}

const initChart = () => {
  if (!chartRef.value) return

  chartInstance = echarts.init(chartRef.value)
  chartInstance.setOption(getChartOption())
}

const updateChart = () => {
  if (chartInstance) {
    chartInstance.setOption(getChartOption(), true)
  }
}

const handleResize = () => {
  if (chartInstance) {
    chartInstance.resize()
  }
}

onMounted(() => {
  initChart()

  // 响应式处理
  window.addEventListener('resize', handleResize)
})

watch(() => [props.scores, props.dimensionScores], () => {
  updateChart()
}, { deep: true })

onBeforeUnmount(() => {
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
  window.removeEventListener('resize', handleResize)
})

defineExpose({
  resize: handleResize
})
</script>

<style scoped>
.win-score-chart {
  width: 100%;
  height: 100%;
}

.radar-chart-container {
  width: 100%;
  height: 320px;
}
</style>
