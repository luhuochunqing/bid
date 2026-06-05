<template>
  <div class="alert-history-container">
    <div class="page-header">
      <h2>告警历史</h2>
    </div>

    <el-table :data="history" v-loading="loading" stripe>
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
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" v-if="row.status === 'ACTIVE'" @click="acknowledge(row)">确认</el-button>
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
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { alertHistoryApi } from '@/api/modules/alerts.js'

const loading = ref(false)
const history = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

onMounted(() => { loadHistory() })

async function loadHistory() {
  loading.value = true
  try {
    const res = await alertHistoryApi.getList({ page: page.value - 1, size: pageSize.value })
    history.value = res.data || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error('加载告警历史失败')
  } finally {
    loading.value = false
  }
}

async function acknowledge(row) {
  try {
    await alertHistoryApi.acknowledge(row.id)
    ElMessage.success('已确认')
    loadHistory()
  } catch (e) {
    ElMessage.error('确认失败')
  }
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
