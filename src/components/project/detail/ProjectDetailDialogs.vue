<template>
  <div class="project-detail-dialogs">
    <SmartAssistantPanel v-model:visible="detail.assistantPanelVisible.value" :project-id="detail.dialogProjectId.value" :show-demo-features="detail.isDemoMode" @open-competition-intel="detail.handleOpenCompetitionIntel" @open-roi-analysis="detail.handleOpenRoiAnalysis" @open-score-coverage="detail.handleOpenScoreCoverage" @open-compliance-check="detail.handleOpenComplianceCheck" @open-version-control="detail.handleOpenVersionControl" @open-collaboration="detail.handleOpenCollaboration" @open-auto-tasks="detail.handleOpenAutoTasks" @open-mobile-card="detail.handleOpenMobileCard" />
    <ProjectDetailResultDialogs />
    <ProjectDetailWorkflowDialog />
    <ProjectDetailReviewerDialog />
    <ProjectDetailFeatureModals />
  </div>
</template>

<script setup>
import { inject } from 'vue'
import SmartAssistantPanel from '@/components/ai/SmartAssistantPanel.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'
import ProjectDetailFeatureModals from './ProjectDetailFeatureModals.vue'
import ProjectDetailResultDialogs from './ProjectDetailResultDialogs.vue'
import ProjectDetailReviewerDialog from './ProjectDetailReviewerDialog.vue'
import ProjectDetailWorkflowDialog from './ProjectDetailWorkflowDialog.vue'

const detail = inject(projectDetailKey)
</script>
