<template>
  <div class="contract-borrow-page">
    <div class="page-header">
      <div>
        <h1>合同借阅</h1>
        <p>合同借阅申请、审批、归还与历史追踪</p>
      </div>
      <el-button type="primary" @click="openCreateDialog">发起申请</el-button>
    </div>

    <el-row :gutter="16" class="summary-row">
      <el-col v-for="card in summaryCards" :key="card.key" :xs="12" :sm="8" :md="4">
        <div class="summary-card">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
        </div>
      </el-col>
    </el-row>

    <div class="toolbar">
      <el-input v-model="filters.keyword" clearable placeholder="合同名/编号/客户/申请人" @keyup.enter="loadList" />
      <el-select v-model="filters.status" clearable placeholder="状态">
        <el-option label="待审批" value="PENDING_APPROVAL" />
        <el-option label="已审批" value="APPROVED" />
        <el-option label="已逾期" value="OVERDUE" />
        <el-option label="已归还" value="RETURNED" />
        <el-option label="已驳回" value="REJECTED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
      <el-button @click="loadList">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border>
      <el-table-column prop="contractNo" label="合同编号" min-width="130" />
      <el-table-column prop="contractName" label="合同名称" min-width="220" />
      <el-table-column prop="borrowerName" label="申请人" width="110" />
      <el-table-column prop="borrowerDept" label="部门" width="130" />
      <el-table-column prop="purpose" label="用途" min-width="180" />
      <el-table-column prop="expectedReturnDate" label="预计归还" width="130" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.displayStatus)">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button v-if="row.status === 'PENDING_APPROVAL'" link type="success" @click="runAction(row, 'approve')">审批</el-button>
          <el-button v-if="row.status === 'PENDING_APPROVAL'" link type="danger" @click="runAction(row, 'reject')">驳回</el-button>
          <el-button v-if="row.status === 'PENDING_APPROVAL'" link @click="runAction(row, 'cancel')">取消</el-button>
          <el-button v-if="row.status === 'APPROVED' || row.status === 'BORROWED'" link type="warning" @click="runAction(row, 'return')">归还</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="contract-borrow-pagination"
      background
      layout="total, sizes, prev, pager, next"
      :current-page="pagination.page"
      :page-size="pagination.size"
      :total="pagination.total"
      :page-sizes="[10, 20, 50, 100]"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />

    <el-dialog v-model="createDialogVisible" title="发起合同借阅" width="640px">
      <el-form :model="createForm" label-width="96px">
        <el-form-item label="合同编号"><el-input v-model="createForm.contractNo" /></el-form-item>
        <el-form-item label="合同名称"><el-input v-model="createForm.contractName" /></el-form-item>
        <el-form-item label="来源"><el-input v-model="createForm.sourceName" /></el-form-item>
        <el-form-item label="申请人"><el-input v-model="createForm.borrowerName" /></el-form-item>
        <el-form-item label="部门"><el-input v-model="createForm.borrowerDept" /></el-form-item>
        <el-form-item label="客户"><el-input v-model="createForm.customerName" /></el-form-item>
        <el-form-item label="借阅类型"><el-input v-model="createForm.borrowType" /></el-form-item>
        <el-form-item label="预计归还"><el-date-picker v-model="createForm.expectedReturnDate" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="用途"><el-input v-model="createForm.purpose" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawerVisible" title="借阅详情" size="520px">
      <el-descriptions v-if="currentRecord" :column="1" border>
        <el-descriptions-item label="合同编号">{{ currentRecord.contractNo }}</el-descriptions-item>
        <el-descriptions-item label="合同名称">{{ currentRecord.contractName }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ currentRecord.sourceName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ currentRecord.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ currentRecord.borrowerName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ currentRecord.statusLabel }}</el-descriptions-item>
        <el-descriptions-item label="用途">{{ currentRecord.purpose || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider>历史事件</el-divider>
      <el-timeline>
        <el-timeline-item
          v-for="event in events"
          :key="event.id"
          :timestamp="formatDateTime(event.createdAt)"
        >
          {{ event.eventType }} - {{ event.actorName || '-' }} {{ event.comment || '' }}
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { contractBorrowApi } from '@/api/modules/contractBorrow.js'
import { useUserStore } from '@/stores/user.js'
import './contractBorrow.css'

const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)
const records = ref([])
const overview = ref({})
const events = ref([])
const currentRecord = ref(null)
const createDialogVisible = ref(false)
const detailDrawerVisible = ref(false)

const filters = reactive({
  keyword: '',
  status: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const emptyForm = () => ({
  contractNo: '',
  contractName: '',
  sourceName: '',
  borrowerName: '',
  borrowerDept: '',
  customerName: '',
  purpose: '',
  borrowType: '原件借阅',
  expectedReturnDate: ''
})

const createForm = reactive(emptyForm())

const summaryCards = computed(() => [
  { key: 'total', label: '总申请', value: overview.value.total || 0 },
  { key: 'pendingApproval', label: '待审批', value: overview.value.pendingApproval || 0 },
  { key: 'approved', label: '已审批', value: overview.value.approved || 0 },
  { key: 'returned', label: '已归还', value: overview.value.returned || 0 },
  { key: 'overdue', label: '已逾期', value: overview.value.overdue || 0 },
  { key: 'rejected', label: '已驳回', value: overview.value.rejected || 0 }
])

function assignForm(target, source) {
  Object.assign(target, source)
}

async function loadOverview() {
  try {
    const response = await contractBorrowApi.getOverview()
    overview.value = response?.data || {}
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '合同借阅概览加载失败'))
  }
}

async function loadList() {
  loading.value = true
  try {
    const response = await contractBorrowApi.getList({
      ...filters,
      page: pagination.page,
      size: pagination.size
    })
    const pageData = response?.data || {}
    records.value = pageData.items || []
    pagination.total = Number(pageData.total || 0)
  } catch (error) {
    records.value = []
    pagination.total = 0
    ElMessage.error(resolveErrorMessage(error, '合同借阅列表加载失败'))
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  pagination.page = 1
  loadList()
}

function openCreateDialog() {
  assignForm(createForm, emptyForm())
  createDialogVisible.value = true
}

async function submitCreate() {
  submitting.value = true
  try {
    await contractBorrowApi.create({ ...createForm })
    ElMessage.success('合同借阅申请已提交')
    createDialogVisible.value = false
    await refresh()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '合同借阅申请提交失败'))
  } finally {
    submitting.value = false
  }
}

