<template>
  <el-form class="dynamic-form-renderer" :model="localValue" label-width="110px">
    <template v-for="field in visibleFields" :key="field.key">
      <!-- SECTION / DIVIDER 分隔线类型 -->
      <template v-if="field.type === 'section' || field.type === 'SECTION'">
        <el-divider content-position="left" class="field-section-divider">
          {{ field.label }}
        </el-divider>
      </template>
      <template v-else-if="field.type === 'divider' || field.type === 'DIVIDER'">
        <el-divider />
      </template>

      <!-- 普通字段 -->
      <el-form-item
        v-else
        :label="field.label"
        :required="isFieldRequired(field)"
        class="dynamic-field"
        :class="{ 'is-readonly': isFieldReadonly(field), 'is-hidden': (fieldStates || {})[field.key]?.hidden || field.hidden }"
      >
        <!-- Readonly badge -->
        <template #label v-if="isFieldReadonly(field)">
          {{ field.label }}
          <el-tag size="small" type="info" class="readonly-indicator">只读</el-tag>
        </template>

        <!-- INFO 说明文本 -->
        <el-alert
          v-if="field.type === 'info'"
          type="info"
          :closable="false"
          :title="field.content || field.label"
        />

        <!-- TEXT 单行文本 -->
        <el-input
          v-else-if="['text', 'TEXT', 'qualification', 'QUALIFICATION', 'project', 'PROJECT', 'person', 'PERSON'].includes(field.type)"
          v-model="localValue[field.key]"
          :placeholder="field.placeholder || `请输入${field.label}`"
          :disabled="isFieldReadonly(field)"
          :maxlength="field.maxLength"
          show-word-limit
          clearable
        />

        <!-- PHONE 手机号 -->
        <el-input
          v-else-if="field.type === 'phone' || field.type === 'PHONE'"
          v-model="localValue[field.key]"
          :placeholder="field.placeholder || '请输入手机号'"
          :disabled="isFieldReadonly(field)"
          maxlength="11"
          clearable
        >
          <template #prepend>+86</template>
        </el-input>

        <!-- EMAIL 邮箱 -->
        <el-input
          v-else-if="field.type === 'email' || field.type === 'EMAIL'"
          v-model="localValue[field.key]"
          :placeholder="field.placeholder || '请输入邮箱'"
          :disabled="isFieldReadonly(field)"
          clearable
        />

        <!-- URL 网址 -->
        <el-input
          v-else-if="field.type === 'url' || field.type === 'URL'"
          v-model="localValue[field.key]"
          :placeholder="field.placeholder || '请输入网址'"
          :disabled="isFieldReadonly(field)"
          clearable
        />

        <!-- TEXTAREA 多行文本 -->
        <el-input
          v-else-if="field.type === 'textarea' || field.type === 'TEXTAREA'"
          v-model="localValue[field.key]"
          type="textarea"
          :rows="field.rows || 3"
          :placeholder="field.placeholder || `请输入${field.label}`"
          :disabled="isFieldReadonly(field)"
          :maxlength="field.maxLength"
          show-word-limit
        />

        <!-- DATE 日期 -->
        <el-date-picker
          v-else-if="field.type === 'date' || field.type === 'DATE'"
          v-model="localValue[field.key]"
          type="date"
          value-format="YYYY-MM-DD"
          :placeholder="field.placeholder || `请选择${field.label}`"
          :disabled="isFieldReadonly(field)"
          style="width: 100%"
          clearable
        />

        <!-- NUMBER 数字 -->
        <el-input-number
          v-else-if="field.type === 'number' || field.type === 'NUMBER'"
          v-model="localValue[field.key]"
          :min="field.min ?? -Infinity"
          :max="field.max ?? Infinity"
          :disabled="isFieldReadonly(field)"
          :precision="0"
          style="width: 100%"
        />

        <!-- CURRENCY 金额 -->
        <el-input-number
          v-else-if="field.type === 'currency' || field.type === 'CURRENCY'"
          v-model="localValue[field.key]"
          :min="field.min ?? 0"
          :max="field.max ?? Infinity"
          :precision="2"
          :controls="false"
          :disabled="isFieldReadonly(field)"
          placeholder="0.00"
          style="width: 100%"
        >
          <template #prepend>¥</template>
        </el-input-number>

        <!-- PERCENT 百分比 -->
        <div v-else-if="field.type === 'percent' || field.type === 'PERCENT'" class="percent-field">
          <el-slider
            v-model="localValue[field.key]"
            :min="0"
            :max="100"
            :disabled="isFieldReadonly(field)"
            :step="field.step || 1"
            show-input
            :show-input-controls="false"
          />
          <span class="percent-suffix">%</span>
        </div>

        <!-- SELECT 下拉选择 -->
        <el-select
          v-else-if="field.type === 'select' || field.type === 'SELECT'"
          v-model="localValue[field.key]"
          :disabled="isFieldReadonly(field)"
          :placeholder="field.placeholder || `请选择${field.label}`"
          style="width: 100%"
          clearable
          filterable
        >
          <el-option
            v-for="option in resolvedOptions(field)"
            :key="option.value"
            :label="option.label"
            :value="option.value"
            :disabled="option.disabled === 'true' || option.disabled === true"
          />
        </el-select>

        <!-- ADDRESS 地址（省市区级联） -->
        <div v-else-if="field.type === 'address' || field.type === 'ADDRESS'" class="address-field">
          <el-cascader
            v-model="localValue[field.key]"
            :options="chinaRegionOptions"
            :props="{ expandTrigger: 'hover', label: 'name', value: 'code' }"
            :placeholder="field.placeholder || `请选择省市区`"
            :disabled="isFieldReadonly(field)"
            clearable
            style="width: 100%"
          />
        </div>

        <!-- ATTACHMENT 附件上传 -->
        <el-upload
          v-else-if="field.type === 'attachment' || field.type === 'ATTACHMENT'"
          :auto-upload="false"
          :file-list="getAttachmentFileList(field)"
          :limit="field.limit"
          :accept="field.accept"
          :disabled="isFieldReadonly(field)"
          multiple
          @change="(file) => handleAttachmentChange(field, file)"
          @remove="(file) => handleAttachmentRemove(field, file)"
        >
          <el-button type="primary" plain :disabled="disabled || field.readonly">选择文件</el-button>
          <template #tip>
            <div class="el-upload__tip" v-if="field.accept">
              支持格式：{{ field.accept }}，最多上传 {{ field.limit || 5 }} 个文件
            </div>
          </template>
        </el-upload>

        <!-- TENDER_SOURCE / PROJECT_STATUS / QUALIFICATION_TYPE 枚举下拉 -->
        <el-select
          v-else-if="['tender_source', 'TENDER_SOURCE', 'project_status', 'PROJECT_STATUS', 'qualification_type', 'QUALIFICATION_TYPE'].includes(field.type)"
          v-model="localValue[field.key]"
          :disabled="isFieldReadonly(field)"
          :placeholder="field.placeholder || `请选择${field.label}`"
          style="width: 100%"
          clearable
        >
          <el-option
            v-for="opt in getEnumOptions(field.type)"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>

        <!-- TABLE 表格编辑 -->
        <div v-else-if="field.type === 'table' || field.type === 'TABLE'" class="table-grid-field">
          <table class="grid-table">
            <thead>
              <tr>
                <th v-for="col in (field.columns || [])" :key="col.key">
                  {{ col.label }}{{ col.required ? ' *' : '' }}
                </th>
                <th style="width: 48px"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, rowIdx) in getTableRows(field)" :key="rowIdx">
                <td v-for="col in (field.columns || [])" :key="col.key">
                  <el-input
                    v-model="row[col.key]"
                    :disabled="isFieldReadonly(field)"
                    size="small"
                    :placeholder="`请输入${col.label}`"
                  />
                </td>
                <td>
                  <el-button
                    type="danger"
                    size="small"
                    text
                    :disabled="isFieldReadonly(field)"
                    @click="removeTableRow(field, rowIdx)"
                  >×</el-button>
                </td>
              </tr>
            </tbody>
          </table>
          <el-button
            type="primary"
            plain
            size="small"
            :disabled="isFieldReadonly(field)"
            @click="addTableRow(field)"
            class="add-row-btn"
          >
            + 添加一行
          </el-button>
        </div>

        <!-- 不支持的类型降级为文本输入 -->
        <el-input
          v-else
          v-model="localValue[field.key]"
          :placeholder="field.placeholder || `请输入${field.label}`"
          :disabled="isFieldReadonly(field)"
          clearable
        />
      </el-form-item>
    </template>
  </el-form>
