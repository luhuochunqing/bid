import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { tasksApi } from '@/api/modules/dashboard'
import { TASK_STATUS, getTaskStatusDisplayName } from '@/constants/taskStatus.js'

const COLUMNS = [
  { key: TASK_STATUS.TODO, title: '待开始', color: '#909399' },
  { key: TASK_STATUS.REVIEW, title: '待审核', color: '#e6a23c' },
  { key: TASK_STATUS.COMPLETED, title: '已完成', color: '#67c23a' }
]

const AVAILABLE_STATUSES = COLUMNS.map(({ key, title }) => ({ code: key, name: title }))

export function useTaskBoard() {
  const items = ref([])
  const loading = ref(false)
  const error = ref('')

  const getTasksByStatus = (status) => items.value.filter((t) => t.status === status)

  const loadTasks = async () => {
    loading.value = true
    error.value = ''
    try {
      const res = await tasksApi.getBoardItems()
      items.value = Array.isArray(res?.data) ? res.data : []
    } catch (e) {
      error.value = e?.message || '加载任务失败'
      items.value = []
    } finally {
      loading.value = false
    }
  }

  const handleStatusChange = async (item, newStatus) => {
    if (item.type !== 'TASK') return
    const oldStatus = item.status
    item.status = newStatus
    try {
      await tasksApi.updateStatus(item.id, newStatus)
      const name = getTaskStatusDisplayName(newStatus)
      ElMessage.success(`任务状态已更新为：${name}`)
    } catch (e) {
      item.status = oldStatus
      ElMessage.error(e?.message || '更新任务状态失败')
    }
  }

  // 卡片内操作完成后刷新看板
  const handleDeliverableChanged = async () => { await loadTasks() }

  onMounted(loadTasks)

  return {
    items,
    loading,
    error,
    columns: COLUMNS,
    availableStatuses: AVAILABLE_STATUSES,
    getTasksByStatus,
    handleStatusChange,
    handleDeliverableChanged,
    loadTasks
  }
}
