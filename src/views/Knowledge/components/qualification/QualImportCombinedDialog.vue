<template>
  <el-dialog
    v-model="visible"
    title="导入台账与附件"
    width="600px"
    :close-on-click-modal="false"
    :append-to-body="false"
    @closed="handleClosed"
  >
    <template v-if="!result">
      <!-- Excel upload area -->
      <div class="upload-section">
        <p class="section-title"><span class="step-badge">1</span> Excel 台账文件 <span class="required">*</span></p>
        <el-upload
          class="upload-area"
          drag
          :auto-upload="false"
          :limit="1"
          :on-change="handleExcelChange"
          :on-remove="handleExcelRemove"
          :file-list="excelFiles"
          accept=".xlsx,.xls"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
        </el-upload>
        <p v-if="!excelFiles.length" class="upload-hint">
          <el-button link type="primary" size="small" @click.stop="downloadTemplate">下载导入模板</el-button>
          了解 Excel 格式
        </p>
      </div>

      <!-- Attachment upload area -->
      <div class="upload-section">
        <p class="section-title"><span class="step-badge">2</span> 证书附件 <span class="optional">选填</span></p>
        <el-upload
          class="upload-area"
          drag
          multiple
          :auto-upload="false"
          :on-change="handleAttachChange"
          :file-list="attachFiles"
          accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png,.zip"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
        </el-upload>
        <div class="attach-naming-guide">
          <p class="guide-title"><el-icon><InfoFilled /></el-icon> 文件命名规范</p>
          <p class="guide-format"><code>QUAL_{证书编号}_{序号}_{文件名}.{扩展名}</code></p>
          <p class="guide-example">示例：<code>QUAL_QC-2023-08812_01_ISO认证.pdf</code></p>
          <p class="guide-zip">支持上传 <code>.zip</code> 压缩包，包内文件按上述规范命名，系统自动解压匹配</p>
        </div>
      </div>
    </template>

    <template v-else>
      <!-- Import result -->
      <div class="result-section">
        <h4 class="result-heading">台账导入</h4>
        <div class="result-summary">
          <div class="result-stat">
            <span class="stat-num">{{ result.import.total }}</span>
            <span class="stat-label">总条数</span>
          </div>
          <div class="result-stat success">
            <span class="stat-num">{{ result.import.success }}</span>
            <span class="stat-label">成功</span>
          </div>
          <div class="result-stat failed" v-if="result.import.failed > 0">
            <span class="stat-num">{{ result.import.failed }}</span>
            <span class="stat-label">失败</span>
          </div>
        </div>
        <div v-if="result.import.errors && result.import.errors.length" class="error-table-wrapper">
          <el-table :data="result.import.errors" size="small" max-height="200">
            <el-table-column prop="row" label="行号" width="60" />
            <el-table-column prop="certificateNo" label="证书编号" width="140" />
            <el-table-column prop="reason" label="失败原因" />
          </el-table>
        </div>
        <div v-if="result.import.success === result.import.total" class="all-success">
          <el-icon color="var(--el-color-success)"><CircleCheckFilled /></el-icon> 全部导入成功
        </div>
      </div>

      <!-- Attachment result (only if attachments were provided) -->
      <div v-if="result.attachments" class="result-section">
        <h4 class="result-heading">附件关联</h4>
        <div class="result-summary">
          <div class="result-stat">
            <span class="stat-num">{{ result.attachments.total }}</span>
            <span class="stat-label">总文件</span>
          </div>
          <div class="result-stat success">
            <span class="stat-num">{{ result.attachments.success }}</span>
            <span class="stat-label">已匹配</span>
          </div>
          <div class="result-stat failed" v-if="result.attachments.failed > 0">
            <span class="stat-num">{{ result.attachments.failed }}</span>
            <span class="stat-label">未匹配</span>
          </div>
        </div>
        <div v-if="result.attachments.unmatched && result.attachments.unmatched.length" class="unmatched-list">
          <p class="section-title">未匹配文件</p>
          <ul>
            <li v-for="(item, i) in result.attachments.unmatched" :key="i">{{ item.fileName || item }}</li>
          </ul>
        </div>
        <div v-if="result.attachments.success === result.attachments.total" class="all-success">
          <el-icon color="var(--el-color-success)"><CircleCheckFilled /></el-icon> 全部附件关联成功
        </div>
      </div>
    </template>

    <template #footer>
      <template v-if="!result">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!excelFiles.length" @click="handleSubmit">
          开始导入
        </el-button>
      </template>
      <template v-else>
        <el-button @click="handleDone">完成</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, InfoFilled, CircleCheckFilled } from '@element-plus/icons-vue'
