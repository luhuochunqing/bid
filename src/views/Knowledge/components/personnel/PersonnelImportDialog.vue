<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="批量导入人员"
    width="560px"
    :close-on-click-modal="false"
    @close="resetAll"
  >
    <div v-if="!task.taskId.value">
      <el-upload
        drag
        :auto-upload="false"
        :limit="1"
        accept=".xlsx"
        :on-change="onFileChange"
        :before-upload="beforeUpload"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">将Excel文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .xlsx 格式，文件大小不能超过 10MB</div>
        </template>
      </el-upload>
      <div v-if="importFile" class="import-file-info">
        <el-icon><Document /></el-icon>
        <span>{{ importFile.name }}</span>
        <el-button type="danger" link size="small" @click="importFile = null">移除</el-button>
      </div>
    </div>

    <div v-else-if="task.isProcessing.value" class="import-progress">
      <el-progress :percentage="task.progressPercent.value" :status="task.progressPercent.value === 100 ? 'success' : ''" />
      <p class="progress-text">{{ task.progressText.value }}</p>
    </div>

    <div v-else-if="task.isCompleted.value" class="import-result">
      <el-result icon="success" title="导入完成">
        <template #sub-title>
          <div class="result-summary">
            <p>成功：{{ task.successCount.value }} 条</p>
            <p v-if="task.failCount.value > 0">失败：{{ task.failCount.value }} 条</p>
          </div>
        </template>
        <template #extra>
          <el-button v-if="task.failCount.value > 0" type="warning" @click="downloadErrorReport">下载错误报告</el-button>
          <el-button type="primary" @click="$emit('update:modelValue', false); $emit('imported')">完成</el-button>
        </template>
      </el-result>
    </div>

    <div v-else-if="task.isFailed.value" class="import-result">
      <el-result icon="error" title="导入失败" :sub-title="task.errorMessage.value">
        <template #extra>
          <el-button type="primary" @click="resetAll">重试</el-button>
        </template>
      </el-result>
    </div>

    <template #footer v-if="!task.taskId.value">
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :disabled="!importFile" :loading="task.active.value" @click="handleStartImport">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Document } from '@element-plus/icons-vue'
import personnelBatchApi from '@/api/modules/personnelBatchApi.js'
import { usePersonnelBatchTask } from './usePersonnelBatchTask.js'

defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue', 'imported'])

const importFile = ref(null)

const task = usePersonnelBatchTask({
  startApi: (file) => personnelBatchApi.startImport(file),
  pollApi: (taskId) => personnelBatchApi.getImportProgress(taskId)
})

function resetAll() {
  importFile.value = null
  task.reset()
}

function onFileChange(file) {
  importFile.value = file.raw
}

function beforeUpload(file) {
  const isXlsx = file.name.toLowerCase().endsWith('.xlsx')
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isXlsx) { ElMessage.error('仅支持 .xlsx 格式'); return false }
  if (!isLt10M) { ElMessage.error('文件大小不能超过 10MB'); return false }
  importFile.value = file
  return false
}

async function handleStartImport() {
  if (!importFile.value) return
  await task.startTask(importFile.value)
}

async function downloadErrorReport() {
  try {
    await personnelBatchApi.downloadErrorReport(task.taskId.value)
    ElMessage.success('错误报告下载成功')
  } catch {
    ElMessage.error('错误报告下载失败')
  }
}
</script>

<style scoped>
.import-file-info { display: flex; align-items: center; gap: 8px; margin-top: 12px; padding: 8px 12px; background: var(--el-fill-color-light); border-radius: 4px; }
.import-progress { text-align: center; padding: 20px 0; }
.progress-text { margin-top: 12px; color: var(--el-text-color-secondary); font-size: 13px; }
.import-result { padding: 20px 0; }
.result-summary p { margin: 4px 0; font-size: 14px; }
</style>
