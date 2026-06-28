<template>
  <el-dialog
    v-model="modelValue"
    title="人工录入标讯"
    width="860px"
    @close="$emit('reset')"
  >
    <!-- Adaptive Dynamic Form (M3: Dynamic Form Engine integration) -->
    <AdaptiveFormPage
      v-if="modelValue"
      ref="adaptiveFormRef"
      scope="tender.entry"
      :model-value="form"
      :disabled="saving || parsingDocument"
      @update:model-value="handleDynamicFormUpdate"
      @submit="handleDynamicFormSubmit"
    >
      <!-- #fallback-form: rendered only when dynamic schema is unavailable -->
      <template #fallback-form>
        <!-- ============================================================
             Fallback: original hardcoded tender entry form.
             Rendered only when the dynamic schema API is unavailable
             (backward compatibility, M3 backward-compat guarantee).
             ============================================================ -->
        <el-form ref="innerFormRef" :model="form" :rules="rules" label-width="100px">
          <el-row :gutter="16">
            <el-col :span="24">
              <el-form-item label="项目名称" prop="title">
                <el-input v-model="form.title" placeholder="请输入项目名称" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="总部所在地" prop="region">
                <el-cascader
                  ref="regionCascaderRef"
                  v-model="regionCascaderValue"
                  :options="chinaRegionOptions"
                  :props="REGION_CASCADER_PROPS"
                  placeholder="选择总部所在地"
                  clearable
                  filterable
                  class="full-width"
                  @change="onRegionCascaderChange"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="招标主体" prop="purchaser">
                <el-input v-model="form.purchaser" placeholder="请输入招标主体" class="full-width" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="报名截止时间" prop="deadline">
                <el-date-picker v-model="form.deadline" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择报名截止时间" class="full-width" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="开标时间" prop="bidOpeningTime">
                <el-date-picker v-model="form.bidOpeningTime" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择开标时间" class="full-width" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="客户类型" prop="customerType">
                <el-select v-model="form.customerType" placeholder="选择客户类型" class="full-width">
                  <el-option v-for="type in customerTypes" :key="type" :label="type" :value="type" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="优先级" prop="priority">
                <el-select v-model="form.priority" placeholder="选择优先级" class="full-width">
                  <el-option v-for="item in priorities" :key="item.value" :label="item.label" :value="item.value">
                    <div class="priority-option">
                      <span>{{ item.label }} · {{ item.desc }}</span>
                      <small>{{ item.standard }}</small>
                    </div>
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="项目类型" prop="projectType">
                <el-select v-model="form.projectType" placeholder="选择项目类型（选填）" clearable class="full-width">
                  <el-option v-for="t in projectTypes" :key="t" :label="t" :value="t" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系人" prop="contact">
                <el-input v-model="form.contact" placeholder="联系人姓名" class="full-width" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系方式" prop="phone">
                <el-input v-model="form.phone" placeholder="手机号/座机/邮箱" class="full-width" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系人2">
                <el-input v-model="form.contact2" placeholder="联系人姓名（选填）" class="full-width" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系方式2">
                <el-input v-model="form.phone2" placeholder="手机号/座机/邮箱（选填）" class="full-width" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="项目描述">
                <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入项目详细描述" />
              </el-form-item>
            </el-col>
            <!-- Paste recognition and file upload are ALWAYS present (tender.entry specific features) -->
            <el-col :span="24">
              <el-form-item label="粘贴识别">
                <div class="paste-recognition-hint">[粘贴识别]或文字输入，系统将智能拆分回填标讯信息</div>
                <el-input
                  v-model="form.pastedText"
                  type="textarea"
                  :rows="4"
                  maxlength="500000"
                  show-word-limit
                  placeholder="直接粘贴招标公告正文，系统将自动识别并回填字段"
                  :disabled="parsingDocument"
                />
                <div class="paste-actions">
                  <el-button
                    type="primary"
                    :icon="DocumentCopy"
                    :loading="parsingDocument"
                    @click="$emit('parse-pasted-text')"
                  >
                    识别粘贴文字
                  </el-button>
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="标讯文件">
                <div class="upload-hint">
                  支持 PDF/Word 文件上传（≤50MB），上传即保存，自动 AI 解析回填表单字段
                </div>
                <el-upload
                  class="manual-tender-upload"
                  :auto-upload="false"
                  :on-change="onFileChange"
                  :file-list="form.attachments"
                  :limit="5"
                  :accept="acceptFileTypes"
                  :on-exceed="onFileExceed"
                  multiple
                  drag
                >
                  <el-icon class="el-icon--upload"><Upload /></el-icon>
                  <div class="el-upload__text">
                    {{ parsingDocument ? 'DeepSeek/AI 解析中...' : '将文件拖到此处，或点击选择附件（PDF/Word ≤50MB）' }}
                  </div>
                </el-upload>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </template>
    </AdaptiveFormPage>

    <template #footer>
      <el-button @click="modelValue = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleFooterSubmit">保存入库</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, shallowRef } from 'vue'
