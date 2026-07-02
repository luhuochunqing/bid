<template>
  <el-card class="borrow-applications-card">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="我的申请" name="applications">
        <el-table :data="applications" stripe v-loading="loading">
          <el-table-column prop="accountId" label="平台" min-width="160">
            <template #default="{ row }">{{ accountName(row.accountId) }}</template>
          </el-table-column>
          <el-table-column prop="purpose" label="使用目的" min-width="180" show-overflow-tooltip />
          <el-table-column prop="expectedReturnAt" label="预计归还" min-width="140">
            <template #default="{ row }">{{ formatDate(row.expectedReturnAt) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'PENDING_APPROVAL'" link type="danger" size="small" @click="cancel(row)">撤销</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我的审批" name="approvals">
        <el-table :data="approvals" stripe v-loading="loading">
          <el-table-column prop="accountId" label="平台" min-width="160">
            <template #default="{ row }">{{ accountName(row.accountId) }}</template>
          </el-table-column>
          <el-table-column prop="applicantName" label="申请人" width="120">
            <template #default="{ row }">{{ row.applicantName || '未知' }}{{ row.applicantEmployeeNo ? `（${row.applicantEmployeeNo}）` : '' }}</template>
          </el-table-column>
          <el-table-column prop="purpose" label="使用目的" min-width="160" show-overflow-tooltip />
          <el-table-column prop="expectedReturnAt" label="预计归还" min-width="140">
            <template #default="{ row }">{{ formatDate(row.expectedReturnAt) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'PENDING_APPROVAL'">
                <el-button link type="primary" size="small" @click="approve(row)">通过</el-button>
                <el-button link type="danger" size="small" @click="reject(row)">拒绝</el-button>
              </template>
              <el-button v-else-if="row.status === 'BORROWED'" link type="primary" size="small" @click="openReturn(row)">登记归还</el-button>
              <span v-else class="op-placeholder">--</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <ReturnDialog v-model="showReturnDialog" :application="currentReturnApplication" @submitted="reload" />
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resourcesApi } from '@/api'
import ReturnDialog from './AccountBorrowReturnDialog.vue'

const props = defineProps({
  accounts: { type: Array, default: () => [] }
})

const activeTab = ref('applications')
const applications = ref([])
const approvals = ref([])
const loading = ref(false)
const showReturnDialog = ref(false)
const currentReturnApplication = ref(null)
const localAccounts = ref([])

const allAccounts = computed(() => props.accounts.length > 0 ? props.accounts : localAccounts.value)

const accountName = (accountId) => {
  const account = allAccounts.value.find(a => a.id === accountId || a.raw?.id === accountId)
  return account?.platform || account?.accountName || `平台#${accountId}`
}

const formatDate = (value) => {
  if (!value) return '-'
  const d = new Date(value)
  return isNaN(d.getTime()) ? value : d.toLocaleDateString('zh-CN')
}

const statusLabel = (status) => {
  const map = {
    PENDING_APPROVAL: '待审批',
    BORROWED: '已借出',
    REJECTED: '已拒绝',
    RETURNED: '已归还',
    CANCELLED: '已撤销'
  }
  return map[status] || status
}

const statusType = (status) => {
  if (status === 'PENDING_APPROVAL') return 'warning'
  if (status === 'BORROWED') return 'success'
  if (status === 'REJECTED' || status === 'CANCELLED') return 'danger'
  if (status === 'RETURNED') return 'info'
  return ''
}

const loadApplications = async () => {
  loading.value = true
  try {
    const res = await resourcesApi.accounts.getMyBorrowApplications()
    applications.value = Array.isArray(res?.data) ? res.data : []
  } catch (e) {
    console.error('Failed to load my applications:', e)
    applications.value = []
  } finally {
    loading.value = false
  }
}

const loadApprovals = async () => {
  loading.value = true
  try {
    const res = await resourcesApi.accounts.getMyBorrowApprovals()
    approvals.value = Array.isArray(res?.data) ? res.data : []
  } catch (e) {
    console.error('Failed to load my approvals:', e)
    approvals.value = []
  } finally {
    loading.value = false
  }
}

const onTabChange = (tab) => {
  if (tab === 'applications') loadApplications()
  else loadApprovals()
}

const reload = () => {
  showReturnDialog.value = false
  loadApprovals()
}

const reloadApplications = () => {
  loadApplications()
}

defineExpose({ reloadApplications, reloadApprovals: loadApprovals })

const approve = async (row) => {
  try {
    const res = await resourcesApi.accounts.approveBorrowApplication(row.id, { comment: '' })
    if (!res?.success) { ElMessage.error(res?.msg || '审批失败'); return }
    ElMessage.success('已审批通过')
    await loadApprovals()
  } catch (e) {
    ElMessage.error('审批失败')
  }
}

const reject = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请填写拒绝原因', '拒绝申请', {
      confirmButtonText: '确认拒绝',
      cancelButtonText: '取消',
      inputValidator: (v) => v ? true : '拒绝原因不能为空',
      inputErrorMessage: '拒绝原因不能为空'
    })
    const res = await resourcesApi.accounts.rejectBorrowApplication(row.id, { comment: value })
    if (!res?.success) { ElMessage.error(res?.msg || '拒绝失败'); return }
    ElMessage.success('已拒绝')
    await loadApprovals()
  } catch {
    // 用户取消
  }
}

const cancel = async (row) => {
  try {
    await ElMessageBox.confirm('确定撤销该申请？', '确认撤销', { type: 'warning' })
    const res = await resourcesApi.accounts.cancelBorrowApplication(row.id)
    if (!res?.success) { ElMessage.error(res?.msg || '撤销失败'); return }
    ElMessage.success('已撤销')
    await loadApplications()
  } catch {
    // 用户取消
  }
}

const openReturn = (row) => {
  currentReturnApplication.value = row
  showReturnDialog.value = true
}

const loadAccounts = async () => {
  if (props.accounts.length > 0) return
  try {
    const res = await resourcesApi.accounts.getList({})
    localAccounts.value = Array.isArray(res?.data) ? res.data : []
  } catch (e) {
    localAccounts.value = []
  }
}

onMounted(() => {
  loadAccounts()
  loadApplications()
})
</script>

<style scoped lang="scss">
.borrow-applications-card {
  margin-top: var(--spacing-md);
}
.op-placeholder {
  color: var(--text-color-placeholder, #c0c4cc);
  font-size: 12px;
}
</style>
