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
    <el-drawer
      v-model="drawerVisible"
      title="任务详情"
      size="520px"
      direction="rtl"
      :destroy-on-close="true"
    >
      <div v-loading="loadingTaskDetail">
        <TaskForm
          v-if="selectedTask"
          ref="taskFormRef"
          v-model="selectedTask"
          mode="view"
        />
      </div>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">关闭</el-button>
          <el-button
            v-if="canSubmitForReview"
            type="primary"
            :loading="submitting"
            @click="handleSubmitForReview"
          >
            提交审核
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import TaskBoardCard from './components/TaskBoardCard.vue'
import TaskForm from '@/components/project/TaskForm.vue'
import { projectsApi } from '@/api/modules/projects.js'
import { tasksApi } from '@/api/modules/tasks.js'
import { tasksApi as dashboardTasksApi } from '@/api/modules/dashboard'
import { TASK_STATUS, getTaskStatusDisplayName } from '@/constants/taskStatus.js'
import { taskBackendToCard } from '@/views/Project/project-utils.js'
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'
import { validateSubmitForReview } from '@/composables/useTaskSubmissionValidation.js'
import { uploadTaskFilesWithFallback } from '@/composables/projectDetail/taskAssigneePayload'

const COLUMNS = [
  { key: TASK_STATUS.TODO, title: '待开始', color: '#909399' },
  { key: TASK_STATUS.REVIEW, title: '待审核', color: '#e6a23c' },
  { key: TASK_STATUS.COMPLETED, title: '已完成', color: '#67c23a' }
]

const AVAILABLE_STATUSES = COLUMNS.map(({ key, title }) => ({ code: key, name: title }))

const items = ref([])
const loading = ref(false)
const error = ref('')

const router = useRouter()

const getTasksByStatus = (status) => items.value.filter((t) => t.status === status)

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
const selectedTask = ref(null)
const taskFormRef = ref(null)
const submitting = ref(false)
const loadingTaskDetail = ref(false)

// 提交审核按钮显隐：仅执行人+TODO状态时显示
const canSubmitForReview = computed(() => {
  return taskFormRef.value?.canDeliver === true
})

async function handleTaskClick(item) {
  if (item.targetUrl) {
    router.push(item.targetUrl)
    return
  }
  if (item.type !== 'TASK') return
  loadingTaskDetail.value = true
  try {
    const res = await tasksApi.getTaskById(item.id)
    const taskData = res?.data?.data || res?.data || {}
    selectedTask.value = { ...taskBackendToCard(taskData), deliverableFiles: [] }
    await nextTick()
    drawerVisible.value = true
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '加载任务详情失败')
  } finally {
    loadingTaskDetail.value = false
  }
}

async function handleSubmitForReview() {
  if (!selectedTask.value?.id || !selectedTask.value?.projectId) {
    ElMessage.warning('任务数据不完整')
    return
  }
  submitting.value = true
  try {
    const form = taskFormRef.value
    const result = form?.submitForReview?.()
    if (!result || result.valid === false) {
      if (result?.message) ElMessage.warning(result.message)
      return
    }

    const data = result.data
    const projectId = data.projectId
    const taskId = data.id

    const validation = validateSubmitForReview({
      deliverables: data.deliverables,
      deliverableFiles: data.deliverableFiles,
      completionNotes: data.completionNotes
    })
    if (!validation.valid) {
      ElMessage.warning(validation.message)
      return
    }

    const projectStore = useProjectStore()
    const userStore = useUserStore()
    const uploadOk = await uploadTaskFilesWithFallback(
      selectedTask.value,
      { attachments: [], deliverableFiles: data.deliverableFiles || [] },
      { projectStore, projectId, userStore },
      {
        attachments: '已提交审核，但附件上传失败，请重试',
        deliverables: '已提交审核，但交付物上传失败，请重试',
      },
      ElMessage,
    )
    if (!uploadOk) return

    await projectsApi.updateTaskStatus(projectId, taskId, 'REVIEW', null, data.completionNotes)

    ElMessage.success('已提交审核')
    drawerVisible.value = false
    selectedTask.value = null
    await loadTasks()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '提交审核失败')
  } finally {
    submitting.value = false
  }
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
