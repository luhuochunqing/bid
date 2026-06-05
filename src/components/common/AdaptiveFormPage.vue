<template>
  <div class="adaptive-form-page">
    <!-- Loading state -->
    <div v-if="loading" class="adaptive-form-loading">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>加载表单配置中...</span>
    </div>

    <!-- Error state: show inline message + fallback form below -->
    <div v-else-if="error && !hasSchema" class="adaptive-form-error-banner">
      <el-alert type="warning" :closable="false" show-icon>
        <template #title>
          <span>动态表单配置不可用（{{ error }}），使用标准表单。</span>
        </template>
      </el-alert>
    </div>

    <!-- Dynamic schema loaded: render DynamicFormRenderer -->
    <template v-if="hasSchema && !forceFallback">
      <DynamicFormRenderer
        ref="formRendererRef"
        :fields="fields"
        :field-states="mergedFieldStates"
        :model-value="modelValue"
        :disabled="disabled"
        :upload-fn="uploadFn"
        @update:model-value="handleUpdate"
        @submit="handleSubmit"
      />
    </template>

    <!-- No dynamic schema: render inline fallback form -->
    <slot
      v-else-if="!forceFallback"
      name="fallback-form"
      :model-value="modelValue"
      :update:model-value="handleUpdate"
      :fields="fields"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, shallowRef } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import DynamicFormRenderer from './DynamicFormRenderer.vue'
import { formDefinitionApi } from '@/api/modules/workflowForm.js'
import { useFormConditions } from '@/composables/useFormConditions'
import { useFieldVisibility, mergeFieldStates } from '@/composables/useFieldVisibility'

const props = defineProps({
  scope: { type: String, required: true },
  modelValue: { type: Object, default: () => ({}) },
  disabled: { type: Boolean, default: false },
  uploadFn: { type: Function, default: null },
  forceFallback: { type: Boolean, default: false },
  autoFetch: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'submit', 'schema-loaded', 'schema-error', 'submit-success', 'submit-error'])

const loading = ref(false)
const error = ref('')
const schemaData = ref(null)
const fields = ref([])
const conditions = ref([])
const visibilityRules = ref([])
const definitionId = ref(null)
const formRendererRef = shallowRef(null)
const formDataRef = { value: props.modelValue }

const hasSchema = computed(() => fields.value && fields.value.length > 0)

// ----- Composable集成 -----
const conditionStates = useFormConditions(conditions.value, formDataRef)
const visibilityStates = useFieldVisibility(visibilityRules.value)

const mergedFieldStates = computed(() => {
  const cond = conditionStates.value
  const vis = visibilityStates.value
  if (!cond && !vis) return {}
  if (!cond) return vis
  if (!vis) return cond
  return mergeFieldStates(vis, cond)
})

// ----- Fetch schema + conditions + visibility -----
async function fetchSchema(scope) {
  if (!scope) return
  loading.value = true
  error.value = ''
  schemaData.value = null
  fields.value = []
  conditions.value = []
  visibilityRules.value = []
  definitionId.value = null
  try {
    const resp = await formDefinitionApi.getActiveFormDefinition(scope)
    const data = resp?.data ?? resp
    if (data && Array.isArray(data.fields)) {
      schemaData.value = data
      fields.value = data.fields
    } else if (data && Array.isArray(data)) {
      schemaData.value = data
      fields.value = data
    } else {
      error.value = '服务端未返回有效表单配置'
      emit('schema-error', new Error('No valid schema'))
      return
    }
    emit('schema-loaded', fields.value)
  } catch (e) {
    const msg = e?.response?.data?.msg || e?.message || '未知错误'
    error.value = msg
    emit('schema-error', e)
  } finally {
    loading.value = false
  }
}

async function fetchConditionsAndVisibility(id) {
  try {
    const [condResp, visResp] = await Promise.all([
      formDefinitionApi.getConditionRules(id),
      formDefinitionApi.getVisibilityRules(id)
    ])
    conditions.value = (condResp?.data ?? []).map(normalizeCondition)
    visibilityRules.value = (visResp?.data ?? []).map(normalizeVisibility)
  } catch (e) {
    console.warn('[AdaptiveFormPage] Failed to load conditions/visibility:', e)
  }
}

function normalizeCondition(raw) {
  return {
    id: raw.id,
    sourceField: raw.sourceField,
    operator: raw.operator,
    targetValue: raw.targetValue ?? null,
    action: raw.action,
    targetField: raw.targetField,
    displayOrder: raw.displayOrder ?? 0
  }
}

function normalizeVisibility(raw) {
  return {
    id: raw.id,
    fieldKey: raw.fieldKey,
    rolePattern: raw.rolePattern ?? null,
    orgId: raw.orgId ?? null,
    visible: raw.visible ?? true,
    readonly: raw.readonly ?? false,
    hidden: raw.hidden ?? false
  }
}

// ----- Sync local model value with parent -----
function handleUpdate(value) {
  emit('update:modelValue', value)
}

// ----- Submit: call backend API -----
async function handleSubmit(formData) {
  const payload = { ...formData, _scope: props.scope }
  emit('submit', payload)
  try {
    const resp = await formDefinitionApi.submitFormDefinition(props.scope, payload)
    const result = resp?.data ?? resp
    if (result.success) {
      emit('submit-success', result.data)
    } else {
      emit('submit-error', { message: result.msg || '提交失败', data: result })
    }
  } catch (e) {
    const msg = e?.response?.data?.msg || e?.message || '提交失败'
    emit('submit-error', { message: msg, error: e })
  }
}

// ----- Public API -----
function validate() {
  return formRendererRef.value?.validate?.() ?? Promise.resolve('')
}

function submit() {
  return formRendererRef.value?.submit?.() ?? Promise.resolve({ valid: false, message: 'DynamicFormRenderer not available' })
}

// ----- Lifecycle -----
onMounted(async () => {
  if (props.autoFetch) {
    await fetchSchema(props.scope)
    if (definitionId.value) {
      await fetchConditionsAndVisibility(definitionId.value)
    }
  }
})

watch(() => props.scope, async (newScope) => {
  if (props.autoFetch) {
    await fetchSchema(newScope)
    if (definitionId.value) {
      await fetchConditionsAndVisibility(definitionId.value)
    }
  }
})

watch(() => props.modelValue, (val) => {
  formDataRef.value = val
}, { deep: true })

defineExpose({
  fetchSchema,
  validate,
  submit,
  getFields: () => fields.value,
  getSchemaData: () => schemaData.value,
  isDynamic: computed(() => hasSchema.value && !props.forceFallback),
  hasSchema,
  loading,
  error
})
</script>

<style scoped>
.adaptive-form-page {
  width: 100%;
}

.adaptive-form-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.adaptive-form-error-banner {
  margin-bottom: 8px;
}
</style>
