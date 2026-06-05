<template>
  <el-dialog
    v-model="dialogVisible"
    title="ROI核算 - 投入产出分析"
    width="1100px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div v-if="currentData" class="roi-analysis">
      <!-- 关键指标卡片 -->
      <div class="metrics-grid">
        <div class="metric-card">
          <div class="metric-icon"></div>
          <div class="metric-content">
            <div class="metric-value">{{ currentData.totalManDays }}</div>
            <div class="metric-label">预计投入人天</div>
          </div>
        </div>
        <div class="metric-card">
          <div class="metric-icon">💰</div>
          <div class="metric-content">
            <div class="metric-value">{{ formatCurrency(currentData.totalCost) }}</div>
            <div class="metric-label">预计费用(元)</div>
          </div>
        </div>
        <div class="metric-card">
          <div class="metric-icon"></div>
          <div class="metric-content">
            <div class="metric-value" :class="getWinRateClass(currentData.winRate)">
              {{ currentData.winRate }}%
            </div>
            <div class="metric-label">{{ currentData.winRateLabel || '预计赢面' }}</div>
          </div>
        </div>
        <div class="metric-card">
          <div class="metric-icon"></div>
          <div class="metric-content">
            <div class="metric-value profit">{{ formatCurrency(currentData.expectedProfit) }}</div>
            <div class="metric-label">预计毛利(元)</div>
          </div>
        </div>
      </div>

      <!-- 预计营收 -->
      <div class="revenue-section">
        <span class="revenue-label">预计营收:</span>
        <span class="revenue-value">{{ formatCurrency(currentData.expectedRevenue) }}</span>
        <span class="revenue-ratio">毛利率 {{ ((currentData.expectedProfit / currentData.expectedRevenue) * 100).toFixed(1) }}%</span>
      </div>

      <!-- 成本明细表格 -->
      <div class="section">
        <h3 class="section-title">💵 成本明细</h3>
        <el-table :data="currentData.costBreakdown" border stripe>
          <el-table-column prop="category" label="类别" width="120" />
          <el-table-column prop="amount" label="金额(元)" width="130">
            <template #default="{ row }">
              {{ formatCurrency(row.amount) }}
            </template>
          </el-table-column>
          <el-table-column prop="manDays" label="人天" width="80" align="center" />
          <el-table-column prop="note" label="说明" />
        </el-table>
      </div>

      <!-- 历史对比图 -->
      <div class="section chart-section" v-if="currentData.historyComparison?.length">
        <h3 class="section-title">历史项目对比</h3>
        <div class="chart-container">
          <BarChart
            ref="chartRef"
            :option="historyChartOption"
            height="280px"
            @chart-click="handleChartClick"
          />
        </div>
      </div>
    </div>

    <div v-else class="no-data">
      <el-empty description="暂无ROI分析数据" />
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="primary" @click="handleExport">导出报告</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import BarChart from '@/components/charts/BarChart.vue'
import { aiApi } from '@/api'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  projectId: {
    type: String,
    default: ''
  },
  data: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['update:modelValue', 'export'])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const chartRef = ref(null)
const loadedData = ref(null)

// 对话框打开后调整图表大小
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    nextTick(() => {
      setTimeout(() => {
        chartRef.value?.resize()
      }, 100)
    })
  }
})

function normalizeRoiView(payload) {
  if (!payload) return null

  if (payload.totalCost !== undefined || payload.expectedRevenue !== undefined) {
    return {
      totalManDays: Number(payload.totalManDays || 0),
      totalCost: Number(payload.totalCost || 0),
      winRate: Number(payload.winRate || payload.profitMargin || 0),
      winRateLabel: payload.winRate !== undefined ? '预计赢面' : '毛利率',
      expectedProfit: Number(payload.expectedProfit || payload.netProfit || 0),
      expectedRevenue: Number(payload.expectedRevenue || payload.estimatedRevenue || 0),
      costBreakdown: Array.isArray(payload.costBreakdown)
        ? payload.costBreakdown.map((item) => ({
            category: item.category,
            amount: Number(item.amount || 0),
            manDays: Number(item.manDays || 0),
            note: item.note || item.description || '',
          }))
        : [],
      historyComparison: Array.isArray(payload.historyComparison)
        ? payload.historyComparison
        : Array.isArray(payload.comparison)
          ? payload.comparison.map((item) => ({
              name: item.name,
              manDays: Number(item.manDays || 0),
              cost: Number(item.cost || 0),
              winRate: Number(item.winRate || item.roi || 0),
              result: item.result || '',
            }))
          : [],
    }
  }

  return {
    totalManDays: 0,
    totalCost: Number(payload.estimatedCost || 0),
    winRate: Number(payload.roiPercentage || 0),
    winRateLabel: 'ROI',
    expectedProfit: Number(payload.estimatedProfit || 0),
    expectedRevenue: Number(payload.estimatedRevenue || 0),
    costBreakdown: [
      {
        category: '预估成本',
        amount: Number(payload.estimatedCost || 0),
        manDays: 0,
        note: payload.assumptions || '后端暂未返回成本拆分明细',
      },
    ],
    historyComparison: [],
  }
}

