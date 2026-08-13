<template>
  <el-dialog
    v-model="dialogVisible"
    :title="drillTitle"
    width="75%"
    top="5vh"
    :close-on-click-modal="false"
    destroy-on-close
    class="drill-modal"
  >
    <div v-loading="loading" class="drill-content">
      <el-empty v-if="!loading && items.length === 0" description="暂无明细数据" :image-size="60" />

      <div v-else-if="summary" class="drill-stats">
        <div class="stat-item">
          <span class="stat-label">项目数</span>
          <strong class="stat-value">{{ summary.totalCount ?? 0 }}</strong>
        </div>
        <div class="stat-item">
          <span class="stat-label">投标数</span>
          <strong class="stat-value">{{ summary.totalBids ?? 0 }}</strong>
        </div>
        <div class="stat-item">
          <span class="stat-label">中标数</span>
          <strong class="stat-value">{{ summary.totalWins ?? 0 }}</strong>
        </div>
        <div class="stat-item">
          <span class="stat-label">中标率</span>
          <strong class="stat-value">{{ summary.winRate ?? 0 }}%</strong>
        </div>
      </div>

      <el-table
        v-if="!loading && items.length > 0"
        :data="items"
        stripe
        size="small"
        highlight-current-row
        @row-click="handleRowClick"
      >
        <el-table-column prop="projectName" label="项目名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click.stop="navigateToProject(row)">
              {{ row.projectName || '-' }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="managerName" label="项目经理" width="120" show-overflow-tooltip />
        <el-table-column prop="techLeaderName" label="技术负责人" width="120" show-overflow-tooltip />
        <el-table-column prop="openTime" label="开标时间" width="130" />
        <el-table-column prop="status" label="项目状态" width="110">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="plain">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > 0" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="handlePageChange"
        />
        <span class="page-info">第 {{ rangeStart }}-{{ rangeEnd }} 条 / 共 {{ total }} 条</span>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getProjectStatusText, getProjectStatusType } from '@/views/Project/project-utils.js'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: '明细数据'
  },
  items: {
    type: Array,
    default: () => []
  },
  summary: {
    type: Object,
    default: null
  },
  pagination: {
    type: Object,
    default: () => ({ page: 1, size: 10, total: 0, totalPages: 0 })
  }
})

const emit = defineEmits([
  'update:modelValue',
  'page-change',
  'navigate-project'
])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const currentPage = computed(() => props.pagination.page || 1)
const pageSize = computed(() => props.pagination.size || 10)
const total = computed(() => props.pagination.total || 0)
const totalPages = computed(() => props.pagination.totalPages || 0)
const rangeStart = computed(() => {
  if (total.value === 0) return 0
  return (currentPage.value - 1) * pageSize.value + 1
})
const rangeEnd = computed(() => {
  return Math.min(currentPage.value * pageSize.value, total.value)
})

const getStatusText = (status) => getProjectStatusText(status)
const getStatusType = (status) => getProjectStatusType(status)

const navigateToProject = (row) => {
  if (row.projectId || row.id) {
    emit('navigate-project', row.projectId || row.id)
  }
}

const handlePageChange = (page) => {
  emit('page-change', page)
}

const handleRowClick = (row) => {
  navigateToProject(row)
}
</script>

<style scoped>
.drill-modal {
  :deep(.el-dialog__body) {
    padding: 16px 24px;
  }
}

.drill-content {
  min-height: 200px;
}

.drill-stats {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #F8FAFC;
  border-radius: 8px;
  border: 1px solid #E2E8F0;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-label {
  font-size: 12px;
  color: #475569;
}

.stat-value {
  font-size: 18px;
  font-weight: 700;
  color: #1E293B;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #E2E8F0;
}

.page-info {
  font-size: 12px;
  color: var(--text-badge, #475569);
  white-space: nowrap;
}
</style>