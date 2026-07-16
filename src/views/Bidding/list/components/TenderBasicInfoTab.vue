<template>
  <div v-show="activeTab === 'basic'" class="tab-content">
    <el-card shadow="never">
      <AdaptiveFormPage
        ref="adaptiveFormRef"
        scope="tender.entry"
        :model-value="form"
        :disabled="saving || isReadOnly"
        :force-fallback="true"
        @update:model-value="$emit('update:form', $event)"
      >
        <template #fallback-form>
          <el-form ref="innerFormRef" :model="form" :rules="MANUAL_FORM_RULES" label-width="110px" :disabled="saving || isReadOnly">
            <el-row :gutter="16">
              <!-- 简单字段区：按 schema 顺序 v-for 渲染 -->
              <el-col v-for="field in orderedSimpleFields" :key="field.key"
                      v-show="fieldEnabled(field.key)" :span="colSpanOf(field.key)">
                <!-- 特殊字段：region（保留 useRegionCascaderValue + 自动关闭） -->
                <el-form-item v-if="field.key === 'region'" :label="fieldLabel('region')" prop="region" :required="fieldRequired('region')">
                  <el-cascader ref="regionCascaderRef" v-model="regionCascaderValue"
                    :options="chinaRegionOptions" :props="REGION_CASCADER_PROPS"
                    placeholder="选择总部所在地" clearable filterable class="full-width"
                    @change="onRegionCascaderChange" />
                </el-form-item>
                <!-- 特殊字段：priority（保留自定义 option 模板） -->
                <el-form-item v-else-if="field.key === 'priority'" :label="fieldLabel('priority')" prop="priority" :required="fieldRequired('priority')">
                  <el-select v-model="form.priority" placeholder="选择优先级" class="full-width">
                    <el-option v-for="item in priorities" :key="item.value" :label="item.label" :value="item.value">
                      <div class="priority-option"><span>{{ item.label }} · {{ item.desc }}</span><small>{{ item.standard }}</small></div>
                    </el-option>
                  </el-select>
                </el-form-item>
                <!-- 特殊字段：deadline / bidOpeningTime（datetime） -->
                <el-form-item v-else-if="field.key === 'deadline'" :label="fieldLabel('deadline')" prop="deadline" :required="fieldRequired('deadline')">
                  <el-date-picker v-model="form.deadline" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm" placeholder="选择报名截止时间" class="full-width" />
                </el-form-item>
                <el-form-item v-else-if="field.key === 'bidOpeningTime'" :label="fieldLabel('bidOpeningTime')" prop="bidOpeningTime" :required="fieldRequired('bidOpeningTime')">
                  <el-date-picker v-model="form.bidOpeningTime" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm" placeholder="选择开标时间" class="full-width" />
                </el-form-item>
                <!-- 特殊字段：customerType / projectType（select，options 来自 schema 或 props） -->
                <el-form-item v-else-if="field.key === 'customerType'" :label="fieldLabel('customerType')" prop="customerType" :required="fieldRequired('customerType')">
                  <el-select v-model="form.customerType" placeholder="选择客户类型" class="full-width">
                    <el-option v-for="opt in resolveOptions(field)" :key="opt.value" :label="opt.label" :value="opt.value" />
                  </el-select>
                </el-form-item>
                <el-form-item v-else-if="field.key === 'projectType'" :label="fieldLabel('projectType')" prop="projectType" :required="fieldRequired('projectType')">
                  <el-select v-model="form.projectType" placeholder="选择项目类型" class="full-width">
                    <el-option v-for="opt in resolveOptions(field)" :key="opt.value" :label="opt.label" :value="opt.value" />
                  </el-select>
                </el-form-item>
                <!-- 特殊字段：pastedText（粘贴识别） -->
                <el-form-item v-else-if="field.key === 'pastedText'" :label="fieldLabel('pastedText')">
                  <div class="paste-hint">[粘贴识别] 或文字输入，系统将智能拆分回填标讯信息</div>
                  <el-input v-model="form.pastedText" type="textarea" :rows="4" maxlength="500000" show-word-limit placeholder="直接粘贴招标公告正文，系统将自动识别并回填字段" :disabled="parsingDocument" />
                  <div class="paste-actions"><el-button type="primary" :icon="DocumentCopy" :loading="parsingDocument" @click="$emit('parse-paste')">识别粘贴文字</el-button></div>
                </el-form-item>
                <!-- 特殊字段：attachments（标讯文件） -->
                <el-form-item v-else-if="field.key === 'attachments'" :label="fieldLabel('attachments')">
                  <div class="upload-hint">支持 PDF/Word 文件上传（≤50MB），上传即保存，自动 AI 解析回填表单字段</div>
                  <el-upload class="manual-tender-upload" :auto-upload="false" @change="(file, fileList) => $emit('file-change', file, fileList)" @remove="(file, fileList) => $emit('file-remove', file, fileList)" :file-list="form.attachments" :limit="10" :accept="acceptFileTypes" multiple drag>
                    <el-icon class="el-icon--upload"><Upload /></el-icon>
                    <div class="el-upload__text">{{ parsingDocument ? 'DeepSeek/AI 解析中...' : '将文件拖到此处，或点击选择附件（PDF/Word ≤50MB）' }}</div>
                  </el-upload>
                </el-form-item>
                <!-- 通用字段：text/textarea -->
                <el-form-item v-else :label="fieldLabel(field.key)" :prop="field.key" :required="fieldRequired(field.key)">
                  <el-input v-if="field.type === 'text'" v-model="form[field.key]" :placeholder="field.placeholder || `请输入${field.label}`" />
                  <el-input v-else-if="field.type === 'textarea'" v-model="form[field.key]" type="textarea" :rows="field.rows || 3" :placeholder="field.placeholder || `请输入${field.label}`" />
                  <el-input v-else v-model="form[field.key]" :placeholder="field.placeholder || `请输入${field.label}`" />
                </el-form-item>
              </el-col>
            </el-row>

            <!-- 固定分组区：联系人1/2 横排（不走 v-for，保持 4+6+7+7） -->
            <el-row :gutter="16" v-if="hasContact1Enabled">
              <el-col :span="24"><div class="contact-group-title">联系人1</div></el-col>
              <el-col :span="4" v-if="fieldEnabled('contact')"><el-form-item label="姓名" prop="contact" label-width="56px" :required="fieldRequired('contact')"><el-input v-model="form.contact" placeholder="联系人姓名" /></el-form-item></el-col>
              <el-col :span="6" v-if="fieldEnabled('phone')"><el-form-item label="手机号" prop="phone" label-width="64px" :required="fieldRequired('phone')"><el-input v-model="form.phone" placeholder="手机号" /></el-form-item></el-col>
              <el-col :span="7" v-if="fieldEnabled('landline')"><el-form-item label="座机" prop="landline" label-width="56px"><el-input v-model="form.landline" placeholder="座机（如 010-12345678）" /></el-form-item></el-col>
              <el-col :span="7" v-if="fieldEnabled('mail')"><el-form-item label="邮箱" prop="mail" label-width="56px"><el-input v-model="form.mail" placeholder="邮箱" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16" v-if="hasContact2Enabled">
              <el-col :span="24"><div class="contact-group-title">联系人2 <span class="optional-tag">选填</span></div></el-col>
              <el-col :span="4" v-if="fieldEnabled('contact2')"><el-form-item label="姓名" label-width="56px"><el-input v-model="form.contact2" placeholder="联系人姓名" /></el-form-item></el-col>
              <el-col :span="6" v-if="fieldEnabled('phone2')"><el-form-item label="手机号" prop="phone2" label-width="64px"><el-input v-model="form.phone2" placeholder="手机号" /></el-form-item></el-col>
              <el-col :span="7" v-if="fieldEnabled('landline2')"><el-form-item label="座机" label-width="56px"><el-input v-model="form.landline2" placeholder="座机" /></el-form-item></el-col>
              <el-col :span="7" v-if="fieldEnabled('mail2')"><el-form-item label="邮箱" label-width="56px"><el-input v-model="form.mail2" placeholder="邮箱" /></el-form-item></el-col>
            </el-row>
          </el-form>
        </template>
      </AdaptiveFormPage>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, shallowRef } from 'vue'
