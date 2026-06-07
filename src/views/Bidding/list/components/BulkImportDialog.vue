<template>
  <el-dialog
    v-model="modelValue"
    title="批量导入标讯"
    width="720px"
    :close-on-click-modal="false"
    @close="$emit('reset')"
  >
    <div class="bulk-import-tips">
      <p>· 仅支持 <strong>.xlsx</strong> 模板，单次最多 <strong>500</strong> 行，文件大小不超过 <strong>5MB</strong>。</p>
      <p>· 请先点击「下载批量导入模板」获取最新模板，按字典参考填写后再上传。</p>
      <p>· 任意一行校验失败会整批回滚，错误信息会在下方表格逐行展示。</p>
    </div>

    <el-upload
      class="bulk-import-upload"
      drag
      :auto-upload="false"
      :show-file-list="false"
      :accept="accept"
      :on-change="onFileChange"
    >
      <el-icon class="el-icon--upload"><Upload /></el-icon>
      <div class="el-upload__text">
        {{ selectedFile ? selectedFile.name : '将 .xlsx 文件拖到此处，或点击选择文件' }}
      </div>
    </el-upload>

    <div v-if="result" class="bulk-import-result">
      <el-alert
        v-if="result.failureCount > 0"
        type="error"
        :closable="false"
        :title="`共 ${result.totalRows} 行，失败 ${result.failureCount} 行（已整批回滚，未写入任何数据）`"
      />
      <el-alert
        v-else
        type="success"
        :closable="false"
        :title="`共 ${result.totalRows} 行全部导入成功`"
      />
      <el-table
        v-if="result.failureCount > 0"
        :data="result.errors"
        size="small"
        class="bulk-import-error-table"
        max-height="320"
      >
        <el-table-column prop="row" label="行号" width="80" />
        <el-table-column prop="field" label="字段" width="160" />
        <el-table-column prop="message" label="错误说明" />
      </el-table>
    </div>

    <template #footer>
      <el-button @click="modelValue = false">取消</el-button>
      <el-button :loading="templateDownloading" @click="$emit('download-template')">
        <el-icon><Download /></el-icon>
        下载批量导入模板
      </el-button>
      <el-button
        type="primary"
        :loading="importing"
        :disabled="!selectedFile"
        @click="$emit('submit')"
      >
        开始导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { Download, Upload } from '@element-plus/icons-vue'

const modelValue = defineModel({ type: Boolean, default: false })

defineProps({
  selectedFile: { type: Object, default: null },
  result: { type: Object, default: null },
  templateDownloading: { type: Boolean, default: false },
  importing: { type: Boolean, default: false },
  accept: { type: String, default: '.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' },
})

const emit = defineEmits(['reset', 'download-template', 'submit', 'file-change'])

const onFileChange = (file) => emit('file-change', file)
</script>

<style scoped>
.bulk-import-tips {
  margin-bottom: 12px;
  padding: 12px 16px;
  background: var(--bg-subtle);
  border-radius: 6px;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.6;
}

.bulk-import-tips p {
  margin: 0;
}

.bulk-import-upload {
  width: 100%;
}

.bulk-import-upload :deep(.el-upload),
.bulk-import-upload :deep(.el-upload-dragger) {
  width: 100%;
  box-sizing: border-box;
}

.bulk-import-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
}

.bulk-import-error-table {
  width: 100%;
}
</style>
