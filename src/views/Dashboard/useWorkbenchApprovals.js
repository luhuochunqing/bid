// Input: approval API and optional initial process rows
// Output: workbench approval state/actions composable for Dashboard Workbench
// Pos: src/views/Dashboard/ - dashboard feature composables
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref, computed } from 'vue'
import { approvalApi as defaultApprovalApi } from '@/api'
import { normalizePendingApproval, normalizeProcess } from '@/views/Dashboard/workbench-core.js'

const defaultInitialProcesses = [
  {
    id: 1,
    title: 'XX市智慧交通项目 - 标书编制',
    status: 'in-progress',
    description: '技术方案编写中，预计2天内完成',
    progress: 65,
    time: '2025-02-26 14:30',
  },
  {
    id: 2,
    title: 'XX区数字政府项目 - 资质准备',
    status: 'pending',
    description: '需要补充CMMI 5级认证文件',
    progress: 30,
    time: '2025-02-26 10:15',
  },
  {
    id: 3,
    title: 'XX县智慧社区项目 - 开标前准备',
    status: 'urgent',
    description: '投标保证金已缴纳，确认密封要求',
    progress: 90,
    time: '2025-02-25 16:45',
  },
]

export function useWorkbenchApprovals({
  api = defaultApprovalApi,
  initialProcesses = defaultInitialProcesses,
  normalizePending = normalizePendingApproval,
  normalizeProcessItem = normalizeProcess,
} = {}) {
  const pendingApprovals = ref([])
  const pendingApprovalsTotalCount = ref(0)
  const approvalDialogVisible = ref(false)
  const approvalMode = ref('approve')
  const currentApprovalItem = ref({})
  const myProcesses = ref([...initialProcesses])
  const approvalsError = ref('')
  const processesError = ref('')

  const bidReviewApprovalCount = computed(() => pendingApprovals.value.filter((item) => item.type === 'bid_review').length)

  const handleApprove = (item) => {
    approvalMode.value = 'approve'
    currentApprovalItem.value = item
    approvalDialogVisible.value = true
  }

  const handleReject = (item) => {
    approvalMode.value = 'reject'
    currentApprovalItem.value = item
    approvalDialogVisible.value = true
  }

  const loadPendingApprovals = async () => {
    approvalsError.value = ''
    try {
      const result = await api.getPendingApprovals({ page: 0, size: 8 })
      pendingApprovalsTotalCount.value = result?.totalCount ?? 0
      pendingApprovals.value = Array.isArray(result?.data)
        ? result.data.map(normalizePending)
        : []
    } catch {
      pendingApprovals.value = []
      approvalsError.value = '审批事项加载失败，请稍后重试'
    }
    return pendingApprovals.value
  }

  const loadMyProcesses = async () => {
    processesError.value = ''
    try {
      const result = await api.getMyApprovals({ page: 0, size: 8 })
      myProcesses.value = Array.isArray(result?.data)
        ? result.data.map(normalizeProcessItem)
        : []
    } catch {
      myProcesses.value = []
      processesError.value = '流程记录加载失败，请稍后重试'
    }
    return myProcesses.value
  }

  const handleApprovalSuccess = async () => {
    await Promise.all([loadPendingApprovals(), loadMyProcesses()])
  }

  return {
    pendingApprovals,
    pendingApprovalsTotalCount,
    approvalDialogVisible,
    approvalMode,
    currentApprovalItem,
    approvalsError,
    processesError,
    bidReviewApprovalCount,
    myProcesses,
    handleApprove,
    handleReject,
    handleApprovalSuccess,
    loadPendingApprovals,
    loadMyProcesses,
  }
}
