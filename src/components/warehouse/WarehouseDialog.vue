<template>
  <el-dialog destroy-on-close v-model="visible" :title="editingId ? '编辑仓库' : '新增仓库'" width="800px" :close-on-click-modal="false" @closed="resetForm">
    <el-form ref="formRef" :model="localForm" :rules="rules" label-width="110px">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基础信息" name="basic">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="仓库名称" prop="name" required>
                <el-input v-model="localForm.name" maxlength="200" />
              </el-form-item>
              <el-form-item label="仓库类型" prop="type" required>
                <el-select v-model="localForm.type" popper-class="type-select-popper" style="width:100%">
                  <el-option label="自营" value="SELF_OPERATED" />
                  <el-option label="云仓" value="CLOUD" />
                </el-select>
              </el-form-item>
              <el-form-item label="所属区域" prop="region" required>
                <el-select v-model="localForm.region" style="width:100%">
                  <el-option v-for="r in regions" :key="r" :label="r" :value="r" />
                </el-select>
              </el-form-item>
              <el-form-item label="所在省份" prop="province" required>
                <el-input v-model="localForm.province" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="具体地址" prop="address" required>
                <el-input v-model="localForm.address" maxlength="500" />
              </el-form-item>
              <el-form-item label="仓库面积(㎡)" prop="area" required>
                <el-input v-model.number="localForm.area" type="number" min="0" step="0.01" />
              </el-form-item>
              <el-form-item label="区域联系人" prop="contactPerson" required>
                <el-input v-model="localForm.contactPerson" maxlength="100" />
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="localForm.remarks" type="textarea" :rows="2" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-tab-pane>
        <el-tab-pane label="租约/服务信息" name="lease">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="开始时间" prop="startDate" required>
                <el-date-picker v-model="localForm.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
              </el-form-item>
              <el-form-item label="出租方" prop="lessor" required>
                <el-input v-model="localForm.lessor" maxlength="200" />
              </el-form-item>
              <el-form-item label="承租方" prop="lessee" required>
                <el-input v-model="localForm.lessee" maxlength="200" placeholder="默认西域" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="结束时间" prop="endDate" required>
                <el-date-picker v-model="localForm.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
              </el-form-item>
              <el-form-item label="发票租期">
                <el-input v-model="localForm.invoicePeriod" maxlength="100" placeholder="如: 2025-01-01~2025-03-31" />
              </el-form-item>
              <el-form-item label="关仓计划">
                <el-input v-model="localForm.closePlan" type="textarea" :rows="2" maxlength="500" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-tab-pane>
        <el-tab-pane label="资料核验" name="cert">
          <el-form-item label="有产权证">
            <el-switch v-model="localForm.hasPropertyCert" />
          </el-form-item>
          <el-form-item v-if="localForm.hasPropertyCert" label="产权证附件">
            <el-upload action="#" :auto-upload="false" :file-list="certFiles" accept=".pdf,.jpg,.jpeg,.png"
              :on-change="(file, list) => handleFileChange(file, list, 'PROPERTY_CERTIFICATE')"
              :on-remove="(file, list) => handleFileRemove(file, list, 'PROPERTY_CERTIFICATE')">
              <el-button size="small" type="primary">选择文件</el-button>
              <template #tip><div class="el-upload__tip">仅支持 PDF/JPG/PNG 格式，单文件 ≤ 10MB</div></template>
            </el-upload>
          </el-form-item>

          <el-form-item label="有发票">
            <el-switch v-model="localForm.hasInvoice" />
          </el-form-item>
          <el-form-item v-if="localForm.hasInvoice" label="发票附件">
            <el-upload action="#" :auto-upload="false" :file-list="invoiceFiles" accept=".pdf,.jpg,.jpeg,.png"
              :on-change="(file, list) => handleFileChange(file, list, 'INVOICE')"
              :on-remove="(file, list) => handleFileRemove(file, list, 'INVOICE')">
              <el-button size="small" type="primary">选择文件</el-button>
              <template #tip><div class="el-upload__tip">仅支持 PDF/JPG/PNG 格式，单文件 ≤ 10MB</div></template>
            </el-upload>
          </el-form-item>

          <el-form-item label="有内外照片">
            <el-switch v-model="localForm.hasPhotos" />
          </el-form-item>
          <el-form-item v-if="localForm.hasPhotos" label="内外照片">
            <el-upload action="#" :auto-upload="false" multiple :file-list="photoFiles" accept=".pdf,.jpg,.jpeg,.png"
              :on-change="(file, list) => handleFileChange(file, list, 'PHOTOS')"
              :on-remove="(file, list) => handleFileRemove(file, list, 'PHOTOS')">
              <el-button size="small" type="primary">选择照片</el-button>
              <template #tip><div class="el-upload__tip">仅支持 PDF/JPG/PNG 格式，单张 ≤ 10MB</div></template>
            </el-upload>
          </el-form-item>

          <el-form-item label="核验备注">
            <el-input v-model="localForm.certRemarks" type="textarea" :rows="2" maxlength="500" />
          </el-form-item>
        </el-tab-pane>
      </el-tabs>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/client'

