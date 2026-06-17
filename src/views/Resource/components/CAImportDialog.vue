<template>
  <el-dialog v-model="visible" title="批量导入CA证书" width="560px" :close-on-click-modal="false" destroy-on-close>
    <!-- 初始态 -->
    <div v-if="!taskId" class="import-init">
      <el-alert title="请先下载模板，按格式填写后上传" type="info" :closable="false" show-icon style="margin-bottom:16px" />
      <div style="margin-bottom:16px">
        <el-button @click="downloadTemplate">
          <el-icon><Download /></el-icon> 下载批量导入模板
        </el-button>
      </div>
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".xlsx"
        :file-list="fileList"
        :on-change="handleFileChange"
        :on-remove="() => fileList = []"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将 Excel 文件拖到此处，或<em>点击上传</em></div>
        <template #tip><div class="el-upload__tip">仅支持 .xlsx 格式，≤10MB</div></template>
      </el-upload>
    </div>

    <!-- 执行中 -->
    <div v-else-if="status === 'PENDING' || status === 'VALIDATING' || status === 'IMPORTING'" class="import-progress">
      <el-progress :percentage="progressPct" :stroke-width="12" striped :striped-flow="true" />
      <p class="status-text">{{ statusText }}</p>
    </div>

    <!-- 完成态 -->
    <div v-else-if="status === 'COMPLETED'" class="import-done">
      <el-result icon="success" title="导入完成">
        <template #sub-title>
          <p>共 {{ task.totalRows }} 条，成功 {{ task.importedRows }} 条，失败 {{ task.invalidRows }} 条</p>
        </template>
      </el-result>
      <div v-if="task.errorDetails" class="error-detail">
        <div class="detail-title">错误明细</div>
        <pre class="error-text">{{ task.errorDetails }}</pre>
      </div>
    </div>

    <!-- 失败态 -->
    <div v-else-if="status === 'FAILED'" class="import-failed">
      <el-result icon="error" title="导入失败" :sub-title="task?.errorDetails || '未知错误'" />
    </div>

    <template #footer>
      <el-button @click="visible = false">{{ taskId ? '关闭' : '取消' }}</el-button>
      <el-button v-if="!taskId" type="primary" :disabled="!fileList.length" @click="startImport">
        开始导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { caApi } from '@/api/modules/ca.js'
import http from '@/api/client'

const props = defineProps({
  modelValue: Boolean
})
const emit = defineEmits(['update:modelValue', 'imported'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const fileList = ref([])
const taskId = ref(null)
const status = ref('')
const task = ref({})
let pollTimer = null

const progressPct = computed(() => {
  if (status.value === 'PENDING') return 10
  if (status.value === 'VALIDATING') return 30
  if (status.value === 'IMPORTING') return 70
  return 0
})

const statusText = computed(() => {
  if (status.value === 'PENDING') return '任务排队中...'
  if (status.value === 'VALIDATING') return '正在校验数据...'
  if (status.value === 'IMPORTING') return '正在导入数据...'
  return ''
})

const handleFileChange = (file) => {
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    ElMessage.error('仅支持 .xlsx 格式')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件不能超过 10MB')
    return
  }
  fileList.value = [file]
}

const stopPolling = () => {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

const startPolling = () => {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const res = await caApi.getImportTask(taskId.value)
      const data = res?.data || res
      status.value = data.status
      task.value = data
      if (data.status === 'COMPLETED' || data.status === 'FAILED') {
        stopPolling()
        if (data.status === 'COMPLETED') emit('imported')
      }
    } catch {
      stopPolling()
    }
  }, 2000)
}

const downloadTemplate = async () => {
  try {
    const res = await http.get('/api/ca-certificates/template', { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res]))
    const a = document.createElement('a')
    a.href = url
    a.download = 'CA证书导入模板.xlsx'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    console.error('下载模板失败', e)
    ElMessage.error('下载模板失败')
  }
}

const startImport = async () => {
  if (!fileList.value.length) return
  try {
    const res = await caApi.importFile(fileList.value[0].raw)
    taskId.value = res?.data?.taskId
    status.value = 'PENDING'
    startPolling()
  } catch (e) {
    ElMessage.error('导入请求失败')
  }
}

onBeforeUnmount(() => stopPolling())
</script>

<style scoped>
.import-progress { text-align: center; padding: 20px 0; }
.status-text { margin-top: 12px; color: var(--el-text-color-secondary); }
.error-detail { margin-top: 16px; }
.detail-title { font-weight: 600; margin-bottom: 8px; }
.error-text { max-height: 200px; overflow-y: auto; background: var(--el-fill-color-light); padding: 12px; border-radius: 4px; font-size: 12px; white-space: pre-wrap; }
</style>
