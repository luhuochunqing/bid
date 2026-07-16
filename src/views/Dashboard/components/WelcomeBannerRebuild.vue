<!-- Input: greeting / userName / subtitle / stats / actions
Output: 改造版欢迎横幅（渐变背景 + 左问候 + 右 4 统计数字）
Pos: src/views/Dashboard/components/ - 工作台改造组件
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <section class="welcome-rebuild">
    <div class="welcome-rebuild-text">
      <div class="welcome-rebuild-greeting">👋 {{ greeting }}，{{ userName }}</div>
      <div class="welcome-rebuild-sub" v-html="subtitle"></div>
    </div>
    <div class="welcome-rebuild-stats">
      <div
        v-for="stat in stats"
        :key="stat.label"
        class="welcome-rebuild-stat"
        role="button"
        tabindex="0"
        @click="emit('stat-click', stat)"
        @keydown.enter.prevent="emit('stat-click', stat)"
      >
        <div class="num">{{ stat.value }}</div>
        <div class="label">{{ stat.label }}</div>
      </div>
    </div>
  </section>
</template>

<script setup>
defineProps({
  greeting: { type: String, default: '你好' },
  userName: { type: String, default: '用户' },
  subtitle: { type: String, default: '' },
  stats: { type: Array, default: () => [] },
})

const emit = defineEmits(['stat-click'])
</script>
