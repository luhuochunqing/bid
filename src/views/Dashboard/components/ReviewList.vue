<!-- Input: Workbench ReviewList props and user actions
Output: presentational Workbench ReviewList section
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="section-card review-card">
    <div class="section-header">
      <h3 class="section-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="section-icon">
          <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
        </svg>
        {{ title }}
      </h3>
    </div>
    <EmptyState
      v-if="reviews.length === 0"
      icon="评"
      title="暂无待评审内容"
      description="需要你评审的标书或方案会显示在这里。"
    />
    <div v-else class="review-list">
      <div v-for="review in reviews" :key="review.id" class="review-item">
        <div class="review-icon">
          <el-icon><Document /></el-icon>
        </div>
        <div class="review-content">
          <span class="review-title">{{ review.title }}</span>
          <span class="review-meta">{{ review.author }} 提交 · {{ review.time }}</span>
        </div>
        <el-button type="primary" size="small" @click="emit('review', review)">评审</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Document } from '@element-plus/icons-vue'
import EmptyState from './EmptyState.vue'

defineProps({
  title: { type: String, default: '待我评审' },
  reviews: { type: Array, default: () => [] },
})

const emit = defineEmits(['review'])
</script>
