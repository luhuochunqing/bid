<template>
  <el-dialog v-model="visible" title="上传交付物" width="500px">
    <el-form :model="form" label-width="80px">
      <el-form-item label="交付物名称">
        <el-input v-model="form.name" placeholder="请输入交付物名称" />
      </el-form-item>
      <el-form-item label="交付物类型">
        <el-select v-model="form.type" placeholder="请选择类型">
          <el-option label="文档" value="document" />
          <el-option label="资质文件" value="qualification" />
          <el-option label="技术方案" value="technical" />
          <el-option label="报价单" value="quotation" />
          <el-option label="其他" value="other" />
        </el-select>
      </el-form-item>
      <el-form-item label="上传文件">
        <el-upload
          class="upload-demo"
          drag
          action="#"
          :auto-upload="false"
          :on-change="handleFileChange"
          :file-list="fileList"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 doc/docx/pdf/xls/xlsx 格式</div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!form.name || !form.file" @click="handleSave">
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  task: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'save'])

const visible = ref(false)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => { emit('update:modelValue', v) })

const form = reactive({ name: '', type: '', file: null })
const fileList = ref([])

watch(() => props.task, () => {
  form.name = ''
  form.type = ''
  form.file = null
  fileList.value = []
})

function handleFileChange(file, list) {
  fileList.value = list
  form.file = file.raw || file
}

function handleSave() {
  if (!form.name || !form.file) return
  emit('save', { ...form })
  visible.value = false
}
</script>

<style scoped>
.el-icon--upload { font-size: 67px; color: var(--el-color-primary); margin-bottom: 16px; }
.el-upload__text { color: var(--el-text-color-regular); }
.el-upload__tip { color: var(--el-text-color-secondary); margin-top: 8px; }
</style>
