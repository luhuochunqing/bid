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
        <button class="toolbar-btn toolbar-btn--primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>
          <span>添加账户</span>
        </button>
        <button v-if="!isProjectLeader" class="toolbar-btn" @click="showImportDialog = true"><el-icon><Upload /></el-icon><span>批量导入</span></button>
        <button class="toolbar-btn" :disabled="selectedRows.length === 0" @click="handleBatchBorrow">
          <el-icon><Key /></el-icon>
          <span>批量借阅</span>
        </button>
        <button class="toolbar-btn" :disabled="selectedRows.length === 0" @click="handleBatchReturn">
          <el-icon><CircleCheck /></el-icon>
          <span>批量归还</span>
        </button>
        <button class="toolbar-btn" :disabled="selectedRows.length === 0" @click="handleBatchEdit">
          <el-icon><Edit /></el-icon>
          <span>批量编辑</span>
        </button>
        <button class="toolbar-btn" :disabled="selectedRows.length === 0" @click="handleBatchDelete">
          <el-icon><Delete /></el-icon>
          <span>批量删除</span>
        </button>
      </div>
      <div class="toolbar-right">
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
        <el-table-column prop="url" label="网址" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link v-if="row.url" :href="row.url" target="_blank" type="primary" :underline="false">
              {{ row.url }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <template v-if="!isProjectLeader">
          <el-table-column prop="username" label="账号" width="150" />
          <el-table-column prop="contactPerson" label="联系人" width="120" />
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
          <el-table-column label="是否有 CA" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.hasCa ? 'success' : 'info'" size="small">{{ row.hasCa ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
                    <el-table-column prop="custodianName" label="账号保管员" width="120" />
          <el-table-column prop="caCustodianName" label="CA 保管员" width="120" />

        </template>
        <template v-else>
          <el-table-column label="是否有 CA" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.hasCa ? 'success' : 'info'" size="small">{{ row.hasCa ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right" align="center">
            <template #default="{ row }">
              <el-tooltip content="申请使用" placement="top">
                <el-button :icon="Key" circle size="small" type="primary"
                  :disabled="row.status !== 'available'"
                  @click.stop="handleBorrow(row)" />
              </el-tooltip>
            </template>
          </el-table-column>
        </template>
      </el-table>
    </el-card>

   <AccountBorrowDialog v-model="showBorrowDialog" :account="currentAccount" @submitted="loadAccounts" />
    <AccountReturnDialog v-model="showReturnDialog" :account="currentReturnAccount" @submitted="onAccountReturned" />
    <AccountDetailDialog v-model="showDetailDialog" :data="currentAccountDetail" @edit="editFromDetail" @return="handleReturnFromDetail" />
    <AccountFormDialog v-model="showCreateDialog" :edit-row="editRow" @saved="loadAccounts" /><AccountImportDialog v-model="showImportDialog" @imported="loadAccounts" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Platform, View, Edit, Delete, Key, Hide, CircleCheck, Download, Upload } from '@element-plus/icons-vue'
import { resourcesApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { usePasswordReveal } from './composables/usePasswordReveal.js'
import AccountFormDialog from './AccountFormDialog.vue'
import AccountDetailDialog from './AccountDetailDialog.vue'
import AccountBorrowDialog from './AccountBorrowDialog.vue'
import AccountReturnDialog from './AccountReturnDialog.vue'; import AccountImportDialog from './components/AccountImportDialog.vue'

const searchForm = ref({
  platform: '',
  hasCa: ''
})
// 选中行
const selectedRows = ref([])
const tableRef = ref(null)

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

const userStore = useUserStore()
const isProjectLeader = computed(() => {
  // 蓝图：项目负责人（bid-projectLeader）使用精简视图；投标组长（bid-TeamLeader）看全量。
  return userStore.userRole === 'bid-projectLeader'
})

const password = usePasswordReveal((id) => resourcesApi.accounts.getPassword(id))

const accounts = ref([])
const showBorrowDialog = ref(false)
const showReturnDialog = ref(false)
const showDetailDialog = ref(false)
const showCreateDialog = ref(false)
const currentAccount = ref(null)
const currentReturnAccount = ref(null)
const currentAccountDetail = ref(null)
const editRow = ref(null); const showImportDialog = ref(false)

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

const onRowClick = (row) => {
  if (isProjectLeader.value) return
  currentAccountDetail.value = row
  showDetailDialog.value = true
}

const handleEdit = (row) => {
  editRow.value = row
  showCreateDialog.value = true
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

const handleBorrow = (row) => {
  currentAccount.value = row
  showBorrowDialog.value = true
}

const handleReturn = (row) => {
  currentReturnAccount.value = row?.raw || row
  showReturnDialog.value = true
}

const onAccountReturned = () => {
  showReturnDialog.value = false
  showDetailDialog.value = false
  loadAccounts()
}

// 批量操作
const handleBatchBorrow = () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要借阅的账户')
    return
  }
  // TODO: 实现批量借阅
  ElMessage.info(`批量借阅 ${selectedRows.value.length} 个账户`)
}

const handleBatchReturn = () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要归还的账户')
    return
  }
  // TODO: 实现批量归还
  ElMessage.info(`批量归还 ${selectedRows.value.length} 个账户`)
}

