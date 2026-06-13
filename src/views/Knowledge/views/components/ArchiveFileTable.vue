<template>
  <div>
    <div class="section-title flex justify-between align-center mt-6">
      <span>文件清单 <span class="file-count">共{{ files.length }}份</span></span>
      <div class="flex gap-2">
        <el-input v-model="localKeyword" placeholder="搜索文件名称..." prefix-icon="Search" clearable style="width: 220px" />
        <el-button type="success" @click="$emit('download-package')">下载文件包</el-button>
      </div>
    </div>
    <el-table :data="displayFiles" border stripe class="mt-2">
      <el-table-column type="index" label="序号" width="110" align="center" />
      <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="file-name-cell">
            <el-icon class="file-icon" :class="getFileIconClass(row.fileName)">
              <Document />
            </el-icon>
            <span>{{ row.fileName || '未命名归档文件' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="文档分类" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="getCategoryTagType(row.category)">{{ getCategoryLabel(row.category) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="uploadUser" label="上传人" width="120" align="center" />
      <el-table-column prop="uploadedAt" label="上传时间" width="160" align="center">
        <template #default="{ row }">{{ formatDateTime(row.uploadedAt) }}</template>
      </el-table-column>
      <el-table-column prop="fileSize" label="文件大小" width="100" align="center">
        <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="$emit('preview', row)">预览</el-button>
          <el-button type="success" link @click="$emit('download', row)">下载</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Document } from '@element-plus/icons-vue'
import { formatDateTime, getFileIconClass } from '../archiveLabels.js'

const props = defineProps({
  files: { type: Array, default: () => [] }
})

defineEmits(['preview', 'download', 'download-package'])

const localKeyword = ref('')

const displayFiles = computed(() => {
  let list = [...props.files]
  if (localKeyword.value.trim()) {
    const kw = localKeyword.value.trim().toLowerCase()
    list = list.filter((f) => f.fileName && f.fileName.toLowerCase().includes(kw))
  }
  return list.sort((a, b) => new Date(b.uploadedAt) - new Date(a.uploadedAt))
})

const getCategoryLabel = (cat) => {
  const map = { TENDER: '招标文件', BID: '标书文件', OPEN_LIST: '开标一览表', WIN_NOTICE: '中标通知书', DEPOSIT_RECEIPT: '保证金银行回单', OTHER: '其他' }
  return map[cat] || cat || '其他'
}

const getCategoryTagType = (cat) => {
  const map = { TENDER: 'primary', BID: 'success', OPEN_LIST: 'warning', WIN_NOTICE: 'success', DEPOSIT_RECEIPT: 'info', OTHER: 'info' }
  return map[cat] || 'info'
}

const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '-'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + 'MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(1) + 'GB'
}
</script>

<style scoped lang="scss">
.file-count {
  font-size: 13px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
  margin-left: 8px;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-icon {
  font-size: 16px;
}

.file-icon.icon-pdf { color: var(--el-color-danger); }
.file-icon.icon-word { color: var(--el-color-primary); }
.file-icon.icon-excel { color: var(--el-color-success); }
.file-icon.icon-default { color: var(--el-color-info); }

.mt-2 { margin-top: 8px; }
.flex { display: flex; }
.gap-2 { gap: 8px; }
.justify-between { justify-content: space-between; }
.align-center { align-items: center; }
</style>
