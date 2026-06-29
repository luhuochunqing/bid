<template>
  <div class="account-page">
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

    <!-- 操作工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <button v-if="!isProjectLeader" class="toolbar-btn toolbar-btn--primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          <span>添加账户</span>
        </button>
        <button v-if="!isProjectLeader" class="toolbar-btn" @click="showImportDialog = true"><el-icon><Upload /></el-icon><span>批量导入</span></button>
        <button v-if="!isProjectLeader" class="toolbar-btn" :disabled="selectedRows.length === 0" @click="handleBatchBorrow">
          <el-icon><Key /></el-icon>
          <span>批量借阅</span>
        </button>
        <button v-if="!isProjectLeader" class="toolbar-btn" :disabled="selectedRows.length === 0" @click="handleBatchReturn">
          <el-icon><CircleCheck /></el-icon>
          <span>批量归还</span>
        </button>
        <button v-if="!isProjectLeader" class="toolbar-btn" :disabled="selectedRows.length === 0" @click="handleBatchEdit">
          <el-icon><Edit /></el-icon>
          <span>批量编辑</span>
        </button>
        <button v-if="!isProjectLeader" class="toolbar-btn" :disabled="selectedRows.length === 0" @click="handleBatchDelete">
          <el-icon><Delete /></el-icon>
          <span>批量删除</span>
        </button>
      </div>
      <div v-if="!isProjectLeader" class="toolbar-right">
        <button class="toolbar-btn" @click="handleExport">
          <el-icon><Download /></el-icon>
          <span>导出</span>
        </button>
      </div>
    </div>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>平台账户管理</span>
          <span class="record-count">共 {{ accounts.length }} 条记录</span>
        </div>
      </template>

      <el-table :data="accounts" stripe @row-click="onRowClick" @selection-change="handleSelectionChange" ref="tableRef">
        <el-table-column type="selection" width="50" align="center" />
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
            <el-link v-if="row.url" :href="row.url" target="_blank" type="primary" :underline="false">
              {{ row.url }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <template v-if="isPrivilegedViewer">
          <el-table-column prop="username" label="账号" width="150" />
          <el-table-column prop="contactPersonLabel" label="联系人" width="140" />
          <el-table-column label="密码" width="100">
            <template #default="{ row }">
              <div class="password-cell">
                <div class="password-row">
                  <span class="password-text">{{ password.displayText(row.id) }}</span>
                </div>
                <button
                  class="password-toggle-btn"
                  :disabled="password.isLoading(row.id)"
                  @click.stop="password.toggle(row.id)">
                  <el-icon size="14">
                    <component :is="password.isVisible(row.id) ? Hide : View" />
                  </el-icon>
                </button>
              </div>
            </template>
          </el-table-column>
        </template>
        <el-table-column label="是否有 CA" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.hasCa ? 'success' : 'info'" size="small">{{ row.hasCa ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <AccountRowActions :row="row" :actions="rowActions(row)" @edit="handleEdit" @return="handleReturn" @borrow="handleBorrow" @take-down="handleTakeDown" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

   <AccountBorrowDialog v-model="showBorrowDialog" :account="currentAccount" @submitted="loadAccounts" />
    <AccountReturnDialog v-model="showReturnDialog" :account="currentReturnAccount" @submitted="onAccountReturned" />
    <AccountDetailDialog v-model="showDetailDialog" :data="currentAccountDetail" @edit="editFromDetail" @return="handleReturnFromDetail" />
    <AccountFormDialog v-model="showCreateDialog" :edit-row="editRow" @saved="loadAccounts" />
    <AccountImportDialog v-model="showImportDialog" @imported="loadAccounts" />
    <AccountBorrowApplications :accounts="accounts" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Platform, View, Hide, Download, Upload } from '@element-plus/icons-vue'
import { resourcesApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { usePasswordReveal } from './composables/usePasswordReveal.js'
import { useAccountBatchActions } from './composables/useAccountBatchActions.js'
import { resolveAccountActions, isCurrentUserContactPerson } from './accountActions.js'
import AccountFormDialog from './AccountFormDialog.vue'
import AccountDetailDialog from './AccountDetailDialog.vue'
import AccountBorrowDialog from './AccountBorrowDialog.vue'
import AccountReturnDialog from './AccountReturnDialog.vue'
import AccountImportDialog from './components/AccountImportDialog.vue'
import AccountBorrowApplications from './AccountBorrowApplications.vue'
import AccountRowActions from './AccountRowActions.vue'

const searchForm = ref({
  platform: '',
  hasCa: ''
})
const selectedRows = ref([])
const tableRef = ref(null)

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

const userStore = useUserStore()
const userRoleCode = computed(() => userStore.currentUser?.roleCode || userStore.currentUser?.role || '')
const isProjectLeader = computed(() => userRoleCode.value === 'bid-projectLeader')
// CO-400 二轮：与后端 PlatformAccountService.isPrivilegedViewer 对齐，
// 管理员 / 投标管理员 / 投标组长 始终能看到敏感列。
// 投标专员对自身为绑定联系人的账户，后端会返回完整 DTO，因此也需要渲染这些列；
// 非绑定联系人的行后端返回脱敏 SummaryDTO，字段不存在，自然显示为空。
const isPrivilegedViewer = computed(() => userStore.isBidManager || userRoleCode.value === 'bid-Team')
const accounts = ref([])

const rowActionsFor = (row) => resolveAccountActions({
  isManager: userStore.isBidManager,
  isBidTeam: userRoleCode.value === 'bid-Team',
  isContactPerson: isCurrentUserContactPerson(row, userStore.currentUser),
  isApplicant: userRoleCode.value === 'bid-projectLeader' || userRoleCode.value === 'sales',
  status: row.status
})
const rowActionsMap = computed(() => {
  const map = new Map()
  for (const row of accounts.value) {
    map.set(row.id, rowActionsFor(row))
  }
  return map
})
const rowActions = (row) => rowActionsMap.value.get(row.id) || {}

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

const loadAccounts = async () => {
  try {
    const res = await resourcesApi.accounts.getList(searchForm.value)
    if (!res?.success) {
      ElMessage.error(res?.msg || '账户数据加载失败')
      accounts.value = []
      return
    }
    let list = Array.isArray(res.data) ? res.data : []
    if (searchForm.value.hasCa === 'yes') list = list.filter(a => a.hasCa)
    if (searchForm.value.hasCa === 'no') list = list.filter(a => !a.hasCa)
    accounts.value = list
  } catch (e) {
    console.error('Failed to load accounts:', e)
    accounts.value = []
    ElMessage.error('账户数据加载失败')
  }
}

// CO-400 二轮：列表 row 对非特权角色是脱敏 SummaryDTO，
// 详情/编辑前都需调详情接口拉完整 PlatformAccountDTO，失败时 fallback 到列表 row。
const loadAccountDetail = async (row) => {
  try {
    const res = await resourcesApi.accounts.getDetail(row.id)
    if (res?.data) return res.data
  } catch (e) {
    console.error('Failed to load account detail:', e)
  }
  return row
}

const onRowClick = async (row) => {
  if (isProjectLeader.value) return
  // CO-400: 列表接口对非特权角色返回 PlatformAccountSummaryDTO（脱敏 6 字段），
  // 直接用列表 row 会让详情 dialog 中 5 字段为空。改为调详情接口拉完整 DTO。
  currentAccountDetail.value = await loadAccountDetail(row)
  showDetailDialog.value = true
}

const handleCreate = () => {
  editRow.value = null
  showCreateDialog.value = true
}

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
    if (!res?.success) {
      ElMessage.error(res?.msg || '下架失败')
      return
    }
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

const {
  handleBatchBorrow,
  handleBatchReturn,
  handleBatchEdit,
  handleBatchDelete
} = useAccountBatchActions({ selectedRows, loadAccounts })

const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

onMounted(() => {
  loadAccounts()
})
</script>

<style scoped src="./Account.scss" lang="scss"></style>
