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
    <template #extra>
      <div class="header-actions" aria-label="项目操作">
        <el-button :icon="Edit" @click="ctx.handleEdit">编辑</el-button>
        <el-button v-if="ctx.canSubmit" type="primary" :icon="DocumentChecked" @click="ctx.handleSubmitApproval">提交审批</el-button>
        <el-button v-if="ctx.canRecordResult" type="success" :icon="Coin" @click="ctx.handleRecordResult">录入结果</el-button>
        <el-button class="secondary-action" type="warning" :icon="DataAnalysis" @click="ctx.goToResultPage">结果闭环</el-button>
        <el-button
          class="secondary-action"
          type="info"
          :icon="MagicStick"
          @click="ctx.toggleAssistantPanel"
          :class="{ 'is-active': ctx.assistantPanelVisible }"
        >
          智能助手
        </el-button>
      </div>
    </template>
  </el-page-header>
</template>

<script setup>
import { Coin, DataAnalysis, DocumentChecked, Edit, MagicStick } from '@element-plus/icons-vue'
import { useProjectDetailContext } from '@/composables/projectDetail/context.js'

const ctx = useProjectDetailContext()
</script>
