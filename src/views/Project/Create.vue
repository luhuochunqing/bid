<template>
  <div class="project-create-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">{{ pageTitle }}</span>
        </div>
      </template>

      <el-steps :active="currentStep" finish-status="success" align-center class="steps">
        <el-step title="基本信息" description="从CRM同步或手动输入" />
        <el-step title="项目详情" description="完善项目信息" />
        <el-step title="任务分解" description="添加项目任务" />
        <el-step v-if="hasAiStep" title="智能辅助" description="AI分析与建议" />
      </el-steps>

      <div class="step-content">
        <div v-show="currentStep === 0" class="step-panel">
          <BasicInfoStep
            ref="basicStepRef"
            v-model:basic-form="basicForm"
            :competitor-analysis="competitorAnalysis"
            :platform-options="platformOptions"
            :user-list="userList"
            :competitor-options="competitorOptions"
            @platform-change="handlePlatformChange"
            @competitors-change="handleCompetitorsChange"
            @sync-crm-data="applyCrmData"
          />
        </div>

        <div v-show="currentStep === 1" class="step-panel">
          <DetailStep ref="detailStepRef" v-model:detail-form="detailForm" />
        </div>

        <div v-show="currentStep === 2" class="step-panel">
          <TaskStep
            ref="taskStepRef"
            :task-form="taskForm"
            :user-list="userList"
            :decomposing="decomposing"
            @add-task="addTask"
            @remove-task="removeTask"
            @auto-decompose="handleCreateAndDecompose"
          />
        </div>

        <div v-if="hasAiStep" v-show="currentStep === 3" class="step-panel">
          <AiAssistStep
            ref="aiStepRef"
            :analyzing="analyzing"
            :ai-summary="aiSummary"
            :score-analysis="scoreAnalysis"
            :ai-generated-tasks="aiGeneratedTasks"
            :score-preview-placeholder="scorePreviewPlaceholder"
          />
        </div>
      </div>

      <div class="step-actions">
        <el-button v-if="currentStep > 0" @click="prevStep">上一步</el-button>
        <el-button v-if="currentStep < lastStepIndex" type="primary" @click="nextStep">下一步</el-button>
        <el-button
          v-if="currentStep === lastStepIndex"
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          确认并创建项目
        </el-button>
      </div>
    </el-card>

    <el-dialog
      v-model="conversion.showWorkbench.value"
      title="项目立项深度核对"
      fullscreen
      destroy-on-close
      append-to-body
    >
      <DocVerificationWorkbench
        v-if="conversion.parseResult.value"
        title="项目立项核对 (AI 证据驱动)"
        :schema="tenderSchema"
        :data="conversion.parseResult.value.requirementProfile"
        :requirements="conversion.parseResult.value.requirementProfile?.items"
        :markdown="conversion.parseResult.value.document?.extractedText"
        @cancel="handleWorkbenchCancel"
        @confirm="handleWorkbenchConfirm"
      />
    </el-dialog>

    <el-dialog
      v-model="conversion.isParsing.value"
      title="证据驱动解析中"
      width="400px"
      :close-on-click-modal="false"
      :show-close="false"
      center
    >
      <div style="text-align: center; padding: 20px;">
        <el-icon class="is-loading" :size="40" color="#409eff"><Loading /></el-icon>
        <p style="margin-top: 20px; font-weight: 600;">正在从文档提取证据链...</p>
        <p style="color: var(--text-muted); font-size: 13px;">这可能需要 30-60 秒，系统正在确保每一项提取都有据可查</p>
      </div>
    </el-dialog>

    <AssetCheckDialog
      v-model="showAssetCheckDialog"
      :asset-check-result="assetCheckResult"
      @confirm="confirmAssetCheck"
      @go-to-management="goToAssetManagement"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'
