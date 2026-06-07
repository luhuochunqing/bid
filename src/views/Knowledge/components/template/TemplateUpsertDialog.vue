<template>
  <el-dialog
    v-model="visible"
    :title="mode === 'create' ? '新建模板' : '编辑模板'"
    width="720px"
    destroy-on-close
  >
    <el-alert
      v-if="submitError"
      :title="submitError"
      type="error"
      show-icon
      class="submit-error"
      :closable="false"
    />
    <el-form :model="form" label-width="110px">
      <el-form-item label="模板名称" required :error="errors.name">
        <el-input v-model="form.name" aria-label="模板名称表单" placeholder="请输入模板名称" />
      </el-form-item>
      <el-form-item label="历史大类">
        <el-select v-model="form.category" aria-label="历史大类" placeholder="选择历史大类" style="width: 100%">
          <el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="产品类型" required :error="errors.productType">
        <el-select v-model="form.productType" aria-label="产品类型" placeholder="选择产品类型" style="width: 100%">
          <el-option v-for="option in productTypeOptions" :key="option" :label="option" :value="option" />
        </el-select>
      </el-form-item>
      <el-form-item label="行业" required :error="errors.industry">
        <el-select v-model="form.industry" aria-label="行业" placeholder="选择行业" style="width: 100%">
          <el-option v-for="option in industryOptions" :key="option" :label="option" :value="option" />
        </el-select>
      </el-form-item>
      <el-form-item label="文档类型" required :error="errors.documentType">
        <el-select v-model="form.documentType" aria-label="文档类型" placeholder="选择文档类型" style="width: 100%">
          <el-option v-for="option in documentTypeOptions" :key="option" :label="option" :value="option" />
        </el-select>
      </el-form-item>
      <el-form-item label="标签">
        <el-input
          v-model="form.tagsText"
          aria-label="标签"
          placeholder="多个标签请用中文逗号分隔"
        />
      </el-form-item>
      <el-form-item label="文件大小">
        <el-input v-model="form.fileSize" aria-label="文件大小" placeholder="例如 1.8 MB" />
      </el-form-item>
      <el-form-item label="文件地址">
        <el-input v-model="form.fileUrl" aria-label="文件地址" placeholder="可选：真实模板文件地址" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="4"
          aria-label="描述"
          placeholder="请输入模板描述"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="$emit('submit')">
        {{ mode === 'create' ? '创建模板' : '保存' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
const visible = defineModel('visible', { type: Boolean, default: false })
const form = defineModel('form', { type: Object, required: true })
defineProps({
  mode: { type: String, default: 'create' },
  errors: {
    type: Object,
    default: () => ({
      name: '',
      productType: '',
      industry: '',
      documentType: ''
    })
  },
  submitError: { type: String, default: '' },
  categoryOptions: { type: Array, default: () => [] },
  productTypeOptions: { type: Array, default: () => [] },
  industryOptions: { type: Array, default: () => [] },
  documentTypeOptions: { type: Array, default: () => [] },
  submitting: { type: Boolean, default: false }
})

defineEmits(['submit'])
</script>

<style scoped>
.submit-error {
  margin-bottom: 16px;
}
</style>
