<template>
  <div class="task-board-page">
    <div class="page-header">
      <h2 class="page-title">任务看板</h2>
      <div class="header-actions">
        <el-tag type="info" size="small">共 {{ items.length }} 个事项</el-tag>
        <el-button size="small" :icon="Refresh" @click="loadTasks" :loading="loading">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
      class="error-alert"
    />

    <div v-loading="loading" class="board-columns">
      <div v-for="column in columns" :key="column.key" class="board-column">
        <div class="column-header" :style="{ borderTopColor: column.color }">
          <span class="column-title">{{ column.title }}</span>
          <el-badge :value="getTasksByStatus(column.key).length" class="column-badge" />
        </div>
        <div class="column-content">
          <TaskBoardCard
            v-for="item in getTasksByStatus(column.key)"
            :key="item.type + '-' + item.id"
            :item="item"
            :available-statuses="availableStatuses"
            @status-change="handleStatusChange"
            @deliverable-changed="handleDeliverableChanged"
            @task-click="handleTaskClick"
          />
          <el-empty
            v-if="getTasksByStatus(column.key).length === 0"
            description="暂无任务"
            :image-size="60"
          />
        </div>
      </div>
    </div>

    <!-- 任务详情抽屉 -->
    <TaskBoardTaskDrawer
      ref="drawerRef"
      v-model="drawerVisible"
      @submitted="loadTasks"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import TaskBoardCard from './components/TaskBoardCard.vue'
import TaskBoardTaskDrawer from './components/TaskBoardTaskDrawer.vue'
import { tasksApi as dashboardTasksApi } from '@/api/modules/dashboard'
import { TASK_STATUS, getTaskStatusDisplayName } from '@/constants/taskStatus.js'

const COLUMNS = [
  { key: TASK_STATUS.TODO, title: '待开始', color: '#909399' },
  { key: TASK_STATUS.REVIEW, title: '待审核', color: '#e6a23c' },
  { key: TASK_STATUS.COMPLETED, title: '已完成', color: '#67c23a' }
]

const AVAILABLE_STATUSES = COLUMNS.map(({ key, title }) => ({ code: key, name: title }))

const items = ref([])
const loading = ref(false)
const error = ref('')

const route = useRoute()
const router = useRouter()

const getTasksByStatus = (status) => items.value.filter((t) => t.status === status)

// CO-474: 支持从 query 参数 taskId 自动打开任务详情抽屉。
// 跨部门协助人员（bid-otherDept）的任务分配通知跳转到 /task-board?taskId=X&projectId=Y，
// 页面加载完成后自动定位到对应任务并打开详情抽屉。
const openTaskFromQuery = async () => {
  const taskId = route?.query?.taskId
  if (!taskId) return
  const id = Number(taskId)
  if (!Number.isFinite(id) || id <= 0) return
  const target = items.value.find((item) => item.type === 'TASK' && Number(item.id) === id)
  if (target) {
    await handleTaskClick(target)
    // 清理 query 参数，避免刷新后重复打开
    router?.replace?.({ path: route.path, query: {} })
  }
}

const loadTasks = async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await dashboardTasksApi.getBoardItems()
    items.value = Array.isArray(res?.data) ? res.data : []
  } catch (e) {
    error.value = e?.message || '加载任务失败'
    items.value = []
  } finally {
    loading.value = false
  }
  // CO-474: 通知跳转 /task-board?taskId=X&projectId=Y 时自动定位任务。
  // 单独 try-catch：查询参数处理失败不应影响任务列表正常展示。
  try {
    await openTaskFromQuery()
  } catch {
    // 忽略 query 参数处理错误
  }
}

const handleStatusChange = async (item, newStatus) => {
  if (item.type !== 'TASK') return
  const oldStatus = item.status
  item.status = newStatus
  try {
    await dashboardTasksApi.updateStatus(item.id, newStatus)
    const name = getTaskStatusDisplayName(newStatus)
    ElMessage.success(`任务状态已更新为：${name}`)
  } catch (e) {
    item.status = oldStatus
    ElMessage.error(e?.message || '更新任务状态失败')
  }
}

const handleDeliverableChanged = async () => { await loadTasks() }

onMounted(loadTasks)

const columns = COLUMNS
const availableStatuses = AVAILABLE_STATUSES

// 抽屉状态
const drawerVisible = ref(false)
const drawerRef = ref(null)

async function handleTaskClick(item) {
  if (item.targetUrl) {
    router.push(item.targetUrl)
    return
  }
  if (item.type !== 'TASK') return
  await drawerRef.value?.loadTask(item.id)
}
</script>

<style scoped lang="scss">
.task-board-page { padding: 20px; }

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .page-title { margin: 0; font-size: 20px; font-weight: 600; }
  .header-actions { display: flex; align-items: center; gap: 12px; }
}

.error-alert { margin-bottom: 16px; }

.board-columns {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  min-height: 400px;
}

.board-column {
  background: #f5f7fa;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  min-height: 400px;
}

.column-header {
  padding: 12px 16px;
  border-top: 3px solid #909399;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;

  .column-title { font-size: 14px; }
}

.column-content {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

@media (max-width: 1200px) { .board-columns { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 768px) { .board-columns { grid-template-columns: minmax(0, 1fr); } }
</style>
