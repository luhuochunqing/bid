<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="批量导入仓库"
    width="640px"
    :close-on-click-modal="false"
    :before-close="handleClose"
    data-testid="warehouse-import-dialog"
  >
    <div v-if="!taskId" class="import-init">
      <el-alert title="请按模板填写 Excel 上传；如模板中标「是」的资料，请按规范命名附件一起上传" :closable="false" type="info" show-icon />
      <div class="template-download" style="margin-top:12px">
        <el-link type="primary" :underline="false" @click="$emit('download-template')">
          <el-icon><DocumentCopy /></el-icon> 下载导入模板
        </el-link>
      </div>
      <el-form label-width="120px" style="margin-top:12px">
        <el-form-item label="选择 Excel 文件" required>
          <el-upload
            ref="excelUploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".xlsx,.xls"
            :on-change="handleExcelChange"
            :on-exceed="handleExcelExceed"
            drag
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="el-upload__text">将 .xlsx 文件拖至此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">仅支持 .xlsx，文件不超过 10MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="附件文件（可选）">
          <el-upload
            ref="attachUploadRef"
            :auto-upload="false"
            multiple
            :limit="100"
            :on-change="handleAttachChange"
            :on-exceed="handleAttachExceed"
            :on-remove="handleAttachRemove"
            drag
          >
            <el-icon class="upload-icon"><Files /></el-icon>
            <div class="el-upload__text">命名规范：WH_{仓库名称}_{附件类型}.{扩展名}</div>
            <div class="el-upload__sub">支持一次拖入多个文件；将与 Excel 中的仓库名称、附件类型自动匹配</div>
            <template #tip>
              <div class="el-upload__tip">命名匹配成功后才归档；不匹配将忽略</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="attachCount > 0 || excelFile"
        :title="`已选择：${excelFile ? excelFile.name : '未选 Excel'} | 附件 ${attachCount} 个`"
        type="success"
        :closable="false"
        show-icon
        style="margin-top:8px"
      />
    </div>
    <div v-else class="import-task">
      <div v-if="status === 'PENDING' || status === 'VALIDATING' || status === 'IMPORTING'" class="import-progress">
        <el-progress :percentage="statusPercent" :stroke-width="12" striped :striped-flow="true" />
        <p class="status-text">{{ statusText }}</p>
      </div>
      <div v-else-if="status === 'COMPLETED'" class="import-done">
        <el-result
          :icon="invalidRows > 0 ? 'warning' : 'success'"
          :title="invalidRows > 0 ? '部分导入成功' : '导入完成'"
          :sub-title="`成功 ${importedRows} 条 | 失败 ${invalidRows} 条`"
        >
          <template #extra>
            <el-button v-if="hasCorrectionFile" type="primary" @click="handleDownloadCorrection">
              <el-icon><Download /></el-icon> 下载修正文件
            </el-button>
            <el-button @click="handleClose">关闭</el-button>
          </template>
        </el-result>
        <div v-if="(attachedCount > 0 || unmatchedFiles.length > 0)" class="report-block">
          <div class="block-title">📎 仓库附件关联</div>
          <div class="block-stats">
            <el-tag type="success" size="default">成功关联 {{ attachedCount }}</el-tag>
            <el-tag :type="unmatchedFiles.length > 0 ? 'warning' : 'info'" size="default">
              未匹配 {{ unmatchedFiles.length }}
            </el-tag>
          </div>
          <el-table v-if="unmatchedFiles.length > 0" :data="unmatchedFiles" size="small" style="margin-top:8px">
            <el-table-column prop="filename" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column prop="reason" label="未匹配原因" min-width="160" />
          </el-table>
        </div>
        <div v-if="errorDetails" class="error-details">
          <div class="error-title">📥 仓库信息导入 — 失败明细（前 200 行）：</div>
          <pre>{{ truncatedErrors }}</pre>
        </div>
      </div>
      <div v-else-if="status === 'FAILED'" class="import-failed">
        <el-result icon="error" title="导入失败" :sub-title="failureReason || '未知原因'">
          <template #extra>
            <el-button @click="handleRetry">重新上传</el-button>
          </template>
        </el-result>
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button v-if="!taskId" @click="handleClose">取消</el-button>
        <el-button v-if="!taskId" type="primary" :disabled="!excelFile" :loading="submitting" @click="startImport">开始导入</el-button>
        <el-button v-else-if="status === 'PENDING' || status === 'VALIDATING' || status === 'IMPORTING'" @click="handleClose">取消</el-button>
        <el-button v-else-if="status === 'COMPLETED' || status === 'FAILED'" @click="handleClose">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Files, Download, DocumentCopy } from '@element-plus/icons-vue'
import http from '@/api/client'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'imported', 'download-template'])

const taskId = ref(null)
const status = ref('')
const totalRows = ref(0)
const validRows = ref(0)
const invalidRows = ref(0)
const importedRows = ref(0)
const attachedCount = ref(0)
const unmatchedFiles = ref([])
const hasCorrectionFile = ref(false)
const correctionFileUrl = ref('')
const errorDetails = ref('')
const failureReason = ref('')
const submitting = ref(false)
const excelFile = ref(null)
const attachFiles = ref([])
let pollTimer = null

const excelUploadRef = ref()
const attachUploadRef = ref()

