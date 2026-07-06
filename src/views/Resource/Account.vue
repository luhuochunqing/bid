<template>
  <div class="account-page">
    <!-- CO-516: 搜索区 -->
    <el-card class="search-card">
        <el-form :inline="true">
          <el-form-item label="平台名称">
            <el-input v-model="searchForm.platform" placeholder="请输入" clearable />
          </el-form-item>
          <el-form-item label="是否有 CA">
            <el-select v-model="searchForm.hasCa" placeholder="全部" clearable>
              <el-option label="是" value="yes" />
              <el-option label="否" value="no" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadAccounts">
              <el-icon><Search /></el-icon> 搜索
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <div class="toolbar">
        <div class="toolbar-left">
          <button v-if="!isProjectLeader" class="toolbar-btn toolbar-btn--primary" @click="handleCreate">
            <el-icon><Plus /></el-icon><span>添加账户</span>
          </button>
          <button v-if="!isProjectLeader" class="toolbar-btn" @click="showImportDialog = true"><el-icon><Upload /></el-icon><span>批量导入</span></button>
        </div>
        <div v-if="!isProjectLeader" class="toolbar-right">
          <button class="toolbar-btn" :disabled="exporting" @click="handleExport">
            <el-icon><Download /></el-icon><span>{{ exporting ? '导出中...' : '导出' }}</span>
          </button>
        </div>
      </div>

      <!-- CO-516: Tabs 切换（平台账户管理 / 我的申请 / 我的审批），tab 栏位于搜索区下方 -->
      <el-tabs v-model="activeTab" class="account-tabs" @tab-change="onTabChange">
        <el-tab-pane label="平台账户管理" name="accounts">
      <el-card>
        <template #header>
          <div class="card-header">
            <span class="card-title">平台账户管理</span>
            <span class="record-count">共 {{ totalCount }} 条记录</span>
          </div>
        </template>
          <el-table :data="pagedAccounts" stripe max-height="calc(100vh - 280px)" scrollbar-always-on @row-click="onRowClick" @selection-change="handleSelectionChange" ref="tableRef">
            <el-table-column type="selection" width="50" align="center" />
            <el-table-column type="index" label="序号" width="65" align="center" />
            <el-table-column prop="platform" label="平台名称" min-width="180">
              <template #default="{ row }">
                <div class="platform-info">
                  <el-icon class="platform-icon"><Platform /></el-icon>
                  <span :class="{ 'row-link': !isProjectLeader }">{{ row.platform }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="url" label="网址" min-width="200">
              <template #default="{ row }">
                <el-link v-if="row.url" :href="row.url" target="_blank" type="primary" :underline="false">{{ row.url }}</el-link>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="username" label="账号" width="150" />
            <el-table-column label="密码" width="100">
              <template #default="{ row }">
                <PasswordCell :row="row" :password="password" :can-reveal="canRevealPasswordFor(row)" />
              </template>
            </el-table-column>
            <el-table-column prop="contactPersonLabel" label="账号保管员" width="140" />
            <el-table-column label="是否有 CA" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="row.hasCa ? 'success' : 'info'" size="small">{{ row.hasCa ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="registrant" label="注册人" width="120" />
            <el-table-column prop="registerPhone" label="注册手机" width="140" />
            <el-table-column prop="registerEmail" label="注册邮箱" width="180" />
            <el-table-column label="操作" width="160" fixed="right" align="center">
              <template #default="{ row }">
                <AccountRowActions :row="row" :actions="rowActions(row)" @edit="handleEdit" @return="handleReturn" @borrow="handleBorrow" @take-down="handleTakeDown" />
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination
              v-if="totalCount > 0"
              v-model:current-page="pagination.page"
              v-model:page-size="pagination.pageSize"
              :page-sizes="pageSizes"
              :total="totalCount"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="handleSizeChange"
            />
          </div>
        </el-card>
        </el-tab-pane>

        <!-- Tab 2: 我的申请 -->
        <el-tab-pane label="我的申请" name="applications">
          <el-table :data="myApplications" stripe max-height="calc(100vh - 280px)" scrollbar-always-on empty-text="暂无借用申请" v-loading="myApplicationsLoading">
            <el-table-column type="index" label="序号" width="70" align="center" />
            <el-table-column prop="accountId" label="平台" min-width="160">
              <template #default="{ row }">{{ accountName(row.accountId) }}</template>
            </el-table-column>
            <el-table-column prop="purpose" label="使用目的" min-width="180" show-overflow-tooltip />
            <el-table-column prop="expectedReturnAt" label="预计归还" min-width="140">
              <template #default="{ row }">{{ formatBorrowDate(row.expectedReturnAt) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="borrowStatusType(row.status)" size="small">{{ borrowStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING_APPROVAL'" link type="danger" size="small" @click="cancelBorrowApplication(row)">撤销</el-button>
                <span v-else class="op-placeholder">--</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 3: 我的审批 -->
        <el-tab-pane label="我的审批" name="approvals">
          <el-table :data="myApprovals" stripe max-height="calc(100vh - 280px)" scrollbar-always-on empty-text="暂无待审批申请" v-loading="myApprovalsLoading">
            <el-table-column type="index" label="序号" width="70" align="center" />
            <el-table-column prop="accountId" label="平台" min-width="160">
              <template #default="{ row }">{{ accountName(row.accountId) }}</template>
            </el-table-column>
            <el-table-column prop="applicantName" label="申请人" width="140">
              <template #default="{ row }">{{ row.applicantName || '未知' }}{{ row.applicantEmployeeNo ? `（${row.applicantEmployeeNo}）` : '' }}</template>
            </el-table-column>
            <el-table-column prop="purpose" label="使用目的" min-width="160" show-overflow-tooltip />
            <el-table-column prop="expectedReturnAt" label="预计归还" min-width="140">
              <template #default="{ row }">{{ formatBorrowDate(row.expectedReturnAt) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="borrowStatusType(row.status)" size="small">{{ borrowStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 'PENDING_APPROVAL'">
                  <el-button link type="primary" size="small" @click="approveBorrowApplication(row)">通过</el-button>
                  <el-button link type="danger" size="small" @click="rejectBorrowApplication(row)">拒绝</el-button>
                </template>
                <el-button v-else-if="row.status === 'BORROWED'" link type="primary" size="small" @click="openBorrowAppReturn(row)">登记归还</el-button>
                <span v-else class="op-placeholder">--</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>

    <AccountBorrowDialog v-model="showBorrowDialog" :account="currentAccount" @submitted="onBorrowSubmitted" />
    <AccountReturnDialog v-model="showReturnDialog" :account="currentReturnAccount" @submitted="onAccountReturned" />
    <AccountDetailDialog v-model="showDetailDialog" :data="currentAccountDetail" :actions="rowActions(currentAccountDetail || {})" @edit="editFromDetail" @return="handleReturnFromDetail" />
    <AccountFormDialog v-model="showCreateDialog" :edit-row="editRow" @saved="loadAccounts" />
    <AccountImportDialog v-model="showImportDialog" @imported="loadAccounts" />
    <!-- CO-516: 审批列表「登记归还」弹窗（原 AccountBorrowApplications 子组件内联） -->
    <AccountBorrowReturnDialog v-model="showBorrowAppReturnDialog" :application="currentReturnApplication" @submitted="onBorrowAppReturned" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Platform, Download, Upload } from '@element-plus/icons-vue'
import { resourcesApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useListPagination } from '@/composables/useListPagination'
import { useAccountRowActions } from './composables/useAccountRowActions.js'
import { usePasswordReveal } from './composables/usePasswordReveal.js'
import { useAccountExport } from './composables/useAccountExport.js'
import { useAccountBorrowApplications } from './composables/useAccountBorrowApplications.js'
import AccountFormDialog from './AccountFormDialog.vue'
import AccountDetailDialog from './AccountDetailDialog.vue'
import AccountBorrowDialog from './AccountBorrowDialog.vue'
import AccountReturnDialog from './AccountReturnDialog.vue'
import AccountImportDialog from './components/AccountImportDialog.vue'
import AccountBorrowReturnDialog from './AccountBorrowReturnDialog.vue'
import AccountRowActions from './AccountRowActions.vue'
import PasswordCell from './components/PasswordCell.vue'

const searchForm = ref({ platform: '', hasCa: '' })
const selectedRows = ref([])
const tableRef = ref(null)
const handleSelectionChange = (rows) => { selectedRows.value = rows }

const userStore = useUserStore()
const userRoleCode = computed(() => userStore.currentUser?.roleCode || userStore.currentUser?.role || '')
const isProjectLeader = computed(() => userRoleCode.value === 'bid-projectLeader')
const accounts = ref([])

const filteredAccounts = computed(() => {
  let list = accounts.value
  if (searchForm.value.hasCa === 'yes') list = list.filter(a => a.hasCa)
  if (searchForm.value.hasCa === 'no') list = list.filter(a => !a.hasCa)
  return list
})

const { pagination, pageSizes, totalCount, pagedData: pagedAccounts, handleSizeChange, resetPage } = useListPagination(filteredAccounts)
const { rowActions, canRevealPasswordFor } = useAccountRowActions({ userStore, userRoleCode })
const password = usePasswordReveal((id) => resourcesApi.accounts.getPassword(id))

const showBorrowDialog = ref(false)
const showReturnDialog = ref(false)
const showDetailDialog = ref(false)
const showCreateDialog = ref(false)
const currentAccount = ref(null)
const currentReturnAccount = ref(null)
const currentAccountDetail = ref(null)
const editRow = ref(null)
const showImportDialog = ref(false)

// CO-516: 视图切换状态 + 我的申请/我的审批 数据（懒加载，逻辑提取至 composable）
const {
  activeTab, myApplications, myApprovals,
  myApplicationsLoading, myApprovalsLoading,
  showBorrowAppReturnDialog, currentReturnApplication,
  accountName, formatBorrowDate, borrowStatusLabel, borrowStatusType,
  loadMyApplications, onTabChange,
  cancelBorrowApplication, approveBorrowApplication,
  rejectBorrowApplication, openBorrowAppReturn, onBorrowAppReturned
} = useAccountBorrowApplications({ accounts })

// CO-400 二轮：列表 row 对非特权角色是脱敏 SummaryDTO，详情/编辑前都需调详情接口拉完整 DTO。
const loadAccountDetail = async (row) => {
  try {
    const res = await resourcesApi.accounts.getDetail(row.id)
    if (res?.data) return res.data
  } catch (e) {
    console.error('Failed to load account detail:', e)
  }
  return row
}

const loadAccounts = async () => {
  try {
    const res = await resourcesApi.accounts.getList(searchForm.value)
    if (!res?.success) {
      ElMessage.error(res?.msg || '账户数据加载失败')
      accounts.value = []
      return
    }
    const list = Array.isArray(res.data) ? res.data : []
    // CO-400 三轮：列表 row 是脱敏 SummaryDTO，对每行调 getDetail 拉完整 DTO（用户已确认接受 N+1）。
    const detailed = await Promise.all(list.map(row => loadAccountDetail(row)))
    accounts.value = detailed
    resetPage()
  } catch (e) {
    console.error('Failed to load accounts:', e)
    accounts.value = []
    ElMessage.error('账户数据加载失败')
  }
}

const onRowClick = async (row) => {
  if (isProjectLeader.value) return
  currentAccountDetail.value = await loadAccountDetail(row)
  showDetailDialog.value = true
}

const handleCreate = () => { editRow.value = null; showCreateDialog.value = true }
const editFromDetail = () => {
  editRow.value = currentAccountDetail.value?.raw || currentAccountDetail.value
  showDetailDialog.value = false
  showCreateDialog.value = true
}
const handleReturnFromDetail = () => {
  currentReturnAccount.value = currentAccountDetail.value?.raw || currentAccountDetail.value
  showReturnDialog.value = true
}
const handleBorrow = (row) => { currentAccount.value = row; showBorrowDialog.value = true }
const onBorrowSubmitted = () => {
  loadAccounts()
  // CO-516: 借用申请提交后同步刷新「我的申请」（若已加载过）
  if (myApplications.value.length > 0 || myApplicationsLoading.value) {
    loadMyApplications()
  }
}
const handleEdit = async (row) => {
  editRow.value = await loadAccountDetail(row.raw || row)
  showCreateDialog.value = true
}
const handleReturn = (row) => { currentReturnAccount.value = row; showReturnDialog.value = true }
const handleTakeDown = async (row) => {
  try {
    await ElMessageBox.confirm(`确定下架平台「${row.platform}」吗？`, '确认下架', { type: 'warning' })
  } catch {
    return
  }
  try {
    const res = await resourcesApi.accounts.delete(row.id)
    if (!res?.success) { ElMessage.error(res?.msg || '下架失败'); return }
    ElMessage.success('下架成功')
    loadAccounts()
  } catch (e) {
    console.error('Failed to take down account:', e)
    ElMessage.error('下架失败')
  }
}
const onAccountReturned = () => {
  showReturnDialog.value = false
  showDetailDialog.value = false
  loadAccounts()
}

const { exporting, handleExport: baseHandleExport } = useAccountExport()
const handleExport = () => baseHandleExport(selectedRows.value)

onMounted(() => { loadAccounts() })
</script>

<style scoped src="./Account.scss" lang="scss"></style>
