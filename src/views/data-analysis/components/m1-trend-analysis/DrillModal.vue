<template>
  <el-dialog
    v-model="dialogVisible"
    :show-close="false"
    width="720px"
    :close-on-click-modal="true"
    destroy-on-close
    align-center
    append-to-body
    class="drill-modal"
  >
    <template #header>
      <div class="m2-modal-header">
        <span class="m2-modal-title">{{ title }}</span>
        <span class="m2-modal-close" @click="dialogVisible = false">✕</span>
      </div>
    </template>

    <div v-loading="loading" class="m2-modal-body">
      <table v-if="!loading && items.length > 0" class="data-table">
        <thead>
          <tr>
            <th class="col-name">项目名称</th>
            <th class="col-manager">项目负责人</th>
            <th class="col-leader">投标负责人</th>
            <th class="col-time">开标时间</th>
            <th class="col-status">项目状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.projectId ?? row.projectName">
            <td class="col-name">
              <span
                class="cell-name"
                :title="row.projectName"
                @click="navigateToProject(row)"
              >{{ row.projectName || '-' }}</span>
            </td>
            <td class="col-manager">{{ row.managerName || '-' }}</td>
            <td class="col-leader">{{ row.techLeaderName || '-' }}</td>
            <td class="col-time">{{ formatOpenTime(row.openTime) }}</td>
            <td class="col-status">
              <span class="tag" :class="getStatusTagClass(row.status)">{{ getStatusText(row.status) }}</span>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else-if="!loading" class="empty-state">暂无数据</div>
    </div>

    <template #footer>
      <div class="m2-modal-footer">
        <span class="m2-page-info">{{ pageInfoText }}</span>
        <div class="m2-page-controls">
          <button
            type="button"
            :disabled="currentPage <= 1"
            @click="handlePageChange(currentPage - 1)"
          >上一页</button>
          <button
            v-for="p in totalPages"
            :key="p"
            type="button"
            :class="{ active: p === currentPage }"
            @click="handlePageChange(p)"
          >{{ p }}</button>
          <button
            type="button"
            :disabled="currentPage >= totalPages"
            @click="handlePageChange(currentPage + 1)"
          >下一页</button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { getProjectStatusText } from '@/views/Project/project-utils.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  title: { type: String, default: '明细数据' },
  items: { type: Array, default: () => [] },
  summary: { type: Object, default: null },
  pagination: {
    type: Object,
    default: () => ({ page: 1, size: 10, total: 0, totalPages: 0 })
  }
})

const emit = defineEmits(['update:modelValue', 'page-change', 'navigate-project'])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const currentPage = computed(() => props.pagination.page || 1)
const pageSize = computed(() => props.pagination.size || 10)
const total = computed(() => props.pagination.total || 0)
const totalPages = computed(() => props.pagination.totalPages || Math.max(1, Math.ceil(total.value / pageSize.value)))

const rangeStart = computed(() => (total.value === 0 ? 0 : (currentPage.value - 1) * pageSize.value + 1))
const rangeEnd = computed(() => Math.min(currentPage.value * pageSize.value, total.value))
const pageInfoText = computed(() => {
  if (total.value === 0) return ''
  return `第 ${rangeStart.value}-${rangeEnd.value} 条 / 共 ${total.value} 条`
})

// 后端英文状态枚举 → 原型 m2-modal tag class 映射
const STATUS_TAG_CLASS = {
  BIDDING: 'tag-blue',
  EVALUATING: 'tag-orange',
  WON: 'tag-green',
  LOST: 'tag-gray',
  FAILED: 'tag-red',
  ABANDONED: 'tag-gray',
  PENDING_INITIATION: 'tag-gray',
  INITIATED: 'tag-blue'
}
const getStatusTagClass = (status) => STATUS_TAG_CLASS[status] || 'tag-gray'
const getStatusText = (status) => getProjectStatusText(status)

const formatOpenTime = (openTime) => {
  if (!openTime) return '-'
  const str = String(openTime)
  return str.length >= 16 ? str.slice(0, 16).replace('T', ' ') : str
}

const navigateToProject = (row) => {
  if (row.projectId || row.id) {
    emit('navigate-project', row.projectId || row.id)
  }
}

