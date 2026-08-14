<template>
  <div class="m3-project-type">
    <div class="card-header">
      <span class="card-title">项目类型维度</span>
    </div>
    <div class="chart-body">
      <div v-if="loading" class="status-overlay">
        <el-skeleton animated :count="1" style="padding: 20px;">
          <template #template>
            <div style="display: flex; justify-content: center; padding: 60px 0;">
              <el-skeleton-item variant="circle" style="width: 200px; height: 200px;" />
            </div>
          </template>
        </el-skeleton>
      </div>
      <div v-else-if="error" class="status-overlay">
        <el-empty description="数据加载失败" :image-size="80" />
      </div>
      <div v-else-if="noData" class="status-overlay">
        <el-empty description="暂无数据" :image-size="80" />
      </div>
      <div v-else ref="chartRef" class="chart-container"></div>
    </div>
  </div>
</template>

<script setup>
/* eslint-disable no-use-before-define */
import { ref, onMounted, onUnmounted, watch, markRaw, nextTick } from 'vue'
import * as echarts from 'echarts'
import { dashboardApi } from '@/api/modules/dashboard.js'

const props = defineProps({
  dateRange: { type: Array, default: null }
})

const chartRef = ref(null)
const loading = ref(true)
const error = ref(false)
const noData = ref(false)
let chartInstance = null

// PRD §8.4 项目类型枚举与颜色
const COLOR_MAP = { '工业品': '#2E7659', '办公': '#10B981', '综合': '#F59E0B', '集采': '#60A5FA', '其他': '#A78BFA', '未分类': '#CBD5E1' }
const FALLBACK_COLOR = COLOR_MAP['未分类']

// 日期格式化（Date 对象/字符串 → yyyy-MM-dd）
const formatDateStr = (date) => {
  if (!date) return null
  if (typeof date === 'string') return date.slice(0, 10)
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const renderChart = (pieData, allEmpty, legendData) => {
  if (!chartRef.value) return

  nextTick(() => {
    if (!chartInstance) {
      chartInstance = markRaw(echarts.init(chartRef.value))
    }

    const seriesData = allEmpty
      ? [{ value: pieData[0]?.count || 1, name: '未分类', itemStyle: { color: COLOR_MAP['未分类'] } }]
      : pieData.map((d) => ({
          value: d.count,
          name: d.name,
          itemStyle: {
            color: COLOR_MAP[d.name] || d.color || FALLBACK_COLOR
          }
        }))

    // PRD §8.8: count=0 的类型不在饼图中显示扇区，但图例中仍显示（灰色禁用态）
    const legendItems = allEmpty
      ? [{ name: '未分类' }]
      : (legendData || pieData).map((d) => ({
          name: d.name,
          textStyle: {
            color: d.count === 0 ? '#CBD5E1' : '#475569',
            fontSize: 12
          }
        }))

    const option = {
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c}个 · {d}%'
      },
      legend: {
        orient: 'horizontal',
        bottom: 0,
        left: 'center',
        data: legendItems
      },
      series: [
        {
          type: 'pie',
          radius: ['40%', '65%'],
          center: ['50%', '45%'],
          label: {
            formatter: '{b}\n{d}%',
            fontSize: 11,
            color: '#475569',
            fontWeight: 500
          },
          labelLine: {
            length: 12,
            length2: 8
          },
          itemStyle: {
            borderColor: '#fff',
            borderWidth: 2
          },
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowColor: 'rgba(0,0,0,0.12)'
            }
          },
          data: seriesData
        }
      ]
    }

    chartInstance.setOption(option, true)
    loading.value = false
  })
}

const fetchData = async () => {
  loading.value = true
  error.value = false
  noData.value = false

  try {
    const params = {}
    const sd = formatDateStr(props.dateRange?.[0])
    const ed = formatDateStr(props.dateRange?.[1])
    if (sd) params.startDate = sd
    if (ed) params.endDate = ed

    const response = await dashboardApi.getProjectTypes(params)
    // 后端返回 { dimensions: [...] }，兼容数组格式
    const rawResp = response?.data
    const rawData = Array.isArray(rawResp) ? rawResp : (rawResp?.dimensions || [])

    if (!Array.isArray(rawData) || rawData.length === 0) {
      noData.value = true
      loading.value = false
      return
    }

    // Normalize: group by project_type, empty → "未分类"
    // 后端字段: projectType, projectCount；兼容旧格式: name, count
    const typeMap = new Map()
    let totalCount = 0

    rawData.forEach((item) => {
      const label = item?.projectType || item?.name || item?.project_type || null
      const displayName = label || '未分类'
      const count = Number(item?.projectCount || item?.count || 0)

      if (typeMap.has(displayName)) {
        typeMap.get(displayName).count += count
      } else {
        typeMap.set(displayName, {
          name: displayName,
          count: count,
          color: item?.color || null,
          isEmpty: !label
        })
      }
      totalCount += count
    })

    // Check if all are empty-category
    const allEmpty = Array.from(typeMap.values()).every((d) => d.isEmpty)
    if (allEmpty) {
      const total = Array.from(typeMap.values()).reduce((s, d) => s + d.count, 0)
      renderChart([{ name: '未分类', count: total, isEmpty: true }], true)
      return
    }

    // Filter out zero-count items for pie, keep in legend
    const pieData = Array.from(typeMap.values()).filter((d) => d.count > 0)
    const legendData = Array.from(typeMap.values()).map((d) => ({
      name: d.name,
      count: d.count
    }))

    if (pieData.length === 0) {
      noData.value = true
      loading.value = false
      return
    }

    renderChart(pieData, false, legendData)
  } catch (err) {
    console.error('M3 ProjectType fetch error:', err)
    error.value = true
    loading.value = false
  }
}

const resizeChart = () => {
  if (chartInstance) chartInstance.resize()
}

const refresh = () => {
  fetchData()
}

watch(
  () => props.dateRange,
  () => {
    fetchData()
  },
  { deep: true }
)

onMounted(() => {
  fetchData()
  window.addEventListener('resize', resizeChart)
})

onUnmounted(() => {
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
  window.removeEventListener('resize', resizeChart)
})

defineExpose({ refresh })
</script>

<style scoped>
.m3-project-type {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  padding: var(--space-5);
  height: 100%;
  display: flex;
  flex-direction: column;
}

.card-header { margin-bottom: 14px; }

.card-title {
  font-size: 15px; font-weight: 700; color: #1E293B;
  display: flex; align-items: center;
}

.card-title::before {
  content: ''; display: inline-block;
  width: 3px; height: 15px;
  background: var(--brand-xiyu-logo);
  border-radius: 2px; margin-right: 8px; flex-shrink: 0;
}

.chart-body {
  flex: 1;
  min-height: 0;
  position: relative;
}

.chart-container {
  width: 100%;
  height: 100%;
  min-height: 300px;
}

.status-overlay {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}
</style>