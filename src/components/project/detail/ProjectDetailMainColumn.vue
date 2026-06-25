<template>
  <div class="main-content">
    <ProjectBasicInfoCard :project="ctx.project" />
    <ProjectStageTimeline
      v-if="ctx.project?.id"
      ref="timelineRef"
      :project-id="ctx.project.id"
      @stage-click="handleStageClick"
      @snapshot="handleSnapshot"
    />

    <div class="stage-tabs-container">
      <InitiationStage
        v-if="activeStageTab === 'INITIATED' && ctx.project?.id"
        :key="ctx.project.id"
        :project-id="ctx.project.id"
        @updated="handleStageUpdated"
      />
      <div v-else-if="activeStageTab === 'DRAFTING'" class="drafting-tab-content">
        <ProjectTaskBoardCard
          :tasks="ctx.project?.tasks || []"
          :project-id="ctx.project?.id"
          :show-submit-button="false"
          :can-manage-project-tasks="ctx.canManageProjectTasks && currentProjectStage === 'DRAFTING'"
          :is-demo-mode="ctx.isDemoMode"
          @add-task="ctx.handleAddTask"
          @reset-tasks="ctx.handleResetTasks"
          @task-click="ctx.handleTaskClick"
          @save-task="ctx.handleSaveTask"
          @status-change="ctx.handleTaskStatusChange"
          @generate-tasks="ctx.handleGenerateTasks"
          @open-score-parse="scoreParseRef?.open()"
          @open-decompose="taskDecomposeRef?.open()"
          @add-deliverable="ctx.handleAddDeliverable"
          @remove-deliverable="ctx.handleRemoveDeliverable"
          @submit-to-document="ctx.handleSubmitToDocument"
        />
        <ScoreParseDrawer ref="scoreParseRef" :project-id="ctx.project?.id" />
        <TaskDecomposeDialog ref="taskDecomposeRef" :project-id="ctx.project?.id" />
        <DraftingStage
          v-if="ctx.project?.id"
          :key="ctx.project.id"
          :project-id="ctx.project.id"
          @advanced="handleStageUpdated"
        />
      </div>
      <EvaluationStage
        v-else-if="activeStageTab === 'EVALUATING' && ctx.project?.id"
        :key="ctx.project.id"
        :project-id="ctx.project.id"
        @advanced="handleStageUpdated"
        @switch-tab="handleSwitchTab"
      />
      <ResultConfirmStage
        v-else-if="activeStageTab === 'RESULT_PENDING' && ctx.project?.id"
        :key="ctx.project.id"
        :project-id="ctx.project.id"
        @registered="handleStageUpdated"
        @switch-tab="handleSwitchTab"
      />
      <RetrospectiveStage
        v-else-if="activeStageTab === 'RETROSPECTIVE' && ctx.project?.id"
        :key="ctx.project.id"
        :project-id="ctx.project.id"
        @submitted="onRetrospectiveSubmitted"
        :result-type="resultType"
      />
      <ClosureStage
        v-else-if="activeStageTab === 'CLOSED' && ctx.project?.id"
        :key="ctx.project.id"
        :project-id="ctx.project.id"
        @closed="handleStageUpdated"
      />
      <el-empty v-else-if="ctx.project?.id" description="项目阶段加载中..." :image-size="60" />
    </div>

    <ProjectApprovalStatusCard
      :approval-history="ctx.approvalHistory"
      :project-status="ctx.project?.status"
      :can-approve-current="ctx.canApproveCurrent"
      @quick-approve="ctx.handleQuickApprove"
      @quick-reject="ctx.handleQuickReject"
    />

    <el-card class="timeline-card">
      <template #header>
        <div class="card-title">
          <el-icon><Clock /></el-icon>
          <span>项目动态</span>
        </div>
      </template>
      <el-timeline>
        <el-timeline-item
          v-for="activity in ctx.activities"
          :key="activity.id"
          :timestamp="activity.time"
          placement="top"
        >
          <div class="activity-content">
            <span class="activity-user">{{ activity.user }}</span>
            <span class="activity-action">{{ activity.action }}</span>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- 项目文档（暂时隐藏）
    <el-card class="document-card">
      ...
    </el-card>
    -->

    <!-- 协作讨论（暂时隐藏
    <ProjectCollaborationCard :project-id="ctx.project?.id" />
    -->
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Clock } from '@element-plus/icons-vue'
// import { Document, DocumentChecked, Folder, Upload } from '@element-plus/icons-vue' // 暂时隐藏项目文档
import { useProjectDetailContext } from '@/composables/projectDetail/context.js'
import { useProjectStore } from '@/stores/project'
import ProjectApprovalStatusCard from '@/components/project/ProjectApprovalStatusCard.vue'
import ProjectBasicInfoCard from '@/components/project/ProjectBasicInfoCard.vue'
import ProjectStageTimeline from '@/components/project/stage/ProjectStageTimeline.vue'
import ProjectTaskBoardCard from '@/components/project/ProjectTaskBoardCard.vue'
// import ProjectCollaborationCard from '@/components/project/ProjectCollaborationCard.vue' // 暂时隐藏
import InitiationStage from '@/views/Project/stages/InitiationStage.vue'
import DraftingStage from '@/views/Project/stages/DraftingStage.vue'
import EvaluationStage from '@/views/Project/stages/EvaluationStage.vue'
import ResultConfirmStage from '@/views/Project/stages/ResultConfirmStage.vue'
import ScoreParseDrawer from '@/views/Project/stages/components/ScoreParseDrawer.vue'
import TaskDecomposeDialog from '@/views/Project/stages/components/TaskDecomposeDialog.vue'
import RetrospectiveStage from '@/views/Project/stages/RetrospectiveStage.vue'
import ClosureStage from '@/views/Project/stages/ClosureStage.vue'

