<!-- Input: Workbench TechnicalTaskList props and user actions
Output: presentational Workbench TechnicalTaskList section
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="section-card my-tasks-card">
    <div class="section-header">
      <h3 class="section-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="section-icon">
          <path d="M9 11l3 3L22 4"/>
          <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/>
        </svg>
        {{ title }}
      </h3>
    </div>
    <EmptyState
      v-if="tasks.length === 0"
      icon="✓"
      title="暂无技术任务"
      description="你负责的技术方案、评审和交付任务会显示在这里。"
    />
    <div v-else class="my-tasks-list">
      <div v-for="task in tasks" :key="task.id" class="my-task-item" :class="`priority-${task.priority}`">
        <div class="task-checkbox">
          <el-checkbox :model-value="task.done" @change="(checked) => emit('task-change', task, checked)" />
        </div>
        <div class="task-content">
          <span class="task-title" :class="{ done: task.done }">{{ task.title }}</span>
          <div class="task-meta">
            <span class="task-project">{{ task.project }}</span>
            <span class="task-deadline">{{ task.deadline }}</span>
          </div>
        </div>
        <el-tag :type="task.priority === 'high' ? 'danger' : 'warning'" size="small">
          {{ task.priority === 'high' ? '紧急' : '普通' }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import EmptyState from './EmptyState.vue'

defineProps({
  title: { type: String, default: '我的任务' },
  tasks: { type: Array, default: () => [] },
})

const emit = defineEmits(['task-change'])
</script>
