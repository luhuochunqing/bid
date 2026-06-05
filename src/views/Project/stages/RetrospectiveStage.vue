<template>
  <el-card class="stage-view" shadow="never">
    <template #header>复盘 (Retrospective)</template>

    <!-- 仅中标/未中标进入复盘，流标/弃标不显示表单 -->
    <div v-if="isApplicable">
      <el-form :model="form" label-width="140px" :disabled="locked || !canEdit">
        <!-- 投标结果：只读展示 -->
        <el-form-item label="投标结果">
          <el-tag :type="resultType === 'WON' ? 'success' : 'danger'">
            {{ resultType === 'WON' ? '中标' : '未中标' }}
          </el-tag>
        </el-form-item>

        <!-- 会议信息 (中标/未中标均必填) -->
        <el-divider content-position="left">会议信息</el-divider>
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

        <!-- 中标专属字段 -->
        <template v-if="resultType === 'WON'">
          <el-divider content-position="left">中标分析</el-divider>
          <el-form-item label="中标优势" required>
            <el-input v-model="form.winFactors" type="textarea" :rows="4" placeholder="本次中标的优势分析" />
          </el-form-item>
          <el-form-item label="流程亮点" required>
            <el-input v-model="form.processHighlights" type="textarea" :rows="4" placeholder="标书制作过程中的亮点" />
          </el-form-item>
          <el-form-item label="后续改进建议" required>
            <el-input v-model="form.postWinImprovements" type="textarea" :rows="4" placeholder="对未来投标的改进建议" />
          </el-form-item>
        </template>

        <!-- 未中标专属字段 -->
        <template v-if="resultType === 'LOST'">
          <el-divider content-position="left">丢标分析</el-divider>
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
        </template>

        <el-divider content-position="left">复盘报告</el-divider>
        <el-form-item label="复盘报告">
          <el-upload
            v-if="canEdit"
            v-model:file-list="reportFiles"
            :action="uploadUrl"
            :headers="uploadHeaders"
            accept=".doc,.docx,.pdf"
            :before-upload="beforeUpload"
            multiple
            :limit="3"
            :on-success="handleUploadSuccess"
            :on-remove="handleUploadRemove"
          >
            <el-button type="primary">上传复盘报告</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 Word/PDF 格式，单文件≤20MB，最多3个</div>
            </template>
          </el-upload>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">提交复盘</el-button>
        </el-form-item>
      </el-form>

      <!-- 审核区域（管理员可见） -->
      <template v-if="isAdmin && view">
        <el-divider />
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
      </template>
    </div>

    <!-- 流标/弃标提示 -->
    <el-empty v-else description="流标/弃标无需复盘，请进入结项页面" />
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { useUserStore } from '@/stores/user'
import { lossReasonOptions } from "./retrospectiveLossReasons.js"

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  resultType: { type: String, default: '' },
})
const emit = defineEmits(["submitted"])

const userStore = useUserStore()
const isAdmin = computed(() => userStore.hasPermission('project:retrospective:review'))

/** 当前用户是否有复盘编辑权限 */
const canEdit = computed(() => {
  const role = userStore.userRole || userStore.currentUser?.role || ''
  return ['bid_admin', 'bid_lead', 'sales', 'manager', 'admin'].includes(role)
})

/** 仅中标/未中标适用复盘 */
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

// 上传配置
const uploadUrl = computed(() => `/api/projects/${props.projectId}/documents`)
const uploadHeaders = computed(() => {
  const token = userStore?.token
  return token ? { Authorization: `Bearer ${token}` } : {}
})
const MAX_REPORT_SIZE_MB = 20

function beforeUpload(file) {
  const allowed = ['application/pdf', 'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document']
  if (!allowed.includes(file.type)) {
    ElMessage.error('仅支持 Word/PDF 格式')
    return false
  }
  if (file.size > MAX_REPORT_SIZE_MB * 1024 * 1024) {
    ElMessage.error(`文件不能超过 ${MAX_REPORT_SIZE_MB}MB`)
    return false
  }
  return true
}

function handleUploadSuccess(response) {
  if (response?.data?.id) {
    form.reportFileIds.push(response.data.id)
  }
}

function handleUploadRemove(uploadFile) {
  const idx = form.reportFileIds.indexOf(uploadFile.response?.data?.id)
  if (idx > -1) form.reportFileIds.splice(idx, 1)
}

async function load() {
  try {
    const r = await projectLifecycleApi.getRetrospective(props.projectId)
    view.value = (r?.data?.success && r?.data?.data) ? r.data.data : null
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
      locked.value = view.value.reviewStatus === 'APPROVED'
    }
  } catch (e) {
    console.warn('[RetrospectiveStage] load failed', e)
  }
}

async function submit() {
  if (!isApplicable.value) return

  // 前端校验
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
.loss-reason-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
