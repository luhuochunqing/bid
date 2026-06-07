<template>
  <el-drawer v-model="visible" :title="drawerTitle" size="620px" @close="$emit('close')">
    <el-form ref="formRef" :model="form" label-width="130px" :rules="formRules">
      <el-divider content-position="left">基础信息区</el-divider>
      <el-form-item label="一级产线" prop="productLine">
        <el-select v-model="form.productLine" filterable style="width:100%" placeholder="请选择一级产线">
          <el-option v-for="p in productLineOptions" :key="p.value" :label="p.label" :value="p.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="品牌 ID" prop="brandId">
        <el-input v-model="form.brandId" placeholder="公司内部品牌编号" />
      </el-form-item>
      <el-form-item label="品牌" prop="brandName">
        <el-input v-model="form.brandName" />
      </el-form-item>
      <el-form-item label="进口/国产" prop="importDomestic">
        <el-radio-group v-model="form.importDomestic">
          <el-radio value="进口">进口</el-radio><el-radio value="国产">国产</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="品牌原厂名称" prop="manufacturerName">
        <el-input v-model="form.manufacturerName" placeholder="法人全称" />
      </el-form-item>
      <template v-if="mode !== 'agent'">
        <el-divider content-position="left">授权信息区</el-divider>
        <el-form-item label="授权开始时间" prop="authStartDate">
          <el-date-picker v-model="form.authStartDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="授权结束时间" prop="authEndDate">
          <el-date-picker v-model="form.authEndDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="原厂授权附件">
          <el-upload :auto-upload="false" :file-list="form.authDocFileList"
            :on-change="(f, l) => onFileChange(f, l, 'authDocFileList')"
            :on-remove="(f) => onFileRemove(f, 'authDocFileList')" :before-upload="() => false"
            accept=".pdf,.jpg,.jpeg,.png" multiple drag>
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽或<em>点击上传</em> PDF/JPG/PNG，≤20MB</div>
          </el-upload>
        </el-form-item>
      </template>
      <el-divider content-position="left">补充信息区</el-divider>
      <el-form-item label="备注">
        <el-input v-model="form.remarks" type="textarea" :rows="3" placeholder="授权范围限制说明" />
      </el-form-item>
      <template v-if="mode === 'agent'">
        <el-divider content-position="left">代理商信息</el-divider>
        <el-form-item label="代理商名称" prop="agentName">
          <el-input v-model="form.agentName" placeholder="代理商公司全称" />
        </el-form-item>
        <el-divider content-position="left">授权 1 区（原厂→代理商）</el-divider>
        <el-form-item label="授权1开始时间" prop="auth1StartDate">
          <el-date-picker v-model="form.auth1StartDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="授权1结束时间" prop="auth1EndDate">
          <el-date-picker v-model="form.auth1EndDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="授权1附件">
          <el-upload :auto-upload="false" :file-list="form.auth1FileList"
            :on-change="(f, l) => onFileChange(f, l, 'auth1FileList')"
            :on-remove="(f) => onFileRemove(f, 'auth1FileList')" :before-upload="() => false"
            accept=".pdf,.jpg,.jpeg,.png" multiple drag>
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽或<em>点击上传</em> PDF/JPG/PNG</div>
          </el-upload>
        </el-form-item>
        <el-form-item label="授权1备注">
          <el-input v-model="form.auth1Remarks" type="textarea" :rows="2" placeholder="授权1是否有限制" />
        </el-form-item>
        <el-divider content-position="left">授权 2 区（代理商→西域）</el-divider>
        <el-form-item label="授权2开始时间" prop="auth2StartDate">
          <el-date-picker v-model="form.auth2StartDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="授权2结束时间" prop="auth2EndDate">
          <el-date-picker v-model="form.auth2EndDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="授权2附件">
          <el-upload :auto-upload="false" :file-list="form.auth2FileList"
            :on-change="(f, l) => onFileChange(f, l, 'auth2FileList')"
            :on-remove="(f) => onFileRemove(f, 'auth2FileList')" :before-upload="() => false"
            accept=".pdf,.jpg,.jpeg,.png" multiple drag>
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽或<em>点击上传</em> PDF/JPG/PNG</div>
          </el-upload>
        </el-form-item>
        <el-form-item label="授权2备注">
          <el-input v-model="form.auth2Remarks" type="textarea" :rows="2" placeholder="授权2是否有限制" />
        </el-form-item>
      </template>
      <el-form-item label="补充材料附件">
        <el-upload :auto-upload="false" :file-list="form.supplementaryFileList"
          :on-change="(f, l) => onFileChange(f, l, 'supplementaryFileList')"
          :on-remove="(f) => onFileRemove(f, 'supplementaryFileList')" :before-upload="() => false"
          accept=".pdf,.jpg,.jpeg,.png" multiple drag>
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽或<em>点击上传</em></div>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { PRODUCT_LINE_OPTIONS } from '@/api/modules/brandAuth.js'

