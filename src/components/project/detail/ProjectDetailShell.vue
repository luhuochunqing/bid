<template>
  <div class="project-detail-page">
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="5" animated />
    </div>
    <div v-else-if="!project" class="empty-container">
      <el-empty description="未找到项目信息">
        <el-button type="primary" @click="goBack">返回项目列表</el-button>
      </el-empty>
    </div>
    <div v-else class="project-detail-container">
      <ProjectDetailHeader />
      <div class="detail-content">
        <ProjectDetailMainColumn />
        <ProjectDetailSidebar />
      </div>
      <ProjectDetailAssistantPanels />
      <ProjectDetailResultDialogs />
      <ProjectDetailWorkflowDialogs />
      <ProjectTenderBreakdownDialog />
      <ProjectDetailBidAgentDrawer />
    </div>
  </div>
</template>

<script setup>
import { computed, markRaw, provide, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { projectsApi } from '@/api'
import * as projectTenderBreakdownApi from '@/api/modules/projectTenderBreakdown.js'
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'
import { useBarStore } from '@/stores/bar'
import { useProjectExpenseAggregation } from '@/components/project/composables/useProjectExpenseAggregation.js'
import { projectDetailKey } from '@/composables/projectDetail/context.js'
import { useProjectDetailAI } from '@/composables/projectDetail/useProjectDetailAI.js'
import { useProjectDetailBidAgent } from '@/composables/projectDetail/useProjectDetailBidAgent.js'
import { useProjectDetailBoot } from '@/composables/projectDetail/useProjectDetailBoot.js'
import { useProjectDetailDocumentActions } from '@/composables/projectDetail/useProjectDetailDocumentActions.js'
import { useProjectDetailFormatting } from '@/composables/projectDetail/useProjectDetailFormatting.js'
import { useProjectDetailNavigation } from '@/composables/projectDetail/useProjectDetailNavigation.js'
import { useProjectDetailResultActions } from '@/composables/projectDetail/useProjectDetailResultActions.js'
import { useProjectDetailQuality } from '@/composables/projectDetail/useProjectDetailQuality.js'
import { useProjectDetailState } from '@/composables/projectDetail/useProjectDetailState.js'
import { useProjectDetailTaskActions } from '@/composables/projectDetail/useProjectDetailTaskActions.js'
import { useProjectDetailWorkflow } from '@/composables/projectDetail/useProjectDetailWorkflow.js'
import ProjectDetailAssistantPanels from './ProjectDetailAssistantPanels.vue'
import ProjectDetailBidAgentDrawer from './ProjectDetailBidAgentDrawer.vue'
import ProjectDetailHeader from './ProjectDetailHeader.vue'
import ProjectDetailMainColumn from './ProjectDetailMainColumn.vue'
import ProjectDetailResultDialogs from './ProjectDetailResultDialogs.vue'
import ProjectDetailSidebar from './ProjectDetailSidebar.vue'
import ProjectTenderBreakdownDialog from './ProjectTenderBreakdownDialog.vue'
import ProjectDetailWorkflowDialogs from './ProjectDetailWorkflowDialogs.vue'
import './project-detail-shell.css'
import './project-detail-sidebar.css'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const userStore = useUserStore()
const barStore = useBarStore()
const isDemoMode = false
const demoAutoTasks = ref([])
const demoMobileCard = ref(null)
const isApiProject = computed(() => !isDemoMode && /^-?\d+$/.test(String(route.params.id || '')))
const message = { success: ElMessage.success, error: ElMessage.error, warning: ElMessage.warning, info: ElMessage.info }
const baseContext = { route, router, projectStore, userStore, barStore, isDemoMode, isApiProject, message }
const state = useProjectDetailState(baseContext)
const workflow = useProjectDetailWorkflow({ ...baseContext, processDialogVisible: state.processDialogVisible, reviewerDialogVisible: state.reviewerDialogVisible })
const expenseAggregation = useProjectExpenseAggregation({ projectStore, project: state.project, isApiProject })
const formatting = useProjectDetailFormatting({ project: state.project })
const ai = useProjectDetailAI({ ...baseContext, project: state.project, state })
const quality = useProjectDetailQuality({ ...baseContext, project: state.project })
const bidAgent = useProjectDetailBidAgent({ ...baseContext, project: state.project })
const navigation = useProjectDetailNavigation({ route, router, project: state.project, assetCheckResult: state.assetCheckResult })
const documentActions = useProjectDetailDocumentActions({ route, project: state.project, projectExpenses: expenseAggregation.projectExpenses, userStore, projectsApi, isApiProject, message, state })
const boot = useProjectDetailBoot({ ...baseContext, state, workflow, expenseAggregation, loadProjectWorkflowData: documentActions.loadProjectWorkflowData, demoAutoTasks, demoMobileCard })
const resultActions = useProjectDetailResultActions({ route, projectStore, message, state, approvalType: state.approvalType, loadApprovalHistory: boot.loadApprovalHistory, navigation })
const taskActions = useProjectDetailTaskActions({
  route,
  userStore,
  projectStore,
  projectsApi,
  tenderBreakdownApi: projectTenderBreakdownApi,
  isApiProject,
  message,
  state,
  workflow,
})

const loading = state.loading
const project = state.project
const goBack = navigation.goBack

const projectDetailContext = reactive({
  route,
  userStore,
  isDemoMode,
  demoAutoTasks,
  demoMobileCard,
  ...state,
  ...workflow,
  ...expenseAggregation,
  ...formatting,
  ...ai,
  ...quality,
  bidAgent: markRaw(bidAgent),
  ...navigation,
  ...documentActions,
  ...resultActions,
  ...taskActions,
  runAICheck: async () => {
    await ai.runAICheck()
    await quality.runQualityCheck({ silent: true })
  },
  runBidDocumentQualityCheck: async () => {
    await ai.runBidDocumentQualityCheck()
  },
})

provide(projectDetailKey, projectDetailContext)
</script>
