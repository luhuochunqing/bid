<template>
  <el-dialog v-model="modelValue" title="资质借阅申请" width="560px">
    <el-alert
      v-if="featurePlaceholder"
      type="warning"
      :closable="false"
      show-icon
      :title="featurePlaceholder.title"
      :description="featurePlaceholder.message"
      class="borrow-alert"
    />
    <DynamicWorkflowForm ref="dynamicFormRef" :schema="schema" :model-value="form" @update:model-value="updateForm" />
    <template #footer>
      <el-button @click="modelValue = false">取消</el-button>
      <el-button type="primary" @click="submit">提交 OA 审批</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import DynamicWorkflowForm from '@/components/common/DynamicWorkflowForm.vue'

const modelValue = defineModel({ type: Boolean, default: false })
const form = defineModel('form', { type: Object, required: true })
defineProps({
  featurePlaceholder: {
    type: Object,
    default: null
  },
  qualification: {
    type: Object,
    default: null
  },
  schema: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['confirm'])
const dynamicFormRef = ref(null)

function updateForm(value) {
  Object.assign(form.value, value)
}

function submit() {
  const result = dynamicFormRef.value?.submit?.()
  if (result?.valid === false) {
    ElMessage.warning(result.msg || '请填写必填项')
    return
  }
  emit('confirm')
}
</script>

<style scoped lang="scss">
.borrow-alert {
  margin-bottom: 16px;
}
</style>
