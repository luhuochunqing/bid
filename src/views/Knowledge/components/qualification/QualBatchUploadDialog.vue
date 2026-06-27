<template>
  <el-dialog
    v-model="visible"
    title="批量上传附件"
    width="560px"
    :close-on-click-modal="false"
    :append-to-body="false"
    @closed="handleClosed"
  >
    <template v-if="!result">
      <div class="naming-guide">
        <p class="guide-title"><el-icon><InfoFilled /></el-icon> 文件命名规范</p>
        <p class="guide-format"><code>QUAL_{证书编号}_{序号}_{文件名}.{扩展名}</code></p>
        <p class="guide-example">示例：<code>QUAL_QC-2023-08812_01_ISO认证.pdf</code></p>
        <p class="guide-zip">支持上传 <code>.zip</code> 压缩包，包内文件按上述规范命名，系统自动解压匹配。</p>
      </div>
      <el-upload
        class="batch-upload-area"
        drag
        multiple
        :auto-upload="false"
        :on-change="handleFileChange"
        accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png,.zip,.rar"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
      </el-upload>
      <div v-if="fileList.length" class="file-preview">
        <p class="file-count">已选择 {{ fileList.length }} 个文件</p>
      </div>
    </template>

    <template v-else>
      <div class="result-summary">
        <div class="result-stat">
          <span class="stat-num">{{ result.total }}</span>
          <span class="stat-label">文件总数</span>
        </div>
        <div class="result-stat success">
          <span class="stat-num">{{ result.success }}</span>
          <span class="stat-label">匹配成功</span>
        </div>
        <div class="result-stat failed" v-if="result.failed > 0">
          <span class="stat-num">{{ result.failed }}</span>
          <span class="stat-label">未匹配</span>
        </div>
      </div>
      <div v-if="result.unmatched && result.unmatched.length" class="unmatched-list">
        <p class="section-title">未匹配文件</p>
        <ul>
          <li v-for="(item, i) in result.unmatched" :key="i">
            {{ item.fileName || item }}
            <span v-if="item.reason" class="unmatched-reason">（{{ item.reason }}）</span>
          </li>
        </ul>
      </div>
    </template>

    <template #footer>
      <template v-if="!result">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!fileList.length" @click="handleUpload">开始上传</el-button>
      </template>
      <template v-else>
        <el-button type="primary" @click="handleDone">完成</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, InfoFilled } from '@element-plus/icons-vue'
import http from '@/api/client'

const props = defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue', 'uploaded', 'closed'])

const visible = ref(props.modelValue)
watch(() => props.modelValue, v => { visible.value = v })
watch(visible, v => { emit('update:modelValue', v) })

const fileList = ref([])
const uploading = ref(false)
const result = ref(null)

const handleFileChange = (_file, files) => { fileList.value = files.map(f => f) }

const handleUpload = async () => {
  if (!fileList.value.length) return
  uploading.value = true
  try {
    const formData = new FormData()
    fileList.value.forEach(f => formData.append('files', f.raw))
    const res = await http.post('/api/knowledge/qualifications/batch-attach', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const data = res?.data || {}
    result.value = {
      total: data.total || fileList.value.length,
      success: data.success || 0,
      failed: data.failed || 0,
      unmatched: data.unmatched || []
    }
    emit('uploaded')
  } catch {
    ElMessage.error('上传失败，请检查网络后重试')
  } finally { uploading.value = false }
}

const handleDone = () => { visible.value = false }
const handleClosed = () => { fileList.value = []; result.value = null; emit('closed') }
</script>

<style scoped>
.naming-guide {
  background: var(--el-color-primary-light-9, #ecf5ff);
  border: 1px solid var(--el-color-primary-light-5, #a0cfff);
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 16px;
}
.guide-title { display: flex; align-items: center; gap: 6px; font-weight: 600; color: var(--el-color-primary); margin: 0 0 8px; }
.guide-format { margin: 0 0 4px; }
.guide-format code, .guide-example code { background: #fff; padding: 2px 6px; border-radius: 4px; font-size: 12px; color: var(--gray-750); }
.guide-example { margin: 0; font-size: 12px; color: var(--gray-550); }
.guide-zip { margin: 8px 0 0; font-size: 12px; color: var(--gray-550); }
.batch-upload-area { margin-bottom: 12px; }
.file-preview { background: var(--el-fill-color-light); border-radius: 6px; padding: 12px 16px; }
.file-count { font-weight: 600; margin: 0; }
.result-summary { display: flex; gap: 24px; justify-content: center; padding: 16px 0; }
.result-stat { text-align: center; }
.stat-num { display: block; font-size: 32px; font-weight: 700; color: var(--gray-750); }
.result-stat.success .stat-num { color: var(--el-color-success); }
.result-stat.failed .stat-num { color: var(--el-color-danger); }
.stat-label { font-size: 13px; color: var(--gray-550); }
.unmatched-list { margin-top: 16px; padding: 12px 16px; background: var(--el-color-danger-light-9); border-radius: 6px; }
.section-title { font-weight: 600; margin: 0 0 8px; color: var(--el-color-danger); }
.unmatched-list ul { margin: 0; padding-left: 18px; }
.unmatched-list li { font-size: 13px; color: var(--gray-650); line-height: 1.8; }
.unmatched-reason { color: var(--el-color-danger); margin-left: 4px; }
</style>
