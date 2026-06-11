<template>
  <el-dialog
    v-model="visible"
    :title="editingId ? '编辑资质证书 - ' + form.name : '新增资质证书'"
    width="760px"
    @close="$emit('close')"
    data-testid="qual-form-dialog"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      label-width="100px"
      class="cert-form"
      data-testid="qual-form"
    >
      <el-row :gutter="24">
        <el-col :span="12">
          <div v-if="editingId && props.status" class="edit-status-bar">
            <span class="edit-status-label">当前状态：</span>
            <el-tag :type="statusTagType(props.status)" size="small">{{ statusLabel(props.status) }}</el-tag>
          </div>
          <el-divider content-position="left">基础信息（必填）</el-divider>
          <el-form-item label="证书名称" prop="name">
            <el-input v-model="form.name" maxlength="200" placeholder="如：ISO 9001 质量管理体系认证" data-testid="qf-name" />
          </el-form-item>
          <!-- CO-155 fix: 领域(category) 改为 el-select，不再硬编码 LICENSE -->
          <el-form-item label="领域" prop="category">
            <el-select v-model="form.category" placeholder="选择领域" data-testid="qf-category" style="width:100%">
              <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="等级" prop="level">
            <el-input v-model="form.level" maxlength="50" placeholder="如: AAA级" data-testid="qf-level" />
          </el-form-item>
          <el-form-item label="认证机构" prop="issuer">
            <el-input v-model="form.issuer" maxlength="200" placeholder="发证/认证机构" data-testid="qf-issuer" />
          </el-form-item>
          <el-form-item label="证书编号" prop="certificateNo">
            <el-input v-model="form.certificateNo" maxlength="120" data-testid="qf-certificateNo" />
          </el-form-item>
          <el-form-item label="发证日期" prop="issueDate">
            <div data-testid="qf-issueDate">
              <el-date-picker
                v-model="form.issueDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width:100%"
              />
            </div>
          </el-form-item>
          <el-form-item label="证书有效期" prop="expiryDate">
            <div data-testid="qf-expiryDate">
              <el-date-picker
                v-model="form.expiryDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width:100%"
              />
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-divider content-position="left">补充信息</el-divider>
          <!-- CO-155 fix: 主体类型与名称改为 el-select/可编辑，不再硬编码 COMPANY/西域 -->
          <el-form-item label="主体类型" prop="subjectType">
            <el-select v-model="form.subjectType" placeholder="选择主体类型" data-testid="qf-subjectType" style="width:100%">
              <el-option v-for="s in subjectTypeOptions" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="主体名称" prop="subjectName">
            <el-input v-model="form.subjectName" maxlength="200" placeholder="如：西域数智化投标管理平台" data-testid="qf-subjectName" />
          </el-form-item>
          <el-form-item label="代理机构" prop="agency">
            <el-input v-model="form.agency" maxlength="200" data-testid="qf-agency" />
          </el-form-item>
          <el-form-item label="代理联系方式" prop="agencyContact">
            <el-input v-model="form.agencyContact" maxlength="200" placeholder="手机/固话/邮箱" data-testid="qf-agencyContact" />
          </el-form-item>
          <el-form-item label="认证范围" prop="certScope">
            <el-input
              v-model="form.certScope"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
              data-testid="qf-certScope"
            />
          </el-form-item>
          <el-form-item label="证书审核提醒" prop="certReviewNote">
            <el-input v-model="form.certReviewNote" maxlength="200" placeholder="年审/复核提醒" data-testid="qf-certReviewNote" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-divider content-position="left">证书附件{{ editingId ? '' : '（必填）' }}</el-divider>
      <el-form-item prop="attachment">
        <div class="unified-upload" :class="{ 'has-file': certFile }" v-loading="parsingAi" data-testid="qf-upload-area">
          <template v-if="!certFile && (!editingId || !currentAttachmentName)">
            <el-upload
              ref="certUploadRef"
              action="#"
              drag
              :auto-upload="false"
              :limit="5"
              accept=".pdf,.jpg,.jpeg,.png"
              :on-change="onCertFileSelect"
              :before-upload="() => false"
              :show-file-list="false"
              data-testid="qf-unified-upload"
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">
                拖拽证书扫描件至此处，或 <em>点击上传</em><br />
                <span class="ai-badge">✨ AI 智能提取字段</span>
              </div>
              <template #tip>
                <!-- CO-155 fix: 50MB / 最多 5 个，与 PR 680122945 对齐（d775e0273 误回退到 10MB） -->
                <div class="el-upload__tip">PDF/JPG/PNG，≤50MB，最多5个</div>
              </template>
            </el-upload>
          </template>
          <div v-else class="uploaded-file-card">
            <el-icon :size="20"><Document /></el-icon>
            <span class="file-name">{{ certFile?.name || currentAttachmentName }}</span>
            <el-button link type="primary" size="small" @click="triggerFileSelect">替换</el-button>
            <el-button v-if="certFile" link type="danger" size="small" @click="clearCertFile">移除</el-button>
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" data-testid="qf-submit" @click="handleSubmit">
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch, computed } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { UploadFilled, Document } from '@element-plus/icons-vue'
import http from '@/api/client'
import { useQualFormRules } from './useQualFormRules'
import { qualificationStatusTagTypes, qualificationStatusLabels } from './qualificationMeta.js'
import { QUALIFICATION_CATEGORY_OPTIONS, QUALIFICATION_SUBJECT_TYPE_OPTIONS } from './qualificationFieldOptions.js'
import { useCertAiParser } from './useCertAiParser.js'

