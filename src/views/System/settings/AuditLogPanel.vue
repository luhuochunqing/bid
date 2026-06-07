<template>
  <el-card shadow="never" class="audit-panel">
    <template #header>
      <div class="panel-header">
        <div>
          <h3>{{ panelTitle }}</h3>
          <p>{{ panelDescription }}</p>
        </div>
        <el-tag type="info" effect="plain">今日操作 {{ todayCount }}</el-tag>
      </div>
    </template>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索操作内容/对象"
        clearable
        class="search-input"
        @keyup.enter="loadLogs"
      />
      <el-button type="primary" :loading="loading" @click="loadLogs">搜索</el-button>
      <el-button :disabled="loading" @click="resetSearch">重置</el-button>
    </div>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="panel-alert"
    />

    <div class="audit-summary">
      <span>共 {{ total }} 条记录</span>
      <span>最近更新时间 {{ lastLoadedAt }}</span>
    </div>

    <el-table v-loading="loading" :data="rows" stripe class="audit-table" style="width: 100%">
      <el-table-column prop="time" label="时间" min-width="170" />
      <el-table-column prop="operator" label="操作人" width="140" />
      <el-table-column prop="action" label="操作内容" min-width="220" />
      <el-table-column prop="target" label="对象" min-width="180" />
    </el-table>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { auditApi } from '@/api'

const props = defineProps({
  mode: {
    type: String,
    default: 'operation',
    validator: (value) => ['operation', 'audit'].includes(value)
  }
})

const loading = ref(false)
const keyword = ref('')
const rows = ref([])
const total = ref(0)
const errorMessage = ref('')
const lastLoadedAt = ref('-')
const isAuditMode = computed(() => props.mode === 'audit')
const panelTitle = computed(() => (isAuditMode.value ? '审计日志' : '操作日志'))
const panelDescription = computed(() => (
  isAuditMode.value
    ? '查看全员关键操作记录，供管理员和审计员追溯新增、修改、删除和状态流转类变更。'
    : '查看自己的新增、修改、删除和状态流转类操作记录，并支持按关键词快速筛选。'
))

const firstPresent = (...values) => values.find((value) => value != null && String(value).trim() !== '')

const toFiniteNumber = (value) => {
  if (value == null || value === '') return null
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : null
}

const normalizeAuditRow = (item = {}) => ({
  id: item.id ?? `${firstPresent(item.timestamp, item.time, '')}-${firstPresent(item.operator, item.userName, '')}-${firstPresent(item.detail, item.action, item.actionType, '')}`,
  time: firstPresent(item.timestamp, item.time, item.createdAt) || '-',
  operator: firstPresent(item.operator, item.userName, item.createdByName, item.username) || '未知用户',
  action: firstPresent(item.detail, item.action, item.actionType, item.description, item.operationContent) || '未知操作',
  target: firstPresent(item.target, item.entityId, item.entityName, item.objectName, item.entityType) || '-'
})

const resolveTotal = (payload, rowCount) => (
  toFiniteNumber(payload?.summary?.totalCount)
  ?? toFiniteNumber(payload?.total)
  ?? toFiniteNumber(payload?.totalCount)
  ?? rowCount
)

const todayCount = computed(() => {
  const today = new Date().toISOString().slice(0, 10)
  return rows.value.filter((item) => String(item.time || '').slice(0, 10) === today).length
})

async function loadLogs() {
  loading.value = true
  errorMessage.value = ''
  try {
    const params = keyword.value ? { keyword: keyword.value } : {}
    const response = isAuditMode.value
      ? await auditApi.getAuditLogs(params)
      : await auditApi.getOperationLogs(params)
    const payload = response?.data
    const items = Array.isArray(payload?.items)
      ? payload.items
      : Array.isArray(payload)
        ? payload
        : []
    rows.value = items.map(normalizeAuditRow)
    total.value = resolveTotal(payload, rows.value.length)
    lastLoadedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
  } catch (error) {
    rows.value = []
    total.value = 0
    errorMessage.value = error?.message || (isAuditMode.value ? '审计日志加载失败' : '操作日志加载失败')
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  keyword.value = ''
  loadLogs()
}

onMounted(loadLogs)
</script>

<style scoped>
.audit-panel {
  border-radius: 20px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.panel-header h3 {
  margin: 0 0 6px;
  color: #1f2937;
}

.panel-header p {
  margin: 0;
  color: #52627a;
  line-height: 1.6;
}

.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.search-input {
  max-width: 360px;
}

.panel-alert {
  margin-bottom: 16px;
}

.audit-summary {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: #52627a;
  font-size: 13px;
}

@media (max-width: 768px) {
  .panel-header,
  .toolbar,
  .audit-summary {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input {
    max-width: none;
  }
}
</style>
