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
          />
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
import { Refresh } from '@element-plus/icons-vue'
import TaskBoardCard from './components/TaskBoardCard.vue'
import { useTaskBoard } from './composables/useTaskBoard.js'

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
  grid-template-columns: repeat(3, 1fr);
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

@media (max-width: 1200px) { .board-columns { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 768px) { .board-columns { grid-template-columns: 1fr; } }
</style>
