<!-- Input: Workbench WelcomeBanner props and user actions
Output: presentational Workbench WelcomeBanner section with readable action labels
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="welcome-banner" :class="`banner-${role}`">
    <div class="banner-content">
      <div class="banner-greeting">
        <h1 class="banner-title">{{ title }}</h1>
        <p class="banner-subtitle">{{ subtitle }}</p>
      </div>
      <div class="banner-actions">
        <el-button
          v-for="action in actions"
          :key="action.key"
          :type="action.type || 'default'"
          :icon="action.icon"
          @click="emit('action-click', action)"
        >
          <span class="banner-action-label">{{ action.label }}</span>
        </el-button>
      </div>
    </div>
    <div class="banner-decoration">
      <div class="decoration-circle circle-1"></div>
      <div class="decoration-circle circle-2"></div>
      <div class="decoration-circle circle-3"></div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  role: { type: String, default: 'staff' },
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  actions: { type: Array, default: () => [] },
})

const emit = defineEmits(['action-click'])
</script>
