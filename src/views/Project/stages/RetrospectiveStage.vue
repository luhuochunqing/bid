<template>
  <div class="retrospective-stage">
    <!-- 仅中标/未中标进入复盘，流标/弃标不显示表单 -->
    <div v-if="isApplicable">
      <!-- 会议信息 -->
      <el-card shadow="never" class="stage-section">
        <template #header><span class="section-title">会议信息</span></template>
        <el-form :model="form" label-width="140px" :disabled="locked || !canEdit">
          <el-form-item label="投标结果">
            <el-tag :type="resultType === 'WON' ? 'success' : 'danger'">
              {{ resultType === 'WON' ? '中标' : '未中标' }}
            </el-tag>
          </el-form-item>
          <el-form-item label="复盘会时间" required>
            <el-date-picker
              v-model="form.meetingTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择复盘会议时间"
            />
          </el-form-item>
          <el-form-item label="会议形式" required>
            <el-select v-model="form.meetingFormat" placeholder="请选择">
              <el-option label="线上" value="ONLINE" />
              <el-option label="线下" value="OFFLINE" />
            </el-select>
          </el-form-item>
          <el-form-item label="会议参与人" required>
            <el-input v-model="form.meetingParticipants" placeholder="请输入参与人姓名，多人用逗号分隔" />
          </el-form-item>
        </el-form>
      </el-card>
      <!-- 中标分析 -->
      <el-card v-if="resultType === 'WON'" shadow="never" class="stage-section">
        <template #header><span class="section-title">中标分析</span></template>
        <el-form :model="form" label-width="140px" :disabled="locked || !canEdit">
          <el-form-item label="中标优势" required>
            <el-input v-model="form.winFactors" type="textarea" :rows="4" placeholder="本次中标的优势分析" />
          </el-form-item>
          <el-form-item label="流程亮点" required>
            <el-input v-model="form.processHighlights" type="textarea" :rows="4" placeholder="标书制作过程中的亮点" />
          </el-form-item>
          <el-form-item label="后续改进建议" required>
            <el-input v-model="form.postWinImprovements" type="textarea" :rows="4" placeholder="对未来投标的改进建议" />
          </el-form-item>
        </el-form>
      </el-card>
      <!-- 丢标分析 -->
      <el-card v-if="resultType === 'LOST'" shadow="never" class="stage-section">
        <template #header><span class="section-title">丢标分析</span></template>
        <el-form :model="form" label-width="140px" :disabled="locked || !canEdit">
          <el-form-item label="丢标原因" required>
            <el-checkbox-group v-model="form.lossReasonFlags" class="loss-reason-group">
              <el-checkbox
                v-for="opt in lossReasonOptions"
                :key="opt.value"
                :label="opt.value"
              >{{ opt.label }}</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="流程存在问题" required>
            <el-input v-model="form.processProblems" type="textarea" :rows="4" placeholder="标书制作过程中暴露的问题" />
          </el-form-item>
          <el-form-item label="具体改进措施" required>
            <el-input v-model="form.postLossMeasures" type="textarea" :rows="4" placeholder="针对问题的改进方案" />
          </el-form-item>
        </el-form>
      </el-card>
      <!-- 复盘报告 -->
      <el-card v-if="canEdit" shadow="never" class="stage-section">
        <template #header><span class="section-title">复盘报告</span></template>
        <el-upload
          v-model:file-list="reportFiles"
          :action="uploadUrl"
          :headers="uploadHeaders"
          :disabled="locked"
          accept=".doc,.docx,.pdf"
          :before-upload="beforeUpload"
          drag
          multiple
          :limit="3"
          :on-success="handleUploadSuccess"
          :on-error="handleUploadError"
          :on-remove="handleUploadRemove"
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 Word/PDF 格式，单文件≤20MB，最多3个</div>
          </template>
        </el-upload>
      </el-card>
      <!-- 审核区域（管理员可见） -->
      <el-card v-if="isAdmin && view" shadow="never" class="stage-section">
        <template #header><span class="section-title">审核</span></template>
        <el-form :model="review" label-width="140px">
          <el-form-item label="审核决定">
            <el-radio-group v-model="review.decision">
              <el-radio value="APPROVE">通过</el-radio>
              <el-radio value="REJECT">驳回</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="审核意见" :required="review.decision === 'REJECT'">
            <el-input v-model="review.comment" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item>
            <el-button
              type="warning"
              :disabled="review.decision === 'REJECT' && !review.comment"
              :loading="reviewing"
              @click="doReview"
            >提交审核</el-button>
          </el-form-item>
        </el-form>
      </el-card>
      <!-- 提交按钮 -->
      <div v-if="canEdit" class="btn-container">
        <el-button type="primary" size="large" :loading="submitting" :disabled="locked" @click="submit">提交复盘</el-button>
      </div>
    </div>
    <!-- 流标/弃标提示 -->
    <el-empty v-else description="流标/弃标无需复盘，请进入结项页面" />
  </div>
