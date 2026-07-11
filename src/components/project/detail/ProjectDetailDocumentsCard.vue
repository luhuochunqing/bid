<template>
  <el-card class="document-card">
    <template #header>
      <div class="card-title">
        <el-icon><Folder /></el-icon>
        <span>项目文档</span>
        <el-button v-if="detail.canManageProjectDocuments.value" link type="success" :icon="DocumentChecked" @click="detail.handleArchiveDocuments">归档资料</el-button>
        <el-upload v-if="detail.canManageProjectDocuments.value" :show-file-list="false" :before-upload="detail.handleUpload" accept=".doc,.docx,.pdf,.xls,.xlsx">
          <el-button link type="primary" :icon="Upload">上传文档</el-button>
        </el-upload>
      </div>
    </template>

    <el-table :data="detail.project.value?.documents || []" style="width: 100%">
      <el-table-column prop="name" label="文档名称" min-width="200">
        <template #default="{ row }">
          <div class="file-name"><el-icon><Document /></el-icon><span>{{ row.name }}</span></div>
        </template>
      </el-table-column>
      <el-table-column prop="uploader" label="上传者" width="120" />
      <el-table-column prop="time" label="上传时间" width="160" />
      <el-table-column prop="size" label="文件大小" width="100" />
      <!-- 下载+删除：width 120 边界易折，加宽并 nowrap 强制单行 -->
      <el-table-column label="操作" width="140" class-name="ops-col">
        <template #default="{ row }">
          <div class="ops-actions">
            <el-button link type="primary" @click="detail.handleDownload(row)">下载</el-button>
            <el-button link type="danger" @click="detail.handleDeleteDoc(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!detail.project.value?.documents?.length" description="暂无文档" />
  </el-card>
</template>

<script setup>
import { inject } from 'vue'
import { Document, DocumentChecked, Folder, Upload } from '@element-plus/icons-vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

const detail = inject(projectDetailKey)
</script>

<style scoped>
/* 操作列：下载/删除 强制单行 */
.ops-actions {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  white-space: nowrap;
  gap: 0;
}

.ops-actions :deep(.el-button + .el-button) {
  margin-left: 4px;
}

:deep(.ops-col > .cell) {
  white-space: nowrap;
  overflow: visible;
}
</style>
