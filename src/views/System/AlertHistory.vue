<template>
  <div class="alert-history-container">
    <div class="page-header">
      <h2>告警历史</h2>
    </div>

    <!-- 统计卡片：总告警数 / 未解决数 / 高严重数 / 严重数 -->
    <AlertHistoryStats :stats="stats" />

    <!-- 过滤栏：视图切换 + 状态/严重性筛选 -->
    <AlertHistoryFilters
      :view-mode="viewMode"
      @update:view-mode="handleViewModeChange"
      :filters="filters"
      @update:filters="handleFilterUpdate"
      @reset="resetFilters"
      @search="handleSearch"
    />

    <el-table :data="history" v-loading="loading" stripe max-height="calc(100vh - 360px)" scrollbar-always-on>
      <el-table-column prop="ruleName" label="规则名称" />
      <el-table-column prop="alertType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag>{{ row.alertType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="severity" label="严重性" width="100">
        <template #default="{ row }">
          <el-tag :type="severityType(row.severity)">{{ row.severity }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="message" label="消息" />
      <el-table-column prop="projectName" label="关联项目" width="150" />
      <el-table-column prop="createdAt" label="时间" width="160">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" v-if="row.status === 'ACTIVE'" @click="acknowledge(row)">确认</el-button>
          <el-button
            link
            type="success"
            v-if="row.status === 'ACTIVE' || row.status === 'ACKNOWLEDGED'"
            @click="resolve(row)"
          >解决</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="loadHistory"
      style="margin-top: 20px; justify-content: flex-end;"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { alertHistoryApi } from '@/api/modules/alerts.js'
import AlertHistoryStats from './components/AlertHistoryStats.vue'
import AlertHistoryFilters from './components/AlertHistoryFilters.vue'

const loading = ref(false)
const history = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 视图模式：all = 全部告警，unresolved = 仅未解决
const viewMode = ref('all')

// 过滤器：status / level 字段对齐后端 getList 参数
const filters = reactive({ status: '', level: '' })

// 统计数据：字段名对齐后端 AlertStatisticsResponse
const stats = ref({ totalAlerts: 0, unresolvedAlerts: 0, highAlerts: 0, criticalAlerts: 0 })

onMounted(() => {
  loadHistory()
  loadStatistics()
})

// 加载告警列表：根据视图模式切换 getList / getUnresolved
async function loadHistory() {
  loading.value = true
  try {
    const params = { page: page.value - 1, size: pageSize.value }
    let res
    if (viewMode.value === 'unresolved') {
      res = await alertHistoryApi.getUnresolved(params)
    } else {
      // 仅在全部视图下应用 status / level 过滤
      if (filters.status) params.status = filters.status
      if (filters.level) params.level = filters.level
      res = await alertHistoryApi.getList(params)
    }
    history.value = res.data || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error('加载告警历史失败')
  } finally {
    loading.value = false
  }
}

// 加载统计数据
async function loadStatistics() {
  try {
    const res = await alertHistoryApi.getStatistics()
    stats.value = {
      totalAlerts: res?.data?.totalAlerts ?? 0,
      unresolvedAlerts: res?.data?.unresolvedAlerts ?? 0,
      highAlerts: res?.data?.highAlerts ?? 0,
      criticalAlerts: res?.data?.criticalAlerts ?? 0
    }
  } catch (e) {
    // 统计失败不影响主流程
    stats.value = { totalAlerts: 0, unresolvedAlerts: 0, highAlerts: 0, criticalAlerts: 0 }
  }
}

// 确认告警
async function acknowledge(row) {
  try {
    await alertHistoryApi.acknowledge(row.id)
    ElMessage.success('已确认')
    loadHistory()
    loadStatistics()
  } catch (e) {
    ElMessage.error('确认失败')
  }
}

// 解决告警
async function resolve(row) {
  try {
    await alertHistoryApi.resolve(row.id)
    ElMessage.success('已解决')
    loadHistory()
    loadStatistics()
  } catch (e) {
    ElMessage.error('解决失败')
  }
}

// 处理过滤栏查询
function handleSearch() {
  page.value = 1
  loadHistory()
}

// 视图模式切换：切换到 unresolved 时清空 status/level 过滤，并立即重新加载
function handleViewModeChange(value) {
  viewMode.value = value
  if (value === 'unresolved') {
    filters.status = ''
    filters.level = ''
  }
  page.value = 1
  loadHistory()
}

// 单字段过滤器更新：合并到 filters 对象（不立即查询，等用户点查询）
function handleFilterUpdate({ key, value }) {
  filters[key] = value
}

// 重置过滤器
function resetFilters() {
  filters.status = ''
  filters.level = ''
  page.value = 1
  loadHistory()
}

function severityType(s) {
  const map = { CRITICAL: 'danger', HIGH: 'danger', MEDIUM: 'warning', LOW: 'info', INFO: 'info' }
  return map[s] || 'info'
}

function statusType(s) {
  return s === 'RESOLVED' ? 'success' : s === 'ACKNOWLEDGED' ? 'warning' : 'info'
}

function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}
</script>

<style scoped>
.alert-history-container { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; }
</style>
