<template>
  <div class="inline-preview">
    <div class="preview-header">
      <h2>实时预览</h2>
      <div class="preview-actions">
        <el-select v-model="role" size="small" placeholder="模拟角色" style="width: 120px">
          <el-option label="管理员" value="admin" />
          <el-option label="经理" value="manager" />
          <el-option label="投标专员" value="bid-Team" />
        </el-select>
        <el-button size="small" @click="$emit('open-full-preview')">全屏预览</el-button>
        <el-button size="small" :loading="trialLoading" @click="$emit('trial-submit')">预览提交数据</el-button>
      </div>
    </div>
    <div class="preview-body">
      <!-- tender.entry：按 schema 顺序渲染简单字段 + 联系人固定分组 -->
      <div v-if="isTenderEntry" class="tender-entry-preview">
        <el-form label-width="110px" :disabled="true">
          <el-row :gutter="16">
            <el-col v-for="field in orderedSimpleFields" :key="field.key"
                    v-show="fieldEnabled(field.key)" :span="colSpanOf(field.key)">
              <el-form-item :label="fieldLabel(field.key)" :required="fieldRequired(field.key)">
                <!-- 特殊字段占位预览 -->
                <div v-if="field.key === 'region'" class="preview-placeholder">省市区级联选择</div>
                <div v-else-if="field.key === 'priority'" class="preview-placeholder">优先级下拉（S/A/B/C 级）</div>
                <div v-else-if="field.key === 'deadline' || field.key === 'bidOpeningTime'" class="preview-placeholder">日期时间选择器</div>
                <div v-else-if="field.key === 'customerType' || field.key === 'projectType'" class="preview-placeholder">下拉选择：{{ (field.options || []).map(o => o.label).join(' / ') || '选项' }}</div>
                <div v-else-if="field.key === 'pastedText'" class="preview-placeholder special-field">
                  <div class="preview-hint">[粘贴识别] 或文字输入，系统将智能拆分回填标讯信息</div>
                  <div class="preview-box">多行文本输入框 + "识别粘贴文字"按钮（触发 AI 解析回填）</div>
                </div>
                <div v-else-if="field.key === 'attachments'" class="preview-placeholder special-field">
                  <div class="preview-hint">支持 PDF/Word 文件上传（≤50MB），上传即保存，自动 AI 解析回填表单字段</div>
                  <div class="preview-box">文件上传区域（拖拽或点击选择附件，触发 AI 解析回填）</div>
                </div>
                <!-- 通用字段占位 -->
                <div v-else-if="field.type === 'textarea'" class="preview-placeholder">多行文本输入（{{ field.rows || 3 }} 行）</div>
                <div v-else class="preview-placeholder">{{ field.placeholder || `请输入${field.label}` }}</div>
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 联系人1 固定分组 -->
          <el-row :gutter="16" v-if="hasContact1Enabled">
            <el-col :span="24"><div class="contact-group-title">联系人1</div></el-col>
            <el-col :span="4" v-if="fieldEnabled('contact')"><el-form-item label="姓名" label-width="56px" :required="fieldRequired('contact')"><div class="preview-placeholder">联系人姓名</div></el-form-item></el-col>
            <el-col :span="6" v-if="fieldEnabled('phone')"><el-form-item label="手机号" label-width="64px" :required="fieldRequired('phone')"><div class="preview-placeholder">手机号</div></el-form-item></el-col>
            <el-col :span="7" v-if="fieldEnabled('landline')"><el-form-item label="座机" label-width="56px"><div class="preview-placeholder">座机</div></el-form-item></el-col>
            <el-col :span="7" v-if="fieldEnabled('mail')"><el-form-item label="邮箱" label-width="56px"><div class="preview-placeholder">邮箱</div></el-form-item></el-col>
          </el-row>
          <el-row :gutter="16" v-if="hasContact2Enabled">
            <el-col :span="24"><div class="contact-group-title">联系人2 <span class="optional-tag">选填</span></div></el-col>
            <el-col :span="4" v-if="fieldEnabled('contact2')"><el-form-item label="姓名" label-width="56px"><div class="preview-placeholder">联系人姓名</div></el-form-item></el-col>
            <el-col :span="6" v-if="fieldEnabled('phone2')"><el-form-item label="手机号" label-width="64px"><div class="preview-placeholder">手机号</div></el-form-item></el-col>
            <el-col :span="7" v-if="fieldEnabled('landline2')"><el-form-item label="座机" label-width="56px"><div class="preview-placeholder">座机</div></el-form-item></el-col>
            <el-col :span="7" v-if="fieldEnabled('mail2')"><el-form-item label="邮箱" label-width="56px"><div class="preview-placeholder">邮箱</div></el-form-item></el-col>
          </el-row>
        </el-form>
      </div>
      <!-- 非 tender.entry：走原有动态表单渲染 -->
      <DynamicWorkflowForm v-else :schema="schema" v-model="model" />
    </div>
    <div v-if="trialPayload" class="preview-payload">
      <div class="payload-label">预览提交数据（本地生成，未发送到后端）：</div>
      <pre>{{ trialPayload }}</pre>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import DynamicWorkflowForm from '@/components/common/DynamicWorkflowForm.vue'
