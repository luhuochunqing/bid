<template>
  <el-dialog
    v-model="visible"
    title="转派标讯"
    width="480px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" label-width="120px">
      <el-form-item label="标讯标题">
        <el-text truncated>{{ form.tenderTitle }}</el-text>
      </el-form-item>
      <el-form-item label="当前负责人">
        <el-text>{{ form.currentOwner }}</el-text>
      </el-form-item>
      <el-form-item
        label="转派给"
        prop="newOwnerId"
        :rules="[{ required: true, message: '请选择新的项目负责人', trigger: 'change' }]"
      >
        <el-select
          v-model="form.newOwnerId"
          filterable
          placeholder="选择新的项目负责人"
          class="full-width"
          :loading="loadingCandidates"
          @change="onOwnerChange"
        >
          <el-option
            v-for="candidate in candidates"
            :key="candidate.id"
            :label="formatAssignmentCandidateLabel(candidate)"
            :value="candidate.id"
            :disabled="candidate.id === form.currentOwnerId"
          >
            {{ formatAssignmentCandidateLabel(candidate) }} · {{ candidate.departmentName || '未设置部门' }}
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.newOwnerId === form.currentOwnerId" label=" ">
        <el-alert type="warning" :closable="false" show-icon title="不能转派给当前负责人" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :loading="transferring"
        :disabled="!form.newOwnerId || form.newOwnerId === form.currentOwnerId"
        @click="handleConfirm"
      >
        确认转派
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import tendersApi from '@/api/modules/tenders.js'
import { formatAssignmentCandidateLabel } from '../helpers.js'

const props = defineProps({
  visible: Boolean,
  tenderId: [Number, String],
  tenderTitle: String,
  currentOwnerId: [Number, String],
  currentOwnerName: String,
  candidates: { type: Array, default: () => [] },
  loadingCandidates: Boolean,
})

const emit = defineEmits(['update:visible', 'success', 'close'])

const formRef = ref(null)
const transferring = ref(false)

const form = reactive({
  tenderTitle: '',
  currentOwner: '',
  currentOwnerId: null,
  newOwnerId: null,
})

watch(() => props.visible, (v) => {
  if (v) {
    form.tenderTitle = props.tenderTitle || ''
    form.currentOwner = props.currentOwnerName || ''
    form.currentOwnerId = props.currentOwnerId
    form.newOwnerId = null
  }
})

function onOwnerChange(val) {
  // allow re-selection — validation handles it
}

async function handleConfirm() {
  if (!form.newOwnerId || form.newOwnerId === form.currentOwnerId) return
  transferring.value = true
  try {
    await tendersApi.transferTender(props.tenderId, { newOwnerId: form.newOwnerId })
    ElMessage.success('转派成功')
    emit('success')
    emit('update:visible', false)
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '转派失败，请重试')
  } finally {
    transferring.value = false
  }
}

function handleClose() {
  emit('update:visible', false)
  emit('close')
}
</script>