</template>

<script setup>
import { computed, nextTick, reactive, watch } from 'vue'

const props = defineProps({
  fields: { type: Array, required: true },
  modelValue: { type: Object, default: () => ({}) },
  uploadFn: { type: Function, default: null },
  disabled: { type: Boolean, default: false },
  /**
   * 字段状态映射（来自 useFormConditions + useFieldVisibility）
   * key: fieldKey, value: { hidden, readonly, required, readonlyText }
   */
  fieldStates: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['submit', 'update:modelValue'])

const localValue = reactive({ ...props.modelValue })

// 可见字段：同时过滤 schema hidden 和 composable hidden 状态
const visibleFields = computed(() =>
  (props.fields || []).filter((field) => {
    const state = props.fieldStates[field.key] || {}
    if (state.hidden) return false
    if (field.hidden) return false
    const t = (field.type || '').toLowerCase()
    if (t === 'section' || t === 'divider') return false
    return true
  })
)
let syncingFromParent = false

function isFieldRequired(field) {
  // 条件规则可以动态设置 required
  const state = props.fieldStates[field.key] || {}
  const isRequired = state.required || field.required
  return isRequired && field.type !== 'info' && field.type !== 'INFO'
}

function isFieldReadonly(field) {
  const state = props.fieldStates[field.key] || {}
  return props.disabled || state.readonly || field.readonly || false
}

function hasSameEntries(left = {}, right = {}) {
  const leftKeys = Object.keys(left)
  const rightKeys = Object.keys(right)
  return leftKeys.length === rightKeys.length &&
    leftKeys.every((key) => Object.is(left[key], right[key]))
}

watch(
  () => props.modelValue,
  (value) => {
    if (hasSameEntries(localValue, value || {})) return
    syncingFromParent = true
    Object.keys(localValue).forEach((key) => delete localValue[key])
    Object.assign(localValue, value || {})
    nextTick(() => { syncingFromParent = false })
  },
  { deep: true }
)

watch(localValue, () => {
  if (syncingFromParent) return
  emit('update:modelValue', { ...localValue })
}, { deep: true })

// ---------- 枚举选项 ----------
const ENUM_OPTIONS = {
  tender_source: [
    { label: '招标公告', value: 'bidding' },
    { label: '比选公告', value: 'selection' },
    { label: '竞争性谈判', value: 'negotiation' },
    { label: '单一来源', value: 'single_source' },
    { label: '询价采购', value: 'inquiry' },
    { label: '其他', value: 'other' }
  ],
  project_status: [
    { label: '进行中', value: 'in_progress' },
    { label: '已暂停', value: 'suspended' },
    { label: '已结项', value: 'closed' },
    { label: '已取消', value: 'cancelled' }
  ],
  qualification_type: [
    { label: '营业执照', value: 'business_license' },
    { label: '资质证书', value: 'qualification_cert' },
    { label: '安全生产许可证', value: 'safety_cert' },
    { label: 'ISO认证', value: 'iso_cert' },
    { label: '其他', value: 'other' }
  ]
}

function getEnumOptions(type) {
  const key = type.toLowerCase()
  return ENUM_OPTIONS[key] || []
}

// ---------- 下拉选项解析 ----------
function resolvedOptions(field) {
  if (!field.options) return []
  if (Array.isArray(field.options)) return field.options
  return []
}

// ---------- 附件 ----------
function getAttachmentValue(field) {
  const value = localValue[field.key]
  return Array.isArray(value) ? value : []
}

function getAttachmentFileList(field) {
  return getAttachmentValue(field).map((file, index) => ({
    uid: file.storagePath || file.fileUrl || `${field.key}-${index}`,
    name: file.fileName,
    url: file.fileUrl,
    status: 'success',
    response: file
  }))
}

async function uploadAttachment(field, request) {
  if (!props.uploadFn) {
    const err = new Error('uploadFn prop is required for attachment fields')
    request?.onError?.(err)
    throw err
  }
  const file = request?.file?.raw || request?.file
  if (!file) return null
  const attachment = await props.uploadFn(field, request)
  if (!attachment?.storagePath && !attachment?.fileUrl) {
    console.warn('[DynamicFormRenderer] uploadFn returned incomplete attachment (missing storagePath/fileUrl):', attachment)
  }
  localValue[field.key] = [...getAttachmentValue(field), attachment]
  request?.onSuccess?.(attachment)
  return attachment
}

async function handleAttachmentChange(field, file) {
  const rawFile = file?.raw
  if (!rawFile || file?.status === 'success') return
  await uploadAttachment(field, { file: rawFile })
}

function handleAttachmentRemove(field, file = {}) {
  const name = file.name || file.fileName
  const url = file.url || file.fileUrl
  const storagePath = file.response?.storagePath || file.storagePath
  localValue[field.key] = getAttachmentValue(field).filter((item) => (
    (storagePath && item.storagePath !== storagePath) ||
    (!storagePath && url && item.fileUrl !== url) ||
    (!storagePath && !url && name && item.fileName !== name)
  ))
}

// ---------- TABLE 表格 ----------
function getTableRows(field) {
  const key = field.key
  if (!localValue[key]) {
    localValue[key] = [{}]
  }
  return localValue[key]
}

function addTableRow(field) {
  const max = field.maxRows || 50
  if (localValue[field.key].length >= max) return
  localValue[field.key].push({})
}

function removeTableRow(field, index) {
  const min = field.minRows || 1
  if (localValue[field.key].length <= min) return
  localValue[field.key].splice(index, 1)
}

// ---------- 校验 ----------
function isEmptyValue(value) {
  if (Array.isArray(value)) return value.length === 0
  return value === null || value === undefined || String(value).trim() === ''
}

/**
 * 逐字段校验（支持 minLength / maxLength / min / max / required）
 * 返回错误信息列表
 */
function validateAll() {
  const errors = []
  for (const field of visibleFields.value) {
    const value = localValue[field.key]
    const state = (props.fieldStates || {})[field.key] || {}
    const effectiveRequired = state.required || field.required

    // 必填
    if (effectiveRequired && field.type !== 'info' && field.type !== 'INFO' && isEmptyValue(value)) {
      const msg = field.errorMessage || `请填写${field.label}`
      errors.push(msg)
      continue
    }
    if (isEmptyValue(value)) continue

    const strVal = String(value).trim()

    // minLength
    if (field.minLength != null && strVal.length < field.minLength) {
      errors.push(field.errorMessage || `${field.label} 长度不能少于 ${field.minLength} 个字符`)
      continue
    }

    // maxLength
    if (field.maxLength != null && strVal.length > field.maxLength) {
      errors.push(field.errorMessage || `${field.label} 长度不能超过 ${field.maxLength} 个字符`)
      continue
    }

    // min / max（仅对数值类型生效）
    const numTypes = ['number', 'NUMBER', 'currency', 'CURRENCY', 'percent', 'PERCENT']
    if (numTypes.includes(field.type)) {
      const num = Number(value)
      if (!isNaN(num)) {
        if (field.min != null && num < field.min) {
          errors.push(field.errorMessage || `${field.label} 最小值为 ${field.min}`)
          continue
        }
        if (field.max != null && num > field.max) {
          errors.push(field.errorMessage || `${field.label} 最大值为 ${field.max}`)
          continue
        }
      }
    }
  }
  return errors
}

function validate() {
  const errors = validateAll()
  if (errors.length === 0) return ''
  return errors[0]
}

function submit() {
  const message = validate()
  if (message) return { valid: false, message }
  emit('submit', { ...localValue })
  return { valid: true, data: { ...localValue } }
}

defineExpose({ submit, validate, uploadAttachment })
</script>

<script>
import { chinaRegionOptions } from './chinaRegionData.js'
export default { name: 'DynamicFormRenderer' }
</script>

<style scoped>
.dynamic-form-renderer {
  width: 100%;
}

.field-section-divider {
  margin-top: 16px;
  margin-bottom: 8px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.percent-field {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.percent-suffix {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  min-width: 24px;
}

.address-field {
  width: 100%;
}

/* TABLE GRID */
.table-grid-field {
  width: 100%;
}
.grid-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 8px;
  font-size: 13px;
}
.grid-table th,
.grid-table td {
  padding: 4px 4px;
  border: 1px solid var(--el-border-color-light);
  vertical-align: middle;
}
.grid-table th {
  background: var(--el-fill-color-light);
  font-weight: 600;
  color: var(--el-text-color-regular);
  text-align: left;
  padding-left: 8px;
}
.grid-table td .el-input {
  width: 100%;
}
.add-row-btn {
  margin-top: 4px;
}

/* Readonly field styling */
.dynamic-field.is-readonly :deep(.el-input.is-disabled .el-input__wrapper),
.dynamic-field.is-readonly :deep(.el-select.is-disabled .el-select__wrapper),
.dynamic-field.is-readonly :deep(.el-textarea.is-disabled .el-textarea__inner) {
  background-color: var(--el-fill-color-light);
  cursor: not-allowed;
}

.dynamic-field.is-readonly :deep(.el-input.is-disabled .el-input__inner),
.dynamic-field.is-readonly :deep(.el-input.is-disabled .el-input__prefix),
.dynamic-field.is-readonly :deep(.el-textarea.is-disabled .el-textarea__inner) {
  color: var(--el-text-color-secondary);
  cursor: not-allowed;
}

/* Readonly indicator badge */
.readonly-indicator {
  margin-left: 6px;
  vertical-align: middle;
  cursor: default;
}

/* Hidden field — never rendered */
.dynamic-field.is-hidden {
  display: none !important;
}
</style>
