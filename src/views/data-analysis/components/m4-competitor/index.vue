<template>
  <div class="m4-competitor">
    <div class="card-header">
      <span class="card-title">竞品分析</span>
    </div>

    <div class="filter-area">
      <div class="filter-row">
        <div class="filter-item">
          <label class="filter-label"><span class="required-star">*</span>竞品公司</label>
          <el-select
            v-model="selectedCompetitors"
            multiple
            filterable
            remote
            :remote-method="searchCompetitors"
            :loading="searchLoading"
            placeholder="请选择竞品公司"
            class="filter-select"
            @change="onCompetitorChange"
          >
            <el-option v-for="item in competitorOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </div>
        <div class="filter-item">
          <label class="filter-label">招标主体</label>
          <el-select
            v-model="selectedEntities"
            multiple
            filterable
            remote
            :remote-method="searchEntities"
            :loading="entityLoading"
            placeholder="请选择招标主体（可选）"
            class="filter-select"
            clearable
            @change="onEntityChange"
          >
            <el-option v-for="item in entityOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </div>
        <div class="filter-item">
          <label class="filter-label">日期范围</label>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            class="filter-date"
          />
        </div>
        <div class="filter-item filter-action">
          <el-button type="primary" :loading="loading" @click="fetchData">查询</el-button>
        </div>
      </div>
    </div>

    <div class="chart-body">
      <div v-if="loading" class="status-overlay">
        <el-skeleton animated :count="1" style="padding: 20px;">
          <template #template>
            <div style="display: flex; gap: 16px; padding: 40px 20px;">
              <el-skeleton-item variant="rect" style="width: 60px; height: 200px;" />
              <el-skeleton-item variant="rect" style="width: 60px; height: 160px;" />
              <el-skeleton-item variant="rect" style="width: 60px; height: 180px;" />
              <el-skeleton-item variant="rect" style="width: 60px; height: 140px;" />
              <el-skeleton-item variant="rect" style="width: 60px; height: 200px;" />
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
import { ref, onMounted, onUnmounted } from 'vue'
import { useCompetitorData } from './composables/useCompetitorData.js'

const chartRef = ref(null)

const {
  loading, error, noData, searchLoading, entityLoading,
  dateRange, selectedCompetitors, selectedEntities,
  competitorOptions, entityOptions, allCompetitors,
  searchCompetitors, searchEntities,
  onCompetitorChange, onEntityChange,
  fetchData, initOptions, resizeChart, disposeChart
} = useCompetitorData(chartRef)

onMounted(async () => {
  await initOptions()
  if (selectedCompetitors.value.length === 0 && allCompetitors.value.length > 0) {
    selectedCompetitors.value = [allCompetitors.value[0]]
  }
  fetchData()
  window.addEventListener('resize', resizeChart)
})

onUnmounted(() => {
  disposeChart()
  window.removeEventListener('resize', resizeChart)
})

defineExpose({ refresh: fetchData })
</script>

<style scoped>
.m4-competitor {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  padding: var(--space-5);
  height: 100%;
  display: flex;
  flex-direction: column;
}

.card-header { margin-bottom: var(--space-4); }

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.filter-area {
  margin-bottom: var(--space-4);
  padding: var(--space-4);
  background: var(--bg-subtle);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 180px;
  flex: 1;
}

.filter-action { flex: 0 0 auto; min-width: auto; }

.filter-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
  white-space: nowrap;
}

.required-star { color: var(--color-danger); margin-right: 2px; }
.filter-select { width: 100%; }
.filter-date { width: 100%; }

.chart-body {
  flex: 1;
  min-height: 0;
  position: relative;
}

.chart-container {
  width: 100%;
  height: 100%;
  min-height: 380px;
}

.status-overlay {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 380px;
}
</style>