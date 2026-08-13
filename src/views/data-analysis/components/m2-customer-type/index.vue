<template>
  <div class="m2-customer-type">
    <div class="card-header">
      <span class="card-title">客户类型维度</span>
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
  startDate: { type: String, default: '' },
  endDate: { type: String, default: '' }
})

const chartRef = ref(null)
const loading = ref(true)
const error = ref(false)
const noData = ref(false)
let chartInstance = null

// PRD §7.4 客户类型枚举与颜色
const COLOR_MAP = { '政府机关/事业单位/高校': '#2E7659', '央企': '#10B981', '地方国企': '#F59E0B', '民企': '#60A5FA', '港澳台及外企': '#A78BFA', '未分类': '#CBD5E1' }
const FALLBACK_COLOR = COLOR_MAP['未分类']

const renderChart = (pieData, total, allEmpty, legendData) => {
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

    const legendItems = allEmpty
      ? [{ name: '未分类', textStyle: { color: '#94A3B8', fontSize: 12 } }]
      : (legendData || pieData).map((d) => ({
          name: d.name,
          textStyle: {
            color: d.disabled ? '#CBD5E1' : '#475569',
            fontSize: 12
          },
          icon: 'circle'
        }))

    const option = {
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(255,255,255,0.96)',
        borderColor: '#E2E8F0',
        borderWidth: 1,
        borderRadius: 8,
        padding: [10, 14],
        formatter: function (params) {
          if (!params.value) return params.name + '<br/>暂无数据'
          const pct = ((params.value / total) * 100).toFixed(1)
          return `<strong>${params.name}</strong><br/>${params.value}个 · ${pct}%`
        }
      },
      legend: {
        type: 'scroll',
        orient: 'horizontal',
        bottom: 0,
        left: 'center',
        data: legendItems,
        itemWidth: 10,
        itemHeight: 10,
        itemGap: 16
      },
      series: [
        {
          type: 'pie',
          radius: ['40%', '65%'],
          center: ['50%', '44%'],
          avoidLabelOverlap: true,
          padAngle: 0,
          itemStyle: {
            borderRadius: 0,
            borderColor: '#fff',
            borderWidth: 2
          },
          label: {
            show: true,
            formatter: function (params) {
              const pct = ((params.value / total) * 100).toFixed(1)
              return params.name + '\n' + pct + '%'
            },
            color: '#475569',
            fontSize: 11,
            fontWeight: 500
          },
          labelLine: {
            show: true,
            lineStyle: { color: '#94A3B8' }
          },
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: 'rgba(0, 0, 0, 0.15)'
            }
          },
          data: seriesData
        }
      ],
      grid: {
        containLabel: true
      }
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
    if (props.startDate) params.startDate = props.startDate
    if (props.endDate) params.endDate = props.endDate

    const response = await dashboardApi.getCustomerTypes(params)
    const rawData = response?.data || []

    if (!Array.isArray(rawData) || rawData.length === 0) {
      noData.value = true
      loading.value = false
      return
    }

    // Normalize: group by customer_type, empty → "未分类"
    // 兼容 PRD §7.5 格式 ({ name, count, percentage, color }) 与旧格式 ({ customerType, count })
    const typeMap = new Map()
    let totalCount = 0

    rawData.forEach((item) => {
      const label = item?.name || item?.customerType || item?.customer_type || null
      const displayName = label || '未分类'
      const count = Number(item?.count || item?.projectCount || 0)

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
      renderChart([{ name: '未分类', count: total, isEmpty: true }], total, true)
      return
    }

    // Filter out zero-count items for pie, keep in legend
    const pieData = Array.from(typeMap.values()).filter((d) => d.count > 0)
    const legendData = Array.from(typeMap.values()).map((d) => ({
      name: d.name,
      count: d.count,
      disabled: d.count === 0
    }))

    if (pieData.length === 0) {
      noData.value = true
      loading.value = false
      return
    }

    renderChart(pieData, totalCount, false, legendData)
  } catch (err) {
    console.error('M2 CustomerType fetch error:', err)
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
  () => [props.startDate, props.endDate],
  () => {
    fetchData()
  },
  { deep: false }
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
.m2-customer-type {
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
  min-height: 320px;
}

.status-overlay {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
}
</style>