<template>
  <div>
    <div class="section-title flex justify-between align-center mt-6">
      <span>文件清单 <span class="file-count">共{{ files.length }}份</span></span>
      <div class="flex gap-2">
        <el-input v-model="localKeyword" placeholder="搜索文件名称..." prefix-icon="Search" clearable style="width: 220px" />
        <el-button type="success" @click="$emit('download-package')">下载文件包</el-button>
      </div>
    </div>
    <el-table :data="displayFiles" border stripe class="mt-2 archive-file-table">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="fileName" label="文件名" min-width="180">
        <template #default="{ row }">
          <div class="file-name-cell">
            <el-icon class="file-icon" :class="getFileIconClass(row.fileName)">
              <Document />
            </el-icon>
            <span class="file-name-text">{{ row.fileName || '未命名归档文件' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="文档分类" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="getDocumentCategoryTagType(row.category)">{{ getDocumentCategoryLabel(row.category) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="uploadUser" label="上传人" width="90" align="center" show-overflow-tooltip>
        <template #default="{ row }">{{ row.uploadUser || '-' }}</template>
      </el-table-column>
      <el-table-column prop="uploadedAt" label="上传时间" width="150" align="center">
        <template #default="{ row }">{{ formatDateTime(row.uploadedAt) }}</template>
      </el-table-column>
      <el-table-column prop="fileSize" label="大小" width="80" align="center">
        <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center" fixed="right">
        <template #default="{ row }">
          <div class="action-row">
            <el-button type="primary" link size="small" @click="$emit('preview', row)">预览</el-button>
            <el-button type="success" link size="small" @click="$emit('download', row)">下载</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Document } from '@element-plus/icons-vue'
import { formatDateTime, getFileIconClass, getDocumentCategoryLabel, getDocumentCategoryTagType, formatFileSize } from '../archiveLabels.js'

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

</script>

<style scoped lang="scss">
.file-count {
  font-size: 13px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
  margin-left: 8px;
}
.action-row { display: flex; justify-content: center; align-items: center; gap: 4px; flex-wrap: nowrap; }

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.file-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.archive-file-table {
  :deep(.el-table__cell) {
    padding: 6px 8px;
  }
  :deep(.el-table th.el-table__cell) {
    padding: 8px;
  }
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
