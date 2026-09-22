<template>
  <el-dialog :model-value="visible" title="上传结果资料" width="560px" @close="$emit('close')">
    <div v-if="target" class="summary">
      <div>{{ target.projectName }}</div>
      <el-tag size="small" :type="target.type === 'notice' ? 'warning' : 'info'">
        {{ target.type === 'notice' ? '中标通知书' : '分析报告' }}
      </el-tag>
    </div>

    <el-form :model="form" label-width="110px">
      <el-form-item label="附件类型">
        <el-select v-model="form.attachmentType" style="width: 100%">
          <el-option label="中标通知书" value="WIN_NOTICE" />
          <el-option label="分析报告" value="LOSS_REPORT" />
        </el-select>
      </el-form-item>
      <el-form-item label="选择文件">
        <el-upload
          :auto-upload="false"
          :limit="1"
          :file-list="form.fileList"
          accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
          @change="handleChange"
          @remove="handleRemove"
        >
          <el-button type="primary" plain>选择文件</el-button>
        </el-upload>
      </el-form-item>
      <el-form-item label="补充说明">
        <el-input v-model="form.comment" type="textarea" :rows="3" placeholder="可填写上传说明" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('close')">取消</el-button>
      <el-button type="primary" :loading="saving" @click="$emit('submit')">上传并回写</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
const form = defineModel('form', { type: Object, required: true })
defineProps({
  visible: Boolean,
  saving: Boolean,
  target: {
    type: Object,
    default: null
  }
})

defineEmits(['close', 'submit'])

const handleChange = (file, fileList) => {
  form.value.file = file.raw || file
  form.value.fileList = fileList.slice(-1)
}

const handleRemove = () => {
  form.value.file = null
  form.value.fileList = []
}
</script>
