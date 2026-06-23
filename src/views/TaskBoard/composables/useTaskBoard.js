import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { tasksApi } from '@/api/modules/dashboard'
import { projectsApi } from '@/api/modules/projects'

const COLUMNS = [
  { key: 'TODO', title: '待开始', color: '#909399' },
  { key: 'IN_PROGRESS', title: '进行中', color: '#409eff' },
  { key: 'REVIEW', title: '待审核', color: '#e6a23c' },
  { key: 'COMPLETED', title: '已完成', color: '#67c23a' }
]

const AVAILABLE_STATUSES = [
  { code: 'TODO', name: '待开始' },
  { code: 'IN_PROGRESS', name: '进行中' },
  { code: 'REVIEW', name: '待审核' },
  { code: 'COMPLETED', name: '已完成' }
]

export function useTaskBoard() {
  const items = ref([])
  const loading = ref(false)
  const error = ref('')

  const getTasksByStatus = (status) => items.value.filter((t) => t.status === status)

  const loadTaskDeliverables = async (item) => {
    if (item.type !== 'TASK' || !item.projectId || !item.id) {
      item.deliverables = []
      return
    }
    try {
      const res = await projectsApi.getTaskDeliverables(item.projectId, item.id)
      item.deliverables = Array.isArray(res?.data) ? res.data : []
    } catch {
      item.deliverables = []
    }
  }

  const loadTasks = async () => {
    loading.value = true
    error.value = ''
    try {
      const res = await tasksApi.getBoardItems()
      items.value = Array.isArray(res?.data) ? res.data : []
      await Promise.all(items.value.map(loadTaskDeliverables))
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
      const name = AVAILABLE_STATUSES.find((s) => s.code === newStatus)?.name || newStatus
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