const currentData = computed(() => {
  return loadedData.value || normalizeRoiView(props.data) || null
})

const historyChartOption = computed(() => {
  if (!currentData.value?.historyComparison) return {}

  const history = currentData.value.historyComparison
  const currentProject = {
    name: '本项目',
    manDays: currentData.value.totalManDays,
    cost: currentData.value.totalCost,
    winRate: currentData.value.winRate,
    result: '进行中'
  }

  const allProjects = [...history, currentProject]

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    legend: {
      data: ['人天', '成本(万元)', '赢面(%)'],
      bottom: 0
    },
    xAxis: {
      type: 'category',
      data: allProjects.map(p => p.name)
    },
    yAxis: [
      { type: 'value', name: '人天', position: 'left' },
      { type: 'value', name: '赢面(%)', position: 'right', max: 100 }
    ],
    series: [
      {
        name: '人天',
        type: 'bar',
        data: allProjects.map(p => p.manDays),
        itemStyle: { color: '#409EFF' },
        yAxisIndex: 0
      },
      {
        name: '成本(万元)',
        type: 'bar',
        data: allProjects.map(p => (p.cost / 10000).toFixed(1)),
        itemStyle: { color: '#67C23A' },
        yAxisIndex: 0
      },
      {
        name: '赢面(%)',
        type: 'line',
        data: allProjects.map(p => p.winRate),
        itemStyle: { color: '#E6A23C' },
        yAxisIndex: 1
      }
    ]
  }
})

const formatCurrency = (value) => {
  return '¥' + Number(value || 0).toLocaleString('zh-CN')
}

const getWinRateClass = (rate) => {
  if (rate >= 70) return 'high'
  if (rate >= 50) return 'medium'
  return 'low'
}

const handleChartClick = (params) => {
  // 图表点击事件处理
  emit('chart-click', params)
}

const handleClose = () => {
  dialogVisible.value = false
}

const handleExport = () => {
  emit('export', currentData.value)
}

const loadData = async () => {
  if (!props.projectId) return

  const response = await aiApi.roi.getAnalysis(props.projectId)
  if (response?.success && response.data) {
    loadedData.value = normalizeRoiView(response.data)
    return
  }

  loadedData.value = normalizeRoiView(props.data)
}

watch(
  () => props.projectId,
  () => {
    loadedData.value = null
  }
)

watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal) {
      loadData()
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.roi-analysis {
  padding: 10px 0;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.metric-card {
  display: flex;
  align-items: center;
  padding: 16px;
  background: var(--bg-subtle);
  border-radius: var(--card-border-radius, 8px);
  border: var(--card-border, 1px solid var(--gray-100));
  transition: all 0.25s ease;
}

.metric-card:hover {
  box-shadow: var(--card-shadow-hover, 0 6px 16px rgba(0, 0, 0, 0.12));
}

.metric-icon {
  font-size: 32px;
  margin-right: 12px;
}

.metric-content {
  flex: 1;
}

.metric-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
}

.metric-value.high {
  color: var(--color-success);
}

.metric-value.medium {
  color: var(--color-warning);
}

.metric-value.low {
  color: var(--color-danger, #DD2200);
}

.metric-value.profit {
  color: var(--color-primary, var(--brand-primary));
}

.metric-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}

.revenue-section {
  display: flex;
  align-items: center;
  padding: 16px;
  background: #f0f9ff;
  border-radius: var(--card-border-radius, 8px);
  margin-bottom: 20px;
  border-left: 4px solid var(--color-primary, var(--brand-primary));
}

.revenue-label {
  font-size: 14px;
  color: var(--text-secondary-ui);
  margin-right: 8px;
}

.revenue-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-primary, var(--brand-primary));
  margin-right: 16px;
}

.revenue-ratio {
  font-size: 14px;
  color: var(--color-success);
  background: #f0f9ff;
  padding: 4px 12px;
  border-radius: 12px;
}

.section {
  margin-bottom: 24px;
}

.chart-section {
  width: 100%;
}

.chart-container {
  width: 100%;
  min-width: 0;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-750);
  margin-bottom: 12px;
  display: flex;
  align-items: center;
}

.no-data {
  text-align: center;
  padding: 40px 0;
}

:deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-table th) {
  background-color: var(--bg-subtle);
}
</style>
