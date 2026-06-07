<template>
  <div class="project-detail-feature-modals">
    <CompetitionIntel v-model="detail.showCompetitionIntel" :project-id="detail.route.params.id" />
    <ComplianceCheck v-model="detail.showComplianceCheck" :project-id="detail.route.params.id" />
    <VersionControl v-model="detail.showVersionControl" :project-id="detail.dialogProjectId" />
    <CollaborationCenter v-model="detail.showCollaboration" :project-id="detail.dialogProjectId" />
    <ROIAnalysis v-model="detail.showROIAnalysis" :project-id="detail.route.params.id" />
    <AutoTasks v-if="detail.isDemoMode" v-model="detail.showAutoTasks" :project-id="detail.route.params.id" :data="detail.demoAutoTasks" />
    <MobileCard v-if="detail.isDemoMode" v-model="detail.showMobileCard" :project-id="detail.route.params.id" :data="detail.demoMobileCard" />
    <ScoreDraftDialog v-model:visible="detail.scoreDraftDialogVisible" :project-id="detail.route.params.id" @generated="detail.handleScoreDraftGenerated" />
    <ApprovalDialog v-model:visible="detail.approvalDialogVisible" :mode="detail.approvalMode" :project-id="detail.project?.id" :project-name="detail.project?.name" :approval-type="detail.approvalType" :approval-info="detail.currentApprovalItem" @success="detail.handleApprovalSuccess" />
  </div>
</template>

<script setup>
import { inject } from 'vue'
import CollaborationCenter from '@/components/ai/CollaborationCenter.vue'
import CompetitionIntel from '@/components/ai/CompetitionIntel.vue'
import ComplianceCheck from '@/components/ai/ComplianceCheck.vue'
import MobileCard from '@/components/ai/MobileCard.vue'
import ROIAnalysis from '@/components/ai/ROIAnalysis.vue'
import AutoTasks from '@/components/ai/AutoTasks.vue'
import VersionControl from '@/components/ai/VersionControl.vue'
import ApprovalDialog from '@/components/common/ApprovalDialog.vue'
import ScoreDraftDialog from '@/components/project/ScoreDraftDialog.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

const detail = inject(projectDetailKey)
</script>
