<template>
  <div class="alert-stats-row">
    <el-card class="stat-card" shadow="hover">
      <div class="stat-content">
        <div class="stat-label">总告警数</div>
        <div class="stat-value">{{ stats.totalAlerts ?? 0 }}</div>
      </div>
    </el-card>
    <el-card class="stat-card" shadow="hover">
      <div class="stat-content">
        <div class="stat-label">未解决数</div>
        <div class="stat-value warning">{{ stats.unresolvedAlerts ?? 0 }}</div>
      </div>
    </el-card>
    <el-card class="stat-card" shadow="hover">
      <div class="stat-content">
        <div class="stat-label">高严重数</div>
        <div class="stat-value danger">{{ stats.highAlerts ?? 0 }}</div>
      </div>
    </el-card>
    <el-card class="stat-card" shadow="hover">
      <div class="stat-content">
        <div class="stat-label">严重数</div>
        <div class="stat-value critical">{{ stats.criticalAlerts ?? 0 }}</div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
defineProps({
  stats: {
    type: Object,
    default: () => ({ totalAlerts: 0, unresolvedAlerts: 0, highAlerts: 0, criticalAlerts: 0 })
  }
})
</script>

<style scoped>
.alert-stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card { border-radius: 8px; }
.stat-content { display: flex; flex-direction: column; align-items: center; padding: 8px 0; }
.stat-label { color: var(--el-text-color-secondary); font-size: 14px; margin-bottom: 8px; }
.stat-value { font-size: 28px; font-weight: 600; color: var(--el-text-color-primary); }
.stat-value.warning { color: var(--el-color-warning); }
.stat-value.danger { color: var(--el-color-danger); }
.stat-value.critical { color: var(--el-color-danger-dark-2); }
</style>
