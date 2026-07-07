<template>
  <div class="alert-stats-row">
    <el-card class="stat-card" shadow="hover">
      <div class="stat-content">
        <div class="stat-label">总告警数</div>
        <div class="stat-value">{{ stats.totalCount ?? 0 }}</div>
      </div>
    </el-card>
    <el-card class="stat-card" shadow="hover">
      <div class="stat-content">
        <div class="stat-label">未解决数</div>
        <div class="stat-value warning">{{ stats.unresolvedCount ?? 0 }}</div>
      </div>
    </el-card>
    <el-card class="stat-card" shadow="hover">
      <div class="stat-content">
        <div class="stat-label">高严重数</div>
        <div class="stat-value danger">{{ stats.highCount ?? 0 }}</div>
      </div>
    </el-card>
    <el-card class="stat-card" shadow="hover">
      <div class="stat-content">
        <div class="stat-label">严重数</div>
        <div class="stat-value critical">{{ stats.criticalCount ?? 0 }}</div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
defineProps({
  stats: {
    type: Object,
    default: () => ({ totalCount: 0, unresolvedCount: 0, highCount: 0, criticalCount: 0 })
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
.stat-label { color: #909399; font-size: 14px; margin-bottom: 8px; }
.stat-value { font-size: 28px; font-weight: 600; color: #303133; }
.stat-value.warning { color: #e6a23c; }
.stat-value.danger { color: #f56c6c; }
.stat-value.critical { color: #ab4567; }
</style>
