<!-- Input: Workbench QuickActions props and user actions
Output: presentational Workbench QuickActions section
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="section-card quick-actions-card">
    <div class="section-header">
      <h3 class="section-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="section-icon">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="16"/>
          <line x1="8" y1="12" x2="16" y2="12"/>
        </svg>
        {{ title }}
      </h3>
    </div>
    <div class="quick-actions-grid">
      <div
        v-for="action in actions"
        :key="action.key"
        class="quick-action-item"
        role="button"
        tabindex="0"
        @click="selectAction(action)"
        @keydown.enter.prevent="selectAction(action)"
        @keydown.space.prevent="selectAction(action)"
      >
        <div class="action-icon" :style="action.iconStyle">
          <el-icon :size="24"><component :is="action.icon" /></el-icon>
        </div>
        <span class="action-title">{{ action.title }}</span>
        <span class="action-desc">{{ action.desc }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '快速发起' },
  actions: { type: Array, default: () => [] },
})

const emit = defineEmits(['action-click'])
const selectAction = (action) => emit('action-click', action)
</script>