const props = defineProps({
  modelValue: Boolean,
  editingId: { type: Number, default: null },
  form: { type: Object, default: () => ({}) },
  initTab: { type: String, default: 'basic' }
})
const emit = defineEmits(['update:modelValue', 'submitted'])

const visible = computed({ get: () => props.modelValue, set: (v) => emit('update:modelValue', v) })
const formRef = ref()
const submitting = ref(false)
const activeTab = ref('basic')
const regions = ['华东','华北','华南','西南','西北','东北','华中']

const certFiles = ref([])
const invoiceFiles = ref([])
const photoFiles = ref([])
const existingAttachments = ref([])

const defaultForm = () => ({
  name:'', type:'SELF_OPERATED', region:'华东', province:'', address:'', area:0, contactPerson:'', remarks:'',
  startDate:null, endDate:null, lessor:'', lessee:'西域', invoicePeriod:'', closePlan:'',
  hasPropertyCert:false, hasInvoice:false, hasPhotos:false, certRemarks:''
})
const localForm = reactive(defaultForm())

const reqRule = (msg, trg = 'blur') => [{ required: true, message: msg, trigger: trg }]
const rules = {
  name: reqRule('请输入仓库名称'), type: reqRule('请选择仓库类型', 'change'), region: reqRule('请选择所属区域', 'change'),
  province: reqRule('请输入所在省份'), address: reqRule('请输入具体地址'), area: reqRule('请输入仓库面积'),
  contactPerson: reqRule('请输入区域联系人'), startDate: reqRule('请选择开始时间', 'change'), endDate: reqRule('请选择结束时间', 'change'),
  lessor: reqRule('请输入出租方'), lessee: reqRule('请输入承租方')
}

const toUploadFile = (a) => ({
  id: a.id,
  name: a.originalFilename,
  status: 'success',
  rawAttachment: a
})

const loadExistingAttachments = async () => {
  if (!props.editingId) return
  try {
    const { data } = await http.get(`/api/knowledge/warehouses/${props.editingId}/attachments`)
    existingAttachments.value = data || []
    certFiles.value = existingAttachments.value.filter(a => a.type === 'PROPERTY_CERTIFICATE').map(toUploadFile)
    invoiceFiles.value = existingAttachments.value.filter(a => a.type === 'INVOICE').map(toUploadFile)
    photoFiles.value = existingAttachments.value.filter(a => a.type === 'PHOTOS').map(toUploadFile)
  } catch {}
}

watch(() => [props.modelValue, props.form, props.initTab], ([v, f, t]) => {
  if (v) {
    Object.assign(localForm, f)
    activeTab.value = t || 'basic'
    if (props.editingId) {
      loadExistingAttachments()
    } else {
      certFiles.value = []
      invoiceFiles.value = []
      photoFiles.value = []
      existingAttachments.value = []
    }
  }
}, { immediate: true })

