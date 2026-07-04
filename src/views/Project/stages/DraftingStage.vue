<template>
  <ProjectDocumentTable :project-id="projectId" @export="exportDocumentsAsZip" />

  <el-card class="stage-view" shadow="never">
    <template #header>
      <div class="bid-header">
        <span class="bid-title">投标文件<span class="required-mark">*</span></span>
        <div class="bid-header-actions">
          <el-button v-if="perm.canAIRecommendCase" type="success" link class="header-action header-action--orange" :icon="Search" @click="aiDrawerVisible = true">
            AI智能推荐案例
          </el-button>
          <el-button v-if="perm.canAIBidDocumentQualityCheck" type="success" link class="header-action header-action--tender" :icon="DocumentChecked" @click="ctx.runBidDocumentQualityCheck?.()">
            AI标书质量核查
          </el-button>
          <el-button type="success" link class="header-action header-action--tender" :icon="MagicStick" @click="ctx.bidAgent?.openDrawer?.()">
            启动AI生成初稿
          </el-button>
        </div>
      </div>
    </template>
    <div class="bid-upload-area">
      <el-upload :with-credentials="true"
        v-model:file-list="bidFiles"
        :action="uploadUrl"
        :headers="uploadHeaders"
        :data="{ documentCategory: 'BID' }"
        :before-upload="beforeBidUpload"
        :disabled="bidDone || !perm.canManageBidFiles || reviewState === 'reviewing' || reviewState === 'approved'"
        drag
        multiple
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">将投标文件拖到此处，或<em>点击上传</em></div>
        <div class="upload-tip">支持 PDF、Word、Excel、图片等格式</div>
        <template #file="{ file }">
          <div class="bid-file-row">
            <a href="javascript:void(0)" class="upload-file-link" :class="{ 'is-readonly': !canDownloadBidFile }" @click.prevent="handleDownloadBidFile(file)">{{ file.name }}</a>
            <el-button
              v-if="!bidDone && canDeleteThisFile(file) && canDeleteBidFile"
              link
              type="danger"
              size="small"
              @click.prevent="handleRemoveBidFile(file)"
            >删除</el-button>
          </div>
        </template>
      </el-upload>
    </div>

    <!-- 标书审核人选择 -->
    <div class="bid-reviewer-row">
      <span style="font-size:13px;color:#606266;">标书审核人：</span>
      <template v-if="reviewState === 'reviewing' || reviewState === 'approved' || reviewState === 'rejected'">
        <span style="font-size:14px;color:#303133;font-weight:500;">{{ reviewerNamesText || '未指定' }}</span>
      </template>
      <UserPicker
        v-else-if="perm.canSelectReviewer"
        v-model="bidReviewerIds"
        mode="search"
        :multiple="true"
        :initial-options="reviewerInitialOptions"
        :exclude-ids="reviewerExcludeIds"
        placeholder="模糊搜索选择审核人（最多 3 人）"
        style="width:340px"
        clearable
      />
      <span v-if="perm.canSelectReviewer && reviewState !== 'reviewing' && reviewState !== 'approved'" class="bid-reviewer-tip">
        标书审核人需包含项目负责人且不能选择自己，支持最多不超过3人。
      </span>
    </div>

    <!-- CO-484 多人审核进度显示 -->
    <el-alert
      v-if="reviewState === 'reviewing' && reviewerProgressText"
      :title="reviewerProgressText"
      type="info"
      :closable="false"
      show-icon
      style="margin-top:8px"
    />

    <!-- CO-484 驳回理由（多人场景下展示"驳回人：原因"列表） -->
    <el-alert v-if="reviewState === 'rejected' && rejectReasonText" :title="'驳回原因：' + rejectReasonText" type="warning" show-icon :closable="false" style="margin-bottom:12px" />

    <!-- 底部按钮区 -->
    <div class="bid-actions">
      <!-- 投标负责人/辅助人员：提交审核 → 审核中 → 重新提交 -->
      <template v-if="perm.canSubmitBidForReview">
        <el-button v-if="reviewState === 'reviewing'" type="warning" disabled>审核中...</el-button>
        <el-button v-else-if="reviewState === 'approved'" type="success" disabled>已通过审核</el-button>
        <el-button v-else type="primary" :loading="submittingReview" @click="submitBidForReview">
          {{ reviewState === 'rejected' ? '重新提交标书审核' : '提交标书审核' }}
        </el-button>
      </template>

      <!-- 仅当前用户是被指定的审核人时显示审核按钮（审核状态为审核中） -->
      <template v-if="isCurrentUserReviewer && reviewState === 'reviewing'">
        <el-button type="danger" @click="handleReviewBid">驳回</el-button>
        <el-button type="success" :loading="reviewApproving" @click="confirmReviewBid('approve')">审核通过</el-button>
      </template>
    </div>

    <el-alert v-if="advanceError" :title="advanceError" type="error" :closable="true" @close="advanceError = ''" style="margin-top:12px" />
  </el-card>

  <!-- 完成投标 -->
  <el-card v-if="reviewState === 'approved' && perm.canSubmitBid" class="stage-view" shadow="never">
    <template #header>
      <span class="bid-title">完成投标</span>
    </template>
    <div class="complete-bid-row">
      <el-checkbox v-model="bidSubmitted" :disabled="bidDone">已完成投标</el-checkbox>
      <el-button v-if="bidDone" type="success" size="large" disabled>已投标</el-button>
      <el-button v-else type="success" size="large" :loading="advancing" :disabled="!bidSubmitted" @click="advanceToEvaluation">提交投标</el-button>
    </div>
  </el-card>

  <!-- 驳回投标对话框 -->
  <el-dialog v-model="showReviewDialog" title="驳回投标" width="420px" :close-on-click-modal="false">
    <el-input v-model="reviewComment" type="textarea" :rows="3" placeholder="驳回原因（必填）" />
    <template #footer>
      <el-button @click="showReviewDialog = false">取消</el-button>
      <el-button type="danger" :loading="reviewRejecting" @click="confirmReviewBid('reject')">确认驳回</el-button>
    </template>
  </el-dialog>

  <AiRecommendDrawer v-model="aiDrawerVisible" :project-id="projectId" />
  <QualityCheckDialog ref="qualityCheckRef" :project-id="projectId" />