const attachCount = computed(() => attachFiles.value.length)
const statusPercent = computed(() => ({ PENDING:15, VALIDATING:45, IMPORTING:80 }[status.value] || 100))
const statusText = computed(() => ({ PENDING:'任务排队中...', VALIDATING:'正在校验 Excel...', IMPORTING:'正在写入数据库...' }[status.value] || ''))
const truncatedErrors = computed(() => errorDetails.value ? errorDetails.value.split('\n').filter(l => !l.startsWith('[CORRECTION_FILE]') && !l.startsWith('[ATTACH_RESULT]') && !l.startsWith('[UNMATCHED] ')).slice(0,200).join('\n') : '')

function applyTaskStatus(data) {
  status.value = data.status
  if (data.totalRows != null) totalRows.value = data.totalRows
  if (data.validRows != null) validRows.value = data.validRows
  if (data.invalidRows != null) invalidRows.value = data.invalidRows
  if (data.importedRows != null) importedRows.value = data.importedRows
  if (data.attachedCount != null) attachedCount.value = data.attachedCount
  if (data.unmatchedFiles) unmatchedFiles.value = data.unmatchedFiles
  if (data.hasCorrectionFile != null) hasCorrectionFile.value = data.hasCorrectionFile
  if (data.correctionFileUrl) correctionFileUrl.value = data.correctionFileUrl
  if (data.errorDetails) errorDetails.value = data.errorDetails
  if (data.failureReason) failureReason.value = data.failureReason
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const startPolling = () => {
  stopPolling()
  pollTimer = setInterval(async () => {
    if (!taskId.value) return
    try {
      const { data } = await http.get(`/api/knowledge/warehouses/import/tasks/${taskId.value}`)
      applyTaskStatus(data)
      if (data.status === 'COMPLETED' || data.status === 'FAILED') {
        stopPolling()
        if (data.status === 'COMPLETED' && data.importedRows > 0) emit('imported', data.importedRows)
      }
    } catch {
      stopPolling()
    }
  }, 2000)
}

const handleDownloadCorrection = async () => {
  if (!correctionFileUrl.value) return
  try {
    const response = await http.get(correctionFileUrl.value, { responseType: 'blob' })
    const blob = response.data
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const ts = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    a.download = `仓库信息导入_${ts}.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch (err) {
    ElMessage.error('下载修正文件失败')
  }
}

const handleExcelChange = (uploadFile) => {
  if (uploadFile && uploadFile.raw) excelFile.value = uploadFile.raw
}

const handleExcelExceed = () => {
  ElMessage.warning('只能选择 1 个 Excel 文件，请先移除旧的')
}

const handleAttachChange = (uploadFile) => {
  if (uploadFile && uploadFile.raw) {
    attachFiles.value.push(uploadFile.raw)
  }
}

const handleAttachExceed = () => {
  ElMessage.warning('附件超过 100 个上限')
}

const handleAttachRemove = (uploadFile) => {
  if (uploadFile && uploadFile.raw) {
    attachFiles.value = attachFiles.value.filter(f => f !== uploadFile.raw)
  }
}

const startImport = async () => {
  if (!excelFile.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  submitting.value = true
  try {
    const formData = new FormData()
    formData.append('file', excelFile.value)
    attachFiles.value.forEach(f => formData.append('attachments', f))
    const { data } = await http.post('/api/knowledge/warehouses/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    taskId.value = data.taskId
    status.value = 'PENDING'
    errorDetails.value = ''
    failureReason.value = ''
    totalRows.value = 0
    validRows.value = 0
    invalidRows.value = 0
    importedRows.value = 0
    startPolling()
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '提交导入任务失败')
  } finally {
    submitting.value = false
  }
}

const handleRetry = () => {
  taskId.value = null
  status.value = ''
  errorDetails.value = ''
  failureReason.value = ''
}

const resetForm = () => {
  excelFile.value = null
  attachFiles.value = []
  taskId.value = null
  status.value = ''
  errorDetails.value = ''
  failureReason.value = ''
  totalRows.value = 0
  validRows.value = 0
  invalidRows.value = 0
  importedRows.value = 0
  if (excelUploadRef.value) excelUploadRef.value.clearFiles()
  if (attachUploadRef.value) attachUploadRef.value.clearFiles()
}

const handleClose = () => {
  stopPolling()
  resetForm()
  emit('update:modelValue', false)
}

watch(() => props.modelValue, (v) => {
  if (v) resetForm()
  else stopPolling()
})

onUnmounted(stopPolling)
</script>

<style scoped>
.import-init { padding: 4px 0; }
.upload-icon { font-size: 48px; color: var(--el-color-primary); }
.import-progress { padding: 24px 0; text-align: center; }
.status-text { margin-top: 12px; color: var(--el-text-color-secondary); font-size: 14px; }
.import-done, .import-failed { padding: 8px 0; }
.error-details { margin-top: 12px; padding: 12px; background: #fdf6ec; border-radius: 6px; }
.error-title { font-weight: 600; color: #b88230; margin-bottom: 8px; }
.error-details pre { font-size: 12px; line-height: 1.6; max-height: 200px; overflow-y: auto; margin: 0; white-space: pre-wrap; word-break: break-all; }
.report-block { margin-top: 16px; padding: 12px; background: #f5f7fa; border-radius: 6px; }
.block-title { font-weight: 600; color: #303133; margin-bottom: 8px; }
.block-stats { display: flex; gap: 8px; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 8px; }
:deep(.el-form-item__label) { color: var(--el-text-color-regular); }
</style>
