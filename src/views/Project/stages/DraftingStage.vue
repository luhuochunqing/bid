<template>
  <ProjectDocumentTable :project-id="projectId" />

  <el-card class="stage-view" shadow="never">
    <template #header>
      <div class="bid-header">
        <span class="bid-title">投标文件</span>
        <div class="bid-header-actions">
          <el-button v-if="perm.canAIRecommendCase" type="primary" link :icon="Search" @click="aiDrawerVisible = true">
            AI智能推荐案例
          </el-button>
          <el-button type="primary" link :icon="Trophy" @click="perfDrawerVisible = true">
            推荐业绩
          </el-button>
          <el-button v-if="perm.canAIBidDocumentQualityCheck" type="danger" link :icon="DocumentChecked" @click="ctx.runBidDocumentQualityCheck?.()">
            AI标书质量核查
          </el-button>
          <el-button type="success" link :icon="MagicStick" @click="ctx.bidAgent?.openDrawer?.()">
            启动AI生成初稿
          </el-button>
        </div>
      </div>
    </template>
    <div class="bid-upload-area">
      <el-upload
        v-model:file-list="bidFiles"
        :action="uploadUrl"
        :headers="uploadHeaders"
        :before-upload="beforeBidUpload"
        :disabled="bidDone || !perm.canManageBidFiles"
        drag
        multiple
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">将投标文件拖到此处，或<em>点击上传</em></div>
        <div class="upload-tip">支持 PDF、Word、Excel、图片等格式</div>
      </el-upload>
    </div>

    <!-- 标书审核人选择 -->
    <div class="bid-reviewer-row">
      <span style="font-size:13px;color:#606266;">标书审核人：</span>
      <template v-if="reviewState === 'reviewing' || reviewState === 'approved'">
        <span style="font-size:14px;color:#303133;font-weight:500;">{{ reviewerName || bidReviewerId || '未指定' }}</span>
      </template>
      <el-select v-else-if="perm.canSelectReviewer" v-model="bidReviewerId" filterable remote placeholder="模糊搜索选择审核人" :remote-method="searchReviewer" :loading="reviewerSearching" style="width:280px" clearable>
        <el-option v-for="u in reviewerOptions" :key="u.id" :label="u.name" :value="u.id" />
      </el-select>
    </div>

    <!-- 驳回理由 -->
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
  <PerformanceRecommendDrawer v-model="perfDrawerVisible" :project-id="projectId" />
  <QualityCheckDialog ref="qualityCheckRef" :project-id="projectId" />
</template>

<script setup>
import { DocumentChecked, MagicStick, Search, Trophy, UploadFilled } from '@element-plus/icons-vue'
import { ref, computed, nextTick, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getApiUrl } from '@/api/config.js'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { usersApi } from '@/api/modules/users.js'
import { useUserStore } from '@/stores/user'
import ProjectDocumentTable from './components/ProjectDocumentTable.vue'
import AiRecommendDrawer from './components/AiRecommendDrawer.vue'
import PerformanceRecommendDrawer from './components/PerformanceRecommendDrawer.vue'
import QualityCheckDialog from './components/QualityCheckDialog.vue'
import { useProjectDetailContext } from '@/composables/projectDetail/context.js'
import { useProjectDraftingPermissions } from '@/composables/projectDetail/useProjectDraftingPermissions.js'

const userStore = useUserStore()
const ctx = useProjectDetailContext()
const { bidAgent } = ctx
const perm = useProjectDraftingPermissions({
  primaryLeadId: ctx.project?.value?.primaryLeadUserId,
  secondaryLeadId: ctx.project?.value?.secondaryLeadUserId,
  currentUserId: ctx.userStore?.currentUser?.id,
})

const props = defineProps({ projectId: { type: [String, Number], required: true } })
const emit = defineEmits(['advanced', 'switch-tab'])