import { useBarStore } from '@/stores/bar'
import { Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import DocVerificationWorkbench from '../../components/common/doc-insight/DocVerificationWorkbench.vue'
import { aiApi } from '@/api'
import { notifyFeatureUnavailable } from '@/utils/featureFeedback'
import { useProjectConversion } from '@/composables/projectDetail/useProjectConversion.js'
import { hasGlobalHttpErrorMessage, tenderSchema } from './createTenderPrefill.js'
import BasicInfoStep from './create/steps/BasicInfoStep.vue'
import DetailStep from './create/steps/DetailStep.vue'
import TaskStep from './create/steps/TaskStep.vue'
import AiAssistStep from './create/steps/AiAssistStep.vue'
import AssetCheckDialog from './create/dialogs/AssetCheckDialog.vue'
import { useProjectCreateModel } from './create/composables/useProjectCreateModel.js'
import { useProjectCreateSubmit } from './create/composables/useProjectCreateSubmit.js'

const router = useRouter()
const route = useRoute()
const projectStore = useProjectStore()
const userStore = useUserStore()
const barStore = useBarStore()

const conversion = useProjectConversion()

const currentStep = ref(0)
const submitting = ref(false)
const decomposing = ref(false)
const analyzing = ref(false)
const showAssetCheckDialog = ref(false)
const assetCheckResult = ref(null)
const scorePreviewPlaceholder = ref(null)
const hasAiStep = true
const lastStepIndex = hasAiStep ? 3 : 2

const basicStepRef = ref(null)
const detailStepRef = ref(null)
const taskStepRef = ref(null)
const aiStepRef = ref(null)

const aiSummary = ref({ winScore: 0, winLevel: 'low', risks: [], suggestions: [] })
const scoreAnalysis = ref({ scoreCategories: [], gapItems: [] })
const aiGeneratedTasks = ref([])

const competitorOptions = [
  '华为技术有限公司',
  '腾讯云计算有限公司',
  '阿里巴巴云计算有限公司',
  '百度智能云',
  '京东科技',
  '科大讯飞股份有限公司',
  '浪潮集团有限公司',
  '中软国际',
  '东软集团',
  '用友网络'
]

const model = useProjectCreateModel({ route, userStore, projectStore, router })
const {
  basicForm,
  detailForm,
  taskForm,
  sourceInfo,
  competitorAnalysis,
  isEditMode,
  editProjectId,
  addTask,
  removeTask,
  handleCompetitorsChange,
  applyOpportunityPrefill,
  applyCrmData,
  loadTenderDetailPrefill,
  loadAvailableTenders,
  loadProjectData,
  buildApiProjectPayload,
  buildTaskCreatePayloads
} = model

const pageTitle = computed(() => (isEditMode.value ? '编辑项目' : '创建项目'))
const userList = computed(() => userStore.users)
const platformOptions = computed(() => barStore.sites || [])
const { handleSubmit, handleCreateAndDecompose } = useProjectCreateSubmit({
  projectStore,
  router,
  sourceInfo,
  submitting,
  decomposing,
  buildApiProjectPayload,
  buildTaskCreatePayloads,
  hasGlobalHttpErrorMessage,
})

async function nextStep() {
  if (currentStep.value === 0) {
    const valid = await basicStepRef.value?.validate()
    if (!valid) return
  } else if (currentStep.value === 1) {
    const valid = await detailStepRef.value?.validate()
    if (!valid) return
  } else if (currentStep.value === 2 && hasAiStep) {
    await runAIAnalysis()
  }
  currentStep.value++
}

function prevStep() {
  currentStep.value--
}

async function runAIAnalysis() {
  analyzing.value = true
  try {
    const response = await aiApi.score.generatePreview({
      industry: basicForm.industry,
      tags: detailForm.tags,
      budget: basicForm.budget
    })

    if (response?.success && response.data) {
      aiSummary.value = response.data.aiSummary
      scoreAnalysis.value = response.data.scoreAnalysis
      aiGeneratedTasks.value = response.data.generatedTasks
      scorePreviewPlaceholder.value = null
      ElMessage.success('AI分析完成')
    } else {
      aiSummary.value = { winScore: 0, winLevel: 'low', risks: [], suggestions: [] }
      scoreAnalysis.value = { scoreCategories: [], gapItems: [] }
      aiGeneratedTasks.value = []
      scorePreviewPlaceholder.value = notifyFeatureUnavailable(response, {
        fallback: {
          title: '评分预览当前不可用',
          hint: '项目创建流程会继续保留，评分建议可在分析服务恢复后补充。'
        }
      }) || {
        title: '评分预览不可用',
        message: response?.msg || '当前场景未生成评分结果',
        hint: '项目创建流程不受影响。'
      }
      if (!scorePreviewPlaceholder.value.feature) {
        ElMessage.info(scorePreviewPlaceholder.value.message)
      }
    }
  } catch (error) {
    ElMessage.error('AI分析失败')
  } finally {
    analyzing.value = false
  }
}

function handleWorkbenchCancel() {
  conversion.showWorkbench.value = false
  const projectId = projectStore.currentProject?.id
  router.push(projectId ? `/project/${projectId}` : '/project')
}

async function handleWorkbenchConfirm() {
  conversion.showWorkbench.value = false
  ElMessage.success('深度核对完成，证据链已同步至项目')
  const projectId = projectStore.currentProject?.id
  router.push(projectId ? `/project/${projectId}` : '/project')
}

async function handlePlatformChange(platformName) {
  if (!platformName) return
  const result = await barStore.checkSiteCapability(platformName)
  if (result.found) {
    assetCheckResult.value = result
    showAssetCheckDialog.value = true
  }
}

function confirmAssetCheck() {
  showAssetCheckDialog.value = false
  ElMessage.success('已确认资产状态，请继续完善项目信息')
}

function goToAssetManagement() {
  showAssetCheckDialog.value = false
  router.push('/resource/bar')
}

onMounted(async () => {
  if (!basicForm.manager && userStore.currentUser?.name) {
    basicForm.manager = userStore.currentUser.name
  }

  await loadAvailableTenders()

  const editId = route.query.editId
  if (editId) {
    isEditMode.value = true
    editProjectId.value = editId
    await loadProjectData(editId)
  } else {
    applyOpportunityPrefill()
    await loadTenderDetailPrefill()
  }
})
</script>

<style scoped>
.project-create-container { padding: 20px; }

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header .title {
  font-size: 16px;
  font-weight: 500;
  color: var(--gray-750);
}

.steps {
  margin: 32px 0;
  padding: 0 40px;
}

.step-content {
  min-height: 400px;
  padding: 20px 40px;
}

.step-panel {
  animation: fadeIn 0.3s ease-in-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateX(10px); }
  to { opacity: 1; transform: translateX(0); }
}

.step-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
  margin-top: 20px;
}

@media (max-width: 768px) {
  .project-create-container { padding: 12px; }

  :deep(.el-steps) { font-size: 12px; }
  :deep(.el-step__title) { font-size: 12px; }

  .step-actions {
    flex-wrap: wrap;
    gap: 12px;
  }

  .step-actions .el-button {
    flex: 1;
    min-width: 100px;
  }

  :deep(.el-dialog) {
    width: 95% !important;
    margin: 0 auto;
  }

  :deep(.el-dialog__body) { padding: 16px; }
}

@media (hover: none) and (pointer: coarse) {
  .step-actions .el-button { min-height: 44px; }
}
</style>
