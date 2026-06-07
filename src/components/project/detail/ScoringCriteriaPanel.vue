<template>
  <section class="agent-section">
    <header>
      评分标准解析
      <span v-if="totalScore" class="total-score">总分：{{ totalScore }}</span>
      <el-badge :value="items?.length || 0" :hidden="!items?.length" type="primary" class="panel-badge">
        <el-button size="small" text :loading="loading" @click="$emit('fetch')">获取评分标准</el-button>
      </el-badge>
    </header>
    <div v-if="items?.length" class="criteria-list">
      <!-- 结构化数据展示（ScoringCriterion） -->
      <div v-for="(item, idx) in items" :key="idx" class="criteria-item">
        <div class="criteria-header">
          <span v-if="item.itemNumber" class="criteria-number">#{{ item.itemNumber }}</span>
          <el-tag :type="tagType(item.subType)" size="small" effect="plain">{{ tagLabel(item.subType) }}</el-tag>
          <span v-if="item.weight" class="criteria-weight">{{ item.weight }}分</span>
        </div>
        <div v-if="item.dimension" class="criteria-dimension">{{ item.dimension }}</div>
        <div class="criteria-text">{{ item.indicator || item.text }}</div>
      </div>
    </div>
    <div v-else-if="fetched" class="criteria-empty">
      <el-empty description="未解析出评分标准" :image-size="48" />
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
  totalScore: { type: [Number, String], default: null },
  loading: { type: Boolean, default: false },
  fetched: { type: Boolean, default: false },
})
defineEmits(['fetch'])

const totalScore = computed(() => props.totalScore)

const tagLabel = (type) => ({
  PRICE_WEIGHT: '价格权重',
  TECHNICAL_EVALUATION: '技术评价',
  SERVICE_EVALUATION: '服务评价',
  QUALIFICATION_THRESHOLD: '资质门槛',
  COMPREHENSIVE_SCORE: '综合评分',
  OTHER: '其他',
}[type] || type)

const tagType = (type) => ({
  PRICE_WEIGHT: 'danger',
  TECHNICAL_EVALUATION: 'primary',
  SERVICE_EVALUATION: 'warning',
  QUALIFICATION_THRESHOLD: 'info',
  COMPREHENSIVE_SCORE: 'success',
  OTHER: '',
}[type] || 'info')
</script>

<style scoped>
.criteria-list { display: grid; gap: 8px; }
.criteria-item {
  padding: 10px; border-radius: 10px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}
.criteria-header {
  display: flex; align-items: center; gap: 6px; margin-bottom: 4px;
}
.criteria-number {
  font-weight: 700; color: var(--el-text-color-primary); font-size: 13px; min-width: 24px;
}
.criteria-weight {
  font-size: 12px; color: var(--el-color-warning); font-weight: 600;
}
.criteria-dimension {
  font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 2px;
}
.criteria-text { line-height: 1.5; color: var(--el-text-color-primary); font-size: 13px; }
.criteria-empty { padding: 8px 0; }
.panel-badge { display: inline-flex; vertical-align: middle; }
.total-score {
  margin-left: 8px; font-size: 12px; color: var(--el-color-success); font-weight: 600;
}
</style>
