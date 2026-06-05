<!-- Input: Workbench PriorityTodos props and user actions
Output: presentational Workbench PriorityTodos section
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="section-card todos-card">
    <div class="section-header">
      <h3 class="section-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="section-icon">
          <path d="M9 11l3 3L22 4"/>
          <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/>
        </svg>
        {{ title }}
      </h3>
      <el-tag size="small" type="danger">{{ todos.length }}</el-tag>
    </div>
    <EmptyState
      v-if="error"
      state="error"
      icon="!"
      title="待办加载失败"
      :description="error"
      action-label="重试"
      @action="emit('retry')"
    />
    <EmptyState
      v-else-if="todos.length === 0"
      icon="✓"
      title="今天没有高优先级待办"
      description="可以先查看项目进度，或等待新的任务分配。"
    />
    <div v-else class="todos-list">
      <div
        v-for="todo in todos"
        :key="todo.id"
        class="todo-item"
        :class="[`priority-${todo.priority}`, todo.type === 'warning' ? 'todo-warning' : '']"
      >
        <div class="todo-checkbox" @click.stop="emit('todo-toggle', todo)">
          <div class="checkbox-custom" :class="{ checked: todo.done }">
            <el-icon v-if="todo.done"><Check /></el-icon>
          </div>
        </div>
        <div class="todo-content">
          <div class="todo-title-row">
            <span class="todo-title" :class="{ done: todo.done }">{{ todo.title }}</span>
            <el-tag v-if="todo.type === 'warning'" type="warning" size="small" effect="dark">系统预警</el-tag>
          </div>
          <div class="todo-meta">
            <span class="todo-deadline">
              <el-icon><Clock /></el-icon>
              {{ todo.deadline }}
            </span>
          </div>
        </div>
        <el-tag v-if="todo.type !== 'warning'" :type="resolvePriorityType(todo.priority)" size="small">
          {{ resolvePriorityLabel(todo.priority) }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Check, Clock } from '@element-plus/icons-vue'
import EmptyState from './EmptyState.vue'

const props = defineProps({
  title: { type: String, default: '待办事项' },
  todos: { type: Array, default: () => [] },
  priorityTypeResolver: { type: Function, default: null },
  priorityLabelResolver: { type: Function, default: null },
  error: { type: String, default: '' },
})

const emit = defineEmits(['todo-toggle', 'retry'])

const defaultPriorityType = (priority) => ({ high: 'danger', medium: 'warning', low: 'info', urgent: 'danger' }[priority] || '')
const defaultPriorityLabel = (priority) => ({ high: '高', medium: '中', low: '低', urgent: '紧急' }[priority] || priority)

const resolvePriorityType = (priority) => (
  props.priorityTypeResolver ? props.priorityTypeResolver(priority) : defaultPriorityType(priority)
)
const resolvePriorityLabel = (priority) => (
  props.priorityLabelResolver ? props.priorityLabelResolver(priority) : defaultPriorityLabel(priority)
)
</script>
