<!-- Input: Workbench section state props and optional retry action
Output: consistent empty/error placeholder for Dashboard cards with optional icon
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="workbench-empty-state" :class="`state-${state}`">
    <div v-if="icon" class="empty-state-icon" aria-hidden="true">{{ icon }}</div>
    <div class="empty-state-copy">
      <p class="empty-state-title">{{ title }}</p>
      <p class="empty-state-description">{{ description }}</p>
    </div>
    <el-button v-if="actionLabel" size="small" text type="primary" @click="emit('action')">
      {{ actionLabel }}
    </el-button>
  </div>
</template>

<script setup>
defineProps({
  state: { type: String, default: 'empty' },
  icon: { type: String, default: '—' },
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '当前没有需要展示的内容。' },
  actionLabel: { type: String, default: '' },
})

const emit = defineEmits(['action'])
</script>