import { DocumentCopy, Upload } from '@element-plus/icons-vue'
import AdaptiveFormPage from '@/components/common/AdaptiveFormPage.vue'
import { chinaRegionOptions } from '@/components/common/chinaRegionData.js'
import { useRegionCascaderValue, REGION_CASCADER_PROPS, createRegionCascaderAutoClose } from '@/composables/useRegionCascaderValue.js'
import { MANUAL_FORM_RULES } from '../constants.js'
import { FIXED_GROUP_KEYS } from '@/views/System/workflow-form-designer/workflowFormDesignerCore.js'

const props = defineProps({
  activeTab: String,
  form: Object,
  rules: Object,  // 保留兼容，但实际用 MANUAL_FORM_RULES
  regions: Array,
  customerTypes: Array,
  projectTypes: Array,
  priorities: Array,
  saving: Boolean,
  isReadOnly: Boolean,
  parsingDocument: Boolean,
  acceptFileTypes: String,
})

defineEmits(['parse-paste', 'file-change', 'file-remove', 'update:form'])

const adaptiveFormRef = shallowRef(null)
const innerFormRef = ref(null)

defineExpose({
  validate: () => innerFormRef.value?.validate()
    ?? adaptiveFormRef.value?.validate()
    ?? Promise.resolve('')
})

// ===== Schema 字段读取（复用 AdaptiveFormPage）=====
const schemaFields = computed(() => adaptiveFormRef.value?.getFields() || [])
const hasSchema = computed(() => schemaFields.value.length > 0)

