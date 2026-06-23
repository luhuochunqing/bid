<template>
  <div class="bidding-detail-page">
    <TenderNotFound v-if="tenderNotFound" />
  <div v-else-if="tender" class="detail-content">
      <!-- 头部信息卡 -->
      <div class="detail-header-card">
        <div class="header-top-row">
          <span class="tender-id-badge">#{{ tender.id }}</span>
        </div>
        <!-- 标题 -->
        <h1 class="tender-title">{{ tender.title }}</h1>
        <!-- 标签行：状态 + 优先级 + 来源平台 -->
        <div class="detail-header-tags">
          <el-tag :type="getStatusType(tender.status)" size="small" effect="dark">
            {{ getStatusText(tender.status) }}
          </el-tag>
          <el-tag v-if="tender.priority" size="small" :class="'priority-tag-' + tender.priority" effect="plain">
            {{ tender.priority }}
          </el-tag>
          <span v-if="tender.source" class="source-tag">{{ getSourceTypeText(tender.sourceType) }}</span>
        </div>
        <!-- 元信息网格 -->
        <div class="detail-meta-grid">
          <div class="detail-meta-item">
            <div class="meta-label">总部所在地</div>
            <el-tooltip v-if="regionMeta.isMissing" :content="regionMeta.tooltip" placement="top">
              <span class="field-missing">{{ regionMeta.text }}</span>
            </el-tooltip>
            <span class="meta-value" v-else>{{ regionMeta.text }}</span>
          </div>
          <div class="detail-meta-item">
            <div class="meta-label">招标主体</div>
            <span class="meta-value">{{ tender.purchaserName || '-' }}</span>
          </div>
          <div class="detail-meta-item">
            <div class="meta-label">报名截止</div>
            <span class="meta-value">{{ formatTenderDateTime(tender.registrationDeadline) || '-' }}</span>
          </div>
          <div class="detail-meta-item">
            <div class="meta-label">开标时间</div>
            <span class="meta-value">{{ formatTenderDateTime(tender.bidOpeningTime) || '-' }}</span>
          </div>
        </div>
        <!-- 全局操作按钮 -->
        <div class="detail-global-actions">
          <el-button
            v-for="action in headerActions"
            :key="action.key"
            :type="action.type"
            :icon="action.icon === 'edit' ? Edit : undefined"
            @click="handleAction(action.key)"
          >
            {{ action.label }}
          </el-button>
        </div>
      </div>
      <!-- Tabs（条件显隐） -->
      <el-tabs v-model="activeTab" class="detail-tabs" type="border-card">
        <el-tab-pane v-if="visibleTabs.some(t => t.name === 'basic')" label="基本信息" name="basic" />
        <el-tab-pane v-if="visibleTabs.some(t => t.name === 'evaluation')" name="evaluation">
          <template #label>
            <span class="evaluation-tab-label">
              项目评估表
              <el-tag
                v-if="requiresReview"
                type="warning"
                size="small"
                effect="dark"
                class="review-badge"
              >需审核</el-tag>
            </span>
          </template>
        </el-tab-pane>
        <el-tab-pane v-if="visibleTabs.some(t => t.name === 'logs')" label="操作日志" name="logs" />
      </el-tabs>
      <!-- Tab content rendered with v-show to preserve form state across tab switches -->
      <div v-show="activeTab === 'basic'" class="tab-content">
        <div v-if="showCrmSelector || tender?.crmOpportunityName" class="crm-section-in-tab">
          <CrmOpportunitySelector
            :enabled="showCrmSelector && !tender?.crmOpportunityId"
            :tenderer="tender?.purchaserName || ''"
            :registration-deadline="tender?.registrationDeadline || ''"
            :bid-opening-time="tender?.bidOpeningTime || ''"
            :already-linked-name="tender?.crmOpportunityName || ''"
            :link-failed="crmLinkFailedSignal"
            @linked="onCrmOpportunityLinked"
          />
          <el-divider />
        </div>
        <BasicInfoReadOnly :tender="tender" />
      </div>
      <div v-show="activeTab === 'evaluation'" class="tab-content">
        <!-- CRM商机关联状态 -->
        <!-- CO-311: 使用 evaluationTabLinked 而非直接读 tender.crmOpportunityName,
             确保关联失败时与基本信息 tab 同步回滚到"未关联"状态 -->
        <div v-if="evaluationTabLinked" class="crm-status-bar">
          <el-alert type="warning" :closable="false" show-icon>
            <template #title>核对评估表内容，如需修改，请返回CRM修改对应商机信息</template>
          </el-alert>
          <el-tag type="success" size="default" effect="plain" class="mt-2">
            已关联商机：{{ tender?.crmOpportunityName }}
          </el-tag>
        </div>
        <div v-else-if="showCrmSelector" class="crm-status-bar">
          <el-tag type="info" size="default" effect="plain">
            尚未关联CRM商机，请前往「基本信息」页关联
          </el-tag>
        </div>
        <el-skeleton v-if="evaluationLoading" :rows="8" animated />
        <template v-else>
          <TenderEvaluationForm
            ref="evaluationFormRef"
            :evaluation="tenderEvaluation"
            :can-fill="canFillEvaluation"
            :can-decide="canFillEvaluation"
            :can-fill-recommendation="canFillRecommendation"
            :tender-id="Number(tender.id)"
            :hide-actions="hideEvaluationActions"
            :saving-draft="savingDraft"
            :submitting="submitting"
            @submit="handleEvaluationSubmit"
            @save-draft="handleEvaluationSaveDraft"
            @bid="handleParticipate"
            @abandon="handleAbandonWithReason"
            @dirty-changed="onFormDirtyChanged"
          />
          <div v-if="canReview" class="review-action-bar">
            <el-button
              type="warning"
              size="large"
              :loading="reviewing"
              @click="handleReviewEvaluation"
            >
              确认审核
            </el-button>
            <span class="review-hint">审核通过后方可恢复投标/弃标操作</span>
          </div>
        </template>
      </div>
      <div v-show="activeTab === 'logs'" class="tab-content">
        <OperationLogTimeline :tender-id="tender.id" />
      </div>

      <!-- 底部操作栏 -->
      <BottomActionBar :actions="bottomActions" @action="handleAction" />
    </div>
    <div v-else class="loading-container">
      <el-skeleton :rows="6" animated />
    </div>
  </div>
  <AssignDialog
    v-model="showAssignDialog"
    v-model:form="assignForm"
    :candidates="assignCandidates"
    :loading="assigning"
    :loading-candidates="loadingCandidates"
    @reset="assignForm.assignee = null"
    @submit="doAssign"
  />

  <el-dialog v-model="showTransferDialog" title="转派标讯" width="420px">
    <el-form label-width="100px">
      <el-form-item label="项目名称"><el-text>{{ tender?.title }}</el-text></el-form-item>
      <el-form-item label="新负责人" required>
        <el-select v-model="transferTarget" filterable placeholder="选择新的项目负责人" style="width:100%">
          <el-option v-for="c in transferCandidates" :key="c.id" :label="formatAssignmentCandidateLabel(c)" :value="c.id">
            {{ formatAssignmentCandidateLabel(c) }} · {{ c.departmentName }}
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showTransferDialog = false">取消</el-button>
      <el-button type="primary" :loading="transferring" @click="doTransfer">确认转派</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, defineAsyncComponent, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
