<template>
  <div class="data-analysis">
    <div v-if="initialLoading" class="page-loading">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>加载数据中...</p>
    </div>

    <template v-else>
      <div class="page-header">
        <h2 class="page-title">数据分析</h2>
        <div class="header-actions">
          <el-date-picker
            v-model="globalDateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            size="default"
            :disabled="refreshing"
            @change="handleGlobalDateChange"
          />
          <el-button
            type="primary"
            :icon="Refresh"
            :loading="refreshing"
            @click="handleRefresh"
          >刷新</el-button>
        </div>
      </div>

      <section class="section section-m0">
        <M0KpiCards
          :kpi-cards="kpiCards"
          :loading="m0Loading"
          :error="m0Error"
          @retry="loadM0Data"
        />
      </section>

      <section class="section section-m1">
        <M1TrendAnalysis
          :filters="trendFilters"
          :chart-option="trendChartOption"
          :loading="m1Loading"
          :chart-loading="m1ChartLoading"
          :drill-data="trendDrillData"
          :drill-loading="trendDrillLoading"
          @update:filters="handleTrendFilterChange"
          @drill="handleTrendDrill"
        />
      </section>

      <section class="section section-m2m3">
        <div class="split-row">
          <div class="split-col">
            <M2CustomerType
              :data="customerTypeData"
              :loading="m2Loading"
              :error="m2Error"
              @retry="loadM2Data"
            />
          </div>
          <div class="split-col">
            <M3ProjectType
              :data="projectTypeData"
              :loading="m3Loading"
              :error="m3Error"
              @retry="loadM3Data"
            />
          </div>
        </div>
      </section>

      <section class="section section-m4">
        <M4Competitor
          :date-range="m4DateRange"
          :data="competitorData"
          :loading="m4Loading"
          :error="m4Error"
          @update:date-range="handleM4DateChange"
          @refresh="loadM4Data"
          @retry="loadM4Data"
        />
      </section>
    </template>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { Refresh, Loading } from '@element-plus/icons-vue'
import { useAnalyticsData } from './composables/useAnalyticsData.js'
import M0KpiCards from './components/m0-kpi-cards/index.vue'
import M1TrendAnalysis from './components/m1-trend-analysis/index.vue'
import M2CustomerType from './components/m2-customer-type/index.vue'
import M3ProjectType from './components/m3-project-type/index.vue'
import M4Competitor from './components/m4-competitor/index.vue'

const {
  globalDateRange, m4DateRange,
  initialLoading, refreshing,
  m0Loading, m0Error, m1Loading, m1ChartLoading, m1Error,
  m2Loading, m2Error, m3Loading, m3Error, m4Loading, m4Error,
  trendDrillLoading,
  kpiCards, customerTypeData, projectTypeData, competitorData,
  trendDrillData, trendChartOption, trendFilters,
  loadM0Data, loadM1Data, loadM2Data, loadM3Data, loadM4Data, loadAllData,
  handleGlobalDateChange, handleM4DateChange, handleRefresh,
  handleTrendFilterChange, handleTrendDrill
} = useAnalyticsData()

onMounted(() => {
  loadAllData()
})
</script>

<style scoped>
.data-analysis {
  padding: 24px;
  background: var(--bg-subtle, #F5F7FA);
  min-height: 100vh;
}

.page-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  color: var(--text-muted, #94A3B8);
}

.page-loading .el-icon {
  font-size: 32px;
  color: var(--brand-xiyu-logo, #2E7659);
  margin-bottom: 16px;
}

.page-loading p {
  font-size: 14px;
  margin: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary, #1E293B);
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.section {
  margin-bottom: 24px;
}

.split-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.split-col {
  min-width: 0;
}

@media (max-width: 1400px) {
  .split-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .data-analysis {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .header-actions .el-date-picker {
    flex: 1;
    min-width: 200px;
  }
}

@media (hover: none) and (pointer: coarse) {
  .el-button {
    min-height: 44px;
  }
}
</style>