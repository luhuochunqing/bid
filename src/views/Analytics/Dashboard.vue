<template>
  <div class="analytics-dashboard">
    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>加载数据中...</p>
    </div>

    <div v-else class="page-header">
      <h2 class="page-title">数据分析</h2>
      <div class="header-actions">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          size="default"
          @change="handleDateChange"
        />
        <el-button type="primary" :icon="Refresh" @click="refreshData">刷新</el-button>
        <el-button :icon="Download" @click="exportData">导出</el-button>
      </div>
    </div>

    <div class="metric-cards">
      <div
        v-for="metric in metrics"
        :key="metric.key"
        class="b2b-metric-card"
        :class="'metric-' + getMetricColorClass(metric.key)"
        @click="handleMetricOverviewClick(metric.key)"
      >
        <div class="b2b-metric-content">
          <div class="b2b-metric-label">{{ metric.label }}</div>
          <div class="b2b-metric-value">{{ metric.value }}</div>
          <div class="b2b-metric-trend" :class="getTrendClass(metric.trendDirection)">
            <span class="trend-value">{{ metric.change }}</span>
            <span class="trend-label">较上月</span>
          </div>
        </div>
      </div>
    </div>

    <div class="charts-row">
      <div class="chart-card">
        <div class="chart-header">
          <h3 class="chart-title">中标率趋势</h3>
          <el-radio-group v-model="trendPeriod" size="small">
            <el-radio-button value="month">月度</el-radio-button>
            <el-radio-button value="quarter">季度</el-radio-button>
            <el-radio-button value="year">年度</el-radio-button>
          </el-radio-group>
        </div>
        <LineChart :option="trendChartOption" height="300px" @chart-click="chartDrillDown.handleTrendClick" />
      </div>

      <div class="chart-card">
        <div class="chart-header">
          <h3 class="chart-title">竞争对手分析</h3>
          <el-tag size="small" type="info">市场份额</el-tag>
        </div>
        <PieChart :option="competitorChartOption" height="300px" @chart-click="chartDrillDown.handleCompetitorClick" />
      </div>
    </div>

    <div class="charts-row">
      <div class="chart-card chart-card-large">
        <div class="chart-header">
          <h3 class="chart-title">投入产出分析（按产品线）</h3>
          <el-radio-group v-model="productMetric" size="small">
            <el-radio-button value="revenue">收入</el-radio-button>
            <el-radio-button value="rate">中标率</el-radio-button>
            <el-radio-button value="roi">ROI</el-radio-button>
          </el-radio-group>
        </div>
        <BarChart :option="productChartOption" height="300px" @chart-click="handleProductClick" />
      </div>
    </div>

    <CustomerTypePanel
      :date-range="dateRange"
      :refresh-key="customerTypeRefreshKey"
      class="dashboard-section"
    />

    <div class="charts-row">
      <div class="chart-card chart-card-large">
        <div class="chart-header">
          <h3 class="chart-title">区域分布</h3>
          <el-radio-group v-model="regionView" size="small">
            <el-radio-button value="amount">金额</el-radio-button>
            <el-radio-button value="bids">投标数</el-radio-button>
            <el-radio-button value="rate">中标率</el-radio-button>
          </el-radio-group>
        </div>
        <BarChart :option="regionChartOption" height="280px" @chart-click="chartDrillDown.handleRegionClick" />
      </div>
    </div>

    <ChartDrillDownDialog
      v-model="chartDrillDown.dialogVisible.value"
      :title="chartDrillDown.dialogTitle.value"
      :data="chartDrillDown.dialogData.value"
      @go-to-project="goToProject"
      @preview-file="chartDrillDown.previewFile"
      @download-file="chartDrillDown.downloadFile"
      @export="exportDrillDownData"
    />

    <FilePreviewDialog
      v-model="chartDrillDown.previewFileDialogVisible.value"
      :file-name="chartDrillDown.previewFileName.value"
      :file-url="chartDrillDown.previewFileUrl.value"
      @close="chartDrillDown.previewFileUrl.value = ''"
      @download="chartDrillDown.downloadFile"
    />

    <MetricDrillDownDrawer
      v-model="metricDrillDown.drawerVisible.value"
      :title="metricDrillDown.drawerTitle.value"
      :drawer-type="metricDrillDown.drawerType.value"
      :loading="metricDrillDown.loading.value"
      :placeholder="metricDrillDown.placeholder.value"
      :items="metricDrillDown.items.value"
      :summary="metricDrillDown.summary.value"
      :dimensions="metricDrillDown.dimensions.value"
      :filter-values="metricDrillDown.filterValues.value"
      :columns="metricDrillDown.columns.value"
      :pagination="metricDrillDown.pagination.value"
      :has-row-action="metricDrillDown.hasRowAction.value"
      @close="metricDrillDown.handleClose"
      @filter-change="metricDrillDown.handleFilterChange"
      @reload="metricDrillDown.reload"
      @page-change="metricDrillDown.handlePageChange"
      @row-action="metricDrillDown.handleRowAction"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Download, Loading } from '@element-plus/icons-vue'
