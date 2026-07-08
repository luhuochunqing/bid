<template>
  <div class="main-content">
    <ProjectBasicInfoCard :project="ctx.project">
      <template #actions>
        <el-button v-if="ctx.canTransfer" :icon="Switch" @click="ctx.openTransfer">项目转移</el-button>
      </template>
    </ProjectBasicInfoCard>
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
          :can-manage-project-tasks="ctx.canManageProjectTasks && canManageTasksInDrafting()"
          :is-demo-mode="ctx.isDemoMode"
          @task-click="ctx.handleTaskClick"
          @save-task="ctx.handleSaveTask"
          @status-change="ctx.handleTaskStatusChange"
          @open-score-parse="scoreParseRef?.open()"
          @open-decompose="taskDecomposeRef?.open()"
          @remove-deliverable="ctx.handleRemoveDeliverable"
          @submit-to-document="ctx.handleSubmitToDocument"
          @submit-review="ctx.handleSubmitReview"
        />
        <ScoreParseDrawer ref="scoreParseRef" :project-id="ctx.project?.id" />
        <TaskDecomposeDialog ref="taskDecomposeRef" :project-id="ctx.project?.id" />
        <DraftingStage
          v-if="ctx.project?.id"
          :key="ctx.project.id"
          :project-id="ctx.project.id"
          :current-stage="currentProjectStage"
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
import { useRoute } from 'vue-router'
import { Clock, Switch } from '@element-plus/icons-vue'
// import { Document, DocumentChecked, Folder, Upload } from '@element-plus/icons-vue' // 暂时隐藏项目文档
import { useProjectDetailContext } from '@/composables/projectDetail/context.js'
import { useProjectStore } from '@/stores/project'
import { routeToStageCode } from '@/constants/projectStages'
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
const route = useRoute()

const activeStageTab = ref('')
// 项目真实当前阶段（来自 timeline snapshot），与 activeStageTab（用户当前查看的 tab）解耦。
// 用于阶段门禁：标书制作阶段结束后，即使通过时间线回到 DRAFTING tab，也不应再展示任务操作按钮。
const currentProjectStage = ref('')
const timelineRef = ref(null)
const scoreParseRef = ref(null)
const taskDecomposeRef = ref(null)
const resultType = ref('')
const bidReviewState = ref(null)

async function loadBidReviewState() {
  if (!ctx.project?.id || currentProjectStage.value !== 'DRAFTING') {
    bidReviewState.value = null
    return
  }
  try {
    const res = await projectLifecycleApi.getDrafting(ctx.project.id)
    const d = res?.data || res
    if (d?.reviewStatus) {
      bidReviewState.value = d.reviewStatus.toLowerCase()
    } else {
      bidReviewState.value = null
    }
  } catch (e) {
    console.warn('[ProjectDetailMainColumn] loadBidReviewState failed', e)
    bidReviewState.value = null
  }
}

const canManageTasksInDrafting = () => {
  if (currentProjectStage.value !== 'DRAFTING') return false
  if (bidReviewState.value === 'approved' || bidReviewState.value === 'reviewing') return false
  return true
}

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
    loadBidReviewState()
  }
}, { immediate: true })

watch(currentProjectStage, () => {
  loadBidReviewState()
})

// Sync with timeline click events and snapshot events
function handleStageClick(stage) {
  activeStageTab.value = stage.code
}

// URL stage 参数 → stage code，未匹配返回 null
// route 可能在外部测试环境未注入，做轻量防御
function stageFromRoute() {
  const stageParam = route?.params?.stage
  if (!stageParam) return null
  return routeToStageCode(stageParam)
}

// CO-497: 复盘提交后的跳转窗口期标志位。
// 防止 handleStageUpdated 内部 timeline.reload() 异步回声的 snapshot 事件
// 把 tab 从 CLOSED 拽回 RETROSPECTIVE。
const isRetrospectiveTransitioning = ref(false)