import { Edit } from '@element-plus/icons-vue'
import { formatTenderDate, formatTenderDateTime, getSourceTypeText } from '../bidding-utils.js'
import { useBiddingDetailPage } from './useBiddingDetailPage.js'
import { tendersApi } from '@/api'
import httpClient from '@/api/client.js'
import { useEvaluationReview } from './useEvaluationReview.js'
import { useDetailTabs } from './useDetailTabs.js'
import { useDetailActions } from './useDetailActions.js'
import { useUserStore } from '@/stores/user'
import { isBidManager } from '@/utils/permission'
import { formatAssignmentCandidateLabel } from '../list/helpers.js'
import { VT } from './useTenderEvaluationForm.js'
const TenderEvaluationForm = defineAsyncComponent(() => import('./TenderEvaluationForm.vue'))
const OperationLogTimeline = defineAsyncComponent(() => import('./components/OperationLogTimeline.vue'))
const AssignDialog = defineAsyncComponent(() => import('../list/components/AssignDialog.vue'))
const BasicInfoReadOnly = defineAsyncComponent(() => import('./components/BasicInfoReadOnly.vue'))
const TenderNotFound = defineAsyncComponent(() => import('./components/TenderNotFound.vue'))
const CrmOpportunitySelector = defineAsyncComponent(() => import('./components/CrmOpportunitySelector.vue'))
import BottomActionBar from './BottomActionBar.vue'
import FavoriteButton from '../list/components/FavoriteButton.vue'
import './styles/detail-layout.css'
import { formatUserLabel } from '@/utils/formatUserLabel.js'
const userStore = useUserStore()
const userRole = computed(() => userStore.userRole?.toLowerCase() || 'bid_specialist')
const {
  tender,
  tenderNotFound,
  matchScore,
  matchScoreState,
  regionMeta,
  industryMeta,
  deadlineParts,
  getScoreClass,
  getStatusType,
  getStatusText,
  getDeadlineClass,
  loadTenderDetail,
  handleParticipate,    // from useTenderActions — 投标/弃标/查看官网按钮
  handleAbandon,
  handleViewOriginal,
  isEditing, handleEdit, handleCancelEdit, handleSaveEdit,
  showAssignDialog, assignForm, assignCandidates, assigning, loadingCandidates, openAssign, doAssign,
  showTransferDialog, transferCandidates, transferTarget, transferring, openTransfer, doTransfer,
} = useBiddingDetailPage()
const {
  tenderEvaluation,
  evaluationStatus,
  submitting,
  savingDraft,
  reviewing,
  requiresReview,
  canReview,
  evaluationLoading,
  hasUnsavedChanges,
  handleEvaluationSaveDraft,
  handleEvaluationSubmit,
  handleReviewEvaluation,
  handleAbandonWithReason,
} = useEvaluationReview(tender)

