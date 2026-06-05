<template>
  <el-drawer
    v-model="visible"
    :title="title"
    size="70%"
    destroy-on-close
    @close="$emit('close')"
  >
    <div class="metric-drawer">
      <FeaturePlaceholder
        v-if="placeholder"
        compact
        :title="placeholder.title"
        :message="placeholder.message"
        :hint="placeholder.hint"
      />
      <template v-else>
        <div class="metric-drawer-toolbar">
          <div class="metric-drawer-filters">
            <el-select
              v-for="dimension in dimensions"
              :key="dimension.key"
              :model-value="filterValues[dimension.key] || 'ALL'"
              size="small"
              style="width: 180px"
              @change="(value) => $emit('filter-change', dimension.key, value)"
            >
              <el-option
                v-for="option in dimension.options"
                :key="`${dimension.key}-${option.value}`"
                :label="`${option.label}${option.count != null ? ` (${option.count})` : ''}`"
                :value="option.value"
              />
            </el-select>
          </div>
          <el-button size="small" @click="$emit('reload')">刷新明细</el-button>
        </div>

        <div class="metric-summary-grid">
          <div class="metric-summary-card">
            <span class="summary-label">记录数</span>
            <span class="summary-value">{{ summary.totalCount ?? 0 }}</span>
          </div>
          <div class="metric-summary-card">
            <span class="summary-label">金额</span>
            <span class="summary-value">{{ formatMetricAmount(summary.totalAmount) }}</span>
          </div>
          <div v-if="summary.wonCount != null" class="metric-summary-card">
            <span class="summary-label">中标数</span>
            <span class="summary-value">{{ summary.wonCount }}</span>
          </div>
          <div v-if="summary.winRate != null" class="metric-summary-card">
            <span class="summary-label">中标率</span>
            <span class="summary-value">{{ formatMetricRate(summary.winRate) }}</span>
          </div>
          <div v-if="summary.activeCount != null" class="metric-summary-card">
            <span class="summary-label">进行中</span>
            <span class="summary-value">{{ summary.activeCount }}</span>
          </div>
          <div v-if="summary.totalTeamMembers != null" class="metric-summary-card">
            <span class="summary-label">成员数</span>
            <span class="summary-value">{{ summary.totalTeamMembers }}</span>
          </div>
          <div v-if="summary.totalCompletedTasks != null" class="metric-summary-card">
            <span class="summary-label">已完成任务</span>
            <span class="summary-value">{{ summary.totalCompletedTasks }}</span>
          </div>
          <div v-if="summary.totalOverdueTasks != null" class="metric-summary-card">
            <span class="summary-label">逾期任务</span>
            <span class="summary-value">{{ summary.totalOverdueTasks }}</span>
          </div>
          <div v-if="summary.averageTaskCompletionRate != null" class="metric-summary-card">
            <span class="summary-label">平均完成率</span>
            <span class="summary-value">{{ formatMetricRate(summary.averageTaskCompletionRate) }}</span>
          </div>
        </div>

        <el-table v-loading="loading" :data="items" stripe>
          <el-table-column
            v-for="column in columns"
            :key="column.key"
            :prop="column.key"
            :label="column.label"
            :min-width="column.minWidth || 120"
            :width="column.width"
            :show-overflow-tooltip="column.overflow !== false"
          >
            <template #default="{ row }">
              <el-tag
                v-if="column.type === 'status'"
                size="small"
                :type="getMetricStatusTagType(row[column.key], drawerType)"
              >
                {{ formatMetricCell(column, row[column.key], row, drawerType) }}
              </el-tag>
              <span v-else>
                {{ formatMetricCell(column, row[column.key], row, drawerType) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column v-if="hasRowAction" label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="$emit('row-action', row)">
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="metric-drawer-pagination">
          <el-pagination
            background
            layout="total, prev, pager, next"
            :current-page="pagination.page || 1"
            :page-size="pagination.size || 10"
            :total="pagination.total || 0"
            @current-change="(page) => $emit('page-change', page)"
          />
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import FeaturePlaceholder from '@/components/common/FeaturePlaceholder.vue'
import {
  formatMetricAmount,
  formatMetricRate,
  formatMetricCell,
  getMetricStatusTagType
} from '../utils/dashboardFormatters.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  drawerType: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  placeholder: { type: Object, default: null },
  items: { type: Array, default: () => [] },
  summary: { type: Object, default: () => ({}) },
  dimensions: { type: Array, default: () => [] },
  filterValues: { type: Object, default: () => ({}) },
  columns: { type: Array, default: () => [] },
  pagination: { type: Object, default: () => ({ page: 1, size: 10, total: 0 }) },
  hasRowAction: { type: Boolean, default: false }
})

const emit = defineEmits([
  'update:modelValue',
  'close',
  'filter-change',
  'reload',
  'page-change',
  'row-action'
])

const visible = ref(props.modelValue)

watch(() => props.modelValue, (val) => {
  visible.value = val
})

watch(visible, (val) => {
  if (val !== props.modelValue) emit('update:modelValue', val)
})
</script>

<style scoped>
.metric-drawer {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.metric-drawer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.metric-drawer-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.metric-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}

.metric-summary-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid #E5E7EB;
  background: linear-gradient(180deg, var(--bg-card) 0%, #F8FAFC 100%);
}

.summary-label { font-size: 12px; color: var(--text-slate); }
.summary-value { font-size: 20px; font-weight: 700; color: #0F172A; }

.metric-drawer-pagination {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .metric-drawer-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