// Import API
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'

const ctx = useProjectDetailContext()
const projectStore = useProjectStore()

const activeStageTab = ref('')
// 项目真实当前阶段（来自 timeline snapshot），与 activeStageTab（用户当前查看的 tab）解耦。
// 用于阶段门禁：标书制作阶段结束后，即使通过时间线回到 DRAFTING tab，也不应再展示任务操作按钮。
const currentProjectStage = ref('')
const timelineRef = ref(null)
const scoreParseRef = ref(null)
const taskDecomposeRef = ref(null)
const resultType = ref('')

async function loadResultType() {
  if (!ctx.project?.id) return
  try {
    const res = await projectLifecycleApi.getResult(ctx.project.id)
    resultType.value = res?.data?.resultType || res?.resultType || ''
  } catch (e) {
    console.warn('[ProjectDetailMainColumn] loadResultType failed', e)
    resultType.value = ''
  }
}

// Watch project ID to load result type
watch(() => ctx.project?.id, (newId) => {
  resultType.value = ''
  if (newId) {
    loadResultType()
  }
}, { immediate: true })

// Sync with timeline click events and snapshot events
function handleStageClick(stage) {
  activeStageTab.value = stage.code
}

function handleSnapshot(snapshot) {
  if (snapshot?.defaultOpenStage || snapshot?.currentStage) {
    const stage = snapshot.defaultOpenStage || snapshot.currentStage
    activeStageTab.value = stage
    currentProjectStage.value = snapshot.currentStage
  }
}

function handleSwitchTab(v) {
  activeStageTab.value = v
}

// 纯函数：同步 tab 到时间线快照的真实阶段
function syncTabToRealStage() {
  const realStage = timelineRef.value?.snapshot?.currentStage
  if (realStage) {
    activeStageTab.value = realStage
    currentProjectStage.value = realStage
  }
}

async function handleStageUpdated() {
  // 并行刷新 timeline 和 resultType（两者无依赖）
  const timelinePromise = timelineRef.value?.reload
    ? timelineRef.value.reload()
    : Promise.resolve()
  const resultTypePromise = loadResultType()

  await timelinePromise

  // 阶段推进后必须同步到真实阶段（不受任何锁影响）
  syncTabToRealStage()

  // 并行刷新项目数据和 resultType
  const projectPromise = ctx.project?.id
    ? projectStore.getProjectById(ctx.project.id)
    : Promise.resolve()

  await Promise.all([projectPromise, resultTypePromise])
}

async function onRetrospectiveSubmitted() {
  await handleStageUpdated()
  activeStageTab.value = 'CLOSED'
}
</script>