import http from '@/api/client'

const props = defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue', 'closed'])

const visible = ref(props.modelValue)
watch(() => props.modelValue, v => { visible.value = v })
watch(visible, v => { emit('update:modelValue', v) })

const excelFiles = ref([])
const attachFiles = ref([])
const submitting = ref(false)
const result = ref(null)

const handleExcelChange = (file) => { excelFiles.value = [file] }
const handleExcelRemove = () => { excelFiles.value = [] }

const handleAttachChange = (_file, files) => { attachFiles.value = files.map(f => f) }

const downloadTemplate = async () => {
  try {
    const resp = await http.get('/api/knowledge/qualifications/template', { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([resp.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = '资质证书导入模板.xlsx'
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  } catch { ElMessage.warning('模板下载失败，请稍后重试') }
}

const handleSubmit = async () => {
  if (!excelFiles.value.length) return
  submitting.value = true
  try {
    const formData = new FormData()
    formData.append('file', excelFiles.value[0].raw)
    if (attachFiles.value.length) {
      attachFiles.value.forEach(f => formData.append('attachments', f.raw))
    }
    const res = await http.post('/api/knowledge/qualifications/import-combined', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const data = res?.data?.data || {}
    result.value = {
      import: {
        total: data.import?.total || 0,
        success: data.import?.success || 0,
        failed: data.import?.failed || 0,
        errors: data.import?.errors || []
      },
      attachments: data.attachments || null
    }
    emit('closed')
  } catch {
    ElMessage.error('导入失败，请检查网络后重试')
  } finally { submitting.value = false }
}

const handleDone = () => { visible.value = false }

const handleClosed = () => {
  excelFiles.value = []
  attachFiles.value = []
  result.value = null
}
</script>

<style scoped>
.upload-section { margin-bottom: 20px; }
.section-title { font-weight: 600; margin: 0 0 10px; color: var(--gray-900); display: flex; align-items: center; gap: 8px; }
.step-badge { display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px; border-radius: 50%; background: var(--el-color-primary); color: var(--el-bg-color); font-size: 12px; font-weight: 700; }
.required { color: var(--el-color-danger); }
.optional { color: var(--el-text-color-secondary); font-weight: 400; font-size: 12px; }
.upload-area { margin-bottom: 8px; }
.upload-hint { font-size: 12px; color: var(--el-text-color-secondary); }

.attach-naming-guide {
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 8px;
  padding: 12px 14px;
}
.guide-title { display: flex; align-items: center; gap: 6px; font-weight: 600; color: var(--el-color-primary); margin: 0 0 6px; font-size: 13px; }
.guide-format { margin: 0 0 4px; }
.guide-format code, .guide-example code { background: var(--el-bg-color); padding: 2px 6px; border-radius: 4px; font-size: 12px; color: var(--gray-750); }
.guide-example { margin: 0; font-size: 12px; color: var(--gray-550); }
.guide-zip { margin: 6px 0 0; font-size: 12px; color: var(--gray-550); }

.result-section { margin-bottom: 24px; }
.result-heading { margin: 0 0 12px; font-weight: 600; color: var(--gray-900); }
.result-summary { display: flex; gap: 24px; justify-content: center; padding: 12px 0; }
.result-stat { text-align: center; }
.stat-num { display: block; font-size: 32px; font-weight: 700; color: var(--gray-750); }
.result-stat.success .stat-num { color: var(--el-color-success); }
.result-stat.failed .stat-num { color: var(--el-color-danger); }
.stat-label { font-size: 13px; color: var(--gray-550); }
.error-table-wrapper { margin-top: 12px; }
.all-success { display: flex; align-items: center; gap: 6px; justify-content: center; margin-top: 12px; font-weight: 600; color: var(--el-color-success); }
.unmatched-list { margin-top: 12px; padding: 10px 14px; background: var(--el-color-danger-light-9); border-radius: 6px; }
.unmatched-list .section-title { font-weight: 600; margin: 0 0 6px; color: var(--el-color-danger); font-size: 13px; }
.unmatched-list ul { margin: 0; padding-left: 18px; }
.unmatched-list li { font-size: 13px; color: var(--gray-650); line-height: 1.8; }
</style>