const resetForm = () => {
  Object.assign(localForm, defaultForm())
  activeTab.value = 'basic'
  certFiles.value = []
  invoiceFiles.value = []
  photoFiles.value = []
  existingAttachments.value = []
}

const handleFileChange = (file, fileList, type) => {
  const isFormatOk = /\.(pdf|jpg|jpeg|png)$/i.test(file.name)
  if (!isFormatOk) {
    ElMessage.error('仅支持 PDF/JPG/PNG 格式')
    const idx = fileList.indexOf(file)
    if (idx !== -1) fileList.splice(idx, 1)
    return
  }
  const isSizeOk = file.size / 1024 / 1024 <= 10
  if (!isSizeOk) {
    ElMessage.error('附件不能超过 10MB')
    const idx = fileList.indexOf(file)
    if (idx !== -1) fileList.splice(idx, 1)
    return
  }

  if (type === 'PROPERTY_CERTIFICATE') certFiles.value = fileList
  else if (type === 'INVOICE') invoiceFiles.value = fileList
  else if (type === 'PHOTOS') photoFiles.value = fileList
}

const handleFileRemove = async (file, fileList, type) => {
  if (file.id) {
    try {
      await ElMessageBox.confirm(`确认删除已存在的附件「${file.name}」？`, '删除确认', { type: 'warning' })
      await http.delete(`/api/knowledge/warehouses/${props.editingId}/attachments/${file.id}`)
      ElMessage.success('附件已删除')
    } catch {
      loadExistingAttachments()
      return
    }
  }

  if (type === 'PROPERTY_CERTIFICATE') certFiles.value = fileList
  else if (type === 'INVOICE') invoiceFiles.value = fileList
  else if (type === 'PHOTOS') photoFiles.value = fileList
}

const uploadSingleFile = (warehouseId, type, file) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('type', type)
  return http.post(`/api/knowledge/warehouses/${warehouseId}/attachments`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

const uploadNewFiles = async (warehouseId) => {
  const newCerts = certFiles.value.filter(f => !f.id)
  const newInvoices = invoiceFiles.value.filter(f => !f.id)
  const newPhotos = photoFiles.value.filter(f => !f.id)
  const uploadPromises = []
  for (const f of newCerts) if (f.raw) uploadPromises.push(uploadSingleFile(warehouseId, 'PROPERTY_CERTIFICATE', f.raw))
  for (const f of newInvoices) if (f.raw) uploadPromises.push(uploadSingleFile(warehouseId, 'INVOICE', f.raw))
  for (const f of newPhotos) if (f.raw) uploadPromises.push(uploadSingleFile(warehouseId, 'PHOTOS', f.raw))
  if (uploadPromises.length > 0) await Promise.all(uploadPromises)
}

const handleSubmit = async () => {
  try { await formRef.value.validate() } catch { return }
  if (localForm.endDate && localForm.startDate && localForm.endDate <= localForm.startDate) {
    ElMessage.error('结束时间必须晚于开始时间'); return
  }

  // 照片必传校验
  if (localForm.hasPhotos && photoFiles.value.length === 0) {
    ElMessage.error('请至少上传 1 张内外照片')
    return
  }

  submitting.value = true
  try {
    const payload = { ...localForm, area: Number(localForm.area) || 0 }
    if (props.editingId) {
      await http.put(`/api/knowledge/warehouses/${props.editingId}`, payload)
      await uploadNewFiles(props.editingId)
      ElMessage.success('更新成功')
    } else {
      const { data } = await http.post('/api/knowledge/warehouses', payload)
      const newId = data.id
      await uploadNewFiles(newId)
      ElMessage.success('新增成功')
    }
    visible.value = false; emit('submitted')
  } catch (e) { ElMessage.error(e.response?.data?.message || '保存失败') }
  finally { submitting.value = false }
}
</script>
