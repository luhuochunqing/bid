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
      <TaskForm
        v-if="selectedTask"
        ref="taskFormRef"
        v-model="selectedTask"
        mode="view"
      />
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
import { ref, computed, nextTick } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import TaskBoardCard from './components/TaskBoardCard.vue'
import TaskForm from '@/components/project/TaskForm.vue'
import { projectsApi } from '@/api/modules/projects.js'
import { useTaskBoard } from './composables/useTaskBoard.js'
import { taskBackendToCard } from '@/views/Project/project-utils.js'

const {
  items,
  loading,
  error,
  columns,
  availableStatuses,
  getTasksByStatus,
  handleStatusChange,
  handleDeliverableChanged,
  loadTasks
} = useTaskBoard()

// 抽屉状态
const drawerVisible = ref(false)
const selectedTask = ref(null)
const taskFormRef = ref(null)
const submitting = ref(false)

// 提交审核按钮显隐：仅执行人+TODO状态时显示
const canSubmitForReview = computed(() => {
  return taskFormRef.value?.canDeliver === true
})

function handleTaskClick(item) {
  if (item.type !== 'TASK') return
  selectedTask.value = { ...taskBackendToCard(item), deliverableFiles: [] }
  nextTick(() => {
    drawerVisible.value = true
  })
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
    if (!result || result.valid === false) return

    const data = result.data
    const projectId = data.projectId
    const taskId = data.id

    // 上传新增交付物
    const files = data.deliverableFiles || []
    for (const file of files) {
      if (file?.raw) {
        const formData = new FormData()
        formData.append('file', file.raw)
        formData.append('taskId', taskId)
        await projectsApi.createTaskDeliverable(projectId, taskId, formData)
      }
    }

    // 保存完成情况说明
    if (data.completionNotes) {
      await projectsApi.updateTask(taskId, { completionNotes: data.completionNotes })
    }

    // 更新状态为 REVIEW
    await projectsApi.updateTaskStatus(projectId, taskId, 'REVIEW')

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
