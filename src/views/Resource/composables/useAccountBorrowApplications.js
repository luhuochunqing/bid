import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resourcesApi } from '@/api'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'

/**
 * CO-516: 账户借用申请/审批的顶层 tab 状态与操作逻辑。
 *
 * 原 AccountBorrowApplications.vue 子组件内联到 Account.vue 时提取，
 * 保持主组件行数在合理范围。对齐 CAManagement.vue 的「我的申请/我的审批」懒加载模式。
 *
 * @param {{ accounts: Ref<Array> }} deps
 */
export function useAccountBorrowApplications({ accounts }) {
  const activeTab = ref('accounts')
  const myApplications = ref([])
  const myApprovals = ref([])
  const myApplicationsLoading = ref(false)
  const myApprovalsLoading = ref(false)
  const showBorrowAppReturnDialog = ref(false)
  const currentReturnApplication = ref(null)

  const accountName = (accountId) => {
    const account = accounts.value.find(a => a.id === accountId || a.raw?.id === accountId)
    return account?.platform || account?.accountName || `平台#${accountId}`
  }

  const formatBorrowDate = (value) => {
    if (!value) return '-'
    const d = new Date(value)
    return isNaN(d.getTime()) ? value : d.toLocaleDateString('zh-CN')
  }

  const borrowStatusLabel = (status) => {
    const map = {
      PENDING_APPROVAL: '待审批',
      BORROWED: '已借出',
      REJECTED: '已拒绝',
      RETURNED: '已归还',
      CANCELLED: '已撤销'
    }
    return map[status] || status
  }

  const borrowStatusType = (status) => {
    if (status === 'PENDING_APPROVAL') return 'warning'
    if (status === 'BORROWED') return 'success'
    if (status === 'REJECTED' || status === 'CANCELLED') return 'danger'
    if (status === 'RETURNED') return 'info'
    return ''
  }

  const loadMyApplications = async () => {
    myApplicationsLoading.value = true
    try {
      const res = await resourcesApi.accounts.getMyBorrowApplications()
      myApplications.value = Array.isArray(res?.data) ? res.data : []
    } catch (e) {
      console.error('Failed to load my applications:', e)
      myApplications.value = []
    } finally {
      myApplicationsLoading.value = false
    }
  }

  const loadMyApprovals = async () => {
    myApprovalsLoading.value = true
    try {
      const res = await resourcesApi.accounts.getMyBorrowApprovals()
      myApprovals.value = Array.isArray(res?.data) ? res.data : []
    } catch (e) {
      console.error('Failed to load my approvals:', e)
      myApprovals.value = []
    } finally {
      myApprovalsLoading.value = false
    }
  }

  // 主组件统一懒加载：切到对应 tab 时才拉取数据，避免进入页面即触发 N+1
  // onTabChange 由 el-tabs 的 @tab-change 触发，v-model 已自动更新 activeTab
  const onTabChange = (tabName) => {
    if (tabName === 'applications') {
      loadMyApplications()
    } else if (tabName === 'approvals') {
      loadMyApprovals()
    }
  }
  // switchTab 用于非 el-tabs 场景（如返回链接），需手动设置 activeTab
  const switchTab = (tabName) => {
    activeTab.value = tabName
    onTabChange(tabName)
  }

  const cancelBorrowApplication = async (row) => {
    try {
      await ElMessageBox.confirm('确定撤销该申请？', '确认撤销', { type: 'warning' })
    } catch {
      return
    }
    try {
      const res = await resourcesApi.accounts.cancelBorrowApplication(row.id)
      if (!res?.success) { ElMessage.error(res?.msg || '撤销失败'); return }
      ElMessage.success('已撤销')
      await loadMyApplications()
    } catch (e) {
      // 429 已由全局 axios interceptor 展示友好提示，业务层不再重复弹窗
      notifyErrorUnlessRateLimit(e, '撤销失败')
    }
  }

  const approveBorrowApplication = async (row) => {
    try {
      const res = await resourcesApi.accounts.approveBorrowApplication(row.id, { comment: '' })
      if (!res?.success) { ElMessage.error(res?.msg || '审批失败'); return }
      ElMessage.success('已审批通过')
      await loadMyApprovals()
    } catch (e) {
      // 429 已由全局 axios interceptor 展示友好提示，业务层不再重复弹窗
      notifyErrorUnlessRateLimit(e, '审批失败')
    }
  }

  const rejectBorrowApplication = async (row) => {
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
      await loadMyApprovals()
    } catch {
      // 用户取消
    }
  }

  const openBorrowAppReturn = (row) => {
    currentReturnApplication.value = row
    showBorrowAppReturnDialog.value = true
  }

  const onBorrowAppReturned = () => {
    showBorrowAppReturnDialog.value = false
    loadMyApprovals()
  }

  return {
    activeTab,
    myApplications,
    myApprovals,
    myApplicationsLoading,
    myApprovalsLoading,
    showBorrowAppReturnDialog,
    currentReturnApplication,
    accountName,
    formatBorrowDate,
    borrowStatusLabel,
    borrowStatusType,
    loadMyApplications,
    loadMyApprovals,
    onTabChange,
    switchTab,
    cancelBorrowApplication,
    approveBorrowApplication,
    rejectBorrowApplication,
    openBorrowAppReturn,
    onBorrowAppReturned
  }
}
