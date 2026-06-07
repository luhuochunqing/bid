<template>
  <div class="project-detail-sidebar-info">
    <el-card class="action-card">
      <template #header><div class="card-title"><el-icon><Operation /></el-icon><span>快捷操作</span></div></template>
      <div class="action-list">
        <el-button v-if="detail.canManageProjectDocuments.value" :icon="DocumentAdd" @click="detail.handleAddDocument">添加文档</el-button>
        <el-button :icon="Share" @click="detail.handleShare">分享项目</el-button>
        <el-button :icon="Download" @click="detail.handleExport">导出资料</el-button>
        <el-button v-if="detail.canSetProjectReminder.value" :icon="Bell" @click="detail.handleSetReminder">设置提醒</el-button>
      </div>
    </el-card>

    <el-card class="timeline-card">
      <template #header><div class="card-title"><el-icon><Clock /></el-icon><span>项目动态</span></div></template>
      <el-timeline><el-timeline-item v-for="activity in detail.activities.value" :key="activity.id" :timestamp="activity.time" placement="top"><div class="activity-content"><span class="activity-user">{{ activity.user }}</span><span class="activity-action">{{ activity.action }}</span></div></el-timeline-item></el-timeline>
    </el-card>
  </div>
</template>

<script setup>
import { inject } from 'vue'
import { Bell, Clock, DocumentAdd, Download, Operation, Share } from '@element-plus/icons-vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

const detail = inject(projectDetailKey)
</script>