const canFillEvaluation = computed(() => {
  // CO-309: 业务约束——评估表第一、二部分值只能从 CRM 传入,任何场景都不可人工编辑
  return false
})

// CO-232: 投标系统主动关联CRM商机时，第三部分（项目负责人建议）仍可编辑
const canFillRecommendation = computed(() => {
  if (!tender.value || !userRole.value) return false
  if (tender.value?.evaluationSource === 'BID_SYSTEM_LINK') {
    return tender.value.status === 'TRACKING' && (isBidManager(userRole.value) || userRole.value === 'sales')
  }
  return canFillEvaluation.value
})

const hideEvaluationActions = computed(
  () => !canFillEvaluation.value && !canFillRecommendation.value
)

const isEvaluationSubmitted = computed(() =>
  tenderEvaluation.value?.evaluationStatus === 'SUBMITTED'
)

// CRM商机关联：标讯分配后仅分配给的项目负责人可用
const currentUserId = computed(() => userStore.currentUser?.id)
const showCrmSelector = computed(() =>
  currentUserId.value != null &&
  tender.value?.status !== 'PENDING_ASSIGNMENT' &&
  tender.value?.status !== 'BIDDING' &&
  tender.value?.status !== 'WON' &&
  tender.value?.status !== 'LOST' &&
  tender.value?.status !== 'ABANDONED' &&
  tender.value?.projectManagerId === currentUserId.value
)

// CRM商机关联后：回填评估表并自动提交
const crmLinking = ref(false)
// CO-308: 关联失败时递增此信号,通知 CrmOpportunitySelector 重置乐观写入的 UI 状态
const crmLinkFailedSignal = ref(0)
// CO-311: 评估表 tab 的"已关联"判断 — 关联失败时强制 false,与基本信息 tab 保持一致
// 基本信息tab 看子组件 linkedOpportunity ref(乐观),评估tab 看 tender 真实数据,
// 失败时 tender.crmOpportunityName 仍是旧值,需通过 crmLinkFailedSignal 同步回滚
const evaluationTabLinked = computed(() => {
  if (crmLinkFailedSignal.value) return false
  return Boolean(tender.value?.evaluationSource || tender.value?.crmOpportunityName)
})
const evaluationFormRef = ref(null)

