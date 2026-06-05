<template>
  <el-main class="main-content">
    <ProjectBasicInfoCard :project="ctx.project" />
    <ProjectStageTimeline
      v-if="ctx.project?.id"
      ref="timelineRef"
      :project-id="ctx.project.id"
      @stage-click="handleStageClick"
      @snapshot="handleSnapshot"
    />

    <div class="stage-tabs-container">
      <el-tabs v-model="activeStageTab" class="custom-stage-tabs">
        <el-tab-pane label="项目立项" name="INITIATED">
          <InitiationStage
            v-if="ctx.project?.id"
            :key="ctx.project.id"
            :project-id="ctx.project.id"
            @updated="handleStageUpdated"
          />
        </el-tab-pane>
        <el-tab-pane label="标书制作" name="DRAFTING">
          <div class="drafting-tab-content">
            <ProjectTaskBoardCard
              :tasks="ctx.project?.tasks || []"
              :project-id="ctx.project?.id"
              :can-manage-project-tasks="ctx.canManageProjectTasks"
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
        </el-tab-pane>
        <el-tab-pane label="评标中" name="EVALUATING">
          <EvaluationStage
            v-if="ctx.project?.id"
            :key="ctx.project.id"
            :project-id="ctx.project.id"
            @advanced="handleStageUpdated"
            @switch-tab="(v) => { snapshotLock = true; activeStageTab = v; setTimeout(() => snapshotLock = false, 2000) }"
          />
        </el-tab-pane>
        <el-tab-pane label="结果确认" name="RESULT_PENDING">
          <ResultConfirmStage
            v-if="ctx.project?.id"
            :key="ctx.project.id"
            :project-id="ctx.project.id"
            @registered="handleStageUpdated"
            @switch-tab="(v) => { snapshotLock = true; activeStageTab = v; setTimeout(() => snapshotLock = false, 2000) }"
          />
        </el-tab-pane>
        <el-tab-pane label="项目复盘" name="RETROSPECTIVE">
          <RetrospectiveStage
            v-if="ctx.project?.id"
            :key="ctx.project.id"
            :project-id="ctx.project.id"
            @submitted="handleStageUpdated"
            :result-type="resultType"
          />
        </el-tab-pane>
        <el-tab-pane label="项目结项" name="CLOSED">
          <ClosureStage
            v-if="ctx.project?.id"
            :key="ctx.project.id"
            :project-id="ctx.project.id"
            @closed="handleStageUpdated"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <ProjectApprovalStatusCard
      :approval-history="ctx.approvalHistory"
      :project-status="ctx.project?.status"
      :can-approve-current="ctx.canApproveCurrent"
      @quick-approve="ctx.handleQuickApprove"
      @quick-reject="ctx.handleQuickReject"
    />

    <el-card class="document-card">
      <template #header>
        <div class="card-title">
          <el-icon><Folder /></el-icon>
          <span>项目文档</span>
          <el-button v-if="ctx.canManageProjectDocuments" link type="success" :icon="DocumentChecked" @click="ctx.handleArchiveDocuments">归档资料</el-button>
          <el-upload v-if="ctx.canManageProjectDocuments" :show-file-list="false" :before-upload="ctx.handleUpload" accept=".doc,.docx,.pdf,.xls,.xlsx">
            <el-button link type="primary" :icon="Upload">上传文档</el-button>
          </el-upload>
        </div>
      </template>
      <el-table :data="ctx.project?.documents || []" style="width: 100%">
        <el-table-column prop="name" label="文档名称" min-width="200">
          <template #default="{ row }">
            <div class="file-name">
              <el-icon><Document /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="uploader" label="上传者" width="120" />
        <el-table-column prop="time" label="上传时间" width="160" />
        <el-table-column prop="size" label="文件大小" width="100" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="ctx.handleDownload(row)">下载</el-button>
            <el-button link type="danger" @click="ctx.handleDeleteDoc(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!ctx.project?.documents?.length" description="暂无文档" />
    </el-card>

    <ProjectCollaborationCard :project-id="ctx.project?.id" />
  </el-main>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Document, DocumentChecked, Folder, Upload } from '@element-plus/icons-vue'
import { useProjectDetailContext } from '@/composables/projectDetail/context.js'
import { useProjectStore } from '@/stores/project'
import ProjectApprovalStatusCard from '@/components/project/ProjectApprovalStatusCard.vue'
import ProjectBasicInfoCard from '@/components/project/ProjectBasicInfoCard.vue'
import ProjectStageTimeline from '@/components/project/stage/ProjectStageTimeline.vue'
import ProjectTaskBoardCard from '@/components/project/ProjectTaskBoardCard.vue'
import ProjectCollaborationCard from '@/components/project/ProjectCollaborationCard.vue'
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

const activeStageTab = ref('INITIATED')
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

const snapshotLock = ref(false)

function handleSnapshot(snapshot) {
  if (snapshotLock.value) return
  if (snapshot?.currentStage) {
    activeStageTab.value = snapshot.currentStage
  }
}

async function handleStageUpdated() {
  if (timelineRef.value?.reload) {
    await timelineRef.value.reload()
  }
  await loadResultType()
  if (ctx.project?.id) {
    await projectStore.getProjectById(ctx.project.id)
  }
}
</script>
