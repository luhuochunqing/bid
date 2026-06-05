<!-- Input: Workbench TeamTaskList props and user actions
Output: presentational Workbench TeamTaskList section
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="section-card team-tasks-card">
    <div class="section-header">
      <h3 class="section-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="section-icon">
          <path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/>
          <circle cx="9" cy="7" r="4"/>
          <path d="M23 21v-2a4 4 0 00-3-3.87"/>
          <path d="M16 3.13a4 4 0 010 7.75"/>
        </svg>
        {{ title }}
      </h3>
    </div>
    <EmptyState
      v-if="members.length === 0"
      icon="任"
      title="暂无团队任务"
      description="团队成员的任务分配和工作量会显示在这里。"
    />
    <div v-else class="team-tasks-list">
      <div v-for="member in members" :key="member.id" class="member-task-item">
        <div class="member-avatar">{{ member.name?.charAt(0) }}</div>
        <div class="member-info">
          <span class="member-name">{{ member.name }}</span>
          <div class="member-tasks">
            <el-tag
              v-for="task in member.tasks.slice(0, visibleTaskCount)"
              :key="task.id"
              size="small"
              :type="task.priority === 'high' ? 'danger' : 'info'"
            >
              {{ task.title }}
            </el-tag>
            <span v-if="member.tasks.length > visibleTaskCount" class="more-tasks">
              +{{ member.tasks.length - visibleTaskCount }}
            </span>
          </div>
        </div>
        <div class="member-workload">
          <span class="workload-label">工作量</span>
          <span class="workload-value" :class="`workload-${member.workloadLevel}`">{{ member.workload }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import EmptyState from './EmptyState.vue'

defineProps({
  title: { type: String, default: '团队任务分配' },
  members: { type: Array, default: () => [] },
  visibleTaskCount: { type: Number, default: 2 },
})
</script>