// 固定分组字段 key 复用自 workflowFormDesignerCore.js（单一真相源）

// 默认 colSpan 映射
const DEFAULT_COL_SPAN = {
  title: 24, region: 12, purchaser: 12, deadline: 12, bidOpeningTime: 12,
  customerType: 12, priority: 12, projectType: 12,
  description: 24, tenderInfo: 24, pastedText: 24, attachments: 24,
}

// 默认字段列表（无 schema 降级用）
// 注意：需与 V1167 schema 保持字段 key/label/type 同步，新增字段时两处都要改
const DEFAULT_FIELDS = [
  { key: 'title', label: '项目名称', type: 'text', required: true, enabled: true },
  { key: 'region', label: '总部所在地', type: 'cascader', required: true, enabled: true },
  { key: 'purchaser', label: '招标主体', type: 'text', required: true, enabled: true },
  { key: 'deadline', label: '报名截止时间', type: 'datetime', required: true, enabled: true },
  { key: 'bidOpeningTime', label: '开标时间', type: 'datetime', required: true, enabled: true },
  { key: 'customerType', label: '客户类型', type: 'select', required: true, enabled: true },
  { key: 'priority', label: '优先级', type: 'select', required: true, enabled: true },
  { key: 'projectType', label: '项目类型', type: 'select', required: true, enabled: true },
  { key: 'description', label: '标讯描述', type: 'textarea', required: false, enabled: true },
  { key: 'tenderInfo', label: '标讯信息', type: 'textarea', required: false, enabled: true },
  { key: 'pastedText', label: '粘贴识别', type: 'textarea', required: false, enabled: true },
  { key: 'attachments', label: '标讯文件', type: 'attachment', required: false, enabled: true },
]

// 简单字段列表（按 schema 顺序，过滤掉固定分组）
// normalize type 为小写，统一大小写不一致（schema 用大写 TEXT/SELECT，DEFAULT_FIELDS 用小写）
const orderedSimpleFields = computed(() => {
  const fields = hasSchema.value ? schemaFields.value : DEFAULT_FIELDS
  return fields
    .filter(f => !FIXED_GROUP_KEYS.includes(f.key))
    .map(f => ({ ...f, type: String(f.type || '').toLowerCase() }))
})

function fieldEnabled(key) {
  const field = schemaFields.value.find(item => item.key === key)
  return field ? field.enabled !== false : true
}

function fieldRequired(key) {
  const field = schemaFields.value.find(item => item.key === key)
  return field ? field.required === true : false
}

function fieldLabel(key) {
  const field = schemaFields.value.find(item => item.key === key)
  return field?.label || DEFAULT_FIELDS.find(item => item.key === key)?.label || key
}

function colSpanOf(key) {
  return DEFAULT_COL_SPAN[key] || 12
}

function resolveOptions(field) {
  if (field.options && Array.isArray(field.options)) return field.options
  // 无 schema options 时降级到 props（保持向后兼容）
  if (field.key === 'customerType') return (props.customerTypes || []).map(t => ({ value: t, label: t }))
  if (field.key === 'projectType') return (props.projectTypes || []).map(t => ({ value: t, label: t }))
  return []
}

const hasContact1Enabled = computed(() =>
  fieldEnabled('contact') || fieldEnabled('phone') || fieldEnabled('landline') || fieldEnabled('mail'),
)
const hasContact2Enabled = computed(() =>
  fieldEnabled('contact2') || fieldEnabled('phone2') || fieldEnabled('landline2') || fieldEnabled('mail2'),
)

// ===== 区域级联（保留原逻辑） =====
const regionCascaderValue = useRegionCascaderValue(
  () => props.form.region,
  (v) => { props.form.region = v },
  { emptyValue: '' },
)
const regionCascaderRef = ref(null)
const onRegionCascaderChange = createRegionCascaderAutoClose(regionCascaderRef)
</script>

<style scoped>
.tab-content { margin-bottom: 80px; }
.full-width { width: 100%; }
.contact-group-title { font-weight: 600; font-size: 14px; margin: 12px 0 4px; color: var(--el-text-color-primary); }
.optional-tag { font-size: 12px; color: var(--el-text-color-secondary); font-weight: 400; margin-left: 4px; }
.priority-option { display: flex; flex-direction: column; gap: 2px; line-height: 1.25; }
.priority-option small { color: var(--el-text-color-secondary); font-size: 12px; }
.paste-hint, .upload-hint { margin-bottom: 8px; color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.4; }
.paste-actions { display: flex; justify-content: flex-end; margin-top: 8px; }
.manual-tender-upload { width: 100%; }
.manual-tender-upload :deep(.el-upload) { display: block; width: 100%; }
.manual-tender-upload :deep(.el-upload-dragger) { width: 100%; box-sizing: border-box; }
.manual-tender-upload :deep(.el-upload-list) { width: 100%; }
</style>
