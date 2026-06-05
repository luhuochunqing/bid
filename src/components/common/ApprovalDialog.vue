<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="560px"
    destroy-on-close
    @close="handleClose"
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="approval-dialog" v-if="dialogData">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="项目名称">{{ dialogData.projectName || projectName || '--' }}</el-descriptions-item>
        <el-descriptions-item label="审批类型">{{ approvalTypeName }}</el-descriptions-item>
        <el-descriptions-item label="申请标题">{{ dialogData.title || defaultTitle }}</el-descriptions-item>
        <el-descriptions-item label="申请人" v-if="mode !== 'submit'">{{ dialogData.requesterName || dialogData.applicantName || '--' }}</el-descriptions-item>
        <el-descriptions-item label="当前审批人" v-if="mode !== 'submit' && dialogData.currentApproverName">{{ dialogData.currentApproverName }}</el-descriptions-item>
        <el-descriptions-item label="提交时间" v-if="mode !== 'submit' && dialogData.submitTime">{{ dialogData.submitTime }}</el-descriptions-item>
        <el-descriptions-item label="申请说明">{{ dialogData.description || approvalForm.description || '暂无说明' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <el-form :model="approvalForm" label-width="88px">
        <template v-if="mode === 'submit'">
          <el-form-item label="审批标题">
            <el-input v-model="approvalForm.title" maxlength="60" show-word-limit placeholder="请输入审批标题" />
          </el-form-item>
          <el-form-item label="优先级">
            <el-radio-group v-model="approvalForm.priority">
              <el-radio :value="0">常规</el-radio>
              <el-radio :value="1">紧急</el-radio>
              <el-radio :value="2">非常紧急</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="审批说明">
            <el-input v-model="approvalForm.description" type="textarea" :rows="4" placeholder="请输入审批说明" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="审批意见">
            <el-input v-model="approvalForm.comment" type="textarea" :rows="4" :placeholder="mode === 'approve' ? '请输入通过意见' : '请输入驳回原因'" />
          </el-form-item>
          <el-form-item label="重新提交" v-if="mode === 'reject'">
            <el-switch v-model="approvalForm.requireResubmit" active-text="要求重提" inactive-text="无需重提" />
          </el-form-item>
        </template>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button :type="submitButtonType" :loading="submitting" @click="handleSubmit">{{ submitButtonLabel }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { approvalApi } from '@/api'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  visible: { type: Boolean, default: false },
  mode: { type: String, default: 'submit' },
  projectId: { type: [String, Number], default: '' },
  projectName: { type: String, default: '' },
  approvalType: {
    type: Object,
    default: () => ({ type: 'project_review', typeName: '项目审批' }),
  },
  approvalInfo: {
    type: Object,
    default: () => ({}),
  },
})

const emit = defineEmits(['update:visible', 'success'])
const userStore = useUserStore()

const approvalForm = reactive({
  title: '',
  description: '',
  priority: 0,
  comment: '',
  requireResubmit: false,
})

const dialogData = computed(() => props.approvalInfo || {})
const approvalTypeName = computed(() => props.approvalType?.typeName || dialogData.value?.typeName || '项目审批')
const defaultTitle = computed(() => `${props.projectName || dialogData.value?.projectName || '当前项目'} - ${approvalTypeName.value}`)
const dialogTitle = computed(() => {
  if (props.mode === 'approve') return '审批通过'
  if (props.mode === 'reject') return '审批驳回'
  return '提交审批'
})

const submitButtonLabel = computed(() => {
  if (props.mode === 'approve') return '确认通过'
  if (props.mode === 'reject') return '确认驳回'
  return '提交审批'
})

const submitButtonType = computed(() => {
  if (props.mode === 'approve') return 'success'
  if (props.mode === 'reject') return 'danger'
  return 'primary'
})

const submitting = computed(() => false)

function resetForm() {
  approvalForm.title = dialogData.value?.title || defaultTitle.value
  approvalForm.description = dialogData.value?.description || ''
  approvalForm.priority = Number(dialogData.value?.priority || 0)
  approvalForm.comment = ''
  approvalForm.requireResubmit = false
}

watch(() => props.visible, (visible) => {
  if (visible) {
    resetForm()
  }
}, { immediate: true })

function handleClose() {
  emit('update:visible', false)
}

async function handleSubmit() {
  try {
    if (props.mode === 'submit') {
      if (!props.projectId) {
        ElMessage.warning('缺少项目 ID，无法提交审批')
        return
      }
      if (!approvalForm.title.trim()) {
        ElMessage.warning('请输入审批标题')
        return
      }

      const result = await approvalApi.submitApproval({
        projectId: Number(props.projectId),
        projectName: props.projectName,
        approvalType: props.approvalType?.type || 'project_review',
        title: approvalForm.title.trim(),
        description: approvalForm.description.trim(),
        priority: approvalForm.priority,
        requesterName: userStore.userName,
      })

      if (!result?.success) {
        ElMessage.error(result?.msg || '审批提交失败')
        return
      }

      ElMessage.success('审批申请已提交')
      emit('success', result.data)
      handleClose()
      return
    }

    if (!dialogData.value?.id) {
      ElMessage.warning('缺少审批单 ID，无法操作')
      return
    }
    if (!approvalForm.comment.trim()) {
      ElMessage.warning('请输入审批意见')
      return
    }

    const payload = {
      comment: approvalForm.comment.trim(),
      requireResubmit: approvalForm.requireResubmit,
    }
    const result = props.mode === 'approve'
      ? await approvalApi.approve(dialogData.value.id, payload)
      : await approvalApi.reject(dialogData.value.id, payload)

    if (!result?.success) {
      ElMessage.error(result?.msg || '审批操作失败')
      return
    }

    ElMessage.success(props.mode === 'approve' ? '审批已通过' : '审批已驳回')
    emit('success', result.data)
    handleClose()
  } catch (error) {
    ElMessage.error(error?.message || '审批操作失败')
  }
}
</script>

<style scoped>
.approval-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