const handlePageChange = (page) => {
  if (page < 1 || page > totalPages.value) return
  emit('page-change', page)
}
</script>

<style scoped>
/* ===== el-dialog 容器覆盖，复刻原型 m2-modal ===== */
.drill-modal :deep(.el-dialog) {
  border-radius: 12px;
  overflow: hidden;
  max-width: 90vw;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  margin: 0;
}
.drill-modal :deep(.el-dialog__header) {
  padding: 0;
  margin: 0;
  border-bottom: 1px solid #E2E8F0;
}
.drill-modal :deep(.el-dialog__header::after) {
  display: none;
}
.drill-modal :deep(.el-dialog__body) {
  padding: 0;
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.drill-modal :deep(.el-dialog__footer) {
  padding: 0;
  border-top: 1px solid #E2E8F0;
  flex-shrink: 0;
}

/* ===== header ===== */
.m2-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
}
.m2-modal-title {
  font-size: 16px;
  font-weight: 700;
  color: #1E293B;
}
.m2-modal-close {
  font-size: 20px;
  color: #94A3B8;
  cursor: pointer;
  line-height: 1;
  transition: color 0.2s;
}
.m2-modal-close:hover {
  color: #DC2626;
}

/* ===== body ===== */
.m2-modal-body {
  padding: 10px 24px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

/* ===== table - 复刻原型 data-table ===== */
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  table-layout: fixed;
}
.data-table th {
  background: #F8FAFC;
  color: #94A3B8;
  font-weight: 600;
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid #E2E8F0;
  white-space: nowrap;
  font-size: 12px;
  letter-spacing: 0.3px;
}
.data-table td {
  padding: 10px 12px;
  border-bottom: 1px solid #F1F5F9;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.data-table tbody tr {
  transition: background 0.15s;
}
.data-table tbody tr:hover {
  background: #F0F9F6;
}
/* 720px 宽度下列宽分配：项目负责人/投标负责人/开标时间加宽确保显示全 */
.data-table .col-name { width: 24%; }
.data-table .col-manager { width: 18%; }
.data-table .col-leader { width: 18%; }
.data-table .col-time { width: 24%; }
.data-table .col-status { width: 16%; }
.data-table .cell-name {
  color: #2E7659;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
  max-width: 100%;
  font-weight: 500;
}
.data-table .cell-name:hover {
  text-decoration: underline;
  color: #1F5A44;
}

/* ===== tags - 复刻原型 tag 样式 ===== */
.tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.5;
  font-weight: 500;
}
.tag-blue {
  background: #E6F4EF;
  color: #2E7659;
  border: 1px solid rgba(46, 118, 89, 0.15);
}
.tag-green {
  background: #DCFCE7;
  color: #16A34A;
  border: 1px solid rgba(22, 163, 74, 0.15);
}
.tag-orange {
  background: #FEF3C7;
  color: #D97706;
  border: 1px solid rgba(217, 119, 6, 0.15);
}
.tag-gray {
  background: #F1F5F9;
  color: #94A3B8;
  border: 1px solid #E2E8F0;
}
.tag-red {
  background: #FEE2E2;
  color: #DC2626;
  border: 1px solid rgba(220, 38, 38, 0.15);
}

/* ===== footer / pagination - 复刻原型 m2-modal-footer ===== */
.m2-modal-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 24px;
  font-size: 13px;
}
.m2-page-info {
  color: #94A3B8;
}
.m2-page-controls {
  display: flex;
  gap: 4px;
  align-items: center;
}
.m2-page-controls button {
  padding: 5px 12px;
  border: 1px solid #E2E8F0;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: #475569;
  transition: all 0.2s;
  font-weight: 500;
}
.m2-page-controls button:hover:not(:disabled) {
  color: #2E7659;
  border-color: #5BAA8A;
  background: #F0F9F6;
}
.m2-page-controls button.active {
  background: linear-gradient(135deg, #2E7659, #1F5A44);
  color: #fff;
  border-color: transparent;
}
.m2-page-controls button:disabled {
  color: #CBD5E1;
  cursor: not-allowed;
}

.empty-state {
  padding: 40px;
  text-align: center;
  color: #94A3B8;
  font-size: 13px;
}
</style>
