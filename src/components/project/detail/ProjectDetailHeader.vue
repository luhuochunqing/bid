<template>
  <el-page-header class="page-header project-detail-toolbar">
    <template #content>
      <div class="header-content">
        <div class="header-title">
          <span class="project-name" :title="ctx.project?.name">{{ ctx.project?.name }}</span>
          <el-tag :type="ctx.getStatusType(ctx.project?.status)" class="status-tag">
            {{ ctx.getStatusText(ctx.project?.status) }}
          </el-tag>
        </div>
      </div>
    </template>
  </el-page-header>
</template>

<script setup>
import { useProjectDetailContext } from '@/composables/projectDetail/context.js'

const ctx = useProjectDetailContext()
</script>
