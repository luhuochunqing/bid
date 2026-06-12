<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="批量关联证书附件"
    width="640px"
    :close-on-click-modal="false"
    @close="resetAll"
  >
    <div v-if="!attachResults">
      <el-upload
        multiple
        :auto-upload="false"
        accept=".pdf,.jpg,.jpeg,.png"
        :on-change="onAttachFilesChange"
        :before-upload="beforeAttachUpload"
        :show-file-list="true"
        :file-list="attachFileList"
      >
        <el-button type="primary" plain>选择附件文件</el-button>
        <template #tip>
          <div class="el-upload__tip">
            文件命名规范：PER_姓名_工号_序号_证书名.扩展名<br/>
            示例：PER_张三_EMP001_01_一级建造师.pdf
          </div>
        </template>
      </el-upload>
    </div>

    <div v-else class="attach-results">
      <el-result
        :icon="attachResults.failedCount === 0 ? 'success' : 'warning'"
        :title="attachResults.failedCount === 0 ? '关联完成' : '关联完成（含未匹配文件）'"
        :sub-title="`成功关联 ${attachResults.successCount} 个文件，${attachResults.failedCount} 个未匹配`"
      >
        <template #extra>
          <div v-if="attachResults.unmatchedFiles && attachResults.unmatchedFiles.length > 0" class="unmatched-list">
            <div v-for="(f, i) in attachResults.unmatchedFiles" :key="i" class="unmatched-item">
              <el-icon><Warning /></el-icon>
              <span class="unmatched-name">{{ f.fileName }}</span>
              <span class="unmatched-reason">{{ f.reason }}</span>
            </div>
          </div>
          <el-button type="primary" @click="resetAll">继续上传</el-button>
        </template>
      </el-result>
    </div>

    <template #footer v-if="!attachResults">
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :disabled="attachFileList.length === 0" :loading="attaching" @click="handleBatchAttach">
        开始关联 ({{ attachFileList.length }} 个文件)
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Warning } from '@element-plus/icons-vue'
import personnelBatchApi from '@/api/modules/personnelBatchApi.js'

defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue', 'attached'])

const attachFileList = ref([])
const attachResults = ref(null)
const attaching = ref(false)

function resetAll() {
  attachFileList.value = []
  attachResults.value = null
  attaching.value = false
}

function onAttachFilesChange(_file, fileList) {
  attachFileList.value = fileList
}

function beforeAttachUpload(file) {
  const okType = ['application/pdf', 'image/jpeg', 'image/png'].includes(file.type)
  const okSize = file.size / 1024 / 1024 < 10
  if (!okType) { ElMessage.error('仅支持 PDF/JPG/PNG'); return false }
  if (!okSize) { ElMessage.error('附件不能超过10MB'); return false }
  return true
}

async function handleBatchAttach() {
  if (attachFileList.value.length === 0) return
  attaching.value = true
  try {
    const files = attachFileList.value.map(f => f.raw).filter(Boolean)
    const res = await personnelBatchApi.batchAttachAttachments(files)
    attachResults.value = res?.data || { successCount: 0, failedCount: 0, unmatchedFiles: [] }
    ElMessage.success(`关联完成：成功 ${attachResults.value.successCount} 个，未匹配 ${attachResults.value.failedCount} 个`)
    emit('attached')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '批量关联失败')
  } finally {
    attaching.value = false
  }
}
</script>

<style scoped>
.attach-results { padding: 20px 0; }
.unmatched-list { text-align: left; margin-top: 16px; max-height: 300px; overflow-y: auto; }
.unmatched-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 4px; background: var(--el-color-danger-light-9); margin-bottom: 4px; font-size: 13px; }
.unmatched-name { font-weight: 500; color: var(--el-color-danger); }
.unmatched-reason { color: var(--el-text-color-secondary); margin-left: auto; }
</style>
