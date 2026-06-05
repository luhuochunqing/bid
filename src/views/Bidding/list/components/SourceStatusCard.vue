<template>
  <el-card v-if="sourceConfig.platforms.length > 0" class="source-status-card" shadow="never">
    <div class="source-status-content">
      <div class="status-left">
        <el-icon class="status-icon"><Setting /></el-icon>
        <span>已配置 {{ sourceConfig.platforms.length }} 个标讯源</span>
        <el-tag v-for="platform in sourceConfig.platforms" :key="platform" size="small" type="info">
          {{ platform }}
        </el-tag>
      </div>
      <div class="status-right">
        <span v-if="sourceConfig.autoSync">
          <el-icon><Refresh /></el-icon>
          每 {{ sourceConfig.syncInterval }} 小时自动同步
        </span>
        <span>上次同步: {{ lastSyncTime }}</span>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { Refresh, Setting } from '@element-plus/icons-vue'

defineProps({
  sourceConfig: { type: Object, required: true },
  lastSyncTime: { type: String, default: '暂未同步' },
})
</script>
