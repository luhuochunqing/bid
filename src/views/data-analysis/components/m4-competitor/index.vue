<template>
  <div class="m4-competitor">
    <!-- 区块标题：竞品分析 + 右侧独立日期筛选（PRD §9.2） -->
    <div class="m4-section-title">
      <span class="label">竞品分析</span>
      <div class="m4-date-filter">
        <el-date-picker v-model="dateRange" type="daterange" range-separator="~"
          start-placeholder="开始日期" end-placeholder="结束日期"
          value-format="YYYY-MM-DD" class="m4-date-picker" />
        <button class="m4-btn" :disabled="loading" @click="fetchData">确认</button>
        <button class="m4-btn reset" :disabled="loading" @click="resetDateRange">重置</button>
      </div>
    </div>

    <!-- 筛选栏：竞品公司 / 招标主体 / 项目名称 / 生成表格（PRD §9.3） -->
    <div class="m4-filter-bar">
      <div class="m4-filter-item m4-required">
        <label>竞品公司</label>
        <el-select v-model="selectedCompetitors" multiple filterable collapse-tags
          collapse-tags-tooltip placeholder="请选择" class="m4-select"
          @change="onCompetitorChange">
          <el-option v-for="c in competitorOptions" :key="c" :label="c" :value="c" />
        </el-select>
      </div>

      <div class="m4-filter-item">
        <el-checkbox v-model="entityActive" class="m4-field-cb" @change="onEntityToggle">招标主体</el-checkbox>
        <el-select v-model="selectedEntities" multiple filterable collapse-tags
          collapse-tags-tooltip placeholder="请选择" class="m4-select" clearable
          :disabled="!entityActive" @change="onEntityChange">
          <el-option v-for="e in entityOptions" :key="e" :label="e" :value="e" />
        </el-select>
      </div>

      <div class="m4-filter-item">
        <el-checkbox v-model="projectNameActive" class="m4-field-cb" @change="onProjectNameToggle">项目名称</el-checkbox>
        <el-select v-model="selectedProjectName" filterable remote :remote-method="searchProjectNames"
          placeholder="请输入关键词搜索" class="m4-select" clearable
          :disabled="!projectNameActive" @change="onProjectNameChange">
          <el-option v-for="p in projectNameOptions" :key="p" :label="p" :value="p" />
        </el-select>
      </div>

      <div class="m4-filter-item">
        <el-checkbox v-model="generateTableChecked" :disabled="generateTableDisabled"
          class="m4-field-cb" @change="onGenerateTableChange">生成表格</el-checkbox>
      </div>

      <div class="m4-filter-actions">
        <button class="m4-btn" :disabled="loading" @click="fetchData">确认</button>
        <button class="m4-btn reset" :disabled="loading" @click="resetFilters">重置</button>
      </div>
    </div>

    <!-- 图表 -->
    <div class="chart-body">
      <div v-if="loading" class="status-overlay">
        <el-skeleton animated :count="1" style="padding: 20px;">
          <template #template>
            <div style="display: flex; gap: 16px; padding: 40px 20px;">
              <el-skeleton-item variant="rect" style="width: 60px; height: 200px;" />
              <el-skeleton-item variant="rect" style="width: 60px; height: 160px;" />
              <el-skeleton-item variant="rect" style="width: 60px; height: 180px;" />
              <el-skeleton-item variant="rect" style="width: 60px; height: 140px;" />
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

    <!-- 竞品明细表格（PRD §9.15，仅项目模式 + 勾选生成表格时显示） -->
    <div v-if="tableVisible && tableData" class="m4-table-wrapper">
      <div class="m4-table-title-bar">
        <span class="m4-table-title">{{ tableData.projectLabel }}</span>
        <button class="m4-export-btn" @click="exportTable">导出 Excel</button>
      </div>
      <div class="m4-table-scroll">
        <table class="m4-table">
          <thead>
            <tr><th>竞品公司</th><th>折扣/百分比</th><th>账期/天</th><th>是否中标</th></tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in sortedTableRows" :key="i">
              <td>{{ r.competitor }}</td>
              <td>{{ parseDiscountValue(r.discount) }}</td>
              <td>{{ parsePaymentDays(r.paymentDays) }}</td>
              <td>
                <span class="m4-won-tag" :class="r.isWon ? 'yes' : 'no'">
                  {{ r.isWon ? '已中标' : '未中标' }}
                </span>
              </td>
            </tr>
            <tr v-if="sortedTableRows.length === 0">
              <td colspan="4" class="m4-table-empty">暂无竞品数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useCompetitorData } from './composables/useCompetitorData.js'
import { exportCompetitorTable } from './chartRenderer.js'

const chartRef = ref(null)
const {
  loading, error, noData,
  dateRange, selectedCompetitors, selectedEntities, selectedProjectName,
  competitorOptions, entityOptions, projectNameOptions,
  entityActive, projectNameActive, generateTableChecked, generateTableDisabled,
  tableData, tableVisible,
  parseDiscountValue, parsePaymentDays,
  searchProjectNames, onCompetitorChange,
  onEntityToggle, onEntityChange, onProjectNameToggle, onProjectNameChange, onGenerateTableChange,
  fetchData, resetDateRange, resetFilters, initOptions, resizeChart, disposeChart
} = useCompetitorData(chartRef)