const handleBatchEdit = () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要编辑的账户')
    return
  }
  // TODO: 实现批量编辑
  ElMessage.info(`批量编辑 ${selectedRows.value.length} 个账户`)
}

const handleBatchDelete = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要删除的账户')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 个账户吗？`, '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    // TODO: 实现批量删除
    ElMessage.success(`已删除 ${selectedRows.value.length} 个账户`)
    selectedRows.value = []
    loadAccounts()
  } catch {
    // 用户取消
  }
}

const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

const handleMoreAction = async (command, row) => {
  switch (command) {
    case 'view':
      currentAccountDetail.value = row
      showDetailDialog.value = true
      {
        const response = await resourcesApi.accounts.getDetail(row.id)
        if (!response?.success) {
          ElMessage.error(response?.msg || '账户详情加载失败')
          return
        }
        currentAccountDetail.value = response.data || row
      }
      break
    case 'reset':
      try {
        await ElMessageBox.confirm(`确定要重置账户"${row.platform}"的密码吗？`, '重置密码', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        const resetResponse = await resourcesApi.accounts.update(row.id, { resetPassword: true })
        if (!resetResponse?.success) {
          ElMessage.error(resetResponse?.message || '重置密码失败')
          return
        }
        ElMessage.success('密码已重置')
      } catch {
        // 用户取消
      }
      break
    case 'toggle': {
      const newStatus = row.status === 'available' ? 'disabled' : 'available'
      const toggleResponse = await resourcesApi.accounts.update(row.id, { status: newStatus.toUpperCase() })
      if (!toggleResponse?.success) {
        ElMessage.error(toggleResponse?.message || '状态更新失败')
        return
      }
      await loadAccounts()
      ElMessage.success(`已${newStatus === 'available' ? '启用' : '禁用'}账户：${row.platform}`)
      break
    }
    case 'delete':
      try {
        await ElMessageBox.confirm(`确定要删除账户"${row.platform}"吗？`, '确认删除', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        const response = await resourcesApi.accounts.delete(row.id)
        if (!response?.success) {
          ElMessage.error(response?.msg || '删除失败')
          return
        }
        await loadAccounts()
        ElMessage.success(`删除账户：${row.platform}`)
      } catch {
        // 用户取消
      }
      break
  }
}

onMounted(() => {
  loadAccounts()
})
</script>

<style scoped lang="scss">
.account-page {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.platform-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.platform-icon {
  color: #409eff;
}

/* 工具栏 */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  margin-bottom: 16px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  font-size: 13px;
  font-weight: 500;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  cursor: pointer;
  transition: all 150ms ease;
  background: var(--bg-card);
  color: var(--text-secondary);
}

.toolbar-btn:hover {
  background: var(--surface-hover);
  border-color: var(--gray-300);
}

.toolbar-btn--primary {
  background: var(--brand-xiyu-logo);
  color: white;
  border-color: var(--brand-xiyu-logo);
}

.toolbar-btn--primary:hover {
  background: #256a4d;
  border-color: #256a4d;
}

/* 工具栏按钮禁用状态 */
.toolbar-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.toolbar-btn:disabled:hover {
  background: var(--bg-card);
  border-color: var(--border-light);
}

/* 密码单元格样式 */
.password-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
}

.password-row {
  display: flex;
  align-items: center;
  width: 100%;
}

.password-text {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: var(--text-secondary);
}

.password-toggle-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 150ms ease;
}

.password-toggle-btn:hover {
  background: var(--surface-hover);
  color: var(--brand-xiyu-logo);
}

/* 移动端响应式样式 */
@media (max-width: 768px) {
  .account-page {
    padding: 12px;
  }

  .search-card {
    margin-bottom: 12px;
  }

  .search-card :deep(.el-form) {
    display: block;
  }

  .search-card :deep(.el-form-item) {
    display: block;
    margin-right: 0;
    margin-bottom: 12px;
  }

  .search-card :deep(.el-input),
  .search-card :deep(.el-select) {
    width: 100% !important;
  }

  /* 表格移动端优化 */
  .table-card :deep(.el-table) {
    font-size: 12px;
  }

  .table-card :deep(.el-table__body-wrapper) {
    overflow-x: auto;
  }

  .table-card :deep(.el-table__cell) {
    padding: 8px 4px;
  }

  /* 头部按钮移动端优化 */
  .card-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .card-header .el-button {
    width: 100%;
  }

  /* 对话框移动端优化 */
  :deep(.el-dialog) {
    width: 95% !important;
    margin: 0 auto;
  }

  :deep(.el-dialog__body) {
    padding: 16px;
  }

  /* 分页移动端优化 */
  .pagination-wrapper {
    justify-content: center;
  }

  .pagination-wrapper :deep(.el-pagination) {
    flex-wrap: wrap;
    justify-content: center;
  }

  .pagination-wrapper :deep(.el-pagination__sizes),
  .pagination-wrapper :deep(.el-pagination__jump) {
    display: none;
  }
}

/* 触摸设备优化 */
@media (hover: none) and (pointer: coarse) {
  .el-button {
    min-height: 44px;
  }
}
.row-link { color: var(--el-color-primary); cursor: pointer; }
.row-link:hover { text-decoration: underline; }
</style>