import LineChart from '@/components/charts/LineChart.vue'
import PieChart from '@/components/charts/PieChart.vue'
import BarChart from '@/components/charts/BarChart.vue'
import CustomerTypePanel from './components/CustomerTypePanel.vue'
import ChartDrillDownDialog from './dashboard/components/ChartDrillDownDialog.vue'
import FilePreviewDialog from './dashboard/components/FilePreviewDialog.vue'
import MetricDrillDownDrawer from './dashboard/components/MetricDrillDownDrawer.vue'
import { useDashboardData } from './dashboard/composables/useDashboardData.js'
import { useChartDrillDown } from './dashboard/composables/useChartDrillDown.js'
import { useMetricDrillDown } from './dashboard/composables/useMetricDrillDown.js'
import {
  buildTrendOption,
  buildCompetitorOption,
  buildProductOption,
  buildRegionOption
} from './dashboard/utils/chartOptions.js'
import {
  formatAmount,
  getMetricColorClass,
  getTrendClass
} from './dashboard/utils/dashboardFormatters.js'
import { useExport } from '@/composables/useExport'
import { ExportType } from '@/api'

const router = useRouter()
const route = useRoute()

const dateRange = ref([])
const trendPeriod = ref('month')
const productMetric = ref('revenue')
const regionView = ref('amount')
const customerTypeRefreshKey = ref(0)

const { loading, dashboardData, pageFeaturePlaceholders, loadData } = useDashboardData()
const chartDrillDown = useChartDrillDown({ dashboardData })
const metricDrillDown = useMetricDrillDown({ route, router, dateRange })

const metrics = computed(() => {
  if (!dashboardData.value) return []

  const getTrendDirection = (val) => {
    if (val === '--') return 'trend-neutral'
    return String(val).startsWith('+') ? 'trend-up' : 'trend-down'
  }

  return [
    {
      key: 'bids',
      label: '年度投标数',
      value: dashboardData.value.totalBids,
      change: dashboardData.value.totalBidsChange,
      trendDirection: getTrendDirection(dashboardData.value.totalBidsChange)
    },
    {
      key: 'winRate',
      label: '中标率',
      value: dashboardData.value.winRate + '%',
      change: dashboardData.value.winRateChange,
      trendDirection: getTrendDirection(dashboardData.value.winRateChange)
    },
    {
      key: 'amount',
      label: '中标金额',
      value: formatAmount(dashboardData.value.totalAmount),
      change: dashboardData.value.totalAmountChange,
      trendDirection: getTrendDirection(dashboardData.value.totalAmountChange)
    },
    {
      key: 'cost',
      label: '投入费用',
      value: formatAmount(dashboardData.value.totalCost),
      change: dashboardData.value.totalCostChange,
      trendDirection: getTrendDirection(dashboardData.value.totalCostChange)
    }
  ]
})

const productLinesPlaceholder = computed(() => pageFeaturePlaceholders.value.productLines || null)

const trendChartOption = computed(() => (dashboardData.value ? buildTrendOption(dashboardData.value.trendData) : {}))
const competitorChartOption = computed(() => (dashboardData.value ? buildCompetitorOption(dashboardData.value.competitors) : {}))
const productChartOption = computed(() => (dashboardData.value ? buildProductOption(dashboardData.value.productLines, productMetric.value) : {}))
const regionChartOption = computed(() => (dashboardData.value ? buildRegionOption(dashboardData.value.regionData, regionView.value) : {}))

