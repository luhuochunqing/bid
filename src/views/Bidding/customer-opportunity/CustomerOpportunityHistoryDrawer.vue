<template>
  <el-drawer v-model="visibleProxy" title="历史采购全景图" size="600px" class="premium-drawer">
    <div v-if="history.length" class="panoramic-view">
      <div class="panoramic-stats">
        <div class="stat-card blue">
          <span class="stat-label">累计采购项目</span>
          <p class="stat-value">{{ drawerStats.totalCount }}</p>
        </div>
        <div class="stat-card purple">
          <span class="stat-label">累计预算总额</span>
          <p class="stat-value">¥{{ drawerStats.totalBudget }}<small>万</small></p>
        </div>
        <div class="stat-card green">
          <span class="stat-label">首选品类</span>
          <p class="stat-value">{{ drawerStats.topCategory }}</p>
        </div>
      </div>

      <div class="panoramic-section">
        <h4 class="section-title">品类购买频率分布</h4>
        <div class="category-bars">
          <div v-for="cat in categoryStats" :key="cat.name" class="cat-bar-item">
            <div class="cat-info">
              <span>{{ cat.name }}</span>
              <span>{{ cat.count }}次 ({{ cat.percent }}%)</span>
            </div>
            <div class="cat-progress-bg">
              <div class="cat-progress-fill" :style="{ width: cat.percent + '%', backgroundColor: cat.color }"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="panoramic-section">
        <h4 class="section-title">采购历史全轨迹</h4>
        <div class="history-scroll-list">
          <div class="history-item-card" v-for="record in history" :key="record.recordId">
            <div class="h-item-top">
              <el-tag size="small" :type="record.budget > 500 ? 'danger' : 'info'" effect="light">¥{{ record.budget }}万</el-tag>
              <span class="h-item-date">{{ record.publishDate }}</span>
            </div>
            <p class="h-item-title">{{ record.title }}</p>
            <div class="h-item-footer">
              <span class="h-item-cat"><el-icon><MagicStick /></el-icon> {{ record.category }}</span>
              <div class="h-item-tags">
                <span v-for="tag in record.extractedTags" :key="tag" class="micro-tag">{{ tag }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="panoramic-empty">
      <el-empty description="该客户暂无更多历史记录" />
    </div>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  history: {
    type: Array,
    default: () => [],
  },
  drawerStats: {
    type: Object,
    default: () => ({ totalCount: 0, totalBudget: 0, topCategory: '未知' }),
  },
  categoryStats: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['update:visible'])

const visibleProxy = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})
</script>

<style scoped>
.panoramic-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panoramic-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stat-card,
.panoramic-section {
  border: 1px solid var(--gray-200);
  border-radius: 16px;
  background: white;
}

.stat-card {
  padding: 16px;
}

.stat-label,
.cat-info,
.h-item-date {
  color: var(--text-slate);
  font-size: 12px;
}

.stat-value {
  margin: 10px 0 0;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.panoramic-section {
  padding: 16px;
}

.section-title {
  margin: 0 0 12px;
  font-size: 15px;
  color: #0f172a;
}

.category-bars,
.history-scroll-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cat-info,
.h-item-top,
.h-item-footer {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.cat-progress-bg {
  height: 8px;
  border-radius: 999px;
  background: var(--gray-200);
  overflow: hidden;
  margin-top: 8px;
}

.cat-progress-fill {
  height: 100%;
  border-radius: inherit;
}

.history-item-card {
  border: 1px solid var(--gray-200);
  border-radius: 14px;
  padding: 14px;
  background: #f8fafc;
}

.h-item-title {
  margin: 10px 0;
  font-weight: 600;
  color: #0f172a;
}

.h-item-cat {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--sidebar-text-secondary);
  font-size: 12px;
}

.h-item-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.micro-tag {
  padding: 2px 8px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 11px;
}

.panoramic-empty {
  display: grid;
  place-items: center;
  min-height: 320px;
}

@media (max-width: 960px) {
  .panoramic-stats {
    grid-template-columns: 1fr;
  }
}
</style>
