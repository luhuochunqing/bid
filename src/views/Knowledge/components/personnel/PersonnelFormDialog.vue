<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" :title="isEdit ? '编辑人员' : '新增人员'" width="720px">
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="基础信息" name="basic">
        <el-form ref="formRef" :model="form" label-width="100px">
          <el-form-item label="姓名" required><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="工号" required>
            <el-input v-model="form.employeeNumber" />
            <div v-if="isEmployeeNumberChanged" class="form-warning">⚠️ 修改工号将影响外部对账，请确认必要性</div>
          </el-form-item>
          <el-form-item label="部门"><el-input v-model="form.departmentName" /></el-form-item>
          <el-form-item label="性别">
            <el-select v-model="form.gender" placeholder="请选择" style="width:100%">
              <el-option label="男" value="男" /><el-option label="女" value="女" />
            </el-select>
          </el-form-item>
          <el-form-item label="入职日期"><el-date-picker v-model="form.entryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="出生日期"><el-date-picker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="手机号码"><el-input v-model="form.phone" /></el-form-item>
          <el-form-item label="学历"><el-input v-model="form.education" /></el-form-item>
          <el-form-item label="技术职称"><el-input v-model="form.technicalTitle" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="教育经历" name="education">
        <div v-for="(edu, idx) in form.educations" :key="idx" class="edu-item">
          <el-form-item label="学校名称" required><el-input v-model="edu.schoolName" placeholder="如：清华大学" /></el-form-item>
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="入学时间"><el-date-picker v-model="edu.startDate" type="month" value-format="YYYY-MM" style="width:100%" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="毕业时间"><el-date-picker v-model="edu.endDate" type="month" value-format="YYYY-MM" style="width:100%" /></el-form-item></el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="最高学历">
                <el-select v-model="edu.highestEducation" style="width:100%" placeholder="请选择">
                  <el-option v-for="o in EDUCATION_FORM_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="学习形式">
                <el-select v-model="edu.studyForm" style="width:100%" placeholder="请选择">
                  <el-option v-for="o in STUDY_FORM_SELECT_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="专业"><el-input v-model="edu.major" placeholder="如：计算机科学与技术" /></el-form-item>
          <el-form-item label="最高学历学校"><el-checkbox v-model="edu.isHighestEducationSchool">是否为最高学历学校</el-checkbox></el-form-item>
          <el-button type="danger" size="small" link @click="removeEducation(idx)">删除此教育经历</el-button>
        </div>
        <el-button type="primary" plain size="small" @click="addEducation">+ 添加教育经历</el-button>
        <div v-if="form.educations.length === 0" class="form-warning" style="margin-top:8px;">⚠️ 建议至少保留 1 条教育经历</div>
      </el-tab-pane>
      <el-tab-pane label="证书与职称" name="certificate">
        <div v-for="(cert, idx) in form.certificates" :key="idx" class="cert-item">
          <el-form-item label="证书名称"><el-input v-model="cert.name" placeholder="如：一级建造师" /></el-form-item>
          <el-form-item label="证书编号"><el-input v-model="cert.certificateNumber" /></el-form-item>
          <el-form-item label="证书类型">
            <el-select v-model="cert.type" style="width:100%">
              <el-option v-for="o in CERT_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="到期日期"><el-date-picker v-model="cert.expiryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="职称">
            <el-select v-model="cert.title" placeholder="请选择" style="width:100%">
              <el-option v-for="o in CERT_TITLE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="永久有效"><el-checkbox v-model="cert.isPermanent">永久有效</el-checkbox></el-form-item>
          <el-form-item label="备注"><el-input v-model="cert.remark" maxlength="500" show-word-limit /></el-form-item>
          <el-form-item label="证书附件" required>
            <el-upload :auto-upload="false" :limit="1" accept=".pdf,.jpg,.jpeg,.png" :on-change="(f) => onCertAttachmentChange(f, idx)" :on-remove="() => onCertAttachmentRemove(idx)" :before-upload="beforeCertAttachmentUpload" :show-file-list="false">
              <el-button type="primary" plain size="small">选择附件</el-button>
            </el-upload>
            <div v-if="cert.attachmentName" class="attach-info">{{ cert.attachmentName }} <el-button type="danger" link size="small" @click="onCertAttachmentRemove(idx)">移除</el-button></div>
            <div class="el-upload__tip">仅支持 PDF/JPG/PNG，≤10MB</div>
          </el-form-item>
          <div v-if="cert.id" style="font-size:12px;color:#909399;margin-bottom:4px;">修改附件将替换原文件（原文件软删除）</div>
          <el-button type="danger" size="small" link @click="form.certificates.splice(idx, 1)">删除证书</el-button>
        </div>
        <el-button type="primary" plain size="small" @click="addCertificate">+ 添加证书</el-button>
      </el-tab-pane>
    </el-tabs>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button v-if="!isLastTab" type="primary" @click="goNext">下一步</el-button>
      <el-button v-else type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import personnelApi from '@/api/modules/personnel.js'
