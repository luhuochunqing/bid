<template>
  <div class="bid-submit-area">
    <el-button
      v-if="canSubmitBidForReview"
      type="warning"
      size="large"
      :loading="submittingReview"
      @click="handleSubmitBidForReview"
    >
      <el-icon><DocumentChecked /></el-icon>
      提交投标审核
    </el-button>
    <el-button
      v-if="canReviewBid"
      type="warning"
      size="large"
      :loading="reviewing"
      @click="handleReviewBid"
    >
      <el-icon><CircleCheck /></el-icon>
      审核投标
    </el-button>
    <el-button
      v-if="canSubmitBid"
      type="success"
      size="large"
      :loading="advancing"
      @click="advanceToEvaluation"
    >
      <el-icon><Right /></el-icon>
      提交投标（推进至评标）
    </el-button>
    <el-alert
      v-if="advanceError"
      :title="advanceError"
      type="error"
      :closable="true"
      style="margin-top: 12px"
      @close="emit('update:advanceError', '')"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, DocumentChecked, Right } from '@element-plus/icons-vue'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  canSubmitBidForReview: Boolean,
  canReviewBid: Boolean,
  canSubmitBid: Boolean,
  advanceError: { type: String, default: '' },
})
const emit = defineEmits([
  'update:advanceError',
  'advanced',
  'open-reviewer-dialog',
])

const submittingReview = ref(false)
const reviewing = ref(false)
const advancing = ref(false)

async function handleSubmitBidForReview() {
  submittingReview.value = true
  try {
    await projectLifecycleApi.submitBidForReview(props.projectId)
    ElMessage.success('已提交投标审核，等待审核人审批')
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '提交投标审核失败')
  } finally {
    submittingReview.value = false
  }
}

async function handleReviewBid() {
  reviewing.value = true
  try {
    emit('open-reviewer-dialog')
  } finally {
    reviewing.value = false
  }
}

async function advanceToEvaluation() {
  emit('update:advanceError', '')
  advancing.value = true
  try {
    await projectLifecycleApi.advanceDrafting(props.projectId)
    ElMessage.success('已推进至评标阶段')
    emit('advanced')
  } catch (e) {
    if (e?.response?.status === 409) {
      emit('update:advanceError', e?.response?.data?.msg || '存在未完成任务，无法推进')
    } else {
      ElMessage.error(e?.response?.data?.msg || '推进失败')
    }
  } finally {
    advancing.value = false
  }
}
</script>
