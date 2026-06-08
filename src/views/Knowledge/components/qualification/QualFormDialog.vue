<template>
  <el-dialog
    v-model="visible"
    :title="editingId ? '编辑资质证书 - ' + form.name : '新增资质证书'"
    width="760px"
    @close="$emit('close')"
    data-testid="qual-form-dialog"
  >
    <div class="ai-upload-area" v-loading="parsingAi" data-testid="qual-form-ai-area">
      <el-upload
        action="#"
        drag
        :auto-upload="false"
        :on-change="handleFileChange"
        :show-file-list="false"
        data-testid="qual-form-ai-upload"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          拖拽证书扫描件至此处，或 <em>点击上传</em><br /><span class="ai-badge">✨ AI 智能提取</span>
        </div>
      </el-upload>
    </div>
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
          <el-form-item label="持证人" prop="holderName">
            <el-input v-model="form.holderName" maxlength="120" placeholder="证书持有人姓名" data-testid="qf-holderName" />
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
      <el-divider content-position="left">附件{{ editingId ? '' : '（必填）' }}</el-divider>
      <el-form-item label="证书附件" prop="attachment">
        <div v-if="editingId && currentAttachmentName && !certFile" class="current-attachment">
          <el-icon><Document /></el-icon>
          <span class="att-name">{{ currentAttachmentName }}</span>
          <el-button link type="primary" size="small" @click="triggerFileSelect">替换</el-button>
        </div>
        <el-upload
          v-else
          ref="certUploadRef"
          :auto-upload="false"
          :limit="1"
          accept=".pdf,.jpg,.jpeg,.png"
          :on-change="onCertFileChange"
          :on-remove="() => certFile = null"
          :before-upload="() => false"
          :file-list="certFileList"
          data-testid="qf-attachment-upload"
        >
          <el-button type="primary" plain>{{ editingId ? '选择新文件' : '选择文件' }}</el-button>
          <template #tip><div class="el-upload__tip">仅支持 PDF/JPG/PNG，≤10MB</div></template>
        </el-upload>
        <div v-if="certFile" class="new-file-tag">
          <el-tag closable size="small" type="info" @close="clearCertFile">{{ certFile.name }}</el-tag>
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

const CONTACT_REGEX = /^(1[3-9]\d{9}|(0\d{2,3})[-]?\d{7,8}|[^\s@]+@[^\s@]+\.[^\s@]+)$/

const props = defineProps({ modelValue: Boolean, initialData: Object, status: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue', 'saved', 'close'])

const visible = ref(false)
const parsingAi = ref(false)
const submitting = ref(false)
const certFile = ref(null)
const certUploadRef = ref(null)
const certFileList = ref([])
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
  certFileList.value = []
}

const form = reactive({
  name: '', level: '', certificateNo: '', issuer: '', holderName: '',
  issueDate: '', expiryDate: '', agency: '', agencyContact: '',
  certScope: '', certReviewNote: '',
  subjectType: 'COMPANY', subjectName: '西域', category: 'LICENSE'
})

const { rules: formRules } = useQualFormRules(form, certFile, editingId)

watch(() => props.modelValue, (v) => {
  visible.value = v
  if (v) initForm()
})
watch(visible, (v) => emit('update:modelValue', v))

function initForm() {
  const d = props.initialData
  if (d?.id) {
    editingId.value = d.id
    Object.assign(form, {
      name: d.name || '', level: d.level || '', certificateNo: d.certificateNo || '',
      issuer: d.issuer || '', holderName: d.holderName || '',
      issueDate: d.issueDate || '', expiryDate: d.expiryDate || '',
      agency: d.agency || '', agencyContact: d.agencyContact || '',
      certScope: d.certScope || '', certReviewNote: d.certReviewNote || '',
      subjectType: 'COMPANY', subjectName: '西域', category: 'LICENSE'
    })
  } else {
    editingId.value = null
    Object.assign(form, {
      name: '', level: '', certificateNo: '', issuer: '', holderName: '',
      issueDate: '', expiryDate: '', agency: '', agencyContact: '',
      certScope: '', certReviewNote: '',
      subjectType: 'COMPANY', subjectName: '西域', category: 'LICENSE'
    })
  }
  certFile.value = null
  certFileList.value = []
  // 清空 formRef 校验状态
  if (formRef.value && typeof formRef.value.clearValidate === 'function') formRef.value.clearValidate()
}

const onCertFileChange = (f) => {
  if (f.raw?.size > 10485760) { ElMessage.error('附件不能超过10MB'); certFileList.value = []; return }
  certFile.value = f.raw
  certFileList.value = [{ name: f.raw.name, uid: f.raw.name + Date.now() }]
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
      if (certFile.value) {
        const fd = new FormData(); fd.append('file', certFile.value)
        await http.post(`/api/knowledge/qualifications/${editingId.value}/upload`, fd, { headers: { 'Content-Type': 'multipart/form-data' } }).catch(() => {})
      }
      ElMessage.success('保存成功')
    } else {
      const resp = await http.post('/api/knowledge/qualifications', payload)
      const newId = resp?.data?.id
      if (newId && certFile.value) {
        const fd = new FormData(); fd.append('file', certFile.value)
        await http
          .post(`/api/knowledge/qualifications/${newId}/upload`, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
          .catch(() => {})
      }
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

const handleFileChange = async (uploadFile) => {
  if (!uploadFile.raw) return
  parsingAi.value = true
  ElMessage.info('AI 正在全息解析证书内容...')
  const fd = new FormData(); fd.append('file', uploadFile.raw)
  try {
    const resp = await http.post('/api/knowledge/qualifications/upload-parse', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (resp?.code === 200) {
      const p = resp.data
      if (p.name) form.name = p.name
      if (p.certificateNo) form.certificateNo = p.certificateNo
      if (p.issuer) form.issuer = p.issuer
      if (p.holderName) form.holderName = p.holderName
      if (p.expiryDate) form.expiryDate = p.expiryDate
      ElNotification({ title: 'AI 提取成功', message: '已自动填入证书特征与有效期等字段', type: 'success' })
    }
  } catch {
    ElMessage.warning('AI解析失败，您可以手动填写')
  } finally {
    parsingAi.value = false
  }
}
</script>

<style scoped lang="scss">
.edit-status-bar { display:flex; align-items:center; gap:8px; margin-bottom:12px; padding:8px 12px; background:var(--el-fill-color-light); border-radius:6px; border:1px solid var(--el-border-color-lighter); .edit-status-label { color:var(--el-text-color-secondary); font-size:13px } }
.current-attachment { display:flex; align-items:center; gap:8px; padding:8px 12px; background:var(--el-fill-color-light); border-radius:6px; border:1px solid var(--el-border-color-lighter); .att-name { flex:1; color:var(--el-text-color-primary); font-size:13px; word-break:break-all } }
.new-file-tag { margin-top:8px }
</style>