</template>

<script setup>
import { DocumentChecked, MagicStick, Search, UploadFilled } from '@element-plus/icons-vue'
import { ref, computed, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getApiUrl } from '@/api/config.js'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { deleteDocument, getDocuments } from '@/api/modules/projectDocuments.js'
import { STAGE_TRANSITION_MAP } from '@/constants/projectStages.js'
import { useUserStore } from '@/stores/user'
import ProjectDocumentTable from './components/ProjectDocumentTable.vue'
import AiRecommendDrawer from './components/AiRecommendDrawer.vue'
import QualityCheckDialog from './components/QualityCheckDialog.vue'
import { useProjectDetailContext } from '@/composables/projectDetail/context.js'
import { useProjectDraftingPermissions, canDeleteDocumentAs } from '@/composables/projectDetail/useProjectDraftingPermissions.js'
import { useProjectDocumentsExport } from '@/composables/projectDetail/useProjectDocumentsExport.js'
import UserPicker from '@/components/common/UserPicker.vue'
import { downloadWithFilename } from '@/utils/download.js'; const userStore = useUserStore()
const ctx = useProjectDetailContext()
const { bidAgent } = ctx

// bidReviewerIds/primaryLeadId/secondaryLeadId 需在 perm 之前定义
// CO-484 v2：bidReviewerIds 为数组（多人审核，最多 3 人）
const bidReviewerIds = ref([])
const bidReviewerId = computed(() => bidReviewerIds.value[0] || null) // 兼容 perm 旧引用
const primaryLeadId = ref(null), secondaryLeadId = ref(null)

