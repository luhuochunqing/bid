<template>
  <div class="stats-cards">
    <el-card class="stat-card" shadow="hover">
      <div class="stat-content">
        <div class="stat-icon bg-primary"><el-icon><Files /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ totalArchives }}</div>
          <div class="stat-label">归档项目总数</div>
        </div>
      </div>
    </el-card>
    <el-card class="stat-card" shadow="hover">
      <div class="stat-content">
        <div class="stat-icon bg-success"><el-icon><CircleCheck /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ closedProjects }}</div>
          <div class="stat-label">已闭环项目</div>
        </div>
      </div>
    </el-card>
    <el-card class="stat-card" shadow="hover">
      <div class="stat-content">
        <div class="stat-icon bg-warning"><el-icon><Collection /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ caseCount }}</div>
          <div class="stat-label">沉淀案例数量</div>
        </div>
      </div>
    </el-card>
    <el-card class="stat-card" shadow="hover">
      <div class="stat-content">
        <div class="stat-icon bg-info"><el-icon><Refresh /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ reuseCount }}</div>
          <div class="stat-label">复用案例总数</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { Files, CircleCheck, Collection, Refresh } from '@element-plus/icons-vue'

const props = defineProps({
  totalArchives: { type: Number, default: 0 },
  closedProjects: { type: Number, default: 0 },
  caseCount: { type: Number, default: 0 },
  reuseCount: { type: Number, default: 0 }
})
</script>

<style scoped lang="scss">
.stats-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  }
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 0;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: var(--el-color-white);
}

.bg-primary { background: linear-gradient(135deg, var(--el-color-primary), var(--el-color-primary-light-3)); }
.bg-success { background: linear-gradient(135deg, var(--el-color-success), var(--el-color-success-light-3)); }
.bg-warning { background: linear-gradient(135deg, var(--el-color-warning), var(--el-color-warning-light-3)); }
.bg-info { background: linear-gradient(135deg, var(--el-color-info), var(--el-color-info-light-3)); }

.stat-info { flex: 1; }

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>
