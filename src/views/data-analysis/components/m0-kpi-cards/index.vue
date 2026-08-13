<template>
  <div class="m0-kpi-cards">
    <div v-if="loading" class="kpi-loading">
      <div v-for="i in 4" :key="i" class="kpi-skeleton">
        <el-skeleton animated />
      </div>
    </div>
    <div v-else-if="error" class="kpi-error">
      <el-empty description="数据加载失败" :image-size="80">
        <el-button size="small" @click="$emit('retry')">重试</el-button>
      </el-empty>
    </div>
    <div v-else class="kpi-grid">
      <div
        v-for="card in kpiCards"
        :key="card.key"
        class="b2b-metric-card"
        :class="'metric-' + card.colorClass"
      >
        <div class="b2b-metric-content">
          <div class="b2b-metric-label">{{ card.label }}</div>
          <div class="b2b-metric-value">{{ card.value }}</div>
          <div class="b2b-metric-trend" :class="card.trend > 0 ? 'positive' : card.trend < 0 ? 'negative' : ''">
            <span class="trend-value">{{ card.trendText }}</span>
            <span class="trend-label">较上月</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  kpiCards: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  error: {
    type: Boolean,
    default: false
  }
})

defineEmits(['retry'])
</script>

<style scoped>
.m0-kpi-cards {
  margin-bottom: 0;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.kpi-loading {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.kpi-skeleton {
  padding: 24px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--gray-100);
}

.kpi-error {
  padding: 40px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--gray-100);
  display: flex;
  justify-content: center;
}

@media (max-width: 1400px) {
  .kpi-grid,
  .kpi-loading {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .kpi-grid,
  .kpi-loading {
    grid-template-columns: 1fr;
  }
}
</style>