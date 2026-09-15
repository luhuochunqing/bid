<template>
  <div class="rebuild-ai-card opportunity-prediction-card">
    <div class="rebuild-card-header">
      <div class="rebuild-card-title">
        <span class="icon"></span>AI 商机预测
        <span class="op-subtitle">基于历史项目、投标项目、标讯数据等，预测下一轮招标时间，只显示近 1 年商机</span>
      </div>
      <div class="op-legend">
        <span class="op-legend-dot"></span>3 个月内
      </div>
    </div>

    <div class="op-filters">
      <el-input
        v-model="projectNameFilter"
        placeholder="项目名称"
        clearable
        size="small"
        class="op-filter-input"
      />
      <el-input
        v-model="customerFilter"
        placeholder="招标主体"
        clearable
        size="small"
        class="op-filter-input"
      />
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
      <div class="op-filter-actions">
        <el-button size="small" :icon="Download" @click="handleExport">导出</el-button>
      </div>
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
import { Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { workbenchApi } from '@/api'
import { exportApi, notifyExportSuccess } from '@/api/modules/export.js'
import OpportunityPredictionTable from '@/views/Dashboard/components/OpportunityPredictionTable.vue'
import { customerTypeOptions, projectTypeOptions } from '@/views/Project/composables/useProjectSearch.js'
import { projectStatusLabel } from '@/views/Dashboard/workbench-utils.js'
import { customerTypeLabel, projectTypeLabel } from '@/views/Project/utils/projectListFormatters.js'

const PAGE_SIZE = 5

const loading = ref(false)
const rows = ref([])
const currentPage = ref(1)

// 筛选条件：项目名称 / 招标主体（模糊匹配）+ 客户类型 / 项目类型（枚举码）+ 预测招标时间范围。
// 数据源一次性拉取全量（近 1 年窗口），沿用客户端过滤+分页。
const projectNameFilter = ref('')
const customerFilter = ref('')
const customerTypeFilter = ref('')
const projectTypeFilter = ref('')
const predictedDateRange = ref(null)

const filteredRows = computed(() =>
  rows.value.filter((row) => {
    if (projectNameFilter.value?.trim()) {
      const q = projectNameFilter.value.trim().toLowerCase()
      if (!row.projectName || !row.projectName.toLowerCase().includes(q)) return false
    }
    if (customerFilter.value?.trim()) {
      const q = customerFilter.value.trim().toLowerCase()
      if (!row.customer || !row.customer.toLowerCase().includes(q)) return false
    }
    if (customerTypeFilter.value && row.customerType !== customerTypeFilter.value) return false
    if (projectTypeFilter.value && row.projectType !== projectTypeFilter.value) return false
    const [start, end] = predictedDateRange.value || []
    // yyyy-MM-dd 字符串字典序即时间序；无预测时间的行只在不限时间时保留
    if (start && (!row.predictedBidDate || row.predictedBidDate < start)) return false
    if (end && (!row.predictedBidDate || row.predictedBidDate > end)) return false
    return true
  }),
)

watch([projectNameFilter, customerFilter, customerTypeFilter, projectTypeFilter, predictedDateRange], () => {
  currentPage.value = 1
})

const hasActiveFilters = computed(
  () => Boolean(
    projectNameFilter.value?.trim() ||
    customerFilter.value?.trim() ||
    customerTypeFilter.value ||
    projectTypeFilter.value ||
    predictedDateRange.value?.length,
  ),
)

function resetFilters() {
  projectNameFilter.value = ''
  customerFilter.value = ''
  customerTypeFilter.value = ''
  projectTypeFilter.value = ''
  predictedDateRange.value = null
}

const emptyText = computed(() =>
  hasActiveFilters.value ? '无匹配商机，请调整筛选条件' : '暂无近 1 年商机预测',
)

const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  return filteredRows.value.slice(start, start + PAGE_SIZE)
})

function handleExport() {
  if (filteredRows.value.length === 0) {
    ElMessage.warning('暂无可导出数据')
    return
  }
  const exportRows = filteredRows.value.map((row, idx) => ({
    '序号': idx + 1,
    '项目名称': row.projectName || '-',
    '项目状态': projectStatusLabel(row.resultType) || '-',
    '招标主体': row.customer || '-',
    '项目负责人': row.projectLeaderName || '-',
    '客户类型': customerTypeLabel(row.customerType) || '-',
    '项目类型': projectTypeLabel(row.projectType) || '-',
    '开标时间': row.bidOpenTime ? String(row.bidOpenTime).slice(0, 10) : '-',
    '项目服务周期（年）': row.servicePeriodYears != null && row.servicePeriodYears !== '' ? String(row.servicePeriodYears) : '-',
    '服务周期截止时间': row.servicePeriodEndDate ? String(row.servicePeriodEndDate).slice(0, 10) : '-',
    '预测招标时间': row.predictedBidDate ? String(row.predictedBidDate).slice(0, 10) : '-',
  }))
  const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')
  exportApi.exportExcel('AI商机预测', exportRows, `AI商机预测-${stamp}.csv`)
  notifyExportSuccess('AI商机预测导出成功')
}

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
  flex-wrap: wrap;
}
.op-filter-input {
  width: 140px;
}
.op-filter-select {
  width: 130px;
}
.op-filter-date {
  width: 240px;
}
.op-filter-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
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