const productLineOptions = PRODUCT_LINE_OPTIONS

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  initialData: { type: Object, default: null },
  mode: { type: String, default: 'manufacturer' }
})
const emit = defineEmits(['update:modelValue', 'save', 'close'])

const isEdit = ref(false)

const drawerTitle = computed(() => {
  const prefix = isEdit.value ? '编辑' : '新增'
  return prefix + (props.mode === 'agent' ? '代理商授权' : '原厂授权')
})

const visible = ref(false)
watch(() => props.modelValue, (v) => { visible.value = v; if (v) initForm() })

const editingId = ref(null)
const submitting = ref(false)

const form = ref(getEmptyForm())
const formRules = {
  productLine: [{ required: true, message: '请选择一级产线' }],
  brandId: [{ required: true, message: '请输入品牌ID' }],
  brandName: [{ required: true, message: '请输入品牌' }],
  importDomestic: [{ required: true, message: '请选择进口/国产' }],
  manufacturerName: [{ required: true, message: '请输入品牌原厂名称' }]
}

function getEmptyForm() {
  return {
    productLine: '', brandId: '', brandName: '', importDomestic: '国产',
    manufacturerName: '', authStartDate: null, authEndDate: null, remarks: '',
    agentName: '', auth1StartDate: null, auth1EndDate: null, auth1Remarks: '',
    auth2StartDate: null, auth2EndDate: null, auth2Remarks: '',
    authDocFileList: [], supplementaryFileList: [], auth1FileList: [], auth2FileList: []
  }
}

function initForm() {
  const d = props.initialData
  if (d?.id) {
    isEdit.value = true; editingId.value = d.id
    form.value = {
      productLine: d.productLine, brandId: d.brandId, brandName: d.brandName,
      importDomestic: d.importDomestic, manufacturerName: d.manufacturerName,
      authStartDate: d.authStartDate, authEndDate: d.authEndDate,
      remarks: d.remarks || '',
      agentName: d.agentName || '', auth1StartDate: d.auth1StartDate || null,
      auth1EndDate: d.auth1EndDate || null, auth1Remarks: d.auth1Remarks || '',
      auth2StartDate: d.auth2StartDate || null, auth2EndDate: d.auth2EndDate || null,
      auth2Remarks: d.auth2Remarks || '',
      authDocFileList: [], supplementaryFileList: [], auth1FileList: [], auth2FileList: []
    }
  } else {
    isEdit.value = false; editingId.value = null
    form.value = getEmptyForm()
  }
}

const onFileChange = (file, fileList, field) => { form.value[field] = fileList }
const onFileRemove = (file, field) => { form.value[field] = form.value[field].filter(f => f.uid !== file.uid) }

const handleSubmit = () => {
  const f = form.value
  if (!f.productLine || !f.brandId || !f.brandName || !f.manufacturerName) {
    ElMessage.warning('请填写所有必填项'); return
  }
  if (props.mode === 'agent') {
    if (!f.agentName || !f.auth1StartDate || !f.auth1EndDate || !f.auth2StartDate || !f.auth2EndDate) {
      ElMessage.warning('代理商授权所有时间段为必填项'); return
    }
    if (f.auth1EndDate <= f.auth1StartDate) { ElMessage.error('授权1结束时间须晚于开始时间'); return }
    if (f.auth2EndDate <= f.auth2StartDate) { ElMessage.error('授权2结束时间须晚于开始时间'); return }
    if (f.auth2StartDate < f.auth1StartDate) { ElMessage.error('授权2开始时间不能早于授权1开始时间'); return }
    if (f.auth2EndDate > f.auth1EndDate) { ElMessage.error('授权2结束时间不能晚于授权1结束时间'); return }
    f.authStartDate = f.auth2StartDate
    f.authEndDate = f.auth2EndDate
  } else {
    if (!f.authStartDate || !f.authEndDate) {
      ElMessage.warning('请选择开始和结束时间'); return
    }
    if (f.authEndDate <= f.authStartDate) { ElMessage.error('结束时间须晚于开始时间'); return }
  }
  emit('save', { isEdit: isEdit.value, id: editingId.value, form: { ...f } })
}
</script>
