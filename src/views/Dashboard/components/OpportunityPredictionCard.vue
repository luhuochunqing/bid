<template>
  <div class="rebuild-ai-card opportunity-prediction-card">
    <div class="rebuild-card-header">
      <div class="rebuild-card-title">
        <span class="icon"></span>AI 商机预测
        <span class="op-subtitle">基于历史项目、投标项目、标讯数据等，预测下一轮招标时间，只显示近 6 个月商机</span>
      </div>
      <div class="op-legend">
        <span class="op-legend-dot"></span>3 个月内
      </div>
    </div>

    <div class="op-filters">
      <el-select v-model="customerTypeFilter" placeholder="客户类型" clearable size="small" class="op-filter-select">
        <el-option v-for="opt in customerTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-select v-model="projectTypeFilter" placeholder="项目类型" clearable size="small" class="op-filter-select">
        <el-option v-for="opt in projectTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-date-picker
        v-model="predictedDateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="—"
        start-placeholder="预测开始"
        end-placeholder="预测结束"
        size="small"
        class="op-filter-date"
      />
      <el-button v-if="hasActiveFilters" link type="primary" size="small" @click="resetFilters">重置</el-button>
    </div>

    <div class="op-table-wrap" v-loading="loading">
      <OpportunityPredictionTable
        v-if="loading || filteredRows.length > 0"
        :rows="pagedRows"
        :start-index="(currentPage - 1) * PAGE_SIZE"
      />

      <div v-if="!loading && filteredRows.length === 0" class="op-empty">
        <el-empty :description="emptyText" :image-size="72" />
      </div>
    </div>

    <div v-if="filteredRows.length > PAGE_SIZE" class="op-pager">
      <span class="op-pager-total">共 <b>{{ filteredRows.length }}</b> 条</span>
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="PAGE_SIZE"
        :total="filteredRows.length"
        layout="prev, pager, next"
        small
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { workbenchApi } from '@/api'
import OpportunityPredictionTable from '@/views/Dashboard/components/OpportunityPredictionTable.vue'
import { customerTypeOptions, projectTypeOptions } from '@/views/Project/composables/useProjectSearch.js'

const PAGE_SIZE = 5

const loading = ref(false)
const rows = ref([])
const currentPage = ref(1)

// 筛选条件：客户类型 / 项目类型（枚举码，与项目列表搜索同源）+ 预测招标时间范围。
// 数据源本身就是一次拉全量（近 6 个月窗口），行数有限，沿用客户端过滤+分页。
const customerTypeFilter = ref('')
const projectTypeFilter = ref('')
const predictedDateRange = ref(null)

const filteredRows = computed(() =>
  rows.value.filter((row) => {
    if (customerTypeFilter.value && row.customerType !== customerTypeFilter.value) return false
    if (projectTypeFilter.value && row.projectType !== projectTypeFilter.value) return false
    const [start, end] = predictedDateRange.value || []
    // yyyy-MM-dd 字符串字典序即时间序；无预测时间的行只在不限时间时保留
    if (start && (!row.predictedBidDate || row.predictedBidDate < start)) return false
    if (end && (!row.predictedBidDate || row.predictedBidDate > end)) return false
    return true
  }),
)

watch([customerTypeFilter, projectTypeFilter, predictedDateRange], () => {
  currentPage.value = 1
})

const hasActiveFilters = computed(
  () => Boolean(customerTypeFilter.value || projectTypeFilter.value || predictedDateRange.value?.length),
)

function resetFilters() {
  customerTypeFilter.value = ''
  projectTypeFilter.value = ''
  predictedDateRange.value = null
}

const emptyText = computed(() =>
  hasActiveFilters.value ? '无匹配商机，请调整筛选条件' : '暂无近 6 个月商机预测',
)

const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  return filteredRows.value.slice(start, start + PAGE_SIZE)
})

async function loadPredictions() {
  loading.value = true
  try {
    const res = await workbenchApi.getOpportunityPredictions()
    rows.value = res.success ? res.data : []
  } finally {
    loading.value = false
  }
}

onMounted(loadPredictions)
</script>

<style scoped>
.opportunity-prediction-card {
  display: flex;
  flex-direction: column;
}
.op-subtitle {
  font-size: 0.76rem;
  color: var(--text-muted);
  font-weight: 400;
  margin-left: 0.35rem;
}
.op-legend {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.76rem;
  color: var(--text-muted);
  flex-shrink: 0;
}
.op-legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-danger, #f56c6c);
}
.op-filters {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 1.4rem 0.6rem;
}
.op-filter-select {
  width: 130px;
}
.op-filter-date {
  width: 240px;
}
.op-table-wrap {
  overflow-x: auto;
  min-height: 120px;
}
.op-empty { padding: 0.5rem 1.4rem; }
.op-pager {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1.4rem;
  border-top: 1px solid var(--gray-100);
}
.op-pager-total {
  font-size: 0.8rem;
  color: var(--text-muted);
}
.op-pager-total b {
  color: var(--brand-xiyu-logo);
  font-weight: 600;
}
</style>
