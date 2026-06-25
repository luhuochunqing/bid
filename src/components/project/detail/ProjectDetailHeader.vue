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
        <!-- CO-330: 隐藏「编辑」按钮，composable ctx.handleEdit 保留不动 -->
        <!-- <el-button :icon="Edit" @click="ctx.handleEdit">编辑</el-button> -->
        <el-button v-if="ctx.canSubmit" type="primary" :icon="DocumentChecked" @click="ctx.handleSubmitApproval">提交审批</el-button>
        <el-button v-if="ctx.canRecordResult" type="success" :icon="Coin" @click="ctx.handleRecordResult">录入结果</el-button>
        <!-- CO-330: 隐藏「结果闭环」按钮，composable ctx.goToResultPage 保留不动 -->
        <!-- <el-button class="secondary-action" type="warning" :icon="DataAnalysis" @click="ctx.goToResultPage">结果闭环</el-button> -->
        <!-- CO-330: 隐藏「智能助手」按钮，composable ctx.toggleAssistantPanel 保留不动，面板去留待产品确认 -->
        <!--
        <el-button
          class="secondary-action"
          type="info"
          :icon="MagicStick"
          @click="ctx.toggleAssistantPanel"
          :class="{ 'is-active': ctx.assistantPanelVisible }"
        >
          智能助手
        </el-button>
        -->
      </div>
    </template>
  </el-page-header>
</template>

<script setup>
// CO-330: 隐藏编辑/结果闭环/智能助手按钮，Edit/DataAnalysis/MagicStick 暂不引入（恢复时取消下一行注释）
import { Coin, DocumentChecked } from '@element-plus/icons-vue'
// import { DataAnalysis, Edit, MagicStick } from '@element-plus/icons-vue'
import { useProjectDetailContext } from '@/composables/projectDetail/context.js'

const ctx = useProjectDetailContext()
</script>
