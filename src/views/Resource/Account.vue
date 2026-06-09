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

    <el-card>
      <template #header>
        <div class="card-header">
          <span>平台账户管理</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon> 添加账户
          </el-button>
        </div>
      </template>

      <el-table :data="accounts" stripe @row-click="onRowClick">
        <el-table-column type="index" label="序号" width="60" />
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
          <el-table-column label="密码" width="80">
            <template #default="{ row }">
              <div class="password-cell">
                <span class="password-text">{{ passwordVisible[row.id] ? row.password : '•••' }}</span>
                <el-button
                  :icon="passwordVisible[row.id] ? Hide : View"
                  link type="primary" size="small"
                  @click.stop="togglePasswordVisibility(row.id)"
                />
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
          <el-table-column label="操作" width="140" fixed="right" align="center">
            <template #default="{ row }">
              <div class="action-buttons">
                <el-tooltip content="借阅" placement="top">
                  <el-button :icon="Key" circle size="small"
                    :type="row.status === 'available' ? 'primary' : 'info'"
                    :disabled="row.status !== 'available'"
                    @click.stop="handleBorrow(row)" />
                </el-tooltip>
              <el-tooltip content="登记归还" placement="top">
                  <el-button :icon="CircleCheck" circle size="small"
                    :type="row.status === 'in_use' ? 'success' : 'info'"
                    :disabled="row.status !== 'in_use'"
                    @click.stop="handleReturn(row)" />
                </el-tooltip>
                <el-tooltip content="编辑" placement="top">
                  <el-button :icon="Edit" circle size="small" type="warning"
                    @click.stop="handleEdit(row)" />
                </el-tooltip>
                <el-tooltip content="复制密码" placement="top">
                  <el-button :icon="CopyDocument" circle size="small" type="success"
                    @click.stop="handleCopyPassword(row)" />
                </el-tooltip>
                <el-dropdown trigger="click" @command="(cmd) => handleMoreAction(cmd, row)">
                  <el-button :icon="MoreFilled" circle size="small" />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="view" :icon="View">查看详情</el-dropdown-item>
                      <el-dropdown-item command="reset" :icon="RefreshLeft">重置密码</el-dropdown-item>
                      <el-dropdown-item command="toggle" :icon="View">
                        {{ row.status === 'available' ? '禁用账户' : '启用账户' }}
                      </el-dropdown-item>
                      <el-dropdown-item divided command="delete" :icon="Delete" style="color:#f56c6c">删除账户</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
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
    <AccountFormDialog v-model="showCreateDialog" :edit-row="editRow" @saved="loadAccounts" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Platform, View, Edit, Delete, CopyDocument, MoreFilled, Key, RefreshLeft, Hide, CircleCheck } from '@element-plus/icons-vue'
import { resourcesApi } from '@/api'
import { useUserStore } from '@/stores/user'
import AccountFormDialog from './AccountFormDialog.vue'
import AccountDetailDialog from './AccountDetailDialog.vue'
import AccountBorrowDialog from './AccountBorrowDialog.vue'
import AccountReturnDialog from './AccountReturnDialog.vue'

const searchForm = ref({
  platform: '',
  hasCa: ''
})

const userStore = useUserStore()
const isProjectLeader = computed(() => {
  const r = userStore.userRole
  return r === 'sales' || r === 'bid_lead'
})

// 密码显示状态
const passwordVisible = ref({})

const accounts = ref([])
const showBorrowDialog = ref(false)
const showReturnDialog = ref(false)
const showDetailDialog = ref(false)
const showCreateDialog = ref(false)
const currentAccount = ref(null)
const currentReturnAccount = ref(null)
const currentAccountDetail = ref(null)
const editRow = ref(null)

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
const handleReturn = (row) => {
  currentReturnAccount.value = row?.raw || row
  showReturnDialog.value = true
}

const onAccountReturned = () => {
  showReturnDialog.value = false
  showDetailDialog.value = false
  loadAccounts()
}
  currentAccount.value = row
  showBorrowDialog.value = true
}

const handleCopyPassword = async (row) => {
  let password = row.password || ''

  if (!password && true) {
    const response = await resourcesApi.accounts.getPassword(row.id)
    if (!response?.success || !response?.data?.password) {
      ElMessage.info(response?.msg || '当前账号密码不可直接查看')
      return
    }
    password = response.data.password
  }

  navigator.clipboard.writeText(password || '').then(() => {
    ElMessage.success('密码已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
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

// 切换密码可见性
const togglePasswordVisibility = (accountId) => {
  passwordVisible.value[accountId] = !passwordVisible.value[accountId]
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

/* 操作按钮样式 */
.action-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.action-buttons .el-button {
  padding: 4px;
}

.action-buttons .el-button.is-disabled {
  opacity: 0.5;
}

/* 密码单元格样式 */
.password-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.password-text {
  font-family: 'Courier New', monospace;
  font-size: 13px;
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
