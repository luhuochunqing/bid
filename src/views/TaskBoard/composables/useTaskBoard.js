import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { tasksApi } from '@/api/modules/dashboard'

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

const PRIORITY_TYPE_MAP = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }
const PRIORITY_TEXT_MAP = { HIGH: '高', MEDIUM: '中', LOW: '低' }

export function useTaskBoard() {
  const router = useRouter()
  const items = ref([])
  const loading = ref(false)
  const error = ref('')

  const getTasksByStatus = (status) => items.value.filter((t) => t.status === status)
  const getPriorityType = (priority) => PRIORITY_TYPE_MAP[priority] || 'info'
  const getPriorityText = (priority) => PRIORITY_TEXT_MAP[priority] || priority

  const isUrgent = (dueDate) => {
    if (!dueDate) return false
    const diff = new Date(dueDate) - new Date()
    return diff > 0 && diff < 3 * 24 * 60 * 60 * 1000
  }

  const formatDate = (dateStr) => {
    if (!dateStr) return ''
    const d = new Date(dateStr)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }

  const canUpdateTask = (item) => item.type === 'TASK' && !!item.id

  const handleCardClick = (item) => {
    if (item.targetUrl) {
      router.push(item.targetUrl)
    }
  }

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
      const name = AVAILABLE_STATUSES.find((s) => s.code === newStatus)?.name || newStatus
      ElMessage.success(`任务状态已更新为：${name}`)
    } catch (e) {
      item.status = oldStatus
      ElMessage.error(e?.message || '更新任务状态失败')
    }
  }

  onMounted(loadTasks)

  return {
    items,
    loading,
    error,
    columns: COLUMNS,
    availableStatuses: AVAILABLE_STATUSES,
    getTasksByStatus,
    getPriorityType,
    getPriorityText,
    isUrgent,
    formatDate,
    canUpdateTask,
    handleCardClick,
    handleStatusChange,
    loadTasks
  }
}
