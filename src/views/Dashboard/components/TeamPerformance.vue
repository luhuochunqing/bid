<!-- Input: Workbench TeamPerformance props and user actions
Output: presentational Workbench TeamPerformance section with brand-colored progress
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="section-card team-performance-card" :class="{ 'side-balance-card': compact }">
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
      v-if="teams.length === 0"
      icon="队"
      title="暂无团队绩效"
      description="团队中标、进行中项目和负载数据会显示在这里。"
    />
    <div v-else class="team-performance-grid" :class="{ compact }">
      <div v-for="team in teams" :key="team.dept" class="team-performance-item">
        <div class="team-info">
          <span class="team-name">{{ team.dept }}</span>
          <span class="team-size">{{ team.size }}人</span>
        </div>
        <div class="team-progress">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: `${team.progress}%` }"></div>
          </div>
          <span class="progress-label">{{ team.progress }}%</span>
        </div>
        <div class="team-metrics">
          <span class="team-metric">中标: {{ team.wins }}</span>
          <span class="team-metric">进行: {{ team.active }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import EmptyState from './EmptyState.vue'

defineProps({
  title: { type: String, default: '团队绩效' },
  teams: { type: Array, default: () => [] },
  compact: { type: Boolean, default: true },
})
</script>
