<template>
  <div class="task-board-page">
    <div class="page-header">
      <h2 class="page-title">任务看板</h2>
      <div class="header-actions">
        <el-tag type="info" size="small">共 {{ tasks.length }} 个任务</el-tag>
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
          <div
            v-for="task in getTasksByStatus(column.key)"
            :key="task.id"
            class="task-card"
            :class="{ 'task-high': task.priority === 'HIGH' }"
          >
            <div class="task-header">
              <el-tag
                v-if="task.priority"
                :type="getPriorityType(task.priority)"
                size="small"
              >
                {{ getPriorityText(task.priority) }}
              </el-tag>
              <el-dropdown v-if="canUpdateTask(task)" trigger="click" @click.stop>
                <el-icon class="more-icon"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-item
                    v-for="s in availableStatuses"
                    :key="s.code"
                    :disabled="task.status === s.code"
                    @click="handleStatusChange(task, s.code)"
                  >
                    设为{{ s.name }}
                  </el-dropdown-item>
                </template>
              </el-dropdown>
            </div>
            <div class="task-name">{{ task.title }}</div>
            <div class="task-desc" v-if="task.description">{{ task.description }}</div>
            <div class="task-meta">
              <div class="task-owner" v-if="task.assigneeName">
                <el-icon><User /></el-icon>
                <span>{{ task.assigneeName }}</span>
              </div>
              <div
                class="task-deadline"
                :class="{ 'deadline-urgent': isUrgent(task.dueDate) }"
                v-if="task.dueDate"
              >
                <el-icon><Calendar /></el-icon>
                <span>{{ formatDate(task.dueDate) }}</span>
              </div>
            </div>
          </div>
          <el-empty
            v-if="getTasksByStatus(column.key).length === 0"
            description="暂无任务"
            :image-size="60"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { MoreFilled, User, Calendar, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { tasksApi } from '@/api/modules/dashboard'

const tasks = ref([])
const loading = ref(false)
const error = ref('')

const columns = [
  { key: 'TODO', title: '待开始', color: '#909399' },
  { key: 'IN_PROGRESS', title: '进行中', color: '#409eff' },
  { key: 'REVIEW', title: '待审核', color: '#e6a23c' },
  { key: 'COMPLETED', title: '已完成', color: '#67c23a' }
]

const availableStatuses = [
  { code: 'TODO', name: '待开始' },
  { code: 'IN_PROGRESS', name: '进行中' },
  { code: 'REVIEW', name: '待审核' },
  { code: 'COMPLETED', name: '已完成' }
]

const getTasksByStatus = (status) => tasks.value.filter((t) => t.status === status)

const getPriorityType = (priority) => {
  const map = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }
  return map[priority] || 'info'
}

const getPriorityText = (priority) => {
  const map = { HIGH: '高', MEDIUM: '中', LOW: '低' }
  return map[priority] || priority
}

const isUrgent = (dueDate) => {
  if (!dueDate) return false
  const due = new Date(dueDate)
  const now = new Date()
  const diff = due - now
  return diff > 0 && diff < 3 * 24 * 60 * 60 * 1000
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 跨部门协同人员作为 assignee 可更新自己任务的状态
const canUpdateTask = (task) => !!task.id

const loadTasks = async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await tasksApi.getMine()
    tasks.value = Array.isArray(res?.data?.data) ? res.data.data : []
  } catch (e) {
    error.value = e?.message || '加载任务失败'
    tasks.value = []
  } finally {
    loading.value = false
  }
}

const handleStatusChange = async (task, newStatus) => {
  const oldStatus = task.status
  task.status = newStatus
  try {
    await tasksApi.updateStatus(task.id, newStatus)
    ElMessage.success(`任务状态已更新为：${availableStatuses.find((s) => s.code === newStatus)?.name || newStatus}`)
  } catch (e) {
    task.status = oldStatus
    ElMessage.error(e?.message || '更新任务状态失败')
  }
}

onMounted(loadTasks)
</script>

<style scoped lang="scss">
.task-board-page {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

  .page-title {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.error-alert {
  margin-bottom: 16px;
}

.board-columns {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
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

  .column-title {
    font-size: 14px;
  }
}

.column-content {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-card {
  background: #fff;
  border-radius: 6px;
  padding: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  cursor: default;

  &.task-high {
    border-left: 3px solid #f56c6c;
  }
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;

  .more-icon {
    cursor: pointer;
    color: #909399;
    &:hover { color: #409eff; }
  }
}

.task-name {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 6px;
  color: #303133;
}

.task-desc {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;

  .task-owner, .task-deadline {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  .deadline-urgent { color: #f56c6c; }
}

@media (max-width: 1200px) { .board-columns { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 768px) { .board-columns { grid-template-columns: 1fr; } }
</style>