const view = ref(null)
const aiDrawerVisible = ref(false)
const perfDrawerVisible = ref(false)
const advancing = ref(false)
const advanceError = ref('')
const bidFiles = ref([])
const bidReviewerId = ref(null)
const bidSubmitted = ref(false)
const bidDone = ref(false)
const submittingReview = ref(false)
const showReviewDialog = ref(false)
const reviewComment = ref('')
const reviewApproving = ref(false)
const reviewRejecting = ref(false)
const reviewerOptions = ref([])
const reviewerSearching = ref(false)
const reviewerName = ref('')
const reviewState = ref(null)        // null | 'reviewing' | 'rejected' | 'approved'
const rejectReasonText = ref('')

// 当前用户是否为该项目指定的审核人（id 必须一致，类型安全比较）
const isCurrentUserReviewer = computed(() => {
  const uid = ctx.userStore?.currentUser?.id
  if (!uid || !bidReviewerId.value) return false
  return String(uid) === String(bidReviewerId.value)
})
const uploadUrl = computed(() => getApiUrl(`/api/projects/${props.projectId}/documents`))
const uploadHeaders = computed(() => { const t = userStore?.token; return t ? { Authorization: 'Bearer ' + t } : {} })

const qualityCheckRef = ref(null)

watch(() => ctx.bidDocQualityResult?.value, (val) => {
  if (val?.issues?.length) qualityCheckRef.value?.open(val)
}, { deep: true })

function beforeBidUpload(file) {
  const valid = ['.pdf', '.doc', '.docx', '.xlsx', '.jpg', '.png'].some(e => file.name.toLowerCase().endsWith(e))
  if (!valid) { ElMessage.error('仅支持 PDF/Word/Excel/图片格式'); return false }
  return true
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
      bidReviewerId.value = d.reviewerId || null
      reviewerName.value = d.reviewerName || ''
      // 预置审核人姓名到下拉选项，使 el-select 能展示姓名而非 ID
      if (d.reviewerId && d.reviewerName) {
        const existing = reviewerOptions.value.find(u => u.id === Number(d.reviewerId))
        if (!existing) {
          reviewerOptions.value.unshift({ id: Number(d.reviewerId), name: d.reviewerName })
        }
      }
      // 恢复投标提交状态
      if (d.bidSubmitted) bidDone.value = true
    }
  } catch (e) {
    console.warn(e)
  }
}

async function searchReviewer(query) {
  if (!query) return
  reviewerSearching.value = true
  try {
    const list = await usersApi.search(query)
    reviewerOptions.value = Array.isArray(list) ? list.map(u => ({ id: Number(u.id), name: u.name || u.fullName || u.username })) : []
  } catch { reviewerOptions.value = [] }
  finally { reviewerSearching.value = false }
}

async function submitBidForReview() {
  if (!bidReviewerId.value) return ElMessage.warning('请先选择标书审核人')
  submittingReview.value = true
  try {
    const res = await projectLifecycleApi.submitBidForReview(props.projectId, { reviewerId: bidReviewerId.value })
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
      await projectLifecycleApi.rejectBid(props.projectId, { reason: reviewComment.value })
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
    nextTick(() => emit('switch-tab', 'EVALUATING'))
  } catch (e) {
    if (e?.response?.status === 409) advanceError.value = e?.response?.data?.msg || '存在未完成任务'
    else ElMessage.error(e?.response?.data?.msg || '推进失败')
  } finally { advancing.value = false }
}

onMounted(load)
</script>

<style scoped>
.bid-header { display: flex; align-items: center; justify-content: space-between; }
.bid-title { font-weight: 600; font-size: 15px; }
.bid-header-actions { display: flex; align-items: center; gap: 4px; }
.required-mark { color: #e65100; margin-left: 2px; font-weight: normal; font-size: 13px; }
.upload-tip { margin-top: 8px; font-size: 12px; color: #909399; text-align: center; }
.bid-reviewer-row { display: flex; align-items: center; gap: 12px; margin-top: 16px; }
.bid-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
.complete-bid-row { display: flex; align-items: center; justify-content: space-between; }
</style>
