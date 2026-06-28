<template>
  <div class="deposit-board">
    <div class="page-header">
      <h2>投标保证金看板</h2>
      <p class="subtitle">跟踪并管理所有投标项目的保证金流转与退还状态</p>
    </div>

    <!-- 顶栏炫彩统计卡片 -->
    <el-row :gutter="20" class="stat-cards">
      <el-col :span="8">
        <div class="stat-card card-paid">
          <div class="stat-title">已支付总额 (元)</div>
          <div class="stat-value">¥ {{ (summary.totalPaid || 0).toLocaleString() }}</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card card-pending">
          <div class="stat-title">未退还总额 (元)</div>
          <div class="stat-value">¥ {{ (summary.totalPending || 0).toLocaleString() }}</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card card-count">
          <div class="stat-title">待催退笔数</div>
          <div class="stat-value">{{ summary.pendingCount || 0 }} 笔</div>
        </div>
      </el-col>
    </el-row>

    <!-- 保证金台账列表 -->
    <el-card class="data-card" shadow="never">
      <el-table :data="deposits" v-loading="loading" style="width: 100%">
        <el-table-column prop="projectId" label="关联项目" width="120">
          <template #default="scope">
            <span class="project-id-tag">#{{ scope.row.projectId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="paymentDate" label="支付日期" width="120" />
        <el-table-column prop="expectedReturnDate" label="预计退还日期" width="140">
          <template #default="scope">
            <span :class="isOverdue(scope.row.expectedReturnDate) && scope.row.status === 'PAID' ? 'text-danger fw-bold' : ''">
              {{ scope.row.expectedReturnDate }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="payee" label="收款方" min-width="160" />
        <el-table-column prop="amount" label="金额 (元)" width="150" align="right">
          <template #default="scope">
            <span class="amount-text">¥ {{ scope.row.amount.toLocaleString() }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'RETURNED' ? 'success' : 'warning'" effect="dark">
              {{ scope.row.status === 'RETURNED' ? '已退还' : '未退还' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="scope">
            <el-button 
              v-if="scope.row.status === 'PAID'"
              size="small" 
              type="success" 
              plain 
              @click="markReturned(scope.row.id)"
            >
              <el-icon><Check /></el-icon> 确认退还
            </el-button>
            <span v-else class="text-muted">已完结</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import http from '@/api/client'

const loading = ref(false)
const summary = ref({
  totalPaid: 0,
  totalPending: 0,
  pendingCount: 0
})
const deposits = ref([])

const fetchSummary = async () => {
  try {
    const { data } = await http.get('/api/knowledge/deposit/summary')
    if (data.code === 200) {
      summary.value = data.data
    }
  } catch (error) {
    console.error('Failed to fetch summary', error)
  }
}

const fetchDeposits = async () => {
  loading.value = true
  try {
    const { data } = await http.get('/api/knowledge/deposit/list')
    if (data.code === 200) {
      deposits.value = data.data || []
    }
  } catch (error) {
    ElMessage.error('加载保证金列表失败')
  } finally {
    loading.value = false
  }
}

const markReturned = (id) => {
  ElMessageBox.confirm(
    '确认该笔保证金已收回？此操作不可逆。',
    '确认退还',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      const { data } = await http.post(`/api/knowledge/deposit/return/${id}`)
      if (data.code === 200) {
        ElMessage.success('已标记为退还状态')
        fetchSummary()
        fetchDeposits()
      }
    } catch (e) {
      ElMessage.error('操作失败')
    }
  }).catch(() => {})
}

const isOverdue = (dateStr) => {
  if (!dateStr) return false
  return new Date(dateStr) < new Date()
}

onMounted(() => {
  fetchSummary()
  fetchDeposits()
})
</script>

<style scoped>
.deposit-board {
  padding: 24px;
}
.page-header {
  margin-bottom: 24px;
}
.page-header h2 {
  font-weight: 600;
  color: var(--gray-950);
  margin: 0 0 8px 0;
}
.subtitle {
  color: var(--gray-650);
  margin: 0;
  font-size: 14px;
}
.stat-cards {
  margin-bottom: 24px;
}
.stat-card {
  padding: 24px;
  border-radius: 16px;
  color: white;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s ease;
}
.stat-card:hover {
  transform: translateY(-5px);
}
.card-paid {
  background: linear-gradient(135deg, var(--brand-primary-light) 0%, var(--brand-primary) 100%);
}
.card-pending {
  background: linear-gradient(135deg, var(--color-warning) 0%, var(--color-warning) 100%);
}
.card-count {
  background: linear-gradient(135deg, var(--color-info) 0%, var(--brand-primary-dark) 100%);
}
.stat-title {
  font-size: 15px;
  opacity: 0.9;
  margin-bottom: 12px;
}
.stat-value {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 1px;
}
.data-card {
  border-radius: 12px;
  border: 1px solid var(--gray-150);
}
.project-id-tag {
  background: var(--bg-subtle);
  padding: 4px 8px;
  border-radius: 6px;
  font-family: monospace;
  color: var(--sidebar-text-secondary);
}
.amount-text {
  font-family: 'Inter', sans-serif;
  font-weight: 600;
  color: var(--gray-950);
}
.text-danger {
  color: var(--color-danger);
}
.fw-bold {
  font-weight: bold;
}
.text-muted {
  color: var(--text-muted);
  font-size: 13px;
}
</style>
