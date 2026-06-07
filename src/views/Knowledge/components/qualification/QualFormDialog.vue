<template>
  <el-dialog v-model="visible" :title="editingId ? '编辑资质证书 - ' + form.name : '新增资质证书'" width="760px" @close="$emit('close')">
    <div class="ai-upload-area" v-loading="parsingAi">
      <el-upload action="#" drag :auto-upload="false" :on-change="handleFileChange" :show-file-list="false">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽证书扫描件至此处，或 <em>点击上传</em><br /><span class="ai-badge">✨ AI 智能提取</span></div>
      </el-upload>
    </div>
    <el-form ref="formRef" :model="form" label-width="100px" class="cert-form">
      <el-row :gutter="24">
        <el-col :span="12">
          <el-divider content-position="left">基础信息</el-divider>
          <el-form-item label="证书名称"><el-input v-model="form.name" maxlength="200" /></el-form-item>
          <el-form-item label="等级"><el-input v-model="form.level" maxlength="50" placeholder="如: AAA级" /></el-form-item>
          <el-form-item label="认证机构"><el-input v-model="form.issuer" maxlength="200" /></el-form-item>
          <el-form-item label="证书编号"><el-input v-model="form.certificateNo" maxlength="120" /></el-form-item>
          <el-form-item label="发证日期"><el-date-picker v-model="form.issueDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="证书有效期"><el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-divider content-position="left">补充信息</el-divider>
          <el-form-item label="代理机构"><el-input v-model="form.agency" maxlength="200" /></el-form-item>
          <el-form-item label="代理联系方式"><el-input v-model="form.agencyContact" maxlength="200" placeholder="手机/固话/邮箱" /></el-form-item>
          <el-form-item label="认证范围"><el-input v-model="form.certScope" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
          <el-form-item label="证书审核提醒"><el-input v-model="form.certReviewNote" maxlength="200" placeholder="年审/复核提醒" /></el-form-item>
        </el-col>
      </el-row>
      <el-divider content-position="left">附件</el-divider>
      <el-form-item label="证书附件">
        <el-upload :auto-upload="false" :limit="1" accept=".pdf,.jpg,.jpeg,.png"
          :on-change="onCertFileChange" :on-remove="() => certFile = null" :before-upload="() => false">
          <el-button type="primary" plain>选择文件</el-button>
          <template #tip><div class="el-upload__tip">仅支持 PDF/JPG/PNG，≤10MB</div></template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import http from '@/api/client'

const CONTACT_REGEX = /^(1[3-9]\d{9}|(0\d{2,3})[-]?\d{7,8}|[^\s@]+@[^\s@]+\.[^\s@]+)$/

const props = defineProps({ modelValue: Boolean, initialData: Object })
const emit = defineEmits(['update:modelValue', 'saved', 'close'])

const visible = ref(false); const parsingAi = ref(false); const certFile = ref(null)
const editingId = ref(null)

const form = reactive({
  name: '', level: '', certificateNo: '', issuer: '', holderName: '',
  issueDate: '', expiryDate: '', agency: '', agencyContact: '',
  certScope: '', certReviewNote: '', subjectType: 'COMPANY', subjectName: '西域', category: 'LICENSE'
})

watch(() => props.modelValue, (v) => { visible.value = v; if (v) initForm() })
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
      certScope: '', certReviewNote: '', subjectType: 'COMPANY', subjectName: '西域', category: 'LICENSE'
    })
  }
  certFile.value = null
}

const onCertFileChange = (f) => {
  if (f.raw?.size > 10485760) { ElMessage.error('附件不能超过10MB'); return }
  certFile.value = f.raw
}

const handleSubmit = async () => {
  if (!form.name || !form.issuer || !form.certificateNo || !form.issueDate || !form.expiryDate || !form.agency || !form.agencyContact || !form.certScope) {
    ElMessage.warning('请填写所有必填项'); return
  }
  if (form.expiryDate <= form.issueDate) { ElMessage.error('证书有效期必须晚于发证日期'); return }
  if (form.agencyContact && !CONTACT_REGEX.test(form.agencyContact)) {
    ElMessage.error('请输入有效的手机号、固话或邮箱'); return
  }
  const payload = { ...form, fileUrl: '', reminderEnabled: false, reminderDays: 30 }
  try {
    if (editingId.value) {
      await http.put(`/api/knowledge/qualifications/${editingId.value}`, payload)
      ElMessage.success('保存成功')
    } else {
      const { data } = await http.post('/api/knowledge/qualifications', payload)
      if (data.code === 200 || data.code === 201) {
        if (certFile.value) {
          const fd = new FormData(); fd.append('file', certFile.value)
          await http.post(`/api/knowledge/qualifications/${data.data.id}/upload`, fd, { headers: { 'Content-Type': 'multipart/form-data' } }).catch(() => {})
        }
        ElMessage.success('新增成功')
      }
    }
    visible.value = false; emit('saved')
  } catch (e) { ElMessage.error(e.response?.data?.message || '保存失败') }
}

const handleFileChange = async (uploadFile) => {
  if (!uploadFile.raw) return
  parsingAi.value = true; ElMessage.info('AI 正在全息解析证书内容...')
  const fd = new FormData(); fd.append('file', uploadFile.raw)
  try {
    const { data } = await http.post('/api/knowledge/qualifications/upload-parse', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    if (data.code === 200) {
      const p = data.data
      if (p.name) form.name = p.name; if (p.certificateNo) form.certificateNo = p.certificateNo
      if (p.issuer) form.issuer = p.issuer; if (p.holderName) form.holderName = p.holderName
      if (p.expiryDate) form.expiryDate = p.expiryDate
      ElNotification({ title: 'AI 提取成功', message: '已自动填入证书特征与有效期等字段', type: 'success' })
    }
  } catch { ElMessage.warning('AI解析失败，您可以手动填写') }
  finally { parsingAi.value = false }
}
</script>