const perm = reactive(useProjectDraftingPermissions({
  primaryLeadId,
  secondaryLeadId,
  currentUserId: ctx.userStore?.currentUser?.id,
  reviewerId: bidReviewerId,
  reviewerIds: bidReviewerIds,
}))

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  // CO-381: 项目当前阶段（来自 ProjectDetailMainColumn 的 currentProjectStage）。
  // 用于投标文件下载守卫：仅 DRAFTING 阶段允许下载，进入下一阶段后只读。
  currentStage: { type: String, default: '' },
})
const emit = defineEmits(['advanced', 'switch-tab'])
// CO-378: 项目文档打包导出（ProjectDocumentTable 的「导出」按钮 @export 事件）
const { exportDocumentsAsZip } = useProjectDocumentsExport(props.projectId)
const view = ref(null)
const aiDrawerVisible = ref(false)
const advancing = ref(false)
const advanceError = ref('')
const bidFiles = ref([])
const bidSubmitted = ref(false)
const bidDone = ref(false)
const submittingReview = ref(false)
const showReviewDialog = ref(false)
const reviewComment = ref('')
const reviewApproving = ref(false)
const reviewRejecting = ref(false)
const reviewerInitialOptions = ref([])
const reviewerName = ref('') // 兼容旧引用
const reviewers = ref([]) // CO-484：[{reviewerId, reviewerName, decision, comment}]
const reviewState = ref(null)        // null | 'reviewing' | 'rejected' | 'approved'
const rejectReasonText = ref('')

// CO-484：审核人姓名拼接文案（用于 reviewState 非选择态的展示）
const reviewerNamesText = computed(() => {
  if (reviewers.value.length > 0) {
    return reviewers.value.map(r => r.reviewerName || r.reviewerId).join('、')
  }
  return reviewerName.value || ''
})

// CO-484：审核进度文案（"审核中（已通过 X/Y）"）
const reviewerProgressText = computed(() => {
  if (reviewers.value.length === 0) return ''
  const total = reviewers.value.length
  const approved = reviewers.value.filter(r => r.decision === 'APPROVED').length
  return `审核中（已通过 ${approved}/${total}）`
})

// CO-484 v2 审核人候选排除项：当前用户（不能选自己）/ 投标负责人 / 团队成员。
// 项目经理（managerId）需包含在审核人中，不排除；投标辅助人员（secondaryLead）解禁可作审核人，不排除。
const reviewerExcludeIds = computed(() => {
  const project = ctx.project?.value || {}
  const currentUid = ctx.userStore?.currentUser?.id
  const teamMembers = Array.isArray(project.teamMembers) ? project.teamMembers.map(Number) : []
  const primaryLeadId = project.primaryLeadUserId ? Number(project.primaryLeadUserId) : null
  return [currentUid, primaryLeadId, ...teamMembers].filter(Boolean)
})

// CO-484：当前用户是否为该项目指定的审核人（多人场景下用 includes 判断）
const isCurrentUserReviewer = computed(() => {
  const uid = ctx.userStore?.currentUser?.id
  if (!uid || bidReviewerIds.value.length === 0) return false
  return bidReviewerIds.value.some(rid => String(uid) === String(rid))
})
const uploadUrl = computed(() => getApiUrl(`/api/projects/${props.projectId}/documents`))
const uploadHeaders = computed(() => { const t = userStore?.token; return t ? { Authorization: 'Bearer ' + t } : {} })
// CO-381: 投标文件下载守卫——仅当项目仍处于 DRAFTING 阶段（含 submit-review 的 REVIEWING 子状态）
// 且当前用户有下载权限时允许下载。推进到 EVALUATING/CLOSED 等后续阶段后，文件名可见但下载禁用。
const canDownloadBidFile = computed(() => perm.canDownloadDocument && props.currentStage === 'DRAFTING')
// CO-382: 删除按钮守卫——仅"上传后、提交前"允许删除。
// 允许删除：reviewState === null（未提交审核）或 'rejected'（被驳回，可修改后重提）。
// 禁止删除：'reviewing'（审核中）/ 'approved'（已通过，bidDone 会接管）/ bidDone（已投标）。
// 前端守卫是体验层，后端 ProjectDocumentWorkflowPolicy.canDeleteProjectDocument 是真权限闸门。
const canDeleteBidFile = computed(() => reviewState.value === null || reviewState.value === 'rejected')
// CO-383: 删除按钮权限——admin_lead 直通 + 上传者本人可删（不管角色，对齐后端 Policy）。
// perm 是组件级 reactive，无法表达 file 级 uploaderId，所以用纯函数 canDeleteDocumentAs 按 file 调用。
function canDeleteThisFile(file) {
  return canDeleteDocumentAs({
    role: userStore.userRole,
    currentUserId: ctx.userStore?.currentUser?.id,
    uploaderId: file.response?.data?.uploaderId,
  })
}
function handleDownloadBidFile(file) {
  // CO-381: 阶段守卫 + 权限守卫。前端守卫是体验层，后端 ProjectDocumentDownloadService 还有深度防御。
  if (!canDownloadBidFile.value) return
  const id = file.response?.data?.id; if (!id) return ElMessage.warning('文件信息缺失，无法下载'); downloadWithFilename(`/api/projects/${props.projectId}/documents/${id}/download`, file.name || '投标文件')
}
const qualityCheckRef = ref(null)