function handleMetricOverviewClick(metricKey) {
  metricDrillDown.handleOverviewClick(metricKey)
}

function handleProductClick(params) {
  if (productLinesPlaceholder.value) {
    ElMessage.info('当前版本暂不开放产品线分析')
    return
  }
  chartDrillDown.handleProductClick(params)
}

function goToProject(projectId) {
  chartDrillDown.dialogVisible.value = false
  router.push({ name: 'ProjectDetail', params: { id: projectId } })
}

function handleDateChange() {
  if (metricDrillDown.drawerVisible.value && metricDrillDown.drawerType.value) {
    metricDrillDown.paginationState.value = { ...metricDrillDown.paginationState.value, page: 1 }
    metricDrillDown.openDrillDown(metricDrillDown.drawerType.value, { resetPaging: false })
  }
  ElMessage.info('日期范围已更新')
}

async function refreshData() {
  await loadData()
  customerTypeRefreshKey.value += 1
  ElMessage.success('数据已刷新')
}

function exportData() {
  const { exportExcel } = useExport()
  const params = {
    startDate: dateRange.value?.[0] || null,
    endDate: dateRange.value?.[1] || null
  }
  exportExcel(ExportType.DASHBOARD_OVERVIEW, params, '数据看板导出成功')
}

function exportDrillDownData() {
  const { exportExcel } = useExport()
  const { type } = chartDrillDown.currentContext.value || {}
  const params = {
    metricType: type,
    startDate: dateRange.value?.[0] || null,
    endDate: dateRange.value?.[1] || null,
    status: metricDrillDown.filterValues.value?.status || null,
    role: metricDrillDown.filterValues.value?.role || null
  }
  exportExcel(ExportType.DASHBOARD_DRILLDOWN, params, '数据明细导出成功')
}

watch(
  () => [route.query.drilldown, route.query.status, route.query.role, route.query.outcome],
  async ([drilldown, status, role, outcome]) => {
    if (!drilldown || loading.value) return

    const nextFilters = {}
    if (status) nextFilters.status = String(status).toUpperCase()
    if (role) nextFilters.role = String(role).toUpperCase()
    if (outcome) nextFilters.outcome = String(outcome).toUpperCase()

    await metricDrillDown.openDrillDown(String(drilldown), { filters: nextFilters })
  }
)

onMounted(async () => {
  await loadData()
  if (route.query.drilldown) {
    const nextFilters = {}
    if (route.query.status) nextFilters.status = String(route.query.status).toUpperCase()
    if (route.query.role) nextFilters.role = String(route.query.role).toUpperCase()
    if (route.query.outcome) nextFilters.outcome = String(route.query.outcome).toUpperCase()
    await metricDrillDown.openDrillDown(String(route.query.drilldown), { filters: nextFilters })
  }
})
</script>

<style scoped>
.analytics-dashboard {
  padding: 20px;
  background-color: var(--bg-subtle);
  min-height: 100%;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  color: var(--text-muted);
}

.loading-container .el-icon {
  font-size: 32px;
  color: #409eff;
  margin-bottom: 16px;
}

.loading-container p {
  font-size: 14px;
  margin: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.metric-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 20px;
}

.b2b-metric-card {
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.b2b-metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
}

.charts-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  margin-bottom: 20px;
}

.dashboard-section {
  margin-bottom: 20px;
}

.chart-card-large {
  grid-column: 1 / -1;
}

.chart-card {
  background: var(--bg-card);
  border-radius: var(--card-border-radius, 8px);
  padding: var(--card-padding, 20px);
  box-shadow: var(--card-shadow, 0 1px 3px rgba(0, 0, 0, 0.08), 0 1px 2px rgba(0, 0, 0, 0.04));
  border: var(--card-border, 1px solid #E8E8E8);
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

@media (max-width: 1400px) {
  .metric-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .metric-cards {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .charts-row {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .chart-card {
    min-height: 250px;
  }
}

@media (hover: none) and (pointer: coarse) {
  .el-button { min-height: 44px; }
}
</style>