function transformCrmBasic(basic) {
  // 字段名对齐后端 EvaluationBasicDTO（V130 三段式 + V1026 字段重构）
  // CO-262: 透传 projectPlanGapFiles（CRM 回填的 GAP 附件外部 URL 引用），
  // 由后端 SubmissionService 在保存评估表时原子性持久化到 project_documents 表。
  return {
    plannedShortlistedCount: basic?.plannedShortlistedCount ?? basic?.shortlistedCount ?? basic?.planSupplierCount ?? null,
    mroOfficeFlowAmount: basic?.mroOfficeFlowAmount ?? basic?.platformServiceFee ?? basic?.ecommerceMroAmount ?? null,
    unfavorableItems: basic?.unfavorableItems ?? basic?.competitorAnalysis ?? basic?.bidDocumentDisadvantage ?? '',
    riskAssessment: basic?.riskAssessment ?? basic?.riskPrediction ?? basic?.projectBackground ?? '',
    contingencyPlan: basic?.contingencyPlan ?? '',
    processKnowledge: basic?.processKnowledge ?? '',
    supportNotes: basic?.supportNotes ?? '',
    projectPlanGap: basic?.projectPlanGap ?? '',
    customerRevenue: basic?.customerRevenue ?? null,
    projectPlanGapFiles: Array.isArray(basic?.projectPlanGapFiles) ? basic.projectPlanGapFiles : [],
  }
}

function transformCrmCustomerInfos(customerInfos) {
  if (!Array.isArray(customerInfos)) return []
  const result = []
  const infoFields = ['NAME','CONTACT_INFO','POSITION','XIYU_CONTACT','CONTACT_METHOD','INFO_TENDENCY_BASIS',
    'CONTACTED','GUIDED_BID','CAN_GET_KEY_INFO','CAN_REMOVE_ADVERSE',
    'CAN_SYNC_EVAL','TENDENCY','INFO_CLEAR_WINNER_BID','INFO_WIN_RATE_IMPACT']
  for (const row of customerInfos) {
    for (const key of infoFields) {
      if (row[key] !== undefined && row[key] !== null && String(row[key]).trim() !== '') {
        result.push({ roleKey: row.roleKey, infoKey: key, value: String(row[key]), valueType: VT[key] || 'TEXT' })
      }
    }
  }
  return result
}

const { activeTab, visibleTabs } = useDetailTabs(tender)

