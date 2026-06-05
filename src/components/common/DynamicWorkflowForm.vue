<template>
  <DynamicFormRenderer
    ref="renderer"
    class="dynamic-workflow-form"
    :fields="visibleFields"
    :model-value="modelValue"
    :upload-fn="workflowUpload"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @submit="(v) => emit('submit', v)"
  />
</template>

<script setup>
import { computed, ref } from 'vue'

import DynamicFormRenderer from './DynamicFormRenderer.vue'
import { workflowFormApi } from '@/api/modules/workflowForm.js'

const props = defineProps({
  schema: {
    type: Object,
    required: true
  },
  modelValue: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['submit', 'update:modelValue'])

const renderer = ref(null)
const visibleFields = computed(() => (props.schema?.fields || []).filter((field) => !field.hidden))

function getTemplateCode() {
  return (
    props.schema?.templateCode ||
    props.schema?.code ||
    props.schema?.workflowType ||
    props.schema?.businessType ||
    'QUALIFICATION_BORROW'
  )
}

function getProjectId() {
  return props.schema?.projectId ?? props.modelValue?.projectId ?? null
}

async function workflowUpload(field, request) {
  const file = request?.file?.raw || request?.file
  if (!file) return null
  const response = await workflowFormApi.uploadWorkflowFormAttachment(
    getTemplateCode(),
    field.key,
    file,
    { projectId: getProjectId() }
  )
  const data = response?.data || response || {}
  return {
    fileName: data.fileName || data.name || file.name || '',
    fileUrl: data.fileUrl || data.url || '',
    storagePath: data.storagePath || '',
    contentType: data.contentType || file.type || '',
    size: data.size ?? file.size ?? 0
  }
}

function submit() {
  return renderer.value?.submit()
}

function validate() {
  return renderer.value?.validate()
}

defineExpose({ submit, validate })
</script>

<style scoped>
.dynamic-workflow-form {
  width: 100%;
}
</style>