watch(() => ctx.bidDocQualityResult?.value, (val) => {
  if (val?.issues?.length) qualityCheckRef.value?.open(val)
}, { deep: true })

function beforeBidUpload(file) {
  const valid = ['.pdf', '.doc', '.docx', '.xlsx', '.jpg', '.png'].some(e => file.name.toLowerCase().endsWith(e))
  if (!valid) { ElMessage.error('仅支持 PDF/Word/Excel/图片格式'); return false }
  return true
}

async function handleRemoveBidFile(file) {
  const documentId = file.response?.data?.id
  if (documentId) {
    try { await deleteDocument(props.projectId, documentId) }
    catch (error) { ElMessage.error(error?.message || '删除投标文件失败'); return }
  }
  bidFiles.value = bidFiles.value.filter((item) => item !== file)
  ElMessage.success('投标文件已删除')
}

async function load() {
  try {
    const r = await projectLifecycleApi.getDrafting(props.projectId)
    const d = r?.data || r
    view.value = d
    if (d) {
      const s = d.reviewStatus
      if (s) {
        reviewState.value = s.toLowerCase()
      } else {
        reviewState.value = null
      }
      rejectReasonText.value = d.rejectReason || ""
      // CO-483 修复：驳回后清空审核人选择 + reviewerInitialOptions，不再无条件预填旧审核人。
      // 审核中/已通过时展示已选审核人（只读），不回填到选择控件。
      if (reviewState.value === 'rejected') {
        bidReviewerIds.value = []
        reviewerInitialOptions.value = []
      } else if (d.reviewers && Array.isArray(d.reviewers) && d.reviewers.length > 0) {
        // CO-484：从后端 reviewers 列表回填（reviewing/approved 状态，只读展示）
        bidReviewerIds.value = d.reviewers.map(r => Number(r.reviewerId)).filter(Boolean)
        reviewerInitialOptions.value = d.reviewers
          .filter(r => r.reviewerId && r.reviewerName)
          .map(r => ({ id: Number(r.reviewerId), name: r.reviewerName }))
        reviewers.value = d.reviewers
      } else if (d.reviewerId) {
        // 兼容旧数据（无 reviewers 列表但有 reviewerId 单值）
        bidReviewerIds.value = [Number(d.reviewerId)]
      } else {
        bidReviewerIds.value = []
      }
      reviewerName.value = d.reviewerName || ''
      primaryLeadId.value = d.primaryLeadUserId || null
      secondaryLeadId.value = d.secondaryLeadUserId || null
      // 恢复投标提交状态
      if (d.bidSubmitted) bidDone.value = true
      // 恢复投标文件列表（BID_DOCUMENT），确保上传组件与后端一致
      await loadBidFiles()
    }
  } catch (e) {
    console.warn(e)
  }
}

async function loadBidFiles() {
  try {
    const res = await getDocuments(props.projectId, { documentCategory: 'BID' })
    const docs = res?.data || []
    bidFiles.value = docs.map(doc => ({
      name: doc.name || '投标文件',
      url: doc.fileUrl || '',
      response: { data: doc },
      status: 'success',
    }))
  } catch (e) {
    console.warn('[DraftingStage] failed to load bid files', e)
    bidFiles.value = []
  }
}

async function submitBidForReview() {
  // CO-484 v2：审核人校验——至少 1 人，最多 3 人（后端另校验需含项目经理）
  if (!bidReviewerIds.value || bidReviewerIds.value.length === 0) {
    return ElMessage.warning('请先选择标书审核人')
  }
  if (bidReviewerIds.value.length > 3) {
    return ElMessage.warning('标书审核人最多 3 人')
  }
  submittingReview.value = true
  try {
    const res = await projectLifecycleApi.submitBidForReview(props.projectId, { reviewerIds: bidReviewerIds.value })
    const d = res?.data || res
    reviewState.value = (d?.reviewStatus || 'reviewing').toLowerCase()
    reviewerName.value = d?.reviewerName || ''
    rejectReasonText.value = ''
    ElMessage.success('已提交标书审核')
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '提交审核失败') }
  finally { submittingReview.value = false }
}


