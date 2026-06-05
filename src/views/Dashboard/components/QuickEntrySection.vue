<template>
  <div class="quick-entry-section">
    <div class="section-title">快捷操作</div>
    <div class="quick-entry-grid">
      <div class="quick-entry-card" @click="$router.push('/project/create')" v-if="canCreateProject">
        <el-icon :size="28"><Plus /></el-icon>
        <span class="entry-label">发起项目</span>
        <span class="entry-desc">创建新的投标项目</span>
      </div>
      <div class="quick-entry-card" @click="$router.push('/bidding')" v-if="canViewTenders">
        <el-icon :size="28"><Document /></el-icon>
        <span class="entry-label">查看标讯</span>
        <span class="entry-desc">浏览和管理标讯信息</span>
      </div>
      <div class="quick-entry-card" @click="$emit('handle-todos')">
        <el-icon :size="28"><List /></el-icon>
        <span class="entry-label">处理待办</span>
        <span class="entry-desc">查看和处理待办事项</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Plus, Document, List } from '@element-plus/icons-vue'

defineProps({
  canCreateProject: {
    type: Boolean,
    default: false
  },
  canViewTenders: {
    type: Boolean,
    default: false
  }
})

defineEmits(['handle-todos'])
</script>

<style scoped>
.quick-entry-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--gray-750);
}

.quick-entry-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.quick-entry-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  background: var(--bg-card);
  border-radius: 8px;
  border: 1px solid var(--gray-250);
  cursor: pointer;
  transition: box-shadow 0.2s;
}

.quick-entry-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.entry-label {
  margin-top: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--gray-750);
}

.entry-desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