// 表格排序：中标在前，折扣降序（PRD §9.15）
const sortedTableRows = computed(() => {
  const rows = tableData.value?.rows || []
  return [...rows].sort((a, b) => {
    if (a.isWon && !b.isWon) return -1
    if (!a.isWon && b.isWon) return 1
    const da = Number(parseDiscountValue(a.discount)) || 0
    const db = Number(parseDiscountValue(b.discount)) || 0
    return db - da
  })
})

const exportTable = () => exportCompetitorTable(tableData.value, parseDiscountValue, parsePaymentDays)

onMounted(async () => {
  await initOptions()
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
.m4-competitor { display: flex; flex-direction: column; height: 100%; }

.m4-section-title {
  font-size: 16px; font-weight: 700; color: var(--text-primary);
  margin-bottom: 18px; display: flex; align-items: center;
  justify-content: space-between; gap: 12px;
}
.m4-section-title .label { display: flex; align-items: center; gap: 10px; white-space: nowrap; }
.m4-section-title .label::before {
  content: ''; width: 4px; height: 20px;
  background: var(--brand-xiyu-logo); border-radius: 2px;
}
.m4-date-filter { display: flex; align-items: center; gap: 8px; }
.m4-date-picker { width: 280px; }

.m4-filter-bar {
  display: flex; gap: 10px 14px; align-items: center; flex-wrap: wrap;
  padding: 14px 16px; background: var(--bg-subtle); border-radius: var(--radius-sm);
  margin-bottom: 16px; border: 1px solid var(--border-light);
}
.m4-filter-item { display: flex; align-items: center; gap: 6px; }
.m4-filter-item label { font-size: 12px; font-weight: 600; color: var(--text-primary); white-space: nowrap; }
.m4-required label::after { content: ' *'; color: var(--color-danger); }
.m4-field-cb { margin-right: 2px; }
.m4-field-cb :deep(.el-checkbox__label) { font-size: 12px; font-weight: 600; color: var(--text-primary); padding-left: 6px; }
.m4-select { width: 200px; }
.m4-filter-actions { display: flex; align-items: center; gap: 8px; margin-left: auto; }

.m4-btn {
  background: var(--brand-xiyu-logo); color: var(--bg-card);
  border: none; border-radius: var(--radius-sm);
  padding: 5px 14px; font-size: 12px; font-weight: 500; line-height: 1;
  cursor: pointer; white-space: nowrap; transition: opacity 0.15s ease;
}
.m4-btn:hover:not(:disabled) { opacity: 0.88; }
.m4-btn:disabled { cursor: not-allowed; opacity: 0.6; }
.m4-btn.reset { background: var(--bg-card); color: var(--text-secondary); border: 1px solid var(--border-light); }
.m4-btn.reset:hover:not(:disabled) { background: var(--bg-subtle); color: var(--text-primary); }

.chart-body { flex: 1; min-height: 0; position: relative; }
.chart-container { width: 100%; height: 100%; min-height: 380px; }
.status-overlay { display: flex; align-items: center; justify-content: center; min-height: 380px; }

/* 竞品明细表格 */
.m4-table-wrapper { margin-top: 20px; border: 1px solid var(--border-color); border-radius: var(--radius-sm); overflow: hidden; background: var(--bg-card); }
.m4-table-title-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 20px; background: var(--bg-subtle); border-bottom: 1px solid var(--border-color);
}
.m4-table-title { font-size: 15px; font-weight: 700; color: var(--text-primary); }
.m4-export-btn {
  background: var(--brand-xiyu-logo); color: var(--bg-card);
  border: none; border-radius: var(--radius-sm); padding: 5px 14px;
  font-size: 12px; font-weight: 500; cursor: pointer; transition: opacity 0.2s;
}
.m4-export-btn:hover { opacity: 0.92; box-shadow: var(--shadow-brand); }
.m4-table-scroll { max-height: 210px; overflow-y: auto; }
.m4-table-scroll::-webkit-scrollbar { width: 6px; }
.m4-table-scroll::-webkit-scrollbar-thumb { background: var(--border-color); border-radius: 3px; }
.m4-table-scroll::-webkit-scrollbar-track { background: transparent; }
.m4-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.m4-table thead { position: sticky; top: 0; z-index: 1; }
.m4-table th {
  background: var(--bg-subtle); color: var(--text-primary); font-weight: 600; text-align: center;
  padding: 10px 16px; border-bottom: 2px solid var(--border-color); white-space: nowrap;
}
.m4-table td {
  text-align: center; padding: 10px 16px; color: var(--text-secondary);
  border-bottom: 1px solid var(--border-light);
}
.m4-table tr:last-child td { border-bottom: none; }
.m4-table tr:hover td { background: var(--bg-subtle); }
.m4-table-empty { text-align: center; color: var(--text-lighter); padding: 20px; }
.m4-won-tag { display: inline-block; padding: 2px 10px; border-radius: 4px; font-size: 12px; font-weight: 600; }
.m4-won-tag.yes { background: var(--status-success-bg); color: var(--status-success-color); }
.m4-won-tag.no { background: var(--status-danger-bg); color: var(--status-danger-color); }
</style>