function handleReviewBid() {
  showReviewDialog.value = true
  reviewComment.value = ''
}

async function confirmReviewBid(action) {
  if (action === 'approve') {
    reviewApproving.value = true
  } else {
    if (!reviewComment.value.trim()) return ElMessage.warning('请填写驳回原因')
    reviewRejecting.value = true
  }
  try {
    if (action === 'approve') {
      await projectLifecycleApi.approveBid(props.projectId, { comment: '' })
      reviewState.value = 'approved'
      ElMessage.success('投标审核通过')
    } else {
      await projectLifecycleApi.rejectBid(props.projectId, { comment: reviewComment.value })
      reviewState.value = 'rejected'
      rejectReasonText.value = reviewComment.value
      ElMessage.success('已驳回')
      showReviewDialog.value = false
      reviewComment.value = ''
    }
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '操作失败') }
  finally { reviewApproving.value = false; reviewRejecting.value = false }
}

async function advanceToEvaluation() {
  if (!bidSubmitted.value) return ElMessage.warning('请先勾选「已完成投标」')
  advanceError.value = ''
  advancing.value = true
  try {
    await projectLifecycleApi.submitBid(props.projectId)
    bidDone.value = true
    ElMessage.success('已推进至评标阶段')
    emit('advanced')
    emit('switch-tab', STAGE_TRANSITION_MAP.DRAFTING)
  } catch (e) {
    if (e?.response?.status === 409) advanceError.value = e?.response?.data?.msg || '存在未完成任务'
    else ElMessage.error(e?.response?.data?.msg || '推进失败')
  } finally { advancing.value = false }
}

onMounted(load)

defineExpose({ load })
</script>

<style scoped>
.bid-header { display: flex; align-items: center; justify-content: space-between; }
.upload-file-link { color: var(--el-color-primary); text-decoration: none; } .upload-file-link:hover { text-decoration: underline; }
.bid-title { font-weight: 600; font-size: 15px; }
.bid-header-actions { display: flex; align-items: center; gap: 4px; }
/* CO-380: AI 按钮统一为 header-action 风格，参考 ProjectTaskBoardCard.vue */
.header-action.el-button.is-link { min-height: 32px; padding: 0 10px; border-radius: 7px; font-weight: 600; transition: background-color 0.16s ease, color 0.16s ease, box-shadow 0.16s ease; }
.header-action--orange.el-button.is-link { --el-button-text-color: #e6a23c; --el-button-hover-link-text-color: #cf8a2b; --el-button-active-color: #b37a1f; }
.header-action--tender.el-button.is-link { --el-button-text-color: #23785d; --el-button-hover-link-text-color: #14684d; --el-button-active-color: #10563f; }
.header-action--orange.el-button.is-link:hover, .header-action--orange.el-button.is-link:focus { background: #fff7e8; }
.header-action--tender.el-button.is-link:hover, .header-action--tender.el-button.is-link:focus { background: #f4f8f6; }
.header-action--orange.el-button.is-link:focus-visible { box-shadow: 0 0 0 3px rgba(230, 162, 60, 0.16); outline: none; }
.header-action--tender.el-button.is-link:focus-visible { box-shadow: 0 0 0 3px rgba(35, 120, 93, 0.16); outline: none; }
.required-mark { color: #e65100; margin-left: 2px; font-weight: normal; font-size: 13px; }
.upload-tip { margin-top: 8px; font-size: 12px; color: #909399; text-align: center; }
.bid-file-row { display: flex; align-items: center; gap: 8px; }
.bid-reviewer-row { display: flex; align-items: center; gap: 12px; margin-top: 16px; flex-wrap: wrap; }
.bid-reviewer-tip { font-size: 12px; color: #909399; }
.bid-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
.complete-bid-row { display: flex; align-items: center; justify-content: space-between; }
</style>
