<template>
  <div class="ai-prediction-placeholder">
    <div class="placeholder-header">
      <el-icon :size="24" color="var(--text-muted)"><TrendCharts /></el-icon>
      <span class="placeholder-title">AI 商机预测</span>
      <el-tag size="small" type="info">规划中</el-tag>
    </div>
    <p class="placeholder-desc">
      基于存量客户历史投标数据分析招标窗口期，自动生成跟进提醒。
      该功能正在规划中，敬请期待。
    </p>
  </div>
</template>

<script setup>
import { TrendCharts } from '@element-plus/icons-vue'
</script>

<style scoped>
.ai-prediction-placeholder {
  background: var(--bg-card);
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
  padding: 24px;
  margin-top: 24px;
}
.placeholder-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.placeholder-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-750);
}
.placeholder-desc {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
  line-height: 1.6;
}
</style>
