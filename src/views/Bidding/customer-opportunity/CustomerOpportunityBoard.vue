<template>
  <div class="top-board">
    <div class="board-card hover-lift" v-for="item in summaries" :key="item.label">
      <div class="card-label">
        <span>{{ item.label }}</span>
        <el-tag size="small" :type="item.tagType" effect="light" class="tag-glow">{{ item.tag }}</el-tag>
      </div>
      <div class="card-main">
        <div class="card-value">{{ item.value }}</div>
        <div class="card-trend" :class="item.placeholder ? 'neutral' : (item.isUp ? 'up' : 'down')">
          <template v-if="item.placeholder">
            <el-tag size="small" type="info" effect="plain">{{ item.trendLabel || '未接入' }}</el-tag>
          </template>
          <template v-else>
            <el-icon><CaretTop v-if="item.isUp" /><CaretBottom v-else /></el-icon>
            {{ item.trend }}%
          </template>
        </div>
      </div>
      <div class="spark-box">
        <div class="spark-line" :class="item.tagType"></div>
      </div>
      <p class="card-note">{{ item.note }}</p>
    </div>
  </div>
</template>

<script setup>
import { CaretTop, CaretBottom } from '@element-plus/icons-vue'

defineProps({
  summaries: {
    type: Array,
    default: () => [],
  },
})
</script>

<style scoped>
.top-board {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
  margin-bottom: 32px;
}

.board-card {
  background: white;
  padding: 24px;
  border-radius: 16px;
  border: 1px solid var(--gray-50);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.board-card.hover-lift:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 30px -8px rgba(3, 105, 161, 0.15);
  border-color: #bae6fd;
}

.card-main {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 16px 0 4px;
}

.card-trend {
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 2px;
}

.card-trend.up { color: var(--color-success); }
.card-trend.down { color: #f43f5e; }
.card-trend.neutral { color: var(--text-slate); }

.spark-box {
  height: 32px;
  margin-bottom: 12px;
  display: flex;
  align-items: flex-end;
}

.spark-line {
  height: 4px;
  width: 100%;
  border-radius: 2px;
  background: var(--gray-50);
  position: relative;
  overflow: hidden;
}

.spark-line::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: 60%;
  border-radius: 2px;
  animation: spark-slide 2s ease-in-out infinite alternate;
}

.spark-line.success::after { background: var(--color-success); }
.spark-line.danger::after { background: #f43f5e; }
.spark-line.warning::after { background: #f59e0b; }
.spark-line.info::after { background: #3b82f6; }

@keyframes spark-slide {
  from { transform: translateX(-20%); }
  to { transform: translateX(120%); }
}

.board-card::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 120px;
  height: 120px;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.08) 0%, transparent 70%);
  pointer-events: none;
}

.card-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.card-label span {
  font-size: 14px;
  color: var(--text-slate);
}

.card-value {
  font-size: 36px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.03em;
  color: #0f172a;
}

.card-note {
  margin: 0;
  color: var(--text-slate);
  font-size: 13px;
}
</style>
