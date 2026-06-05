<!-- Input: Workbench TenderList props and user actions
Output: presentational Workbench TenderList section
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="section-card tenders-card">
    <div class="section-header">
      <h3 class="section-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="section-icon">
          <path d="M19 20H5a2 2 0 01-2-2V6a2 2 0 012-2h10a2 2 0 012 2v1m2 13a2 2 0 01-2-2V7m2 13a2 2 0 002-2V9a2 2 0 00-2-2h-2m-4-3H9M7 16h6M7 8h6v4H7V8z"/>
        </svg>
        {{ title }}
      </h3>
      <el-link v-if="showViewAll" type="primary" underline="hover" @click="emit('view-all')">
        {{ viewAllLabel }}
        <el-icon class="el-icon--right"><ArrowRight /></el-icon>
      </el-link>
    </div>
    <EmptyState
      v-if="tenders.length === 0"
      icon="标"
      title="暂无重点标讯"
      description="当前没有匹配的重点标讯，可以查看全部标讯继续筛选。"
      :action-label="showViewAll ? viewAllLabel : ''"
      @action="emit('view-all')"
    />
    <div v-else class="tenders-list">
      <div
        v-for="tender in tenders"
        :key="tender.id"
        class="tender-card"
        role="button"
        tabindex="0"
        @click="selectTender(tender)"
        @keydown.enter.prevent="selectTender(tender)"
        @keydown.space.prevent="selectTender(tender)"
      >
        <div class="tender-score" :class="`score-${tender.scoreLevel}`">{{ tender.aiScore }}</div>
        <div class="tender-info">
          <h4 class="tender-title">{{ tender.title }}</h4>
          <div class="tender-meta">
            <span class="tender-budget">{{ tender.budget }}万</span>
            <span class="tender-region">{{ tender.region }}</span>
            <el-tag :type="tender.probability === 'high' ? 'success' : 'warning'" size="small">
              {{ tender.probibilityText }}
            </el-tag>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ArrowRight } from '@element-plus/icons-vue'
import EmptyState from './EmptyState.vue'

defineProps({
  title: { type: String, default: '重点标讯' },
  tenders: { type: Array, default: () => [] },
  showViewAll: { type: Boolean, default: true },
  viewAllLabel: { type: String, default: '查看全部' },
})

const emit = defineEmits(['view-all', 'tender-click'])
const selectTender = (tender) => emit('tender-click', tender)
</script>
