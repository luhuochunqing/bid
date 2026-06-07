<template>
  <section class="agent-section">
    <header>
      技术要点分类
      <el-badge :value="items?.length || 0" :hidden="!items?.length" type="primary" class="panel-badge">
        <el-button size="small" text :loading="loading" @click="$emit('fetch')">获取技术要点</el-button>
      </el-badge>
    </header>
    <div v-if="items?.length" class="tech-list">
      <div v-for="(item, idx) in items" :key="idx" class="tech-item">
        <el-tag :type="tagType(item.subType)" size="small" effect="plain">{{ tagLabel(item.subType) }}</el-tag>
        <span class="tech-text">{{ item.text }}</span>
      </div>
    </div>
    <div v-else-if="fetched" class="tech-empty">
      <el-empty description="未解析出技术要点" :image-size="48" />
    </div>
  </section>
</template>

<script setup>
defineProps({
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  fetched: { type: Boolean, default: false },
})
defineEmits(['fetch'])

const tagLabel = (type) => ({
  HARD_INDEX: '硬指标',
  FUNCTIONAL: '功能',
  COMPATIBILITY: '兼容性',
  BONUS: '加分项',
}[type] || type)

const tagType = (type) => ({
  HARD_INDEX: 'danger',
  FUNCTIONAL: 'primary',
  COMPATIBILITY: 'warning',
  BONUS: 'success',
}[type] || 'info')
</script>

<style scoped>
.tech-list { display: grid; gap: 8px; }
.tech-item {
  display: flex; align-items: flex-start; gap: 8px;
  padding: 10px; border-radius: 10px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}
.tech-text { line-height: 1.5; color: var(--el-text-color-primary); font-size: 13px; }
.tech-empty { padding: 8px 0; }
.panel-badge { display: inline-flex; vertical-align: middle; }
</style>