const CONTACT_REGEX = /^(1[3-9]\d{9}|(0\d{2,3})[-]?\d{7,8}|[^\s@]+@[^\s@]+\.[^\s@]+)$/
// CO-155 fix: 52428800 = 50MB，与 PR 680122945 对齐
const MAX_ATTACHMENT_BYTES = ref(52428800)

const props = defineProps({ modelValue: Boolean, initialData: Object, status: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue', 'saved', 'close'])

const visible = ref(false)
const certFile = ref(null)
const certUploadRef = ref(null)
const editingId = ref(null)
const formRef = ref(null)

const currentAttachmentName = computed(() => {
  const d = props.initialData
  if (!d) return ''
  return d.fileUrl ? d.fileUrl.split('/').pop() : (d.attachments?.[0]?.fileName || '')
})

const statusLabel = (s) => qualificationStatusLabels[s] || s || '—'
const statusTagType = (s) => qualificationStatusTagTypes[s] || 'info'

const triggerFileSelect = () => {
  certUploadRef.value?.$el?.querySelector('input')?.click()
}

const clearCertFile = () => {
  certFile.value = null
}

const form = reactive({
  name: '', level: '', certificateNo: '', issuer: '',
  issueDate: '', expiryDate: '', agency: '', agencyContact: '',
  certScope: '', certReviewNote: '',
  // CO-155 fix: 改用空字符串占位 + 让 el-select 触发必填校验，不再硬编码 LICENSE/COMPANY/西域
  subjectType: '', subjectName: '', category: ''
})

// CO-155 fix: 引用拆出的静态选项常量（避免本文件超 line-budget 300 行）
const categoryOptions = QUALIFICATION_CATEGORY_OPTIONS
const subjectTypeOptions = QUALIFICATION_SUBJECT_TYPE_OPTIONS

const { rules: formRules } = useQualFormRules(form, certFile, editingId)

// CO-155 refactor: AI 解析逻辑独立为 composable
const submitting = ref(false)
const { parsingAi, onCertFileSelect } = useCertAiParser(MAX_ATTACHMENT_BYTES, certFile, (parsed) => {
  if (parsed.name) form.name = parsed.name
  if (parsed.certificateNo) form.certificateNo = parsed.certificateNo
  if (parsed.issuer) form.issuer = parsed.issuer
  if (parsed.expiryDate) form.expiryDate = parsed.expiryDate
})

watch(() => props.modelValue, (v) => {
  visible.value = v
  if (v) initForm()
})
watch(visible, (v) => emit('update:modelValue', v))

function initForm() {
  const d = props.initialData
  editingId.value = d?.id ?? null
  // CO-155 fix: 编辑取后端真值，新增用空字符串占位（不再硬编码 LICENSE/COMPANY/西域）
  Object.assign(form, blankFormFields(d))
  certFile.value = null
  if (formRef.value && typeof formRef.value.clearValidate === 'function') formRef.value.clearValidate()
}

// CO-155 fix: 把 initForm 中两段重复 Object.assign 抽出，单测时易 mock
function blankFormFields(d) {
  const empty = { name:'', level:'', certificateNo:'', issuer:'', issueDate:'', expiryDate:'', agency:'', agencyContact:'', certScope:'', certReviewNote:'', subjectType:'', subjectName:'', category:'' }
  if (!d?.id) return empty
  return {
    ...empty,
    name: d.name || '', level: d.level || '', certificateNo: d.certificateNo || '',
    issuer: d.issuer || '', issueDate: d.issueDate || '', expiryDate: d.expiryDate || '',
    agency: d.agency || '', agencyContact: d.agencyContact || '',
    certScope: d.certScope || '', certReviewNote: d.certReviewNote || '',
    subjectType: d.subjectType || '', subjectName: d.subjectName || '', category: d.category || ''
  }
}

const handleSubmit = async () => {
  // 4.1.3.1 必填校验：5 个核心字段（用 formRef.validate 自动校验）
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('请完整填写必填项')
    return
  }

  // 二次校验：有效期 > 发证日期（rules 也会校验，但兜底）
  if (form.expiryDate && form.issueDate && form.expiryDate <= form.issueDate) {
    ElMessage.error('证书有效期必须晚于发证日期')
    return
  }
  if (form.agencyContact && !CONTACT_REGEX.test(form.agencyContact)) {
    ElMessage.error('请输入有效的手机号、固话或邮箱')
    return
  }

  // 过滤空字符串 / null 字段，避免后端 enum 解析空串
  const payload = { ...form, fileUrl: '', reminderEnabled: false, reminderDays: 30 }
  for (const k of Object.keys(payload)) {
    if (payload[k] === '' || payload[k] === null || payload[k] === undefined) {
      // 必填字段已被 formRef.validate 拦截，留空的都是非必填 → 移除避免后端 enum 报错
      delete payload[k]
    }
  }
  submitting.value = true
  try {
    if (editingId.value) {
      await http.put(`/api/knowledge/qualifications/${editingId.value}`, payload)
      await uploadCertAttachment(editingId.value)
      ElMessage.success('保存成功')
    } else {
      const resp = await http.post('/api/knowledge/qualifications', payload)
      const newId = resp?.data?.id
      if (newId) await uploadCertAttachment(newId)
      ElMessage.success('新增成功')
    }
    visible.value = false
    emit('saved')
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.response?.data?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

// CO-155 refactor: 抽取附件上传（编辑/新增两条路径共用）。失败静默（不影响主流程保存成功提示）。
async function uploadCertAttachment(qualificationId) {
  if (!qualificationId || !certFile.value) return
  const fd = new FormData()
  fd.append('file', certFile.value)
  await http
    .post(`/api/knowledge/qualifications/${qualificationId}/upload`, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    .catch(() => {})
}

</script>

<style scoped lang="scss">
.edit-status-bar { display:flex; align-items:center; gap:8px; margin-bottom:12px; padding:8px 12px; background:var(--el-fill-color-light); border-radius:6px; border:1px solid var(--el-border-color-lighter); .edit-status-label { color:var(--el-text-color-secondary); font-size:13px } }
.unified-upload { border:1px dashed var(--el-border-color); border-radius:8px; padding:20px; text-align:center; transition:border-color .2s; &:hover { border-color:var(--el-color-primary) } &.has-file { border-style:solid; padding:12px 16px; text-align:left } }
.ai-badge { font-size:12px; color:var(--el-color-primary); background:var(--el-color-primary-light-9); padding:2px 8px; border-radius:10px }
.uploaded-file-card { display:flex; align-items:center; gap:10px; padding:8px 0; .file-name { flex:1; font-size:13px; color:var(--el-text-color-primary); word-break:break-all } }
</style>
