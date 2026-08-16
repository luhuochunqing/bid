<template>
  <div class="data-analysis">
    <div v-if="initialLoading" class="page-loading">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>加载数据中...</p>
    </div>

    <template v-else>
      <div class="page-header">
        <h1>数据分析</h1>
        <div class="global-date-filter">
          <el-date-picker
            v-model="globalDateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            size="default"
            :disabled="refreshing"
          />
          <button class="confirm-btn" :disabled="refreshing" @click="handleGlobalDateChange">确认</button>
          <button class="confirm-btn reset" :disabled="refreshing" @click="handleGlobalDateReset">重置</button>
        </div>
      </div>

      <div class="section" id="m0">
        <div class="section-title">
          <span class="label">关键指标</span>
        </div>
        <M0KpiCards
          :kpi-cards="kpiCards"
          :loading="m0Loading"
          :error="m0Error"
          @retry="loadM0Data"
        />
      </div>

      <div class="section" id="m1">
        <div class="section-title">
          <span class="label">多维度趋势分析</span>
        </div>
        <M1TrendAnalysis :date-range="globalDateRange" />
      </div>

      <div class="section section-pie" id="m2m3">
        <div class="pie-row">
          <div class="pie-col">
            <M2CustomerType :date-range="globalDateRange" />
          </div>
          <div class="pie-col">
            <M3ProjectType :date-range="globalDateRange" />
          </div>
        </div>
      </div>

      <div class="section section-m4" id="m4">
        <M4Competitor />
      </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { useAnalyticsData } from './composables/useAnalyticsData.js'
import M0KpiCards from './components/m0-kpi-cards/index.vue'
import M1TrendAnalysis from './components/m1-trend-analysis/index.vue'
import M2CustomerType from './components/m2-customer-type/index.vue'
import M3ProjectType from './components/m3-project-type/index.vue'
import M4Competitor from './components/m4-competitor/index.vue'

const {
  globalDateRange,
  initialLoading, refreshing,
  m0Loading, m0Error,
  kpiCards,
  loadM0Data, loadAllData,
  handleGlobalDateChange, handleGlobalDateReset
} = useAnalyticsData()

onMounted(() => {
  loadAllData()
})
</script>

<style scoped>
.data-analysis { padding: 24px; background: var(--bg-subtle); min-height: 100vh; }

.page-loading {
  display: flex; flex-direction: column; align-items: center;
  justify-content: center; min-height: 60vh; color: #94A3B8;
}
.page-loading .el-icon { font-size: 32px; color: var(--brand-xiyu-logo); margin-bottom: 16px; }
.page-loading p { font-size: 14px; margin: 0; }

.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h1 { font-size: 20px; font-weight: 700; color: #1E293B; margin: 0; }

.global-date-filter { display: flex; gap: 10px; align-items: center; }

.confirm-btn {
  padding: 8px 20px; border: none; border-radius: 6px;
  background: var(--brand-xiyu-logo); color: var(--bg-card);
  font-size: 14px; font-weight: 500; cursor: pointer;
  transition: background 0.2s; height: 32px;
}
.confirm-btn:hover:not(:disabled) { background: var(--brand-xiyu-logo-hover); }
.confirm-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.confirm-btn.reset { background: var(--bg-card); color: #475569; border: 1px solid #E2E8F0; }
.confirm-btn.reset:hover:not(:disabled) { background: var(--bg-subtle); color: #1E293B; }

.section {
  background: var(--bg-card); border-radius: 12px; padding: 22px 28px;
  margin-bottom: 20px; box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  border: 1px solid #E2E8F0; transition: box-shadow 0.25s;
}
.section:hover { box-shadow: 0 4px 6px -1px rgba(15, 23, 42, 0.07), 0 2px 4px -2px rgba(15, 23, 42, 0.05); }

.section-title {
  font-size: 16px; font-weight: 700; color: #1E293B; margin-bottom: 18px;
  display: flex; align-items: center; justify-content: space-between;
}
.section-title .label { display: flex; align-items: center; gap: 10px; }
.section-title .label::before {
  content: ''; width: 4px; height: 20px;
  background: var(--brand-xiyu-logo); border-radius: 2px;
}

.pie-row { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }

.pie-col {
  background: var(--bg-card); border-radius: 12px; border: 1px solid #E2E8F0;
  padding: 22px 24px; box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  transition: box-shadow 0.25s; min-width: 0;
}
.pie-col:hover { box-shadow: 0 4px 6px -1px rgba(15, 23, 42, 0.07), 0 2px 4px -2px rgba(15, 23, 42, 0.05); }

/* M2/M3 组件内部样式由 pie-col 提供，重置避免双重卡片 */
.section-pie :deep(.m2-customer-type),
.section-pie :deep(.m3-project-type) { background: transparent; border: none; box-shadow: none; padding: 0; }

/* M4 组件内部由 section 提供卡片背景，避免双重背景 */
.section-m4 :deep(.m4-competitor) { background: transparent; border: none; box-shadow: none; padding: 0; }

@media (max-width: 1400px) { .pie-row { grid-template-columns: 1fr; } }

@media (max-width: 768px) {
  .data-analysis { padding: 16px; }
  .page-header { flex-direction: column; align-items: flex-start; gap: 12px; }
  .global-date-filter { width: 100%; flex-wrap: wrap; }
  .global-date-filter .el-date-picker { flex: 1; min-width: 200px; }
}
</style>
