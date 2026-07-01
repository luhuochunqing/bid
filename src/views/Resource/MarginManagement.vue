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
      <div class="filter-header">
        <span class="filter-title">筛选条件</span>
        <div class="filter-actions">
          <button class="filter-btn" @click="handleReset">
            <el-icon><RefreshRight /></el-icon>
            <span>重置</span>
          </button>
          <button class="filter-btn filter-btn--primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            <span>筛选</span>
          </button>
          <button class="filter-btn filter-btn--success" @click="handleExport">
            <el-icon><Download /></el-icon>
            <span>导出</span>
          </button>
        </div>
      </div>
      <div class="filter-body">
        <div class="filter-row">
          <div class="filter-field">
            <label class="filter-label">项目名称</label>
            <el-input v-model="filters.projectName" placeholder="模糊匹配" clearable />
          </div>
          <div class="filter-field">
            <label class="filter-label">业主单位</label>
            <el-input v-model="filters.ownerUnit" placeholder="模糊匹配" clearable />
          </div>
          <div class="filter-field">
            <label class="filter-label">项目负责人</label>
            <el-input v-model="filters.projectLeaderName" placeholder="精准匹配" clearable />
          </div>
          <div class="filter-field">
            <label class="filter-label">投标负责人</label>
            <el-input v-model="filters.biddingLeaderName" placeholder="精准匹配" clearable />
          </div>
        </div>
        <div class="filter-row">
          <div class="filter-field filter-field--wide">
            <label class="filter-label">缴纳日期</label>
            <el-date-picker v-model="filters.paymentDateRange" type="daterange" range-separator="~"
              start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" />
          </div>
          <div class="filter-field filter-field--wide">
            <label class="filter-label">应退日期</label>
            <el-date-picker v-model="filters.expectedReturnDateRange" type="daterange" range-separator="~"
              start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" />
          </div>
          <div class="filter-field">
            <label class="filter-label">状态</label>
            <el-select v-model="filters.status" placeholder="全部" clearable>
              <el-option label="已退回" value="RETURNED" />
              <el-option label="未到期" value="PENDING" />
              <el-option label="已超期" value="OVERDUE" />
            </el-select>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%; table-layout: fixed;" class="margin-table">
        <el-table-column type="index" label="序号" width="65" align="center" />
        <el-table-column prop="projectName" label="项目名称" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" @click="goToProject(row.projectId)">{{ row.projectName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="ownerUnit" label="业主单位" min-width="160" />
        <el-table-column prop="projectLeaderName" label="项目负责人" width="120" />
        <el-table-column prop="depositAmount" label="保证金金额" width="160" align="right">
          <template #default="{ row }">¥{{ fmtMoney(row.depositAmount) }}</template>
        </el-table-column>
        <el-table-column prop="paymentDate" label="缴纳日期" width="115">
          <template #default="{ row }">{{ fmtDate(row.paymentDate) }}</template>
        </el-table-column>
        <el-table-column prop="depositPaymentMethod" label="缴纳方式" width="110" />
        <el-table-column prop="payeeName" label="收款方" min-width="140" />
        <el-table-column prop="payeeAccount" label="收款账号" width="150" />
        <el-table-column prop="expectedReturnDate" label="应退日期" width="110">
          <template #default="{ row }">{{ fmtDate(row.expectedReturnDate) }}</template>
        </el-table-column>
        <el-table-column prop="returnedAmount" label="退回金额" width="150" align="right">
          <template #default="{ row }">{{ row.returnedAmount != null ? '¥' + fmtMoney(row.returnedAmount) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="serviceFeeAmount" label="服务费" width="130" align="right">
          <template #default="{ row }">{{ row.serviceFeeAmount != null ? '¥' + fmtMoney(row.serviceFeeAmount) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="actualReturnDate" label="退回日期" width="110">
          <template #default="{ row }">{{ fmtDate(row.actualReturnDate) }}</template>
        </el-table-column>
        <el-table-column prop="statusLabel" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.statusLabel)" effect="dark" size="small">
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

function buildFilterParams() {
  const p = {}
  if (filters.projectName) p.projectName = filters.projectName
  if (filters.ownerUnit) p.ownerUnit = filters.ownerUnit
  if (filters.projectLeaderName) p.projectLeaderName = filters.projectLeaderName
  if (filters.biddingLeaderName) p.biddingLeaderName = filters.biddingLeaderName
  if (filters.paymentDateRange?.[0]) p.paymentDateStart = filters.paymentDateRange[0]
  if (filters.paymentDateRange?.[1]) p.paymentDateEnd = filters.paymentDateRange[1]
  if (filters.expectedReturnDateRange?.[0]) p.expectedReturnDateStart = filters.expectedReturnDateRange[0]
  if (filters.expectedReturnDateRange?.[1]) p.expectedReturnDateEnd = filters.expectedReturnDateRange[1]
  if (filters.status) p.status = filters.status
  return p
}
async function fetchList() {
  loading.value = true
  try {
    const params = { page: pagination.page, size: pagination.size, ...buildFilterParams() }
    const res = await httpClient.get('/api/resource/margin/list', { params })
    if (res?.data) { tableData.value = res.data.data || []; pagination.total = res.data.total || 0 }
  } finally { loading.value = false }
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

async function handleExport() {
  try {
    const res = await httpClient.get('/api/resource/margin/export', { params: buildFilterParams(), responseType: 'blob' })
    const url = window.URL.createObjectURL(res.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `保证金台账_${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.xlsx`
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  } catch (e) { console.error('Export failed:', e) }
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

/* 筛选区域新样式 */
.filter-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--border-light);
}

.filter-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: 500;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  cursor: pointer;
  transition: all 150ms ease;
  background: var(--bg-card);
  color: var(--text-secondary);
}

.filter-btn:hover {
  background: var(--surface-hover);
  border-color: var(--gray-300);
}

.filter-btn--primary {
  background: var(--brand-xiyu-logo);
  color: white;
  border-color: var(--brand-xiyu-logo);
}

.filter-btn--primary:hover {
  background: #256a4d;
  border-color: #256a4d;
}

.filter-btn--success {
  background: #67c23a;
  color: white;
  border-color: #67c23a;
}

.filter-btn--success:hover {
  background: #529b2e;
  border-color: #529b2e;
}

.filter-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-row {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  flex-wrap: wrap;
}

.filter-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  min-width: 140px;
}

.filter-field--wide {
  flex: 2;
  min-width: 240px;
}

.filter-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
}

.filter-field .el-input,
.filter-field .el-select,
.filter-field .el-date-editor {
  width: 100% !important;
}

.table-card { margin-bottom: 20px; }

/* 表格样式优化 */
.margin-table .el-table__cell {
  white-space: nowrap !important;
}

.margin-table .el-table__cell .cell {
  white-space: nowrap !important;
  padding-left: 12px;
  padding-right: 12px;
}

.margin-table .el-table__cell:last-child .cell {
  padding-right: 12px;
}

.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
