<template>
  <el-main class="main-content">
    <ProjectBasicInfoCard :project="detail.project.value" />
    <ProjectApprovalStatusCard :approval-history="detail.approvalHistory.value" :project-status="detail.project.value?.status" :can-approve-current="detail.canApproveCurrent.value" @quick-approve="detail.handleQuickApprove" @quick-reject="detail.handleQuickReject" />
    <ProjectExpenseSummaryCard :expenses="detail.projectExpenses.value" :summary="detail.expenseSummary.value" :loading="detail.expenseLoading.value" :error="detail.expenseError.value" @manage="detail.goToExpensePage" />
    <ProjectTaskBoardCard :tasks="detail.project.value?.tasks || []" :project-id="detail.project.value?.id" :can-manage-project-tasks="detail.canManageProjectTasks.value" :is-demo-mode="detail.isDemoMode" @add-task="detail.handleAddTask" @reset-tasks="detail.handleResetTasks" @task-click="detail.handleTaskClick" @save-task="detail.handleSaveTask" @status-change="detail.handleTaskStatusChange" @generate-tasks="detail.handleGenerateTasks" @tender-breakdown="detail.handleOpenTenderBreakdown" @score-draft-decompose="detail.handleOpenScoreDraftDecompose" @add-deliverable="detail.handleAddDeliverable" @remove-deliverable="detail.handleRemoveDeliverable" @submit-to-document="detail.handleSubmitToDocument" />
    <ProjectDetailWorkflowCard />
    <ProjectDetailDocumentsCard />
  </el-main>
</template>

<script setup>
import { inject } from 'vue'
import ProjectApprovalStatusCard from '@/components/project/ProjectApprovalStatusCard.vue'
import ProjectBasicInfoCard from '@/components/project/ProjectBasicInfoCard.vue'
import ProjectExpenseSummaryCard from '@/components/project/ProjectExpenseSummaryCard.vue'
import ProjectTaskBoardCard from '@/components/project/ProjectTaskBoardCard.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'
import ProjectDetailDocumentsCard from './ProjectDetailDocumentsCard.vue'
import ProjectDetailWorkflowCard from './ProjectDetailWorkflowCard.vue'

const detail = inject(projectDetailKey)
</script>