async function openDetail(row) {
  currentRecord.value = row
  detailDrawerVisible.value = true
  try {
    const response = await contractBorrowApi.getEvents(row.id)
    events.value = response?.data || []
  } catch (error) {
    events.value = []
    ElMessage.error(resolveErrorMessage(error, '合同借阅历史加载失败'))
  }
}

async function runAction(row, action) {
  const payload = {
    actorName: userStore.userName || '当前用户',
    comment: action === 'return' ? '已归还' : '同意',
    reason: action === 'reject' ? '信息不完整' : '不再需要'
  }
  try {
    if (action === 'approve') {
      await contractBorrowApi.approve(row.id, payload)
    } else if (action === 'reject') {
      await contractBorrowApi.reject(row.id, payload)
    } else if (action === 'return') {
      await contractBorrowApi.returnBack(row.id, payload)
    } else if (action === 'cancel') {
      await contractBorrowApi.cancel(row.id, payload)
    }
    ElMessage.success('状态已更新')
    await refresh()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '合同借阅状态更新失败'))
  }
}

async function refresh() {
  await Promise.all([loadOverview(), loadList()])
}

function statusTagType(status) {
  if (status === 'APPROVED') return 'success'
  if (status === 'OVERDUE') return 'danger'
  if (status === 'RETURNED') return 'info'
  if (status === 'REJECTED' || status === 'CANCELLED') return 'warning'
  return 'primary'
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

function handlePageChange(page) {
  pagination.page = page
  loadList()
}

function handleSizeChange(size) {
  pagination.size = size
  pagination.page = 1
  loadList()
}

function resolveErrorMessage(error, fallback) {
  return error?.response?.data?.msg || error?.message || fallback
}

onMounted(refresh)
</script>