async function onCrmOpportunityLinked({ opportunityId, opportunityName, evaluationData }) {
  if (!tender.value?.id) return
  crmLinking.value = true
  try {
    // CO-310 修复：关联 CRM 商机时一步完成评估表回填。
    // 原 CO-310 改动砍掉了 saveEvaluationDraft/submitEvaluationFinal 调用，导致评估表数据不落库。
    // 现在通过扩展后的 linkCrmOpportunity 端点（新增 evaluationPayload 字段）一步完成，
    // 后端 backfillFromCrmLink 方法绕过 canFill 守卫（sales 关联商机是其核心职责）。
    const payload = {
      crmOpportunityId: opportunityId,
      crmOpportunityName: opportunityName,
    }
    if (evaluationData) {
      payload.evaluationPayload = {
        evaluationBasic: transformCrmBasic(evaluationData.basic),
        evaluationCustomerInfos: transformCrmCustomerInfos(evaluationData.customerInfos),
      }
      // CO-312: 是否投标/弃标原因由项目负责人手动填写，关联 CRM 商机时不再带入。
      // 仅当调用方仍显式传入 recommendation 时才组装（防御性，当前 selector 不传）。
      if (evaluationData.recommendation) {
        payload.evaluationPayload.bidRecommendation =
          evaluationData.recommendation.shouldBid ? 'RECOMMEND' : 'NOT_RECOMMEND'
        payload.evaluationPayload.evaluationRecommendation = {
          shouldBid: evaluationData.recommendation.shouldBid,
          reason: evaluationData.recommendation.reason || '',
        }
      }
    }
    await tendersApi.linkCrmOpportunity(tender.value.id, payload)
    ElMessage.success('CRM商机已关联，请在评估表填写是否投标后提交')

    // 刷新 tender 和评估表
    await loadTenderDetail()
    // CO-311: 关联成功后重置失败信号,让评估表 tab 恢复"已关联"显示
    crmLinkFailedSignal.value = 0
    try {
      const evalResult = await tendersApi.loadEvaluation(tender.value.id)
      // CO-310 两步流程：关联成功后切到评估表 tab，引导项目负责人填写是否投标
      activeTab.value = 'evaluation'
      if (evalResult?.success !== false) tenderEvaluation.value = evalResult?.data || null
    } catch { /* ignore */ }
  } catch (e) {
    // CO-308: 404 是标讯已被删除(给出明确提示);其他错误(含 409 业务冲突)透传后端真实信息
    // 注:后端 GlobalExceptionHandler 当前把 BusinessException(409,...) 映射为 HTTP 400,
    // 因此前端按 400 处理;响应体 code 字段仍为 409,msg 含真实占用信息。
    const status = e?.response?.status
    if (status === 404) {
      ElMessage.warning('该标讯已被删除，无法关联CRM商机')
    } else {
      // 业务冲突(如 CRM 商机已被占用)或其他错误:透传后端 msg
      ElMessage.error(e?.response?.data?.msg || 'CRM关联提交失败')
      // 通知子组件重置乐观写入的 UI 状态,引导用户重新选择商机
      crmLinkFailedSignal.value++
    }
  } finally {
    crmLinking.value = false
  }
}
// Suppress unused-var warnings for refs used as v-loading bindings in template
void submitting, savingDraft, ElMessage
// ---- Tab 管理 ----
const currentUserIdVal = computed(() => userStore.currentUser?.id)
const tenderCreatorId = computed(() => tender.value?.creatorId)
const { headerActions, bottomActions, handleAction } = useDetailActions(tender, userRole, loadTenderDetail, {
  bid: handleParticipate, abandon: handleAbandon, viewAnnouncement: handleViewOriginal,
  assign: openAssign, transfer: openTransfer, edit: handleEdit,
  editEvaluation: () => { activeTab.value = 'evaluation' },
  save: handleSaveEdit, cancel: handleCancelEdit,
  nextStep: () => { activeTab.value = 'evaluation' },
  prevStep: () => { activeTab.value = 'basic' },
  submit: () => evaluationFormRef.value?.handleSubmit(),
  viewProject: () => {
    if (tender.value?.projectId) {
      router.push({ name: 'ProjectDetail', params: { id: tender.value.projectId } })
    } else {
      ElMessage.info('该项目尚未创建投标项目')
    }
  },
  afterDelete: () => router.push('/bidding'),
}, activeTab, isEvaluationSubmitted, currentUserIdVal, tenderCreatorId)

// ---- unsaved-changes guard ----
function onFormDirtyChanged(dirty) {
  hasUnsavedChanges.value = dirty
}

onBeforeRouteLeave(async () => {
  if (!hasUnsavedChanges.value) return true

  try {
    await ElMessageBox.confirm('你有未保存的更改，确定要离开吗？', '未保存的更改', {
      confirmButtonText: '离开',
      cancelButtonText: '取消',
      type: 'warning',
    })
    return true
  } catch {
    return false
  }
})
</script>

<style scoped>
.crm-status-bar .mt-2 { margin-top: 8px; display: inline-block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.evaluation-tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.review-badge {
  font-weight: 600;
}
.review-action-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: center;
  margin-top: 20px;
  padding: 16px;
  background: var(--el-color-warning-light-9);
  border-radius: 8px;
  border: 1px solid var(--el-color-warning-light-7);
}
.review-hint {
  font-size: 13px;
  color: var(--el-color-warning-dark-2);
}
.crm-section-in-tab {
  margin-bottom: 8px;
}
.crm-status-bar {
  margin-bottom: 16px;
  padding: 8px 12px;
  background: var(--el-color-info-light-9);
  border-radius: 6px;
  border: 1px solid var(--el-color-info-light-7);
}
</style>