import { DocumentCopy, Upload } from '@element-plus/icons-vue'
import AdaptiveFormPage from '@/components/common/AdaptiveFormPage.vue'
import { chinaRegionOptions } from '@/components/common/chinaRegionData.js'
import { useRegionCascaderValue, REGION_CASCADER_PROPS, createRegionCascaderAutoClose } from '@/composables/useRegionCascaderValue.js'
import {
  CUSTOMER_TYPE_OPTIONS,
  MANUAL_FORM_RULES,
  PRIORITY_OPTIONS,
  PROJECT_TYPE_OPTIONS,
} from '../constants.js'

const ACCEPT_FILE_TYPES = '.pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document'

const modelValue = defineModel({ type: Boolean, default: false })
const form = defineModel('form', { type: Object, required: true })
defineProps({
  rules: { type: Object, default: () => MANUAL_FORM_RULES },
  saving: { type: Boolean, default: false },
  parsingDocument: { type: Boolean, default: false },
  regions: { type: Array, default: () => chinaRegionOptions },
  customerTypes: { type: Array, default: () => CUSTOMER_TYPE_OPTIONS },
  priorities: { type: Array, default: () => PRIORITY_OPTIONS },
  projectTypes: { type: Array, default: () => PROJECT_TYPE_OPTIONS },
})

const emit = defineEmits(['reset', 'submit', 'file-change', 'parse-pasted-text'])

const innerFormRef = ref(null)
const adaptiveFormRef = shallowRef(null)

/**
 * 双向绑定 cascader path ↔ form.value.region（省+市 / 直辖市仅市 / 港澳台仅本级）。
 */
const regionCascaderValue = useRegionCascaderValue(
  () => form.value.region,
  (v) => { form.value.region = v },
  { emptyValue: '' },
)

// CO-381: 选中市级后自动关闭下拉框（setTimeout(0) 绕过 doExpand 同步重开）
const regionCascaderRef = ref(null)
const onRegionCascaderChange = createRegionCascaderAutoClose(regionCascaderRef)

const acceptFileTypes = ACCEPT_FILE_TYPES

const onFileChange = (file, fileList) => emit('file-change', file, fileList)

const onFileExceed = () => {
  // 已在 useManualTenderCreate 中处理，此处为兼容 el-upload 的 on-exceed 事件
}

/**
 * Sync updates from the DynamicFormRenderer back to parent form.
 * Called whenever the dynamic form emits update:modelValue.
 */
function handleDynamicFormUpdate(value) {
  // Merge dynamic form values back into the parent-provided form object.
  Object.assign(form.value, value)
}

/**
 * Submit handler for the DynamicFormRenderer.
 * When dynamic form validates and emits 'submit', forward to parent's submit handler.
 */
function handleDynamicFormSubmit(formData) {
  // Merge any dynamic form data that might not be in the parent form yet
  Object.assign(form.value, formData)
  emit('submit')
}

/**
 * Footer "保存入库" button handler.
 * If dynamic form is active, try to validate and submit it first.
 * Falls back to emitting 'submit' for the parent to handle.
 */
async function handleFooterSubmit() {
  // Attempt dynamic form submission first (if active)
  if (adaptiveFormRef.value?.isDynamic?.value) {
    const result = await adaptiveFormRef.value.submit()
    if (result?.valid === false) {
      return
    }
    // Dynamic form's internal submit emit → handleDynamicFormSubmit → Object.assign → emit('submit')
    return
  }
  // Fallback: emit submit for parent to validate via innerFormRef
  emit('submit')
}

defineExpose({
  /**
   * Expose adaptive form ref so parent can call validate on it.
   * Parent calls manualFormRef.value?.validate() for backward compat.
   */
  validate: async () => {
    if (adaptiveFormRef.value?.isDynamic?.value) {
      const result = await adaptiveFormRef.value.validate()
      if (result !== '') return true
      return false
    }
    return innerFormRef.value?.validate() ?? false
  },
  adaptiveFormRef,
})
</script>

<style scoped src="./manual-tender-dialog.css"></style>
