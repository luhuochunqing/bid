<template>
  <div class="m0-kpi-cards">
    <div v-if="loading" class="kpi-grid">
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
        class="kpi-card"
        :class="card.colorClass"
      >
        <div class="kpi-label">{{ card.label }}</div>
        <div class="kpi-value">
          {{ card.value }}<span class="unit">{{ card.unit }}</span>
        </div>
        <div class="kpi-foot">{{ card.foot }}</div>
        <div class="kpi-trend" :class="card.trendDirection">{{ card.trendText }}</div>
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

.kpi-skeleton {
  background: var(--bg-card);
  border-radius: 12px;
  padding: 24px 28px;
  border: 1px solid #E2E8F0;
}

.kpi-error {
  padding: 40px;
  background: var(--bg-card);
  border-radius: 12px;
  border: 1px solid #E2E8F0;
  display: flex;
  justify-content: center;
}

.kpi-card {
  background: var(--bg-card);
  border-radius: 12px;
  padding: 24px 28px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  cursor: default;
  transition: all 0.3s;
  border: 1px solid #E2E8F0;
  position: relative;
  overflow: hidden;
}

.kpi-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
}

.kpi-card.kpi-blue::before { background: var(--brand-xiyu-logo); }
.kpi-card.kpi-green::before { background: #10B981; }
.kpi-card.kpi-orange::before { background: #F59E0B; }
.kpi-card.kpi-purple::before { background: #60A5FA; }

.kpi-card::after {
  content: '';
  position: absolute;
  top: -30px;
  right: -30px;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  opacity: 0.04;
  pointer-events: none;
}

.kpi-card.kpi-blue::after { background: var(--brand-xiyu-logo); }
.kpi-card.kpi-green::after { background: #10B981; }
.kpi-card.kpi-orange::after { background: #F59E0B; }
.kpi-card.kpi-purple::after { background: #60A5FA; }

.kpi-card:hover {
  box-shadow: 0 10px 15px -3px rgba(15, 23, 42, 0.08);
  transform: translateY(-3px);
}

.kpi-label {
  font-size: 13px;
  color: #94A3B8;
  margin-bottom: 10px;
  font-weight: 500;
}

.kpi-value {
  font-size: 34px;
  font-weight: 800;
  color: #1E293B;
  line-height: 1.1;
  letter-spacing: -1px;
}

.kpi-value .unit {
  font-size: 16px;
  font-weight: 500;
  margin-left: 4px;
  color: #475569;
}

.kpi-foot {
  margin-top: 10px;
  font-size: 12px;
  color: #94A3B8;
}

.kpi-trend {
  font-size: 12px;
  margin-top: 6px;
  font-weight: 600;
}

.kpi-trend.up { color: #10B981; }
.kpi-trend.down { color: #EF4444; }
.kpi-trend.flat { color: #94A3B8; }

@media (max-width: 1400px) {
  .kpi-grid,
  .kpi-grid:has(.kpi-skeleton) {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .kpi-grid {
    grid-template-columns: 1fr;
  }
}
</style>