function handleSnapshot(snapshot) {
  // currentProjectStage 始终记录项目真实阶段（用于阶段门禁）
  if (snapshot?.currentStage) {
    currentProjectStage.value = snapshot.currentStage
  }
  // CO-497: 复盘提交后的跳转窗口期内，禁止 snapshot 事件覆盖 tab。
  // 时序：onRetrospectiveSubmitted 设 tab=CLOSED → handleStageUpdated 内部
  //   timeline.reload() 异步 emit('snapshot') 回声 → handleSnapshot 把 tab 拽回 RETROSPECTIVE。
  // 该标志位仅在 onRetrospectiveSubmitted 流程期间生效，正常浏览时 handleSnapshot 行为不变。
  if (isRetrospectiveTransitioning.value) {
    return
  }
  // URL stage 参数优先（用户从通知主动跳转表达明确意图）
  // 其次才用 timeline 推荐的 defaultOpenStage
  const fromRoute = stageFromRoute()
  if (fromRoute) {
    activeStageTab.value = fromRoute
  } else if (snapshot?.defaultOpenStage || snapshot?.currentStage) {
    const stage = snapshot.defaultOpenStage || snapshot.currentStage
    activeStageTab.value = stage
  }
}

// 同项目不同 stage 跳转（如用户在通知中心连续点不同 stage 通知）
// immediate: 初始化时若 URL 带 stage 参数，立即切换到对应 tab
watch(() => route?.params?.stage, (newStage) => {
  if (newStage) {
    const code = routeToStageCode(newStage)
    if (code) activeStageTab.value = code
  }
}, { immediate: true })

function handleSwitchTab(v) {
  activeStageTab.value = v
}

// 纯函数：同步 tab 到时间线快照的真实阶段
function syncTabToRealStage() {
  const realStage = timelineRef.value?.snapshot?.currentStage
  if (realStage) {
    currentProjectStage.value = realStage
    // CO-497: 复盘提交后的跳转窗口期内，不覆盖 tab（已在 onRetrospectiveSubmitted 设为 CLOSED）
    if (!isRetrospectiveTransitioning.value) {
      activeStageTab.value = realStage
    }
  }
}

async function handleStageUpdated() {
  // 并行刷新 timeline 和 resultType（两者无依赖）
  const timelinePromise = timelineRef.value?.reload
    ? timelineRef.value.reload()
    : Promise.resolve()
  const resultTypePromise = loadResultType()
  const bidReviewPromise = loadBidReviewState()

  await timelinePromise

  // 阶段推进后必须同步到真实阶段（不受任何锁影响）
  syncTabToRealStage()

  // CO-468: 阶段切换后端会同步创建保证金等任务，必须 forceRefresh 强制走 API，
  // 否则 getProjectById 命中 this.projects 缓存返回 stale tasks，任务看板渲染不出新任务。
  const projectPromise = ctx.project?.id
    ? projectStore.getProjectById(ctx.project.id, { forceRefresh: true })
    : Promise.resolve()

  await Promise.all([projectPromise, resultTypePromise, bidReviewPromise])

  // CO-468 根因修复：tasks 不在 getProjectById 返回里（后端只返回 tasksJson 字符串），
  // 任务看板的 tasks 来自 projectsApi.getTasks。阶段切换后必须重新拉取 tasks，
  // 否则任务看板仍显示旧 tasks（如保证金缴纳任务不显示），需刷新浏览器才可见。
  if (ctx.project?.id && typeof ctx.loadProjectWorkflowData === 'function') {
    await ctx.loadProjectWorkflowData(ctx.project.id)
  }
}

async function onRetrospectiveSubmitted() {
  // CO-497: 复盘提交后 stage 停在 RETROSPECTIVE（PR #1667 修复直达 CLOSED bug）。
  // 用户期望进入结项阶段（CLOSED tab），但 handleStageUpdated 内部
  //   timeline.reload() 异步 emit('snapshot') 回声 → handleSnapshot 把 tab 拽回 RETROSPECTIVE。
  // PR #1673 的 try-finally 只堵住同步路径，堵不住异步回声。
  // 方案 A: 用 isRetrospectiveTransitioning 标志位包裹跳转窗口期，
  //   handleSnapshot 检测到该标志时跳过 tab 覆盖，保证 tab=CLOSED 不被拽回。
  isRetrospectiveTransitioning.value = true
  try {
    await handleStageUpdated()
  } catch (e) {
    console.warn('[ProjectDetailMainColumn] onRetrospectiveSubmitted handleStageUpdated failed', e)
  } finally {
    activeStageTab.value = 'CLOSED'
    // 延迟清标志(100ms 而非 0)：让 handleStageUpdated 的异步 snapshot 回声也落在窗口期内，避免测试 flushPromises 在回声到达前清除标志
    setTimeout(() => { isRetrospectiveTransitioning.value = false }, 100)
  }
}
</script>
