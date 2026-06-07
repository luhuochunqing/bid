<template>
  <el-dialog v-model="modelValue" title="上传资质文件" width="600px">
    <el-form :model="form" label-width="100px">
      <el-form-item label="资质名称" required>
        <el-input v-model="form.name" placeholder="请输入资质名称" />
      </el-form-item>
      <el-form-item label="资质类型" required>
        <el-select v-model="form.type" placeholder="请选择类型">
          <el-option label="企业资质" value="enterprise" />
          <el-option label="人员资质" value="personnel" />
          <el-option label="产品资质" value="product" />
          <el-option label="行业认证" value="industry" />
        </el-select>
      </el-form-item>
      <el-form-item label="归属主体" required>
        <el-select v-model="form.subjectType" placeholder="请选择主体类型">
          <el-option label="公司" value="company" />
          <el-option label="子公司" value="subsidiary" />
        </el-select>
      </el-form-item>
      <el-form-item label="主体名称" required>
        <el-input v-model="form.subjectName" placeholder="请输入公司或子公司名称" />
      </el-form-item>
      <el-form-item label="证书编号">
        <el-input v-model="form.certificateNo" placeholder="请输入证书编号" />
      </el-form-item>
      <el-form-item label="发证机关">
        <el-input v-model="form.issuer" placeholder="请输入发证机关" />
      </el-form-item>
      <el-form-item label="持有人">
        <el-input v-model="form.holderName" placeholder="请输入证书持有人" />
      </el-form-item>
      <el-form-item label="发证日期" required>
        <el-date-picker
          v-model="form.issueDate"
          type="date"
          placeholder="选择日期"
          value-format="YYYY-MM-DD"
        />
      </el-form-item>
      <el-form-item label="有效期至" required>
        <el-date-picker
          v-model="form.expiryDate"
          type="date"
          placeholder="选择日期"
          value-format="YYYY-MM-DD"
        />
      </el-form-item>
      <el-form-item label="上传文件" required>
        <el-upload
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          accept=".pdf,.jpg,.jpeg,.png"
        >
          <el-button :icon="Upload">选择文件</el-button>
          <template #tip>
            <div class="el-upload__tip">
              当前首版先保存附件元数据；如已接入真实文件流，可继续传入下载地址
            </div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="modelValue = false">取消</el-button>
      <el-button type="primary" @click="$emit('confirm')">确认上传</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { Upload } from '@element-plus/icons-vue'

const modelValue = defineModel({ type: Boolean, default: false })
const form = defineModel('form', { type: Object, required: true })

const emit = defineEmits(['confirm', 'file-change'])

function handleFileChange(file) {
  emit('file-change', file?.raw || null)
}
</script>
