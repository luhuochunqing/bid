<!-- Input: Workbench ActivityList props and user actions
Output: presentational Workbench ActivityList section
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="section-card activity-card">
    <div class="section-header">
      <h3 class="section-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="section-icon">
          <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"/>
          <path d="M13.73 21a2 2 0 01-3.46 0"/>
        </svg>
        {{ title }}
      </h3>
    </div>
    <EmptyState
      v-if="activities.length === 0"
      icon="动"
      title="暂无最新动态"
      description="团队协作、评审通过和中标动态会显示在这里。"
    />
    <div v-else class="activity-list">
      <div v-for="activity in activities" :key="activity.id" class="activity-item">
        <div class="activity-dot" :class="`type-${activity.type}`"></div>
        <div class="activity-content">
          <p class="activity-text">{{ activity.text }}</p>
          <span class="activity-time">{{ activity.time }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import EmptyState from './EmptyState.vue'

defineProps({
  title: { type: String, default: '最新动态' },
  activities: { type: Array, default: () => [] },
})
</script>
