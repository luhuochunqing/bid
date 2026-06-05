<template>
  <div class="margin-management">
    <div class="page-header">
      <h2>保证金管理</h2>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stat-cards">
      <el-col :span="4">
        <div class="stat-card card-paid">
          <div class="stat-label">保证金支付总额</div>
          <div class="stat-value">¥ {{ fmtMoney(summary.totalPaid) }}</div>
        </div>
      </el-col>
      <el-col :span="5">
        <div class="stat-card card-pending">
          <div class="stat-label">保证金未退回总额</div>
          <div class="stat-value">¥ {{ fmtMoney(summary.totalPending) }}</div>
        </div>
      </el-col>
      <el-col :span="5">
        <div class="stat-card card-count">
          <div class="stat-label">保证金未退回笔数</div>
          <div class="stat-value">{{ summary.pendingCount || 0 }} 笔</div>
        </div>
      </el-col>
      <el-col :span="5">
        <div class="stat-card card-overdue-amount">
          <div class="stat-label">超期未退回金额</div>
          <div class="stat-value overdue">¥ {{ fmtMoney(summary.overdueAmount) }}</div>
        </div>
      </el-col>
      <el-col :span="5">
        <div class="stat-card card-overdue-count">
          <div class="stat-label">超期未退回笔数</div>
          <div class="stat-value overdue">{{ summary.overdueCount || 0 }} 笔</div>
        </div>
      </el-col>
    </el-row>

    <!-- 搜索筛选 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="项目名称">
          <el-input v-model="filters.projectName" placeholder="模糊匹配" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="业主单位">
          <el-input v-model="filters.ownerUnit" placeholder="模糊匹配" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="项目负责人">
          <el-input v-model="filters.projectLeaderName" placeholder="精准匹配" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="投标负责人">
          <el-input v-model="filters.biddingLeaderName" placeholder="精准匹配" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="缴纳日期">
          <el-date-picker v-model="filters.paymentDateRange" type="daterange" range-separator="~"
            start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 240px" />
        </el-form-item>
        <el-form-item label="应退日期">
          <el-date-picker v-model="filters.expectedReturnDateRange" type="daterange" range-separator="~"
            start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 240px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="已退回" value="RETURNED" />
            <el-option label="未到期" value="PENDING" />
            <el-option label="已超期" value="OVERDUE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">筛选</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="goToProject(row.projectId)">{{ row.projectName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="ownerUnit" label="业主单位" min-width="160" show-overflow-tooltip />
        <el-table-column prop="projectLeaderName" label="项目负责人" width="120" />
        <el-table-column prop="biddingLeaderName" label="投标负责人" width="120" />
        <el-table-column prop="depositAmount" label="缴纳保证金金额" width="140" align="right">
          <template #default="{ row }">¥ {{ fmtMoney(row.depositAmount) }}</template>
        </el-table-column>
        <el-table-column prop="paymentDate" label="缴纳日期" width="110">
          <template #default="{ row }">{{ fmtDate(row.paymentDate) }}</template>
        </el-table-column>
        <el-table-column prop="depositPaymentMethod" label="保证金缴纳方式" width="130" />
        <el-table-column prop="payeeName" label="收款方名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="payeeAccount" label="收款方账号" width="180" show-overflow-tooltip />
        <el-table-column prop="expectedReturnDate" label="应退日期" width="110">
          <template #default="{ row }">{{ fmtDate(row.expectedReturnDate) }}</template>
        </el-table-column>
        <el-table-column prop="returnedAmount" label="退回金额" width="120" align="right">
          <template #default="{ row }">{{ row.returnedAmount != null ? '¥ ' + fmtMoney(row.returnedAmount) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="serviceFeeAmount" label="转服务费金额" width="130" align="right">
          <template #default="{ row }">{{ row.serviceFeeAmount != null ? '¥ ' + fmtMoney(row.serviceFeeAmount) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="actualReturnDate" label="实际退回日期" width="120">
          <template #default="{ row }">{{ fmtDate(row.actualReturnDate) }}</template>
        </el-table-column>
        <el-table-column prop="statusLabel" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.statusLabel)" effect="dark">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchList"
          @size-change="fetchList"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import httpClient from '@/api/client'

const router = useRouter()

const summary = reactive({
  totalPaid: 0, totalPending: 0, pendingCount: 0,
  overdueAmount: 0, overdueCount: 0
})

const filters = reactive({
  projectName: '', ownerUnit: '', projectLeaderName: '',
  biddingLeaderName: '', paymentDateRange: null,
  expectedReturnDateRange: null, status: ''
})

const tableData = ref([])
const loading = ref(false)
const pagination = reactive({ page: 1, size: 20, total: 0 })

function fmtMoney(v) { return (Number(v ?? 0)).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }
function fmtDate(d) { return d ? d.substring(0, 10) : '—' }
function statusTagType(label) {
  if (label === '已退回') return 'success'
  if (label === '已超期') return 'danger'
  return ''
}

async function fetchSummary() {
  try {
    const res = await httpClient.get('/api/resource/margin/summary')
    if (res?.data) Object.assign(summary, res.data)
  } catch (e) { /* no data */ }
}

async function fetchList() {
  loading.value = true
  try {
    const params = { page: pagination.page, size: pagination.size }
    if (filters.projectName) params.projectName = filters.projectName
    if (filters.ownerUnit) params.ownerUnit = filters.ownerUnit
    if (filters.projectLeaderName) params.projectLeaderName = filters.projectLeaderName
    if (filters.biddingLeaderName) params.biddingLeaderName = filters.biddingLeaderName
    if (filters.paymentDateRange?.[0]) params.paymentDateStart = filters.paymentDateRange[0]
    if (filters.paymentDateRange?.[1]) params.paymentDateEnd = filters.paymentDateRange[1]
    if (filters.expectedReturnDateRange?.[0]) params.expectedReturnDateStart = filters.expectedReturnDateRange[0]
    if (filters.expectedReturnDateRange?.[1]) params.expectedReturnDateEnd = filters.expectedReturnDateRange[1]
    if (filters.status) params.status = filters.status
    const res = await httpClient.get('/api/resource/margin/list', { params })
    if (res?.data) {
      tableData.value = res.data.data || []
      pagination.total = res.data.total || 0
    }
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchList()
}

function handleReset() {
  Object.assign(filters, {
    projectName: '', ownerUnit: '', projectLeaderName: '',
    biddingLeaderName: '', paymentDateRange: null,
    expectedReturnDateRange: null, status: ''
  })
  pagination.page = 1
  fetchList()
}

function handleExport() {
  const params = new URLSearchParams()
  if (filters.projectName) params.append('projectName', filters.projectName)
  if (filters.ownerUnit) params.append('ownerUnit', filters.ownerUnit)
  if (filters.projectLeaderName) params.append('projectLeaderName', filters.projectLeaderName)
  if (filters.biddingLeaderName) params.append('biddingLeaderName', filters.biddingLeaderName)
  if (filters.paymentDateRange?.[0]) params.append('paymentDateStart', filters.paymentDateRange[0])
  if (filters.paymentDateRange?.[1]) params.append('paymentDateEnd', filters.paymentDateRange[1])
  if (filters.expectedReturnDateRange?.[0]) params.append('expectedReturnDateStart', filters.expectedReturnDateRange[0])
  if (filters.expectedReturnDateRange?.[1]) params.append('expectedReturnDateEnd', filters.expectedReturnDateRange[1])
  if (filters.status) params.append('status', filters.status)
  window.open(`/api/resource/margin/list?${params.toString()}`, '_blank')
}

function goToProject(id) { router.push(`/project/${id}`) }

onMounted(() => { fetchSummary(); fetchList() })
</script>

<style scoped>
.margin-management { padding: 20px; }
.page-header { margin-bottom: 20px; }
.page-header h2 { margin: 0; font-size: 20px; }

.stat-cards { margin-bottom: 20px; }
.stat-card {
  padding: 16px; border-radius: 8px; color: #fff; text-align: center;
}
.stat-label { font-size: 13px; opacity: 0.9; margin-bottom: 8px; }
.stat-value { font-size: 18px; font-weight: 700; }
.card-paid { background: var(--el-color-primary); }
.card-pending { background: var(--el-color-warning); }
.card-count { background: var(--el-color-info); }
.card-overdue-amount { background: var(--el-color-danger); }
.card-overdue-count { background: var(--el-color-danger); filter: brightness(0.85); }

.filter-card { margin-bottom: 20px; }
.filter-form .el-form-item { margin-bottom: 0; }

.table-card { margin-bottom: 20px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