</template>
<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { useUserStore } from '@/stores/user'
import { lossReasonOptions } from './retrospectiveLossReasons.js'
import { getApiUrl } from '@/api/config.js'
const props = defineProps({
  projectId: { type: [String, Number], required: true },
  resultType: { type: String, default: '' },
})
const emit = defineEmits(["submitted"])
const userStore = useUserStore()
const isAdmin = computed(() => userStore.hasPermission('project:retrospective:review'))
const canEdit = computed(() => {
  const role = userStore.userRole || userStore.currentUser?.role || ''
  return ['bid_admin', 'bid_lead', 'bid_senior', 'bid_specialist', 'admin'].includes(role)
})
const isApplicable = computed(() => props.resultType === 'WON' || props.resultType === 'LOST')
const form = reactive({
  meetingTime: '',
  meetingFormat: 'ONLINE',
  meetingParticipants: '',
  winFactors: '',
  processHighlights: '',
  postWinImprovements: '',
  lossReasonFlags: [],
  processProblems: '',
  postLossMeasures: '',
  reportFileIds: [],
})
const reportFiles = ref([])
const review = reactive({ decision: 'APPROVE', comment: '' })
const view = ref(null)
const locked = ref(false)
const submitting = ref(false)
const reviewing = ref(false)
const uploadUrl = computed(() => getApiUrl(`/api/projects/${props.projectId}/documents`))
const uploadHeaders = computed(() => userStore?.token ? { Authorization: `Bearer ${userStore.token}` } : {})
const MAX_FILE_MB = 20
function beforeUpload(file) {
  const valid = ['application/pdf', 'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document']
  if (!valid.includes(file.type)) return ElMessage.error('仅支持 Word/PDF 格式') || false
  if (file.size > MAX_FILE_MB * 1024 * 1024) return ElMessage.error(`文件不能超过 ${MAX_FILE_MB}MB`) || false
  return true
}
function handleUploadSuccess(res) {
  if (res?.data?.id) form.reportFileIds.push(res.data.id)
}
function handleUploadError(err) {
  ElMessage.error('复盘报告上传失败: ' + (err?.response?.data?.msg || err?.message || '上传失败'))
}
function handleUploadRemove(f) {
  const idx = form.reportFileIds.indexOf(f.response?.data?.id)
  if (idx > -1) form.reportFileIds.splice(idx, 1)
}
async function load() {
  try {
    const r = await projectLifecycleApi.getRetrospective(props.projectId)
    view.value = (r?.success && r?.data) ? r.data : null
    if (view.value) {
      form.meetingTime = view.value.meetingTime || ''
      form.meetingFormat = view.value.meetingFormat || 'ONLINE'
      form.meetingParticipants = view.value.meetingParticipants || ''
      form.winFactors = view.value.winFactors || ''
      form.processHighlights = view.value.processHighlights || ''
      form.postWinImprovements = view.value.postWinImprovements || ''
      form.lossReasonFlags = view.value.lossReasonFlags || []
      form.processProblems = view.value.processProblems || ''
      form.postLossMeasures = view.value.postLossMeasures || ''
      form.reportFileIds = view.value.reportFileIds || []
      locked.value = view.value.reviewStatus === 'APPROVED' || view.value.reviewStatus === 'PENDING_REVIEW'
    }
  } catch (e) {
    console.warn('[RetrospectiveStage] load failed', e)
  }
}
async function submit() {
  if (!isApplicable.value) return
  if (!form.meetingTime) return ElMessage.warning('请选择复盘会时间')
  if (!form.meetingFormat) return ElMessage.warning('请选择会议形式')
  if (!form.meetingParticipants?.trim()) return ElMessage.warning('请填写会议参与人')
  if (props.resultType === 'WON') {
    if (!form.winFactors?.trim()) return ElMessage.warning('请填写中标优势')
    if (!form.processHighlights?.trim()) return ElMessage.warning('请填写流程亮点')
    if (!form.postWinImprovements?.trim()) return ElMessage.warning('请填写后续改进建议')
  }
  if (props.resultType === 'LOST') {
    if (!form.lossReasonFlags.length) return ElMessage.warning('请至少选择一项丢标原因')
    if (!form.processProblems?.trim()) return ElMessage.warning('请填写流程存在问题')
    if (!form.postLossMeasures?.trim()) return ElMessage.warning('请填写具体改进措施')
  }
  submitting.value = true
  try {
    await projectLifecycleApi.submitRetrospective(props.projectId, {
      resultType: props.resultType,
      meetingTime: form.meetingTime,
      meetingFormat: form.meetingFormat,
      meetingParticipants: form.meetingParticipants,
      winFactors: form.winFactors,
      processHighlights: form.processHighlights,
      postWinImprovements: form.postWinImprovements,
      lossReasonFlags: form.lossReasonFlags,
      processProblems: form.processProblems,
      postLossMeasures: form.postLossMeasures,
      reportFileIds: form.reportFileIds,
    })
    ElMessage.success('复盘已提交')
    emit('submitted')
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '提交失败')
  } finally {
    submitting.value = false
  }
}
async function doReview() {
  if (review.decision === 'REJECT' && !review.comment.trim()) {
    return ElMessage.warning('驳回必须填写审核意见')
  }
  reviewing.value = true
  try {
    await projectLifecycleApi.reviewRetrospective(props.projectId, review)
    ElMessage.success('审核已提交')
    emit('submitted')
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '审核失败')
  } finally {
    reviewing.value = false
  }
}
onMounted(load)
</script>
<style scoped>
.retrospective-stage { display: flex; flex-direction: column; gap: 16px; }
.section-title { font-size: 15px; font-weight: 600; color: #2E7659; }
.loss-reason-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.btn-container { display: flex; justify-content: flex-end; }
</style>