import { FIXED_GROUP_KEYS } from '../workflowFormDesignerCore.js'

const props = defineProps({
  schema: { type: Object, required: true },
  trialPayload: { type: String, default: '' },
  trialLoading: { type: Boolean, default: false },
})

const model = defineModel({ type: Object, default: () => ({}) })
const role = defineModel('role', { type: String, default: 'admin' })

defineEmits(['open-full-preview', 'trial-submit'])

// tender.entry scope 判断：schema fields 中包含 purchaser 字段
const isTenderEntry = computed(() => {
  const fields = props.schema?.fields || []
  return fields.some(f => f.key === 'purchaser')
})

// schema 字段列表
const schemaFields = computed(() => props.schema?.fields || [])

// 简单字段列表（按 schema 顺序，过滤掉固定分组）
const orderedSimpleFields = computed(() => {
  return schemaFields.value.filter(f => !FIXED_GROUP_KEYS.includes(f.key))
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
  return field?.label || key
}

// 默认 colSpan 映射（与业务页 TenderBasicInfoTab 保持一致）
const DEFAULT_COL_SPAN = {
  title: 24, region: 12, purchaser: 12, deadline: 12, bidOpeningTime: 12,
  customerType: 12, priority: 12, projectType: 12,
  description: 24, tenderInfo: 24, pastedText: 24, attachments: 24,
}

function colSpanOf(key) {
  return DEFAULT_COL_SPAN[key] || 12
}

const hasContact1Enabled = computed(() =>
  fieldEnabled('contact') || fieldEnabled('phone') || fieldEnabled('landline') || fieldEnabled('mail'),
)
const hasContact2Enabled = computed(() =>
  fieldEnabled('contact2') || fieldEnabled('phone2') || fieldEnabled('landline2') || fieldEnabled('mail2'),
)
</script>

<style scoped>
.inline-preview { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; overflow: hidden; }
.preview-header { display: flex; align-items: center; justify-content: space-between; padding: 10px 16px; background: var(--el-fill-color-light); border-bottom: 1px solid var(--el-border-color-lighter); }
.preview-header h2 { margin: 0; font-size: 14px; font-weight: 600; }
.preview-actions { display: flex; gap: 8px; align-items: center; }
.preview-body { padding: 16px; max-height: 500px; overflow-y: auto; }
.preview-payload { padding: 12px 16px; border-top: 1px solid var(--el-border-color-lighter); background: var(--el-fill-color-light); }
.payload-label { font-size: 12px; font-weight: 600; color: var(--el-text-color-secondary); margin-bottom: 4px; }
.preview-payload pre { margin: 0; font-size: 12px; white-space: pre-wrap; word-break: break-all; max-height: 200px; overflow-y: auto; }
.tender-entry-preview { padding: 4px 0; }
.preview-placeholder { padding: 6px 10px; background: var(--el-fill-color-light); border: 1px dashed var(--el-border-color-lighter); border-radius: 4px; font-size: 12px; color: var(--el-text-color-placeholder); min-height: 24px; }
.preview-placeholder.special-field { padding: 0; border: none; background: transparent; }
.preview-hint { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 4px; }
.preview-box { padding: 8px 12px; background: var(--el-fill-color-light); border: 1px dashed var(--el-border-color-lighter); border-radius: 4px; font-size: 12px; color: var(--el-text-color-placeholder); }
.contact-group-title { font-weight: 600; font-size: 14px; margin: 12px 0 4px; color: var(--el-text-color-primary); }
.optional-tag { font-size: 12px; color: var(--el-text-color-secondary); font-weight: 400; margin-left: 4px; }
</style>