import { EDUCATION_FORM_OPTIONS, STUDY_FORM_SELECT_OPTIONS, CERT_TYPE_OPTIONS, CERT_TITLE_OPTIONS } from './personnelConstants.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  personnel: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const activeTab = ref('basic')
const submitting = ref(false)
const TABS = ['basic', 'education', 'certificate']
const isLastTab = computed(() => activeTab.value === TABS[TABS.length - 1])
const isEdit = ref(false)
const originalEmployeeNumber = ref('')
const certAttachmentFiles = ref({})

const defaultForm = () => ({ name: '', employeeNumber: '', departmentName: '', gender: '', entryDate: null, birthDate: null, phone: '', education: '', technicalTitle: '', remark: '', certificates: [], educations: [] })
const form = ref(defaultForm())
const isEmployeeNumberChanged = computed(() => isEdit.value && originalEmployeeNumber.value && form.value.employeeNumber !== originalEmployeeNumber.value)

const validateTab = (tabName) => {
  if (tabName === 'basic') {
    if (!form.value.name || !form.value.employeeNumber) {
      ElMessage.warning('姓名和工号必填')
      return false
    }
    if (form.value.phone && !/^\d{11}$/.test(form.value.phone)) {
      ElMessage.warning('请输入有效的手机号')
      return false
    }
  }
  if (tabName === 'education') {
    if (!form.value.educations?.length) {
      ElMessage.error('请至少添加1条完整的教育经历')
      return false
    }
    for (const e of form.value.educations) {
      if (!e.schoolName || !e.highestEducation || !e.studyForm || !e.startDate || !e.endDate) {
        ElMessage.error('教育经历每条都必须填写学校、最高学历、学习形式、入学/毕业时间')
        return false
      }
    }
  }
  return true
}

const goNext = () => {
  if (!validateTab(activeTab.value)) return
  const idx = TABS.indexOf(activeTab.value)
  if (idx < TABS.length - 1) activeTab.value = TABS[idx + 1]
}

watch(() => props.modelValue, (visible) => {
  if (!visible) return
  if (props.personnel) {
    isEdit.value = true
    form.value = {
      id: props.personnel.id, name: props.personnel.name, employeeNumber: props.personnel.employeeNumber,
      departmentName: props.personnel.departmentName, gender: props.personnel.gender || '',
      entryDate: props.personnel.entryDate || null, birthDate: props.personnel.birthDate || null,
      phone: props.personnel.phone || '', education: props.personnel.education,
      technicalTitle: props.personnel.technicalTitle, remark: props.personnel.remark || '',
      certificates: (props.personnel.certificates || []).map(c => ({ ...c, attachmentName: c.attachmentUrl ? (c.attachmentUrl.split('/').pop() || c.attachmentUrl) : '' })),
      educations: (props.personnel.educations || []).map(e => ({ ...e }))
    }
    originalEmployeeNumber.value = props.personnel.employeeNumber || ''
  } else {
    isEdit.value = false; form.value = defaultForm(); originalEmployeeNumber.value = ''
  }
  activeTab.value = 'basic'; certAttachmentFiles.value = {}
})

function addEducation() { form.value.educations.push({ schoolName: '', startDate: null, endDate: null, highestEducation: '', studyForm: '', major: '', isHighestEducationSchool: false }) }
function removeEducation(idx) { form.value.educations.splice(idx, 1) }
function addCertificate() { form.value.certificates.push({ name: '', certificateNumber: '', type: 'OTHER', expiryDate: null, attachmentName: '', attachmentUrl: '', title: '', isPermanent: false, remark: '' }) }

function beforeCertAttachmentUpload(file) {
  const okType = ['application/pdf', 'image/jpeg', 'image/png'].includes(file.type)
  if (!okType) { ElMessage.error('仅支持 PDF/JPG/PNG'); return false }
  if (file.size / 1024 / 1024 >= 10) { ElMessage.error('附件不能超过10MB'); return false }
  return true
}

function onCertAttachmentChange(uploadFile, idx) {
  const f = uploadFile.raw
  if (!f) return
  form.value.certificates[idx] = form.value.certificates[idx] || {}
  form.value.certificates[idx].attachmentName = f.name
  certAttachmentFiles.value[idx] = f
  form.value.certificates[idx].attachmentUrl = `pending:${f.name}`
}

function onCertAttachmentRemove(idx) {
  if (form.value.certificates[idx]) { form.value.certificates[idx].attachmentName = ''; form.value.certificates[idx].attachmentUrl = '' }
  delete certAttachmentFiles.value[idx]
}

async function uploadPendingCertAttachments(createdPerson) {
  if (!createdPerson?.certificates || !createdPerson.id) return
  for (let i = 0; i < form.value.certificates.length; i++) {
    const localFile = certAttachmentFiles.value[i]; const serverCert = createdPerson.certificates[i]
    if (localFile && serverCert?.id) {
      try { await personnelApi.uploadCertAttachment(createdPerson.id, serverCert.id, localFile) }
      catch { ElMessage.warning(`证书「${form.value.certificates[i].name || '未命名'}」附件上传失败`) }
    }
  }
  certAttachmentFiles.value = {}
}

function highlightNewPerson(empNo) {
  setTimeout(() => {
    document.querySelectorAll('.el-table__row').forEach(row => {
      const noCell = row.querySelector('.emp-no')
      if (noCell && noCell.textContent.trim() === String(empNo)) {
        row.classList.add('new-person-highlight'); setTimeout(() => row.classList.remove('new-person-highlight'), 3000)
      }
    })
  }, 200)
}

async function handleSubmit() {
  form.value.educations = (form.value.educations || []).filter(e => e?.schoolName || e?.highestEducation || e?.studyForm)
  const origCerts = form.value.certificates || []
  form.value.certificates = origCerts.filter(c => c?.name || c?.certificateNumber)
  const newFiles = {}; let newIdx = 0
  origCerts.forEach((c, oldIdx) => { if (c && (c.name || c.certificateNumber) && certAttachmentFiles.value[oldIdx]) { newFiles[newIdx] = certAttachmentFiles.value[oldIdx]; newIdx++ } })
  certAttachmentFiles.value = newFiles

  if (!validateTab('basic') || !validateTab('education')) return
  if ((form.value.certificates || []).some((c, idx) => c?.name && !c.attachmentUrl && !certAttachmentFiles.value[idx])) {
    ElMessage.error('请为已填写的证书上传附件（PDF/JPG/PNG ≤10MB）'); return
  }
  submitting.value = true
  try {
    let result = null
    if (isEdit.value) {
      const res = await personnelApi.update(form.value.id, form.value); const data = res?.data || {}
      result = data.personnel || data
      if (data.warnings?.length) ElMessageBox.alert(data.warnings.join('\n'), '更新成功（含警示）', { type: 'warning', confirmButtonText: '我知道了' })
      else ElMessage.success('更新成功')
    } else { const res = await personnelApi.create(form.value); ElMessage.success('创建成功'); result = res?.data }
    emit('update:modelValue', false)
    if (result && Object.keys(certAttachmentFiles.value).length) await uploadPendingCertAttachments(result)
    emit('saved')
    if (!isEdit.value && result) highlightNewPerson(result.employeeNumber)
  } catch (e) { ElMessage.error(e.message || e?.response?.data?.message || '保存失败') }
  finally { submitting.value = false }
}
</script>

<style scoped>
.cert-item { border: 1px dashed var(--el-border-color-lighter); border-radius: 4px; padding: 12px; margin-bottom: 12px; }
.edu-item { border: 1px dashed var(--el-border-color-lighter); border-radius: 4px; padding: 12px; margin-bottom: 12px; }
.form-warning { color: var(--el-color-warning); font-size: 12px; margin-top: 4px; line-height: 1.4; }
.attach-info { font-size: 12px; color: var(--el-text-color-regular); margin-top: 4px; }
</style>
